package com.lonx.lyrico.viewmodel

import android.app.RecoverableSecurityException
import com.lonx.lyrico.data.model.log.recordOperation
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.audiotag.model.addPicture
import com.lonx.audiotag.model.artistPictureTypes
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.audiotag.model.pictureOfType
import com.lonx.audiotag.model.removePictureType
import com.lonx.audiotag.model.replacePicture
import com.lonx.audiotag.model.type
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldScene
import com.lonx.lyrico.data.exception.RequiresUserPermissionException
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsProcessingOptions
import com.lonx.lyrico.data.model.lyrics.sanitizeStandardFields
import com.lonx.lyrico.data.model.metadata.MetadataApplyPolicy
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.metadata.SearchResultApplier
import com.lonx.lyrico.data.model.metadata.StandardPluginField
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.model.search.LyricsSearchResult
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterEdits
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import com.lonx.lyrico.domain.song.usecase.OverwriteSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.ReadAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.CoverSourceType
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import com.lonx.lyrico.utils.ReplayGainCalculateState
import com.lonx.lyrico.utils.ReplayGainError
import com.lonx.lyrico.utils.ReplayGainScanner
import com.lonx.lyrico.utils.SafSiblingFileWriter
import com.lonx.lyrico.utils.ArtistPosterFileWriter
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.getCoverSourceType
import com.lonx.lyrico.utils.lyrics.LyricsColumnSorter
import com.lonx.lyrico.utils.lyrics.LyricsTextCleanup
import com.lonx.lyrico.utils.lyrics.document.LyricsDocumentPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.OutputStream

data class EditMetadataUiState(
    val songInfo: SongInfo? = null,

    val originalTagData: AudioTagData? = null,
    val editingTagData: AudioTagData? = null,

    val replayGainCalculateProgress: Float? = null,
    val isEditing: Boolean = false,
    val fileName: String? = null,
    /**
     * 编辑态封面（只要不为 null，就代表用户替换过封面）
     */
    val coverUri: Any? = null,
    val exportCoverResult: Boolean? = null,
    val exportArtistImageResult: Boolean? = null,
    val isSavingArtistPoster: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean? = null,
    val originalCover: Any? = null,
    val picture: AudioPicture? = null,
    val permissionIntentSender: IntentSender? = null,
    val isReplayGainCalculating: Boolean = false,
    val replayGainScanMessage: UiMessage? = null,
    val saveFailureMessage: String? = null,
    val saveFailureLogText: String? = null,

    /**
     * 歌词导出导入状态
     */
    val exportLyricsResult: Boolean? = null,
    val importLyricsResult: Boolean? = null,

    /**
     * 同专辑封面加载结果消息
     */
    val sameAlbumCoverMessage: UiMessage? = null
)

private data class LyricsFormatConversionSession(
    val sourceFormat: LyricFormat,
    val sourceLyrics: String,
    val lastRenderedLyrics: String
)

private const val LYRICS_EXPORT_MIME_TYPE = "application/octet-stream"

class EditMetadataViewModel(
    private val songLibraryRepository: SongLibraryRepository,
    private val readAudioTagsUseCase: ReadAudioTagsUseCase,
    private val overwriteSongTagsUseCase: OverwriteSongTagsUseCase,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val replayGainScanner: ReplayGainScanner,
    private val appLogRepository: AppLogRepository,
    private val editFieldConfigRepository: EditFieldConfigRepository,
) : ViewModel() {

    private val TAG = "EditMetadataVM"
    val limitLyricsInputLines = settingsRepository.limitLyricsInputLines.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsDefaults.LIMIT_LYRICS_INPUT_LINES
    )
    private val lyricRenderConfig = settingsRepository.lyricRenderConfigFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null
    )

    /**
     * 艺术家拆分配置。海报描述要与拆分后的艺术家对齐，所以与艺术家视图使用同一份规则。
     */
    val artistSplitConfig: StateFlow<ArtistSplitConfig> =
        settingsRepository.artistSplitConfigFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ArtistSplitConfig()
        )

    private var currentSong: SongEntity? = null

    private var currentSongUri: String? = null
    private var preOffsetLyrics: String? = null
    private var lyricsFormatConversionSession: LyricsFormatConversionSession? = null
    private var scanJob: Job? = null
    private val _currentShiftOffset = MutableStateFlow(0L)
    val currentShiftOffset: StateFlow<Long> = _currentShiftOffset.asStateFlow()
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    /** 单曲编辑页按配置排序后的可见字段。 */
    val visibleFields: StateFlow<List<EditFieldDefinition>> =
        editFieldConfigRepository.configFlow
            .map { config ->
                config.visibleFieldsForScene(EditFieldScene.SingleEdit)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    fun readMetadata(uriString: String) {
        currentSongUri = uriString
        lyricsFormatConversionSession = null

        viewModelScope.launch {
            try {
                val song = songLibraryRepository.getSongByUri(uriString)
                currentSong = song

                val audioTagData = readAudioTagsUseCase(uriString)
                val displayFileName = song?.fileName ?: audioTagData.fileName
                val displayPicture = audioTagData.pictures.frontCoverOrFallback()
                val displayCover = displayPicture?.data

                _uiState.update { state ->
                    state.copy(
                        songInfo = SongInfo(
                            uriString = uriString,
                            tagData = audioTagData
                        ),
                        originalTagData = audioTagData,
                        fileName = displayFileName.substringBeforeLast(
                            ".",
                            missingDelimiterValue = displayFileName
                        ),
                        editingTagData = if (state.isEditing) state.editingTagData else audioTagData,
                        picture = displayPicture,
                        originalCover = if (state.isEditing) state.originalCover else displayCover,
                        coverUri = if (state.isEditing) state.coverUri else displayCover
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $uriString", e)
                recordMetadataException(
                    message = "Failed to read metadata",
                    relatedId = uriString,
                    throwable = e
                )
            }
        }
    }

    fun updateTag(block: AudioTagData.() -> AudioTagData) {
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                editingTagData = current.block(),
                isEditing = true
            )
        }
    }

    fun updateCustomFieldValue(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        updateTag {
            val fields = customFields.toMutableList()
            val index = fields.indexOfFirst { it.key.equals(normalizedKey, ignoreCase = true) }

            if (index >= 0) {
                fields[index] = fields[index].copy(key = normalizedKey, value = value)
            } else {
                fields += CustomTagField(
                    key = normalizedKey,
                    value = value,
                )
            }

            copy(customFields = fields)
        }
    }

    fun removeCustomFieldValue(key: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        updateTag {
            copy(
                customFields = customFields.filterNot { it.key.equals(normalizedKey, ignoreCase = true) }
            )
        }
    }

    fun revertCustomField(key: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return
        val original = _uiState.value.originalTagData
            ?.customFields
            .orEmpty()
            .firstOrNull { it.key.equals(normalizedKey, ignoreCase = true) }
            ?.copy(key = normalizedKey)

        updateTag {
            val fields = customFields
                .filterNot { it.key.equals(normalizedKey, ignoreCase = true) }
                .toMutableList()

            if (original != null) {
                fields += original
            }

            copy(customFields = fields)
        }
    }

    fun addCustomFieldAndShow(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        viewModelScope.launch {
            editFieldConfigRepository.addCustomTag(normalizedKey)
        }

        updateCustomFieldValue(normalizedKey, value)
    }

    private fun normalizeCustomTagKey(input: String): String? = CustomTagKey.normalize(input)
    /**
     * 打开弹窗前准备：拍快照，并重置累计偏移量
     */
    fun prepareLyricsOffset() {
        preOffsetLyrics = _uiState.value.editingTagData?.lyrics
        _currentShiftOffset.value = 0L
    }

    fun setLimitLyricsInputLines( limit: Boolean ) {
        viewModelScope.launch {
            settingsRepository.saveLimitLyricsInputLines(limit)
        }
    }
    /**
     * 应用绝对偏移量（相对于快照）
     */
    fun applyLyricsOffset(totalOffset: Long) {
        val originalLyrics = preOffsetLyrics ?: return

        _currentShiftOffset.value = totalOffset

        // 始终基于 originalLyrics 快照偏移，避免来回计算导致精度丢失或触底失真
        val shiftedLyrics = LyricEncoder.shiftLyricsOffset(originalLyrics, totalOffset)

        _uiState.update { state ->
            state.copy(
                editingTagData = state.editingTagData?.copy(lyrics = shiftedLyrics),
                isEditing = true
            )
        }
    }
    /**
     * 重置回打开 BottomSheet 时的状态
     */
    fun resetLyricsOffset() {
        _currentShiftOffset.value = 0L
        preOffsetLyrics?.let { original ->
            _uiState.update { state ->
                state.copy(
                    editingTagData = state.editingTagData?.copy(lyrics = original)
                )
            }
        }
    }
    fun updateMetadataFromSearchResult(result: LyricsSearchResult) {
        _uiState.update { state ->
            val current = state.editingTagData ?: AudioTagData()

            val renderConfig = lyricRenderConfig.value
            val fieldProcessor = PluginFieldPostProcessor(
                GlobalFieldProcessSettings(
                    scriptConversion = renderConfig?.conversionMode ?: ConversionMode.NONE,
                    removeEmptyLines = renderConfig?.removeEmptyLines ?: SettingsDefaults.REMOVE_EMPTY_LINES
                )
            )
            val rawFields = result.normalizedFields() +
                    result.lyrics?.takeIf { it.isNotBlank() }?.let { mapOf("lyrics" to it) }.orEmpty()
            val processedFields = fieldProcessor.processFields(
                pluginId = result.pluginId,
                fields = rawFields.sanitizeStandardFields(),
                config = defaultPluginFieldProcessConfig(result.pluginId),
                fieldDefinitions = emptyList(),
                writeRules = emptyList()
            )
            val applyTargets = when {
                result.applyTargets.isNotEmpty() -> result.applyTargets
                else -> processedFields.keys
                    .mapNotNull { key -> StandardPluginField.fromKey(key)?.target }
                    .toSet()
            }
            val applyPolicy = MetadataApplyPolicy(
                applyTargets.associateWith { MetadataWriteMode.OVERWRITE }
            )
            val applied = SearchResultApplier.applyFields(
                current = current,
                fields = processedFields,
                policy = applyPolicy
            )
            if (!result.lyrics.isNullOrBlank()) {
                lyricsFormatConversionSession = null
            }
            val nextCoverUri = if (applyPolicy.modeOf(MetadataFieldTarget.COVER) != MetadataWriteMode.DISABLED) {
                applied.picUrl
                    ?.takeIf { it.isNotBlank() && it != current.picUrl }
                    ?: state.coverUri
            } else {
                state.coverUri
            }
            state.copy(
                isEditing = true,
                editingTagData = applied,
                coverUri = nextCoverUri
            )
        }
    }
    /**
     * 更新封面（从本地选择）
     */
    fun updateCover(uri: Uri) {
        _uiState.update { state ->
            state.copy(
                coverUri = uri,
                isEditing = true,
                editingTagData = state.editingTagData?.copy(picUrl = uri.toString())
            )
        }
    }

    fun updateCover(context: Context, uri: Uri) {
        readPictureBytes(
            context = context,
            uri = uri,
            onResult = { bytes, mimeType ->
                if (bytes == null) return@readPictureBytes

                val audioPicture = AudioPicture(
                    data = bytes,
                    mimeType = mimeType,
                    description = "",
                    pictureType = AudioPictureType.FrontCover.tagLibName
                )

                _uiState.update { state ->
                    state.withUpdatedFrontCover(
                        displaySource = uri,
                        audioPicture = audioPicture
                    )
                }
            }
        )
    }

    fun updateCover(picUrl: String) {
        _uiState.update { state ->
            state.copy(
                coverUri = picUrl,
                isEditing = true,
                editingTagData = state.editingTagData?.copy(picUrl = picUrl)
            )
        }
    }

    fun updateCover(bitmap: Bitmap) {
        val byteArray = bitmap.toJpegBytes()
        val audioPicture = AudioPicture(
            data = byteArray,
            mimeType = "image/jpeg",
            description = "",
            pictureType = AudioPictureType.FrontCover.tagLibName
        )

        _uiState.update { state ->
            state.withUpdatedFrontCover(
                displaySource = byteArray,
                audioPicture = audioPicture
            )
        }
    }

    // ---------------------------------------------------------------- 艺术家海报

    /**
     * 新增或替换 [artistName] 的艺术家海报：该艺术家名下的旧海报会被替换。
     * 一个艺术家只保留一张（ID3v2 同一 content descriptor 只能挂一张图）。
     */
    fun setArtistImage(context: Context, uri: Uri, artistName: String) {
        readPictureBytes(
            context = context,
            uri = uri,
            onResult = { bytes, mimeType ->
                if (bytes == null) return@readPictureBytes
                val picture = newArtistPicture(bytes, mimeType, artistName)
                updateArtistPictures { pictures, _ ->
                    ArtistPosterEdits.setFor(
                        pictures = pictures,
                        artistNames = currentArtistNames(),
                        artistName = artistName,
                        picture = picture
                    )
                }
            }
        )
    }

    fun setArtistPosterFolder(uri: Uri, onAdded: () -> Unit, onFailure: (UiMessage) -> Unit) {
        viewModelScope.launch {
            try {
                settingsRepository.setArtistPosterFolder(uri.toString())
                settingsRepository.refreshArtistPosters()
                onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "添加艺术家海报文件夹失败", e)
                onFailure(UiMessage.StringResource(R.string.artist_poster_folder_error))
            }
        }
    }

    /** Folder saves apply immediately and never change the song's metadata draft. */
    fun saveArtistImageToFolder(context: Context, uri: Uri, artistName: String, onResult: (UiMessage) -> Unit) {
        if (_uiState.value.isSavingArtistPoster) return
        _uiState.update { it.copy(isSavingArtistPoster = true) }
        val appContext = context.applicationContext
        readPictureBytes(appContext, uri) { bytes, mimeType ->
            viewModelScope.launch {
                try {
                    if (bytes == null || bytes.isEmpty()) {
                        onResult(UiMessage.StringResource(R.string.msg_read_artist_image_failed))
                        return@launch
                    }
                    val writtenName = ArtistPosterFileWriter.write(
                        context = appContext,
                        artistName = artistName,
                        data = bytes,
                        mimeType = mimeType,
                        posterFolder = settingsRepository.artistPosterFolder.first(),
                    )
                    if (writtenName != null) {
                        try {
                            settingsRepository.refreshArtistPosters()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "刷新艺术家海报缓存失败", e)
                        }
                    }
                    onResult(if (writtenName != null)
                        UiMessage.StringResource(R.string.msg_artist_poster_saved_to_folder, writtenName)
                        else UiMessage.StringResource(R.string.msg_artist_poster_folder_save_failed))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "保存艺术家海报文件失败", e)
                    onResult(UiMessage.StringResource(R.string.msg_artist_poster_folder_save_failed))
                } finally {
                    _uiState.update { it.copy(isSavingArtistPoster = false) }
                }
            }
        }
    }

    /** 移除某一张艺术家图片（标签里若残留多张，只删这一张）。 */
    fun removeArtistImage(target: AudioPicture) {
        updateArtistPictures { pictures, _ -> ArtistPosterEdits.remove(pictures, target) }
    }

    /** 裁剪结果：替换某一张艺术家图片的数据，保留它的归属描述。 */
    fun replaceArtistImage(bitmap: Bitmap, target: AudioPicture) {
        val bytes = bitmap.toJpegBytes()
        updateArtistPictures { pictures, _ ->
            ArtistPosterEdits.replaceData(pictures, target, bytes, "image/jpeg")
        }
    }

    /**
     * 把 [target] 改挂到 [artistName]，该艺术家原有的海报会被替换。
     * 两张都想留下时用 [swapArtistImages]。
     */
    fun reassignArtistImages(target: AudioPicture, artistName: String) {
        updateArtistPictures { pictures, _ ->
            ArtistPosterEdits.reassign(
                pictures = pictures,
                artistNames = currentArtistNames(),
                target = target,
                artistName = artistName
            )
        }
    }

    /** [target] 与 [artistName] 原有的海报**交换归属**，两张图都留着。 */
    fun swapArtistImages(target: AudioPicture, artistName: String) {
        updateArtistPictures { pictures, _ ->
            ArtistPosterEdits.swapOwners(
                pictures = pictures,
                artistNames = currentArtistNames(),
                target = target,
                artistName = artistName
            )
        }
    }

    /** 导出某张艺术家海报到系统相册。 */
    fun exportArtistImage(context: Context, target: AudioPicture) = exportArtistImage(context, target.data)

    fun exportArtistImage(context: Context, bytes: ByteArray) {
        exportPictureToGallery(
            context = context,
            source = bytes,
            albumName = "ArtistPosters",
            filenamePrefix = "Artist",
            failureLogMessage = "Failed to export artist poster",
            onResult = { success -> _uiState.update { it.copy(exportArtistImageResult = success) } }
        )
    }

    private fun newArtistPicture(
        bytes: ByteArray,
        mimeType: String,
        artistName: String
    ) = AudioPicture(
        data = bytes,
        mimeType = mimeType,
        description = artistName.trim(),
        pictureType = AudioPictureType.Artist.tagLibName
    )

    /** 当前编辑态的艺术家名（艺术家字段按配置拆分）。 */
    private fun currentArtistNames(): List<String> =
        _uiState.value.editingTagData?.let {
            ArtistNameSplitter.splitArtists(it.artist, artistSplitConfig.value)
        }.orEmpty()

    /** 用 [transform] 生成新的图片列表；结果没变时不动状态。 */
    private fun updateArtistPictures(
        transform: (pictures: List<AudioPicture>, original: AudioTagData?) -> List<AudioPicture>
    ) {
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            val nextPictures = transform(current.pictures, state.originalTagData)
            if (nextPictures == current.pictures) return@update state
            state.copy(
                isEditing = true,
                editingTagData = current.copy(pictures = nextPictures)
            )
        }
    }

    /** 读取本地图片字节；失败时记录日志并以 `null` 回调。 */
    private fun readPictureBytes(
        context: Context,
        uri: Uri,
        onResult: (bytes: ByteArray?, mimeType: String) -> Unit
    ) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = appContext.contentResolver
                val bytes = resolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }

                if (bytes == null) {
                    recordMetadataFailure(
                        message = "Failed to read image source",
                        relatedId = currentSongUri,
                        detail = "Source URI: $uri"
                    )
                    onResult(null, "image/jpeg")
                    return@launch
                }

                val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg"
                onResult(bytes, mimeType)
            } catch (e: Exception) {
                Log.e(TAG, "读取图片失败: $uri", e)
                recordMetadataException(
                    message = "Failed to read image source",
                    relatedId = currentSongUri,
                    throwable = e
                )
                onResult(null, "image/jpeg")
            }
        }
    }

    private fun Bitmap.toJpegBytes(): ByteArray =
        java.io.ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.toByteArray()
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

    fun getCoverFileName(): String = "${_uiState.value.fileName ?: "Cover"}.jpg"

    fun exportCover(context: Context, destinationUri: Uri) {
        exportPictureToUri(
            context = context,
            source = currentCoverSource(),
            destinationUri = destinationUri,
            failureLogMessage = "Failed to export cover",
            onResult = { success -> _uiState.update { it.copy(exportCoverResult = success) } }
        )
    }

    fun exportCoverToAudioDirectory(context: Context) {
        val source = currentCoverSource()
        val audioUri = (currentSong?.uri ?: currentSongUri)?.toUri()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (source == null || audioUri == null) {
                    recordMetadataFailure(
                        message = "Failed to export cover next to audio: source unavailable",
                        relatedId = currentSongUri
                    )
                    _uiState.update { it.copy(exportCoverResult = false) }
                    return@launch
                }

                SafSiblingFileWriter.write(
                    context = context,
                    sourceDocumentUri = audioUri,
                    mimeType = "image/jpeg",
                    fileName = getCoverFileName()
                ) { outputStream ->
                    if (!writePictureSource(context, outputStream, source)) {
                        throw IllegalStateException("Cover source stream unavailable")
                    }
                }
                _uiState.update { it.copy(exportCoverResult = true) }
            } catch (e: Exception) {
                Log.e(TAG, "导出封面到音频同目录失败", e)
                recordMetadataException(
                    message = "Failed to export cover next to audio",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(exportCoverResult = false) }
            }
        }
    }

    private fun currentCoverSource(): Any? {
        val state = _uiState.value
        return state.coverUri ?: state.originalCover ?: state.picture?.data
    }

    private fun exportPictureToUri(
        context: Context,
        source: Any?,
        destinationUri: Uri,
        failureLogMessage: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (source == null) {
                    recordMetadataFailure(
                        message = "$failureLogMessage: no picture source",
                        relatedId = currentSongUri
                    )
                    onResult(false)
                    return@launch
                }
                val wrotePicture = context.contentResolver.openOutputStream(destinationUri, "wt")
                    ?.use { outputStream -> writePictureSource(context, outputStream, source) }
                    ?: false
                if (!wrotePicture) {
                    recordMetadataFailure(
                        message = "$failureLogMessage: source stream unavailable",
                        relatedId = currentSongUri,
                        detail = "Destination: $destinationUri"
                    )
                } else {
                    appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Picture exported",
                        "uri=$currentSongUri\ndestination=$destinationUri\nsourceType=${getCoverSourceType(source)}", currentSongUri, AppLogLevel.INFO)
                }
                onResult(wrotePicture)
            } catch (e: Exception) {
                Log.e(TAG, "导出图片失败: $destinationUri", e)
                recordMetadataException(
                    message = failureLogMessage,
                    relatedId = currentSongUri,
                    throwable = e
                )
                onResult(false)
            }
        }
    }

    /**
     * 把内嵌/已选/原始图片写入系统相册，结果通过 [onResult] 回传
     */
    private fun exportPictureToGallery(
        context: Context,
        source: Any?,
        albumName: String,
        filenamePrefix: String,
        failureLogMessage: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (source == null) {
                    recordMetadataFailure(
                        message = "$failureLogMessage: no picture source",
                        relatedId = currentSongUri,
                        detail = "No embedded picture, selected picture, or original picture is available."
                    )
                    onResult(false)
                    return@launch
                }

                val filename = "${filenamePrefix}_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + albumName
                    )
                }

                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (destUri != null) {
                    val wrotePicture = resolver.openOutputStream(destUri)?.use { outputStream ->
                        writePictureSource(context, outputStream, source)
                    } ?: false

                    if (wrotePicture) {
                        onResult(true)
                    } else {
                        recordMetadataFailure(
                            message = "$failureLogMessage: source stream unavailable",
                            relatedId = currentSongUri,
                            detail = "Source: $source"
                        )
                        onResult(false)
                    }
                } else {
                    recordMetadataFailure(
                        message = "$failureLogMessage: MediaStore insert returned null",
                        relatedId = currentSongUri,
                        detail = "Destination image URI could not be created."
                    )
                    onResult(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "导出图片失败: $filenamePrefix", e)
                recordMetadataException(
                    message = failureLogMessage,
                    relatedId = currentSongUri,
                    throwable = e
                )
                onResult(false)
            }
        }
    }

    fun clearExportCoverStatus() {
        _uiState.update { it.copy(exportCoverResult = null) }
    }

    fun clearExportArtistImageStatus() {
        _uiState.update { it.copy(exportArtistImageResult = null) }
    }
    fun revertCover() {
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
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                coverUri = coverUri,
                picture = picture,
                isEditing = true,
                editingTagData = current.copy(
                    pictures = pictures,
                    picUrl = picUrl
                )
            )
        }
    }

    fun restoreArtistImageSnapshot(pictures: List<AudioPicture>) {
        _uiState.update { state ->
            val current = state.editingTagData ?: return@update state
            state.copy(
                isEditing = true,
                editingTagData = current.copy(
                    pictures = pictures
                )
            )
        }
    }

    /**
     * 保存元数据
     */
    private var saveAttempt = 0
    private var saveAttemptUri: String? = null

    fun onWritePermissionResult(granted: Boolean) {
        val uri = _uiState.value.songInfo?.uriString
        viewModelScope.launch {
            appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Write permission result",
                "uri=$uri\ngranted=$granted\npreviousAttempt=$saveAttempt\nretry=$granted", uri,
                if (granted) AppLogLevel.INFO else AppLogLevel.WARNING)
            if (granted) saveMetadata()
        }
    }

    fun saveMetadata() {
        val state = _uiState.value
        val uriString = state.songInfo?.uriString ?: return
        val editingTagData = state.editingTagData ?: return

        if (_uiState.value.isSaving) return
        if (saveAttemptUri != uriString) saveAttempt = 0
        saveAttemptUri = uriString
        val attempt = ++saveAttempt

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    saveSuccess = null,
                    permissionIntentSender = null,
                    saveFailureMessage = null,
                    saveFailureLogText = null
                )
            }

            try {
                appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Editor save attempt",
                    "uri=$uriString\nattempt=$attempt", uriString)
                val audioTagData = editingTagData.filterHiddenEditFields(
                    original = state.originalTagData,
                    visibleFieldCodes = editFieldConfigRepository.configFlow.first()
                        .visibleFieldCodesForScene(EditFieldScene.SingleEdit),
                )
                when (
                    val saveResult = overwriteSongTagsUseCase(
                        uri = uriString,
                        tagData = audioTagData,
                        // 用户基于读取到的图片列表改过图（含删空）时，必须把这份列表写下去
                        picturesAuthored = audioTagData.pictures != state.originalTagData?.pictures
                    )
                ) {
                    is SaveAudioTagsResult.Success -> {
                        saveAttempt = 0
                        val savedTagData = saveResult.tagData
                        val savedDisplayPicture = savedTagData.pictures.frontCoverOrFallback()
                        val savedDisplayCover = savedDisplayPicture?.data

                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = true,
                                isEditing = false,
                                originalTagData = savedTagData,
                                editingTagData = savedTagData,
                                originalCover = savedDisplayCover,
                                coverUri = savedDisplayCover,
                                picture = savedDisplayPicture,
                            )
                        }
                        currentSong = saveResult.song
                    }
                    is SaveAudioTagsResult.PermissionRequired -> {
                        appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Editor waiting for write permission",
                            "uri=$uriString\nattempt=$attempt", uriString, AppLogLevel.WARNING)
                        Log.w(TAG, "需要用户授权修改文件: $uriString")
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                permissionIntentSender = saveResult.intentSender
                            )
                        }
                    }
                    is SaveAudioTagsResult.Failed -> {
                        val reason = saveResult.error.localizedMessage
                            ?: saveResult.error::class.java.simpleName
                        recordSaveFailure(uriString, reason, saveResult.error)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = false,
                                saveFailureMessage = reason,
                                saveFailureLogText = buildSaveFailureLog(uriString, reason)
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                if (e is RequiresUserPermissionException) {
                    Log.w(TAG, "需要用户授权修改文件: $uriString")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            permissionIntentSender = e.intentSender
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    Log.w(TAG, "需要用户授权修改文件: $uriString")
                    _uiState.update {
                        it.copy(
                            isSaving = false, // 暂停保存状态
                            permissionIntentSender = e.userAction.actionIntent.intentSender
                        )
                    }
                } else {
                    Log.e(TAG, "保存元数据发生未知错误", e)
                    val reason = e.localizedMessage ?: e::class.java.simpleName
                    recordSaveFailure(uriString, reason, e)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = false,
                            saveFailureMessage = reason,
                            saveFailureLogText = buildSaveFailureLog(uriString, reason, e)
                        )
                    }
                }
            }
        }
    }

    private fun AudioTagData.filterHiddenEditFields(
        original: AudioTagData?,
        visibleFieldCodes: Set<String>,
    ): AudioTagData {
        val base = original ?: return this

        fun visible(code: String): Boolean = code in visibleFieldCodes

        return copy(
            customFields = customFields.filter { field ->
                visible(com.lonx.lyrico.data.editfield.EditFieldRegistry.customTagCode(field.key.uppercase(Locale.ROOT)))
            } + base.customFields.filterNot { field ->
                visible(com.lonx.lyrico.data.editfield.EditFieldRegistry.customTagCode(field.key.uppercase(Locale.ROOT)))
            },
            title = if (visible("title")) title else base.title,
            artist = if (visible("artist")) artist else base.artist,
            albumArtist = if (visible("album_artist")) albumArtist else base.albumArtist,
            album = if (visible("album")) album else base.album,
            date = if (visible("date")) date else base.date,
            language = if (visible("language")) language else base.language,
            genre = if (visible("genre")) genre else base.genre,
            trackNumber = if (visible("track_number")) trackNumber else base.trackNumber,
            discNumber = if (visible("disc_number")) discNumber else base.discNumber,
            composer = if (visible("composer")) composer else base.composer,
            lyricist = if (visible("lyricist")) lyricist else base.lyricist,
            copyright = if (visible("copyright")) copyright else base.copyright,
            comment = if (visible("comment")) comment else base.comment,
            replayGainTrackGain = if (visible("track_gain")) {
                replayGainTrackGain
            } else {
                base.replayGainTrackGain
            },
            replayGainTrackPeak = if (visible("track_peak")) {
                replayGainTrackPeak
            } else {
                base.replayGainTrackPeak
            },
            replayGainAlbumGain = if (visible("album_gain")) {
                replayGainAlbumGain
            } else {
                base.replayGainAlbumGain
            },
            replayGainAlbumPeak = if (visible("album_peak")) {
                replayGainAlbumPeak
            } else {
                base.replayGainAlbumPeak
            },
            replayGainReferenceLoudness = if (visible("reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                base.replayGainReferenceLoudness
            },
            lyrics = if (visible("lyrics")) lyrics else base.lyrics,
            pictures = if (visible("picture")) pictures else base.pictures,
            picUrl = if (visible("picture")) picUrl else base.picUrl,
            rating = if (visible("rating")) rating else base.rating,
        )
    }

    private suspend fun recordSaveFailure(
        uriString: String,
        reason: String,
        throwable: Throwable? = null
    ) {
        try {
            if (throwable == null) {
                appLogRepository.log(
                    level = AppLogLevel.ERROR,
                    type = AppLogType.METADATA,
                    tag = TAG,
                    message = "Failed to save metadata: $reason",
                    detail = buildSaveFailureLog(uriString, reason),
                    relatedId = uriString
                )
            } else {
                appLogRepository.log(
                    level = AppLogLevel.ERROR,
                    type = AppLogType.METADATA,
                    tag = TAG,
                    message = "Failed to save metadata: $reason",
                    detail = buildSaveFailureLog(uriString, reason, throwable),
                    relatedId = uriString
                )
            }
        } catch (logError: Exception) {
            Log.w(TAG, "Failed to write metadata save log", logError)
        }
    }

    private suspend fun recordMetadataFailure(
        message: String,
        relatedId: String? = currentSongUri,
        detail: String? = null,
        level: AppLogLevel = AppLogLevel.ERROR
    ) {
        try {
            appLogRepository.log(
                level = level,
                type = AppLogType.METADATA,
                tag = TAG,
                message = message,
                detail = detail,
                relatedId = relatedId
            )
        } catch (logError: Exception) {
            Log.w(TAG, "Failed to write metadata log", logError)
        }
    }

    private suspend fun recordMetadataException(
        message: String,
        relatedId: String? = currentSongUri,
        throwable: Throwable
    ) {
        try {
            appLogRepository.logException(
                type = AppLogType.METADATA,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        } catch (logError: Exception) {
            Log.w(TAG, "Failed to write metadata exception log", logError)
        }
    }

    private fun buildSaveFailureLog(
        uriString: String,
        reason: String,
        throwable: Throwable? = null
    ): String = buildString {
        appendLine("Save metadata failed")
        appendLine("Uri: $uriString")
        appendLine("Reason: $reason")
        throwable?.let {
            appendLine("Exception: ${it::class.java.name}")
            appendLine(it.stackTraceToString())
        }
    }

    fun calculateReplayGain() {
        val uriString = currentSongUri ?: _uiState.value.songInfo?.uriString ?: return
        if (_uiState.value.isReplayGainCalculating) return

        scanJob = viewModelScope.launch {
            val replayGainSettings = settingsRepository.getReplayGainSettings()
            _uiState.update {
                it.copy(
                    isReplayGainCalculating = true,
                    replayGainCalculateProgress = 0f,
                    replayGainScanMessage = null
                )
            }

            try {
                replayGainScanner.analyze(uriString, replayGainSettings.peakMode)
                    .flowOn(Dispatchers.IO) // 将解码和 DSP 计算放入 IO 线程池，不卡顿 UI
                    .collect { state ->
                        when (state) {
                            is ReplayGainCalculateState.Progress -> {
                                _uiState.update {
                                    it.copy(replayGainCalculateProgress = state.percent)
                                }
                            }

                            is ReplayGainCalculateState.Success -> {
                                _uiState.update { ui ->
                                    val current = ui.editingTagData ?: AudioTagData(fileName = ui.fileName.orEmpty())
                                    ui.copy(
                                        editingTagData = current.copy(
                                            replayGainTrackGain = replayGainScanner.formatGain(
                                                state.analysis,
                                                replayGainSettings.targetLoudness
                                            ),
                                            replayGainTrackPeak = replayGainScanner.formatPeak(state.analysis.peak),
                                            replayGainReferenceLoudness = replayGainScanner.formatReferenceLoudness(
                                                replayGainSettings.targetLoudness
                                            )
                                        ),
                                        isEditing = true,
                                        isReplayGainCalculating = false,
                                        replayGainCalculateProgress = 1.0f,
                                        replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_success)
                                    )
                                }
                            }

                            is ReplayGainCalculateState.Cancelled -> {
                                // 这里处理 Flow 内部抛出异常前发出的 Cancelled 状态
                                _uiState.update { it.copy(
                                    isReplayGainCalculating = false,
                                    replayGainCalculateProgress = 0f,
                                    replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_cancelled)
                                )}
                            }

                            is ReplayGainCalculateState.Failed -> {
                                val errorMsg = mapErrorToUiMessage(state.mimeType, state.error)
                                recordMetadataFailure(
                                    message = "ReplayGain scan failed",
                                    relatedId = uriString,
                                    detail = buildString {
                                        appendLine("Uri: $uriString")
                                        appendLine("MimeType: ${state.mimeType ?: "unknown"}")
                                        appendLine("Error: ${state.error::class.java.name}")
                                        appendLine("Message: ${state.error.toLogMessage()}")
                                    }
                                )
                                _uiState.update { it.copy(
                                    isReplayGainCalculating = false,
                                    replayGainCalculateProgress = 0f,
                                    replayGainScanMessage = errorMsg
                                )}
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // 协程被取消时，collect 可能会停止。如果是外部 job.cancel() 触发，
                // 且 state 没有发回 Cancelled，这里可以补底线逻辑
                if (_uiState.value.isReplayGainCalculating) {
                    _uiState.update { it.copy(isReplayGainCalculating = false, replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_cancelled)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "扫描 ReplayGain 失败: $uriString", e)
                recordMetadataException(
                    message = "ReplayGain scan crashed",
                    relatedId = uriString,
                    throwable = e
                )
                _uiState.update { it.copy(
                    isReplayGainCalculating = false,
                    replayGainCalculateProgress = 0f,
                    replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_failed,e.localizedMessage?: "未知错误")
                )}
            } finally {
                _uiState.update { it.copy(
                    isReplayGainCalculating = false,
                    replayGainCalculateProgress = if (it.replayGainCalculateProgress == 1f) 1f else null
                )}
            }
        }
    }

    /**
     * 具体的 UI 文本映射逻辑
     */
    private fun mapErrorToUiMessage(mimeType: String?, error: ReplayGainError): UiMessage {
        val format = when (mimeType?.lowercase()) {
            "audio/alac" -> "ALAC"
            "audio/ape", "audio/x-ape" -> "APE"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm" -> "AAC"
            null -> "unknown"
            else -> mimeType
        }

        return when (error) {
            is ReplayGainError.NoAudioTrack -> UiMessage.StringResource(R.string.replay_gain_error_no_audio_track)
            is ReplayGainError.UnknownMimeType -> UiMessage.StringResource(R.string.replay_gain_error_unknown_mime_type)
            is ReplayGainError.ZeroSampleCount -> UiMessage.StringResource(R.string.replay_gain_error_zero_sample_count)
            is ReplayGainError.UnsupportedCodec -> UiMessage.StringResource(R.string.replay_gain_error_unsupported_codec, format)
            is ReplayGainError.CodecException -> {
                if (error.isAlacIssue) UiMessage.StringResource(R.string.replay_gain_error_alac_issue)
                else UiMessage.StringResource(R.string.replay_gain_error_codec_exception, error.message ?: "")
            }
            is ReplayGainError.GeneralException -> UiMessage.StringResource(R.string.replay_gain_error_general, error.message ?: "")
        }
    }

    private fun ReplayGainError.toLogMessage(): String = when (this) {
        is ReplayGainError.NoAudioTrack -> "No audio track"
        is ReplayGainError.UnknownMimeType -> "Unknown MIME type"
        is ReplayGainError.ZeroSampleCount -> "Zero sample count"
        is ReplayGainError.UnsupportedCodec -> "Unsupported codec: ${mimeType ?: "unknown"}"
        is ReplayGainError.CodecException -> message.orEmpty()
        is ReplayGainError.GeneralException -> message.orEmpty()
    }
    
    fun cancelScan() {
        scanJob?.cancel()  // 这会触发 ensureActive() 抛出 CancellationException
        scanJob = null
    }
    
    fun clearReplayGainScanMessage() {
        _uiState.update { it.copy(replayGainScanMessage = null) }
    }


    /**
     * UI层在成功发起弹窗或处理完权限请求后调用此方法清理状态
     */
    fun consumePermissionRequest() {
        _uiState.update { it.copy(permissionIntentSender = null) }
    }

    fun clearSaveStatus() {
        _uiState.update {
            it.copy(
                saveSuccess = null,
                saveFailureMessage = null,
                saveFailureLogText = null
            )
        }
    }

    /** 检测歌词格式，返回 "lrc" 或 "ttml"。 */
    private fun getLyricsMimeType(lyrics: String?): String {
        if (lyrics.isNullOrBlank()) return "lrc"
        
        if (lyrics.contains("begin=") && lyrics.contains("end=") && lyrics.contains("<?xml")) {
            return "ttml"
        }
        
        // 检测 LRC 格式时间戳 [mm:ss.xxx] 或 <mm:ss.xxx>
        if (Regex("[\\[<]\\d{1,2}:\\d{2}\\.\\d{2,3}[>\\]]").containsMatchIn(lyrics)) {
            return "lrc"
        }
        
        return "lrc"
    }


    /**
     * 获取导出歌词的默认文件名
     */
    fun getLyricsFileName(): String? {
        val lyrics = _uiState.value.editingTagData?.lyrics ?: return null
        val format = getLyricsMimeType(lyrics)
        val fileExtension = if (format == "ttml") ".ttml" else ".lrc"
        val fileName = _uiState.value.fileName ?:""
        return "$fileName$fileExtension"
    }


    /**
     * 导出歌词
     */
    fun exportLyrics(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val lyrics = _uiState.value.editingTagData?.lyrics
                if (lyrics.isNullOrBlank()) {
                    recordMetadataFailure(
                        message = "Failed to export lyrics: lyrics are empty",
                        relatedId = currentSongUri,
                        detail = "Destination: $uri",
                        level = AppLogLevel.WARNING
                    )
                    _uiState.update { it.copy(exportLyricsResult = false) }
                    return@launch
                }

                requireNotNull(context.contentResolver.openOutputStream(uri)) { "No lyrics output stream: $uri" }.use { outputStream ->
                    outputStream.write(lyrics.toByteArray(Charsets.UTF_8))
                }
                _uiState.update { it.copy(exportLyricsResult = true) }
                appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Lyrics exported",
                    "uri=$currentSongUri\ndestination=$uri\ncharacters=${lyrics.length}\nformat=${LyricDecoder.detectFormat(lyrics)}", currentSongUri, AppLogLevel.INFO)
            } catch (e: Exception) {
                Log.e(TAG, "导出歌词失败", e)
                recordMetadataException(
                    message = "Failed to export lyrics: destination=$uri",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(exportLyricsResult = false) }
            }
        }
    }

    private fun writePictureSource(
        context: Context,
        outputStream: OutputStream,
        source: Any
    ): Boolean = when (getCoverSourceType(source)) {
        CoverSourceType.BYTE_ARRAY -> {
            outputStream.write(source as ByteArray)
            true
        }

        CoverSourceType.NETWORK_URL -> {
            java.net.URL(source.toString().trim()).openStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }

        CoverSourceType.CONTENT_OR_FILE_URI,
        CoverSourceType.URI -> {
            val sourceUri = when (source) {
                is Uri -> source
                is String -> source.trim().toUri()
                else -> null
            }
            sourceUri?.let {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                    true
                }
            } ?: false
        }

        CoverSourceType.FILE_PATH -> {
            java.io.FileInputStream(source.toString().trim()).use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            true
        }

        CoverSourceType.BITMAP -> (source as Bitmap).compress(
            Bitmap.CompressFormat.JPEG,
            100,
            outputStream
        )

        CoverSourceType.UNSUPPORTED -> false
    }

    fun exportLyricsToAudioDirectory(context: Context) {
        viewModelScope.launch {
            try {
                val lyrics = _uiState.value.editingTagData?.lyrics
                val audioUri = (currentSong?.uri ?: currentSongUri)?.toUri()
                val fileName = getLyricsFileName()
                if (lyrics.isNullOrBlank() || audioUri == null || fileName == null) {
                    recordMetadataFailure(
                        message = "Failed to export lyrics next to audio: source or lyrics unavailable",
                        relatedId = currentSongUri,
                        level = AppLogLevel.WARNING
                    )
                    _uiState.update { it.copy(exportLyricsResult = false) }
                    return@launch
                }

                val outputUri = SafSiblingFileWriter.write(
                    context = context,
                    sourceDocumentUri = audioUri,
                    mimeType = LYRICS_EXPORT_MIME_TYPE,
                    fileName = fileName,
                    bytes = lyrics.toByteArray(Charsets.UTF_8)
                )
                _uiState.update { it.copy(exportLyricsResult = true) }
            } catch (e: Exception) {
                Log.e(TAG, "导出歌词到音频同目录失败", e)
                recordMetadataException(
                    message = "Failed to export lyrics next to audio",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(exportLyricsResult = false) }
            }
        }
    }

    /**
     * 导入歌词文件
     */
    fun importLyrics(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val lyrics = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                }

                if (!lyrics.isNullOrBlank()) {
                    lyricsFormatConversionSession = null
                    updateTag { copy(lyrics = lyrics) }
                    _uiState.update { it.copy(importLyricsResult = true) }
                    appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Lyrics imported into draft",
                        "uri=$currentSongUri\nsource=$uri\ncharacters=${lyrics.length}\nformat=${LyricDecoder.detectFormat(lyrics)}", currentSongUri)
                } else {
                    _uiState.update { it.copy(importLyricsResult = false) }
                    Log.w(TAG, "歌词导入失败: 文件内容为空")
                    recordMetadataFailure(
                        message = "Failed to import lyrics: file content is empty",
                        relatedId = currentSongUri,
                        detail = "Source: $uri",
                        level = AppLogLevel.WARNING
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "导入歌词失败", e)
                recordMetadataException(
                    message = "Failed to import lyrics: source=$uri",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(importLyricsResult = false) }
            }
        }
    }

    fun clearExportLyricsStatus() {
        _uiState.update { it.copy(exportLyricsResult = null) }
    }

    /**
     * 对当前歌词进行简繁转换
     */
    fun convertLyrics(conversionMode: ConversionMode) {
        val currentLyrics = _uiState.value.editingTagData?.lyrics ?: return
        if (currentLyrics.isBlank()) return

        val convertedLyrics = LyricEncoder.convertLyricsText(currentLyrics, conversionMode)
        lyricsFormatConversionSession = null
        updateTag { copy(lyrics = convertedLyrics) }
    }

    /** 把歌词转换为 [targetFormat]。 */
    fun convertLyricsFormat(targetFormat: LyricFormat) {
        processLyrics(
            LyricsProcessingOptions(
                targetFormat = targetFormat
            )
        )
    }

    fun processLyrics(options: LyricsProcessingOptions) {
        val currentLyrics = _uiState.value.editingTagData?.lyrics ?: return
        if (currentLyrics.isBlank()) return

        viewModelScope.launch {
            try {
                if (options.formatLineOrder) {
                    val converted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        LyricsColumnSorter.apply(currentLyrics, options.twoColumnMapping, options.threeColumnMapping)
                    }
                    if (converted != currentLyrics && _uiState.value.editingTagData?.lyrics == currentLyrics) {
                        lyricsFormatConversionSession = null
                        updateTag { copy(lyrics = converted) }
                    }
                    if (options.targetFormat == null && !options.removeEmptyLines && !options.removeTagLines) return@launch
                }
                val currentFormat = LyricDecoder.detectFormat(currentLyrics)
                val targetFormat = options.targetFormat ?: currentFormat
                if (options.targetFormat != null && options.targetFormat == currentFormat && !options.hasTextOperations()) return@launch
                val tagLineKeywords = if (options.removeTagLines) {
                    settingsRepository.lyricsTagLineKeywords.first()
                } else {
                    emptyList()
                }

                val converted = if (options.targetFormat == null && !options.formatLineOrder && currentFormat != LyricFormat.TTML) {
                    LyricsTextCleanup.process(
                        raw = currentLyrics,
                        removeEmptyLines = options.removeEmptyLines,
                        tagLineKeywords = tagLineKeywords
                    )
                } else {
                    if (currentFormat == null || targetFormat == null) return@launch
                    LyricsDocumentPipeline.process(
                        raw = currentLyrics,
                        sourceFormat = currentFormat,
                        targetFormat = targetFormat,
                        removeEmptyLines = options.removeEmptyLines,
                        removeTagLineKeywords = tagLineKeywords
                    ) ?: convertLyricsFormatFromCurrent(currentLyrics, targetFormat)
                } ?: return@launch

                if (_uiState.value.editingTagData?.lyrics == currentLyrics) {
                    lyricsFormatConversionSession = null
                    updateTag { copy(lyrics = converted) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "歌词处理失败", e)
                recordMetadataException(
                    message = "Failed to process lyrics",
                    relatedId = currentSongUri,
                    throwable = e
                )
            }
        }
    }

    private fun getOrCreateLyricsFormatConversionSession(
        currentLyrics: String
    ): LyricsFormatConversionSession? {
        lyricsFormatConversionSession
            ?.takeIf { it.lastRenderedLyrics == currentLyrics }
            ?.let { return it }

        val sourceFormat = LyricDecoder.detectFormat(currentLyrics) ?: return null
        return LyricsFormatConversionSession(
            sourceFormat = sourceFormat,
            sourceLyrics = currentLyrics,
            lastRenderedLyrics = currentLyrics
        ).also {
            lyricsFormatConversionSession = it
        }
    }

    private fun convertLyricsFormatFromCurrent(
        currentLyrics: String,
        targetFormat: LyricFormat
    ): String? {
        val lyricsResult = LyricDecoder.decode(currentLyrics) ?: return null
        if (lyricsResult.original.isEmpty()) return null

        val config = LyricRenderConfig(
            format = targetFormat,
            conversionMode = ConversionMode.NONE,
            showTranslation = lyricsResult.translated != null,
            showRomanization = lyricsResult.romanization != null,
            removeEmptyLines = false,
            onlyTranslationIfAvailable = false
        )

        return LyricEncoder.encode(lyricsResult, config).takeIf { it.isNotBlank() }
    }

    fun clearImportLyricsStatus() {
        _uiState.update { it.copy(importLyricsResult = null) }
    }

    fun play(context: Context) {
        val uriStr = currentSong?.uri ?: currentSongUri ?: return
        playbackRepository.play(context, uriStr.toUri())
    }

    /** 获取同专辑歌曲封面，优先同专辑且同歌手的结果。 */
    suspend fun getSameAlbumCovers(): List<Pair<String, Any?>> {
        val currentAlbum = _uiState.value.editingTagData?.album ?: return emptyList()
        val currentArtist = _uiState.value.editingTagData?.artist ?: ""

        val sameAlbumSongs = songLibraryRepository.getSongsByAlbum(currentAlbum, currentArtist)
        val covers = mutableListOf<Pair<String, Any?>>()

        for (song in sameAlbumSongs) {
            if (song.uri == currentSongUri) continue // 跳过当前歌曲
            
            try {
                val tagData = readAudioTagsUseCase(song.uri)
                val cover = tagData.pictures.frontCoverOrFallback()?.data
                if (cover != null) {
                    val title = "${song.title} - ${song.artist}"
                    covers.add(title to cover)
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取同专辑歌曲封面失败: ${song.uri}", e)
                recordMetadataException(
                    message = "Failed to read same-album cover",
                    relatedId = song.uri,
                    throwable = e
                )
            }
        }

        return covers
    }

    /**
     * 加载同专辑封面（直接使用第一张）
     */
    fun loadSameAlbumCovers() {
        viewModelScope.launch {
            try {
                val covers = getSameAlbumCovers()
                if (covers.isNotEmpty()) {
                    val (_, cover) = covers.first()
                    when (cover) {
                        is String -> updateCover(cover)
                        is ByteArray -> {
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(cover, 0, cover.size)
                            updateCover(bitmap)
                        }
                    }
                    _uiState.update { it.copy(sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_same_album_cover_applied)) }
                } else {
                    _uiState.update { it.copy(sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_no_same_album_cover_found)) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载同专辑封面失败", e)
                recordMetadataException(
                    message = "Failed to load same-album cover",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(sameAlbumCoverMessage = UiMessage.StringResource(R.string.msg_load_same_album_cover_failed)) }
            }
        }
    }

    fun clearSameAlbumCoverMessage() {
        _uiState.update { it.copy(sameAlbumCoverMessage = null) }
    }

    fun getPlainLyrics(
        showRomanization: Boolean = true,
        showTranslation: Boolean = true
    ): String? {
        val lyricsResult = LyricDecoder.decode(_uiState.value.editingTagData?.lyrics ?: "")
            ?: return null
        if (lyricsResult.original.isEmpty()) return null
        val config = LyricRenderConfig(
            format = LyricFormat.PLAIN_LRC,
            conversionMode = ConversionMode.NONE,
            showTranslation = showTranslation && lyricsResult.translated != null,
            showRomanization = showRomanization && lyricsResult.romanization != null,
            removeEmptyLines = true,
            onlyTranslationIfAvailable = false
        )
        return LyricEncoder.encodePlainText(lyricsResult, config)
    }
}
