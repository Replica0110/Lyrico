package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterBatchEdit
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import com.lonx.lyrico.domain.poster.ArtistPosterEdits
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.ArtistPosterFileWriter
import com.lonx.lyrico.utils.SafDocumentRow
import com.lonx.lyrico.utils.SafDocuments
import com.lonx.lyrico.domain.poster.ArtistPosterMatcher
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.model.artwork.CoverCandidate
import com.lonx.lyrico.data.model.artwork.CoverRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtistPosterMenuState(
    val loading: Boolean = false,
    val source: ResolvedArtistPoster? = null,
    val error: Boolean = false
)

data class ArtistPosterExportSettings(val folder: String?)

/** One execution path for the list and detail menu, with fixed targets across permission retries. */
class ArtistPosterEditViewModel(
    private val library: LibraryIndexRepository,
    private val tags: AudioTagRepository,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase,
    private val settings: SettingsRepository,
    private val application: Application,
    private val posterReader: ArtistPosterReader
) : ViewModel() {
    private enum class Operation { Remove, Export }
    private data class Plan(
        val config: ArtistSplitConfig,
        val songs: Map<String, SongEntity>,
        val owners: Map<String, Set<String>>,
        val artists: Map<String, List<SongEntity>>,
        val folder: String?,
        val files: List<SafDocumentRow>,
        val folderUnavailable: Boolean
    )
    private val _state = MutableStateFlow(ArtistPosterEmbedUiState())
    val state = _state.asStateFlow()
    private val _titleRes = MutableStateFlow(R.string.poster_remove_artwork)
    val titleRes = _titleRes.asStateFlow()
    val exportSettings = settings.artistPosterFolder
        .map { ArtistPosterExportSettings(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var artistIds = emptySet<Long>()
    private var job: Job? = null
    @Volatile private var generation = 0
    private var pendingPlan: Plan? = null
    private var operation = Operation.Remove
    private val _menu = MutableStateFlow(ArtistPosterMenuState())
    val menu = _menu.asStateFlow()
    @Volatile private var menuGeneration = 0
    private var menuJob: Job? = null
    private var cropTarget: ResolvedArtistPoster? = null
    private var cropBytes: ByteArray? = null
    private var cropPending = false

    fun loadPoster(id: Long, candidates: List<CoverCandidate>) {
        val request = ++menuGeneration
        menuJob?.cancel()
        _menu.value = ArtistPosterMenuState(loading = true)
        menuJob = viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                ArtistPosterMenuState(source = resolvePoster(id, candidates))
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.w("ArtistPosterEdit", "Cannot resolve artist artwork", e)
                ArtistPosterMenuState(error = true)
            }
            _menu.update { if (menuGeneration == request) result else it }
        }
    }

    private suspend fun resolvePoster(id: Long, candidates: List<CoverCandidate>): ResolvedArtistPoster? {
        val artist = library.observeArtistById(id).first() ?: return null
        val folder = settings.artistPosterFolder.first()
        return posterReader.resolve(
            CoverRequest(
                uri = artist.coverSongUri.orEmpty().toUri(),
                lastUpdate = artist.coverSongLastModified,
                pictureType = AudioPictureType.Artist,
                fallbackPictureTypes = listOf(AudioPictureType.LeadArtist, AudioPictureType.Band),
                candidates = candidates,
                artistName = artist.name,
                artistPosterFolders = listOfNotNull(folder)
            ),
            strict = true
        )
    }

    fun cropPoster(target: ResolvedArtistPoster, bitmap: Bitmap) = runCrop(target, bitmap)

    private fun runCrop(target: ResolvedArtistPoster, bitmap: Bitmap?) {
        if (_state.value.isRunning || !target.canCrop) return
        val runGeneration = ++generation
        cropTarget = target
        cropPending = true
        _titleRes.value = R.string.label_crop_artist_image
        _state.value = ArtistPosterEmbedUiState(isRunning = true, total = 1, activeSong = target.artistName)
        job = viewModelScope.launch(Dispatchers.IO) {
            fun updateState(transform: (ArtistPosterEmbedUiState) -> ArtistPosterEmbedUiState) {
                _state.update { if (generation == runGeneration) transform(it) else it }
            }
            try {
                val bytes = if (bitmap != null) java.io.ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    output.toByteArray().also { cropBytes = it }
                } else checkNotNull(cropBytes)
                when (target) {
                    is ResolvedArtistPoster.Embedded -> {
                        val current = tags.read(target.songUri, AudioTagReadOptions(strict = true))
                        check(current.supportsTypedPictures) { "Typed artwork is not supported" }
                        val config = settings.artistSplitConfigFlow.first()
                        val key = ArtistPosterGrouping.ownerKeyOf(target.artistName)
                        val entries = ArtistPosterGrouping.entries(current.pictures, ArtistNameSplitter.splitArtists(current.artist, config))
                            .filter { it.artist != null && it.ownerKey == key }
                        val picture = entries.firstOrNull { it.picture.data.contentEquals(target.bytes) }?.picture
                        if (picture == null) {
                            check(entries.any { it.picture.data.contentEquals(bytes) }) { "Poster changed" }
                        } else {
                            val next = ArtistPosterEdits.replaceData(current.pictures, picture, bytes, "image/png")
                            when (val result = saveAudioTagsUseCase.overwrite(target.songUri, current.copy(pictures = next), picturesAuthored = true)) {
                                is SaveAudioTagsResult.Success -> Unit
                                is SaveAudioTagsResult.Failed -> throw result.error
                                is SaveAudioTagsResult.PermissionRequired -> {
                                    updateState { it.copy(isRunning = false, permissionIntentSender = result.intentSender,
                                        message = application.getString(R.string.msg_artist_poster_permission_required)) }
                                    return@launch
                                }
                            }
                        }
                    }
                    is ResolvedArtistPoster.File -> {
                        val cropped = bitmap ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Cannot decode crop")
                        ArtistPosterFileWriter.replaceFile(application, target.folderUri.toUri(), target.uri.toUri(), target.name, cropped, target.bytes)
                    }
                }
                refreshPosters()
                updateState { it.copy(isRunning = false, done = 1, activeSong = null,
                    message = application.getString(R.string.msg_save_success)) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { refreshPosters() }
                throw e
            } catch (e: Exception) {
                AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Artist artwork crop failed",
                    "artists=$artistIds", level = AppLogLevel.ERROR, error = e)
                updateState { it.copy(isRunning = false, activeSong = null,
                    message = application.getString(R.string.poster_operation_failed)) }
            }
        }
    }

    fun deletePosterFile(target: ResolvedArtistPoster.File) {
        if (_state.value.isRunning) return
        val runGeneration = ++generation
        cropPending = false
        _titleRes.value = R.string.action_delete
        _state.value = ArtistPosterEmbedUiState(isRunning = true, total = 1, activeSong = target.name)
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = SafDocuments.children(application, target.folderUri.toUri()) ?: error("Folder unavailable")
                check(files.any { it.uri.toString() == target.uri && it.displayName == target.name }) { "File changed" }
                check(DocumentsContract.deleteDocument(application.contentResolver, target.uri.toUri()))
                refreshPosters()
                if (generation == runGeneration) _state.update {
                    it.copy(isRunning = false, done = 1, activeSong = null,
                        message = application.getString(R.string.msg_save_success))
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { refreshPosters() }
                throw e
            } catch (e: Exception) {
                AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Poster file delete failed",
                    "uri=${target.uri}\nfolder=${target.folderUri}", target.uri, AppLogLevel.ERROR, e)
                if (generation == runGeneration) _state.update {
                    it.copy(isRunning = false, activeSong = null,
                        message = application.getString(R.string.poster_operation_failed))
                }
            }
        }
    }

    fun start(ids: Set<Long>) = launchOperation(ids, Operation.Remove)

    fun export(ids: Set<Long>) = launchOperation(ids, Operation.Export)

    fun exportToFolder(ids: Set<Long>, folder: Uri) {
        if (_state.value.isRunning || ids.isEmpty()) return
        val runGeneration = ++generation
        _titleRes.value = R.string.poster_export_embedded
        _state.value = ArtistPosterEmbedUiState(isRunning = true)
        job = viewModelScope.launch {
            try {
                settings.setArtistPosterFolder(folder.toString())
                coroutineContext.ensureActive()
                if (generation != runGeneration) return@launch
                _state.value = ArtistPosterEmbedUiState()
                export(ids)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e("ArtistPosterEdit", "Failed to set export folder", e)
                if (generation == runGeneration) _state.value = ArtistPosterEmbedUiState(message = application.getString(R.string.artist_poster_folder_error))
            }
        }
    }

    private fun launchOperation(ids: Set<Long>, action: Operation, resume: Boolean = false) {
        if (_state.value.isRunning || ids.isEmpty()) return
        cropPending = false
        artistIds = ids.toSet()
        operation = action
        if (!resume) pendingPlan = null
        _titleRes.value = when (action) {
            Operation.Remove -> R.string.poster_remove_artwork
            Operation.Export -> R.string.poster_export_embedded
        }
        val runGeneration = ++generation
        _state.value = ArtistPosterEmbedUiState(isRunning = true)
        job = viewModelScope.launch(Dispatchers.IO) {
            fun updateState(transform: (ArtistPosterEmbedUiState) -> ArtistPosterEmbedUiState) {
                _state.update { current -> if (generation == runGeneration) transform(current) else current }
            }
            val failures = mutableListOf<String>()
            var changed = 0
            var unchanged = 0
            try {
                val plan = if (resume) pendingPlan ?: preparePlan(action) else preparePlan(action)
                pendingPlan = plan
                if (action == Operation.Export) {
                    checkNotNull(plan.folder) { "No poster folder" }
                    updateState { it.copy(total = plan.artists.size) }
                    for ((index, artist) in plan.artists.entries.withIndex()) {
                        coroutineContext.ensureActive()
                        updateState { it.copy(done = index, activeSong = artist.key) }
                        try {
                            val picture = posterReader.findEmbeddedPoster(
                                artist.key, artist.value.map { it.uri }, plan.config
                            )?.picture
                            if (picture == null) unchanged++
                            else if (ArtistPosterFileWriter.write(application, artist.key, picture.data, picture.mimeType, plan.folder) != null) changed++
                            else failures += artist.key
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Artist poster export failed",
                                "artist=${artist.key}\nfolder=${plan.folder}", level = AppLogLevel.ERROR, error = e)
                            failures += artist.key
                        }
                        updateState { it.copy(done = index + 1, failures = failures.toList()) }
                    }
                } else {
                    val fileUnits = if (action == Operation.Remove) plan.files.size + if (plan.folderUnavailable) 1 else 0 else 0
                    updateState { it.copy(total = plan.songs.size + fileUnits) }
                    for ((index, song) in plan.songs.values.withIndex()) {
                        coroutineContext.ensureActive()
                        val title = song.title?.takeIf { it.isNotBlank() } ?: song.fileName
                        updateState { it.copy(done = index, activeSong = title) }
                        try {
                            val current = tags.read(song.uri, AudioTagReadOptions(strict = true))
                            val next = ArtistPosterBatchEdit.apply(current.pictures,
                                ArtistNameSplitter.splitArtists(current.artist, plan.config), plan.owners[song.uri].orEmpty(), null)
                            if (next == current.pictures) unchanged++
                            else when (val result = saveAudioTagsUseCase.overwrite(song.uri, current.copy(pictures = next), picturesAuthored = true)) {
                                is SaveAudioTagsResult.Success -> changed++
                                is SaveAudioTagsResult.Failed -> failures += title
                                is SaveAudioTagsResult.PermissionRequired -> {
                                    refreshPosters()
                                    updateState { it.copy(isRunning = false, activeSong = null, failures = failures.toList(),
                                        permissionIntentSender = result.intentSender,
                                        message = application.getString(R.string.msg_artist_poster_permission_required)) }
                                    return@launch
                                }
                            }
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) {
                            AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Embedded poster edit failed",
                                "uri=${song.uri}\naction=$action", song.uri, AppLogLevel.ERROR, e)
                            failures += title
                        }
                        updateState { it.copy(done = index + 1, failures = failures.toList()) }
                    }
                    if (action == Operation.Remove) {
                        if (plan.folderUnavailable) {
                            failures += application.getString(R.string.artist_poster_folder_unavailable)
                            updateState { it.copy(done = it.done + 1, failures = failures.toList()) }
                        } else if (plan.files.isNotEmpty()) {
                            val present = plan.folder?.let { SafDocuments.children(application, it.toUri()) }
                            for (file in plan.files) {
                                coroutineContext.ensureActive()
                                updateState { it.copy(activeSong = file.displayName) }
                                try {
                                    checkNotNull(present) { "Folder unavailable" }
                                    val current = present.firstOrNull { it.uri == file.uri }
                                    if (current == null) unchanged++
                                    else if (current.displayName != file.displayName) failures += file.displayName
                                    else if (DocumentsContract.deleteDocument(application.contentResolver, file.uri)) changed++
                                    else failures += file.displayName
                                } catch (e: CancellationException) { throw e
                                } catch (e: Exception) {
                                    AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Poster removal failed",
                                        "uri=${file.uri}\nname=${file.displayName}", file.uri.toString(), AppLogLevel.ERROR, e)
                                    failures += file.displayName
                                }
                                updateState { it.copy(done = it.done + 1, failures = failures.toList()) }
                            }
                        }
                    }
                }
                refreshPosters()
                AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Artist poster operation finished",
                    "action=$action\nartists=$artistIds\nchanged=$changed\nunchanged=$unchanged\nfailures=${failures.size}\nfailedTargets=${failures.take(20)}",
                    level = if (failures.isEmpty()) AppLogLevel.INFO else AppLogLevel.WARNING)
                updateState { it.copy(isRunning = false, activeSong = null,
                    message = application.getString(R.string.poster_edit_result, changed, unchanged, failures.size)) }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { refreshPosters() }
                throw e
            } catch (e: Exception) {
                AppLog.record(AppLogType.METADATA, "ArtistPosterEdit", "Artist poster operation failed",
                    "action=$action\nartists=$artistIds", level = AppLogLevel.ERROR, error = e)
                updateState { it.copy(isRunning = false, activeSong = null,
                    message = application.getString(R.string.poster_operation_failed)) }
            }
        }
    }

    private suspend fun preparePlan(action: Operation): Plan {
        val config = settings.artistSplitConfigFlow.first()
        val owners = linkedMapOf<String, MutableSet<String>>()
        val songs = linkedMapOf<String, SongEntity>()
        val artists = linkedMapOf<String, List<SongEntity>>()
        for (id in artistIds) {
            val artist = library.observeArtistById(id).first() ?: continue
            val artistSongs = library.observeSongsByArtistId(id).first()
            artists[artist.name] = artistSongs
            for (song in artistSongs) {
                songs[song.uri] = song
                owners.getOrPut(song.uri) { mutableSetOf() } += artist.name
            }
        }
        val folder = settings.artistPosterFolder.first()
        val children = if (action == Operation.Remove && folder != null) SafDocuments.children(application, folder.toUri()) else emptyList()
        val libraryNames = if (action == Operation.Remove) library.observeArtists().first().map { it.name } else emptyList()
        val files = children.orEmpty().filter { !it.isDirectory && ArtistPosterMatcher.ownedBySelection(it.displayName, artists.keys, libraryNames) }
        return Plan(config, songs, owners.mapValues { it.value.toSet() }, artists, folder, files, children == null)
    }

    private suspend fun refreshPosters() {
        try { settings.refreshArtistPosters() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("ArtistPosterEdit", "Cannot refresh posters", e) }
    }

    fun retry() {
        if (cropPending) cropTarget?.let { runCrop(it, null) }
        else launchOperation(artistIds, operation, resume = true)
    }
    fun cancel() {
        generation++
        job?.cancel()
        _state.update { it.copy(isRunning = false, activeSong = null, permissionIntentSender = null,
            message = application.getString(R.string.msg_artist_poster_cancelled)) }
    }
    fun clear() {
        if (!_state.value.isRunning) {
            generation++
            _state.value = ArtistPosterEmbedUiState()
            pendingPlan = null
            cropTarget = null
            cropBytes = null
            cropPending = false
            artistIds = emptySet()
        }
    }
}
