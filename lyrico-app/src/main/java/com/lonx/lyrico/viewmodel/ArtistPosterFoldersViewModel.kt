package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel

import android.app.Application
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import com.lonx.lyrico.R
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.utils.SafDocuments
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.utils.coil.ArtistPosterMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ArtistPosterFile(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val artistId: Long? = null,
    val artistName: String? = null
)

data class ArtistPosterFolder(
    val uri: String,
    val path: String,
    val posters: List<ArtistPosterFile>?,
    /** How many posters in this folder are named after an artist of the library. */
    val matchedPosterCount: Int = 0,
    val name: String = path.substringAfterLast('/').ifBlank { path }
)

data class ArtistPosterFoldersUiState(
    val folder: ArtistPosterFolder? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val revision: Long = 0L,
    val isEditing: Boolean = false,
    val operationMessage: String? = null
)

class ArtistPosterFoldersViewModel(
    private val settings: SettingsRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val application: Application
) : ViewModel() {
    private val _uiState = MutableStateFlow(ArtistPosterFoldersUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.artistPosterFolder,
                settings.artistPosterRevision,
                libraryIndexRepository.observeArtists()
            ) { folder, revision, artists -> Triple(folder, revision, artists) }
                .collectLatest { (folder, revision, artists) ->
                    _uiState.update { it.copy(isLoading = true) }
                    val artistNames = artists.map { it.name }
                    val entry = withContext(Dispatchers.IO) {
                        folder?.let {
                            val uri = folder.toUri()
                            val posters = readPosters(uri, artists)
                            ArtistPosterFolder(
                                uri = folder,
                                path = UriUtils.getFileAbsolutePath(application, uri)
                                    ?: Uri.decode(uri.lastPathSegment ?: folder),
                                posters = posters,
                                matchedPosterCount = countMatchedPosters(posters, artistNames)
                            )
                        }
                    }
                    _uiState.update { it.copy(folder = entry, isLoading = false, revision = revision) }
                }
        }
    }

    /**
     * 海报按文件名匹配；整文件夹都对不上库内艺术家时需要说明，而不是静默显示空。
     */
    private fun countMatchedPosters(posters: List<ArtistPosterFile>?, artistNames: List<String>): Int =
        ArtistPosterMatcher.countMatchedFiles(posters.orEmpty().map { it.name }, artistNames)

    /** `null` when the folder cannot be queried any more (deleted or access revoked). */
    private fun readPosters(uri: Uri, artists: List<ArtistEntity>): List<ArtistPosterFile>? {
        return SafDocuments.children(application, uri)
            ?.filterNot { it.isDirectory }
            ?.filter { ArtistPosterMatcher.isPosterFile(it.displayName) }
            ?.map { file ->
                val matches = artists.mapNotNull { artist ->
                    ArtistPosterMatcher.rank(file.displayName, artist.name)?.let { it to artist }
                }
                val bestRank = matches.minOfOrNull { it.first }
                val owner = matches.filter { it.first == bestRank }.singleOrNull()?.second
                ArtistPosterFile(
                    uri = file.uri.toString(), name = file.displayName,
                    lastModified = file.lastModified, artistId = owner?.id, artistName = owner?.name
                )
            }
            ?.sortedBy { it.name.lowercase() }
    }

    private fun update(operation: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { action() }
                _uiState.update { it.copy(error = false) }
                AppLog.record(AppLogType.METADATA, "PosterFolders", operation,
                    "folder=${_uiState.value.folder?.uri}", level = AppLogLevel.INFO)
            } catch (e: CancellationException) {
                throw e
            } catch (error: Exception) {
                AppLog.record(AppLogType.METADATA, "PosterFolders", "$operation failed",
                    "folder=${_uiState.value.folder?.uri}", level = AppLogLevel.ERROR, error = error)
                _uiState.update { it.copy(error = true) }
            }
        }
    }

    fun addFolder(uri: Uri) = update("Register poster folder") {
        settings.setArtistPosterFolder(uri.toString())
        settings.refreshArtistPosters()
    }

    fun removeFolder() = update("Remove poster folder registration") {
        // Grants can be shared with the music library; removing a poster folder only removes its registration.
        settings.clearArtistPosterFolder()
    }

    fun refresh() = update("Refresh poster folder") { settings.refreshArtistPosters() }

    fun clearOperationMessage() { _uiState.update { it.copy(operationMessage = null) } }

    private fun editFile(file: ArtistPosterFile, operation: String, onSuccess: () -> Unit = {}, action: (Uri, List<com.lonx.lyrico.utils.SafDocumentRow>) -> Unit) {
        if (_uiState.value.isEditing) return
        val folder = _uiState.value.folder?.uri ?: return
        _uiState.update { it.copy(isEditing = true, operationMessage = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val tree = folder.toUri()
                    val children = SafDocuments.children(application, tree) ?: error("Folder unavailable")
                    check(children.any { it.uri.toString() == file.uri && it.displayName == file.name }) { "File changed" }
                    action(tree, children)
                }
                onSuccess()
                settings.refreshArtistPosters()
                AppLog.record(AppLogType.METADATA, "PosterFiles", "$operation completed",
                    "folder=$folder\nuri=${file.uri}\nname=${file.name}", file.uri, AppLogLevel.INFO)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                AppLog.record(AppLogType.METADATA, "PosterFiles", "$operation failed",
                    "folder=$folder\nuri=${file.uri}\nname=${file.name}", file.uri, AppLogLevel.ERROR, e)
                _uiState.update { it.copy(operationMessage = application.getString(R.string.poster_operation_failed)) }
            } finally {
                _uiState.update { it.copy(isEditing = false) }
            }
        }
    }

    fun deleteFile(file: ArtistPosterFile) = editFile(file, "Delete poster") { _, _ ->
        check(DocumentsContract.deleteDocument(application.contentResolver, file.uri.toUri()))
    }

    fun renameFile(file: ArtistPosterFile, stem: String, onRenamed: (ArtistPosterFile) -> Unit = {}) {
        var renamed: ArtistPosterFile? = null
        editFile(file, "Rename poster", onSuccess = {
            renamed?.let { replacement ->
                onRenamed(replacement)
                _uiState.update { state -> state.copy(folder = state.folder?.let { folder ->
                    folder.copy(posters = folder.posters?.map { if (it.uri == file.uri) replacement else it })
                }) }
            }
        }) { _, children ->
            val name = com.lonx.lyrico.domain.poster.PosterFileEditNames.rename(
                file.name, stem, children.filter { it.uri.toString() != file.uri }.map { it.displayName }
            ) ?: error("Invalid or duplicate name")
            if (name != file.name) {
                val uri = checkNotNull(DocumentsContract.renameDocument(application.contentResolver, file.uri.toUri(), name))
                renamed = file.copy(uri = uri.toString(), name = name, artistId = null, artistName = null)
            }
        }
    }

    fun readBitmap(file: ArtistPosterFile, onReady: (Bitmap) -> Unit) {
        if (_uiState.value.isEditing) return
        _uiState.update { it.copy(isEditing = true, operationMessage = null) }
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(file.uri.toUri())?.use { BitmapFactory.decodeStream(it) }
                } ?: error("Cannot decode image")
                onReady(bitmap)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(operationMessage = application.getString(R.string.msg_read_artist_image_failed)) }
            } finally { _uiState.update { it.copy(isEditing = false) } }
        }
    }

    fun cropFile(file: ArtistPosterFile, bitmap: Bitmap) = editFile(file, "Crop poster") { tree, _ ->
        com.lonx.lyrico.utils.ArtistPosterFileWriter.replaceFile(application, tree, file.uri.toUri(), file.name, bitmap)
    }

}
