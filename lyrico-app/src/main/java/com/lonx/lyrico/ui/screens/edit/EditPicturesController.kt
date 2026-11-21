package com.lonx.lyrico.ui.screens.edit

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.audiotag.model.pictureOfType
import com.lonx.audiotag.model.removePictureType
import com.lonx.audiotag.model.replacePicture
import com.lonx.lyrico.R
import com.lonx.lyrico.data.artwork.ArtistPosterFileWriter
import com.lonx.lyrico.data.artwork.ArtworkFiles
import com.lonx.lyrico.data.artwork.getCoverSourceType
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.log.recordOperation
import com.lonx.lyrico.data.settings.ArtistSplitConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.domain.artist.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterEdits
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import com.lonx.lyrico.domain.song.usecase.ReadAudioTagsUseCase
import com.lonx.lyrico.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns picture draft transactions and external writes for one editor session. */
internal class EditPicturesController(
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<EditMetadataUiState>,
    private val songUri: () -> String?,
    private val artistSplitConfig: () -> ArtistSplitConfig,
    private val settingsRepository: SettingsRepository,
    private val songLibraryRepository: SongLibraryRepository,
    private val readAudioTagsUseCase: ReadAudioTagsUseCase,
    private val appLogRepository: AppLogRepository,
    private val recordFailure: suspend (String, String?, String?) -> Unit,
    private val recordException: suspend (String, String?, Throwable) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val session = PictureEditSession()

    /** Picker requests outlive a composition during configuration changes, just like the editor draft. */
    class PendingRequests {
        var coverPicker: PictureEditSession.Ticket? = null
        var coverSearch: PictureEditSession.Ticket? = null
        var artistPicker: PictureEditSession.Ticket? = null
        var coverExport: ExportSnapshot? = null
    }

    val pendingRequests = PendingRequests()

    data class ExportSnapshot internal constructor(
        val source: Any?,
        val audioUri: String,
        val fileName: String,
        internal val ticket: PictureEditSession.Ticket,
    )

    private data class DraftSnapshot(
        val ticket: PictureEditSession.Ticket,
        val uri: String,
        val state: EditMetadataUiState,
        val artistNames: List<String>,
    )

    fun beginCoverEdit(): PictureEditSession.Ticket = session.begin("cover")
    fun beginArtistEdit(artistName: String): PictureEditSession.Ticket =
        session.begin("artist:${ArtistPosterGrouping.ownerKeyOf(artistName)}")

    fun songTicket(): PictureEditSession.Ticket = session.ticket()
    fun isCurrent(ticket: PictureEditSession.Ticket): Boolean = session.isCurrent(ticket)

    fun onSongChanged(uri: String) {
        session.changeSong(uri)
        _uiState.update {
            it.copy(
                exportCoverResult = null,
                exportArtistImageResult = null,
                sameAlbumCoverMessage = null,
                isSavingArtistPoster = false,
            )
        }
    }

    private fun capture(key: String? = null): DraftSnapshot? {
        val uri = songUri() ?: return null
        val state = _uiState.value
        if (state.songInfo?.uriString != uri) return null
        return DraftSnapshot(
            if (key == null) session.ticket() else session.begin(key), uri, state,
            ArtistNameSplitter.splitArtists(state.editingTagData?.artist.orEmpty(), artistSplitConfig()),
        )
    }

    private fun apply(snapshot: DraftSnapshot, transform: (EditMetadataUiState) -> EditMetadataUiState) {
        _uiState.update { state -> if (session.isCurrent(snapshot.ticket)) transform(state) else state }
    }

    /** The loader is also the cancellation/late-result boundary for local imports and bitmap encoding. */
    fun importCover(displaySource: Any?, load: suspend () -> AudioPicture?) {
        val snapshot = capture("cover") ?: return
        scope.launch {
            try {
                val picture = withContext(ioDispatcher) { load() }
                if (picture == null) {
                    recordFailure("Failed to read image source", snapshot.uri, "Source: $displaySource")
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                apply(snapshot) { it.withUpdatedFrontCover(displaySource ?: picture.data, picture) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { recordException("Failed to read image source", snapshot.uri, e) }
        }
    }

    fun updateCover(picUrl: String) = updateCoverSource(picUrl)

    private fun updateCoverSource(source: Any) {
        val snapshot = capture("cover") ?: return
        apply(snapshot) { it.copy(coverUri = source, isEditing = true,
            editingTagData = it.editingTagData?.copy(picUrl = source.toString())) }
    }

    fun updateCover(context: Context, uri: Uri) {
        val appContext = context.applicationContext
        importCover(uri) {
            ArtworkFiles.readLocal(appContext, uri)?.let {
                AudioPicture(it.bytes, it.mimeType, "", AudioPictureType.FrontCover.tagLibName)
            }
        }
    }

    fun updateCover(bitmap: Bitmap) = importCover(null) {
        AudioPicture(ArtworkFiles.encodeJpeg(bitmap), "image/jpeg", "", AudioPictureType.FrontCover.tagLibName)
    }

    fun importArtistImage(artistName: String, load: suspend () -> AudioPicture?) {
        val key = ArtistPosterGrouping.ownerKeyOf(artistName)
        val snapshot = capture("artist:$key") ?: return
        scope.launch {
            try {
                val picture = withContext(ioDispatcher) { load() }
                if (picture == null) {
                    recordFailure("Failed to read image source", snapshot.uri, "Artist: $artistName")
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                apply(snapshot) { state -> updateArtistState(state) { pictures, _ ->
                    ArtistPosterEdits.setFor(pictures, snapshot.artistNames, artistName,
                        picture.copy(description = artistName.trim(), pictureType = AudioPictureType.Artist.tagLibName))
                } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { recordException("Failed to read image source", snapshot.uri, e) }
        }
    }

    fun setArtistImage(context: Context, uri: Uri, artistName: String) {
        val appContext = context.applicationContext
        importArtistImage(artistName) {
            ArtworkFiles.readLocal(appContext, uri)?.let {
                AudioPicture(it.bytes, it.mimeType, artistName.trim(), AudioPictureType.Artist.tagLibName)
            }
        }
    }

    private fun updateArtistState(
        state: EditMetadataUiState,
        transform: (List<AudioPicture>, AudioTagData?) -> List<AudioPicture>,
    ): EditMetadataUiState {
        val current = state.editingTagData ?: return state
        val pictures = transform(current.pictures, state.originalTagData)
        return if (pictures == current.pictures) state else state.copy(isEditing = true,
            editingTagData = current.copy(pictures = pictures))
    }

    private fun updateArtistPictures(transform: (List<AudioPicture>, AudioTagData?) -> List<AudioPicture>) {
        _uiState.update { updateArtistState(it, transform) }
    }

    fun removeArtistImage(target: AudioPicture) {
        invalidateArtist(target)
        updateArtistPictures { pictures, _ -> ArtistPosterEdits.remove(pictures, target) }
    }

    private fun invalidateArtist(target: AudioPicture): String {
        val snapshot = capture()
        val key = ArtistPosterGrouping.ownerKeyOf(target, snapshot?.artistNames.orEmpty())
            ?: ArtistPosterGrouping.ownerKeyOf(target.description)
        session.begin("artist:$key")
        return key
    }

    fun replaceArtistImage(bitmap: Bitmap, target: AudioPicture) {
        val key = invalidateArtist(target)
        val snapshot = capture("artist:$key") ?: return
        scope.launch {
            try {
                val bytes = withContext(ioDispatcher) { ArtworkFiles.encodeJpeg(bitmap) }
                apply(snapshot) { state -> updateArtistState(state) { pictures, _ ->
                    ArtistPosterEdits.replaceData(pictures, target, bytes, "image/jpeg")
                } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { recordException("Failed to encode artist image", snapshot.uri, e) }
        }
    }

    fun reassignArtistImages(target: AudioPicture, artistName: String) {
        session.invalidateArtists()
        val snapshot = capture() ?: return
        apply(snapshot) { state -> updateArtistState(state) { pictures, _ ->
            ArtistPosterEdits.reassign(pictures, snapshot.artistNames, target, artistName)
        } }
    }

    fun swapArtistImages(target: AudioPicture, artistName: String) {
        session.invalidateArtists()
        val snapshot = capture() ?: return
        apply(snapshot) { state -> updateArtistState(state) { pictures, _ ->
            ArtistPosterEdits.swapOwners(pictures, snapshot.artistNames, target, artistName)
        } }
    }

    suspend fun readBitmap(context: Context, source: Any): Bitmap? {
        val snapshot = capture() ?: return null
        return try {
            val bitmap = withContext(ioDispatcher) { ArtworkFiles.readBitmap(context.applicationContext, source) }
            if (bitmap == null) recordFailure("Failed to read image source", snapshot.uri, "Source type: ${getCoverSourceType(source)}")
            if (isCurrent(snapshot.ticket)) bitmap else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordException("Failed to read image source", snapshot.uri, e)
            null
        }
    }

    fun captureExport(): ExportSnapshot? {
        val snapshot = capture() ?: return null
        val state = snapshot.state
        return ExportSnapshot(state.coverUri ?: state.originalCover ?: state.picture?.data,
            snapshot.uri, "${state.fileName ?: "Cover"}.jpg", snapshot.ticket)
    }

    private fun export(
        snapshot: ExportSnapshot?,
        artist: Boolean = false,
        work: suspend (ExportSnapshot) -> Boolean,
    ) {
        if (snapshot == null) return
        val ticket = session.begin(if (artist) "artistExport" else "coverExport")
        scope.launch {
            val success = try {
                withContext(ioDispatcher) { work(snapshot) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordException(if (artist) "Failed to export artist poster" else "Failed to export cover", snapshot.audioUri, e)
                false
            }
            if (isCurrent(snapshot.ticket) && isCurrent(ticket)) _uiState.update {
                if (artist) it.copy(exportArtistImageResult = success) else it.copy(exportCoverResult = success)
            }
        }
    }

    fun exportCover(context: Context, destinationUri: Uri, snapshot: ExportSnapshot? = captureExport()) {
        val appContext = context.applicationContext
        export(snapshot) { captured ->
            val source = captured.source
            if (source == null) {
                recordFailure("Failed to export cover: no picture source", captured.audioUri, null)
                return@export false
            }
            ArtworkFiles.writeToUri(appContext, destinationUri, source).also { written ->
                if (written) appLogRepository.recordOperation(AppLogType.METADATA, "EditMetadataVM", "Picture exported",
                    "uri=${captured.audioUri}\ndestination=$destinationUri\nsourceType=${getCoverSourceType(source)}",
                    captured.audioUri, AppLogLevel.INFO)
                else recordFailure("Failed to export cover: source stream unavailable", captured.audioUri, "Destination: $destinationUri")
            }
        }
    }

    fun exportCoverToAudioDirectory(context: Context) {
        val snapshot = captureExport()
        val appContext = context.applicationContext
        export(snapshot) { captured ->
            val source = captured.source ?: run {
                recordFailure("Failed to export cover next to audio: source unavailable", captured.audioUri, null)
                return@export false
            }
            ArtworkFiles.writeNextToAudio(appContext, captured.audioUri.toUri(), captured.fileName, source)
            true
        }
    }

    fun exportArtistImage(context: Context, target: AudioPicture) = exportArtistImage(context, target.data)
    fun exportArtistImage(context: Context, bytes: ByteArray) {
        val snapshot = captureExport()?.copy(source = bytes)
        val appContext = context.applicationContext
        export(snapshot, artist = true) { captured ->
            when (ArtworkFiles.writeToGallery(appContext, bytes, "ArtistPosters", "Artist")) {
                ArtworkFiles.GalleryWriteResult.WRITTEN -> true
                else -> {
                    recordFailure("Failed to export artist poster", captured.audioUri, "Picture destination or source unavailable")
                    false
                }
            }
        }
    }

    fun setArtistPosterFolder(uri: Uri, onAdded: () -> Unit, onFailure: (UiMessage) -> Unit) {
        val ticket = songTicket()
        scope.launch {
            try {
                settingsRepository.setArtistPosterFolder(uri.toString())
                settingsRepository.refreshArtistPosters()
                if (isCurrent(ticket)) onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordException("Failed to set artist poster folder", null, e)
                if (isCurrent(ticket)) onFailure(UiMessage.StringResource(R.string.artist_poster_folder_error))
            }
        }
    }

    fun saveArtistImageToFolder(context: Context, uri: Uri, artistName: String, onResult: (UiMessage) -> Unit) {
        if (_uiState.value.isSavingArtistPoster) return
        val snapshot = capture("folderSave") ?: return
        _uiState.update { it.copy(isSavingArtistPoster = true) }
        val appContext = context.applicationContext
        scope.launch {
            try {
                val folder = settingsRepository.artistPosterFolder.first()
                val result = withContext(ioDispatcher) {
                    val image = ArtworkFiles.readLocal(appContext, uri)
                    if (image == null || image.bytes.isEmpty()) UiMessage.StringResource(R.string.msg_read_artist_image_failed)
                    else {
                        val name = ArtistPosterFileWriter.write(appContext, artistName, image.bytes, image.mimeType, folder)
                        if (name == null) UiMessage.StringResource(R.string.msg_artist_poster_folder_save_failed)
                        else {
                            try { settingsRepository.refreshArtistPosters()
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { recordException("Failed to refresh artist posters", snapshot.uri, e) }
                            UiMessage.StringResource(R.string.msg_artist_poster_saved_to_folder, name)
                        }
                    }
                }
                if (isCurrent(snapshot.ticket)) onResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordException("Failed to save artist poster", snapshot.uri, e)
                if (isCurrent(snapshot.ticket)) onResult(UiMessage.StringResource(R.string.msg_artist_poster_folder_save_failed))
            } finally {
                apply(snapshot) { it.copy(isSavingArtistPoster = false) }
            }
        }
    }

    private suspend fun firstSameAlbumCover(snapshot: DraftSnapshot): AudioPicture? {
        val data = snapshot.state.editingTagData ?: return null
        val album = data.album ?: return null
        val artist = data.artist.orEmpty()
        return withContext(ioDispatcher) {
            for (song in songLibraryRepository.getSongsByAlbum(album, artist)) {
                currentCoroutineContext().ensureActive()
                if (song.uri == snapshot.uri) continue
                try {
                    val picture = readAudioTagsUseCase(song.uri).pictures.frontCoverOrFallback()
                    if (picture != null) return@withContext picture.takeIf { isCurrent(snapshot.ticket) }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { recordException("Failed to read same-album cover", song.uri, e) }
            }
            null
        }
    }

    fun loadSameAlbumCovers() {
        val snapshot = capture("cover") ?: return
        scope.launch {
            try {
                val picture = firstSameAlbumCover(snapshot)
                apply(snapshot) { state ->
                    if (picture == null) state.copy(sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_no_same_album_cover_found))
                    else state.withUpdatedFrontCover(picture.data, picture.copy(
                        description = "", pictureType = AudioPictureType.FrontCover.tagLibName)).copy(
                        sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_same_album_cover_applied))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordException("Failed to load same-album cover", snapshot.uri, e)
                apply(snapshot) { it.copy(sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_load_same_album_cover_failed)) }
            }
        }
    }

    fun clearExportCoverStatus() {
        session.begin("coverExport")
        _uiState.update { it.copy(exportCoverResult = null) }
    }

    fun clearExportArtistImageStatus() {
        session.begin("artistExport")
        _uiState.update { it.copy(exportArtistImageResult = null) }
    }

    fun clearSameAlbumCoverMessage() {
        _uiState.update { it.copy(sameAlbumCoverMessage = null) }
    }

    private fun restoreFrontCover(current: List<AudioPicture>, snapshot: List<AudioPicture>): List<AudioPicture> {
        val original = snapshot.pictureOfType(AudioPictureType.FrontCover)
        val others = current.removePictureType(AudioPictureType.FrontCover)
        return if (original == null) others else others.replacePicture(original, AudioPictureType.FrontCover)
    }

    private fun EditMetadataUiState.withUpdatedFrontCover(
        displaySource: Any?,
        audioPicture: AudioPicture
    ): EditMetadataUiState {
        val current = editingTagData ?: AudioTagData()
        return copy(
            coverUri = displaySource,
            picture = audioPicture,
            isEditing = true,
            editingTagData = current.copy(
                pictures = current.pictures.replacePicture(
                    picture = audioPicture,
                    type = AudioPictureType.FrontCover
                ),
                picUrl = null
            )
        )
    }

    /**
     * 移除封面
     */
    fun removeFrontCover() {
        session.begin("cover")
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            val newPictures = current.pictures.removePictureType(AudioPictureType.FrontCover)
            val displayPicture = newPictures.frontCoverOrFallback()

            state.copy(
                coverUri = displayPicture?.data,
                picture = displayPicture,
                isEditing = true,
                editingTagData = current.copy(
                    pictures = newPictures,
                    picUrl = ""
                )
            )
        }
    }

    fun revertCover() {
        session.begin("cover")
        _uiState.update { state ->
            val original = state.originalTagData
            if (original == null) {
                state.copy(
                    coverUri = state.originalCover,
                    editingTagData = state.editingTagData?.copy(picUrl = null)
                )
            } else {
                val displayPicture = original.pictures.frontCoverOrFallback()
                val current = state.editingTagData ?: AudioTagData()
                val originalFrontCover = original.pictures.pictureOfType(AudioPictureType.FrontCover)
                val revertedPictures = originalFrontCover?.let { picture ->
                    current.pictures
                        .removePictureType(AudioPictureType.FrontCover)
                        .replacePicture(
                            picture = picture,
                            type = AudioPictureType.FrontCover
                        )
                } ?: current.pictures.removePictureType(AudioPictureType.FrontCover)

                state.copy(
                    coverUri = state.originalCover,
                    picture = displayPicture,
                    editingTagData = state.editingTagData?.copy(
                        pictures = revertedPictures,
                        picUrl = null
                    )
                )
            }
        }
    }

    /**
     * 还原所有艺术家海报。重挂或裁剪后无法逐张识别原图，只能整组还原。
     */
    fun revertArtistImages() {
        session.invalidateArtists()
        updateArtistPictures { pictures, original ->
            if (original == null) pictures
            else ArtistPosterEdits.revertAll(pictures, original.pictures)
        }
    }

    fun restoreCoverSnapshot(
        coverUri: Any?,
        picture: AudioPicture?,
        pictures: List<AudioPicture>,
        picUrl: String?
    ) {
        session.begin("cover")
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                coverUri = coverUri,
                picture = picture,
                isEditing = true,
                editingTagData = current.copy(
                    pictures = restoreFrontCover(current.pictures, pictures),
                    picUrl = picUrl
                )
            )
        }
    }

    fun restoreArtistImageSnapshot(pictures: List<AudioPicture>) {
        session.invalidateArtists()
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                isEditing = true,
                editingTagData = current.copy(
                    pictures = ArtistPosterEdits.revertAll(current.pictures, pictures)
                )
            )
        }
    }
}
