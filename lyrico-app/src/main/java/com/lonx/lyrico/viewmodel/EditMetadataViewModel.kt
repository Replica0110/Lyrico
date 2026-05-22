package com.lonx.lyrico.viewmodel

import android.app.RecoverableSecurityException
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
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldScene
import com.lonx.lyrico.data.editfield.EditFieldVisibilityRepository
import com.lonx.lyrico.data.editfield.VisibleEditFieldGroup
import com.lonx.lyrico.data.exception.RequiresUserPermissionException
import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.data.model.MetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.CoverSourceType
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MetadataFieldResolver
import com.lonx.lyrico.utils.ReplayGainCalculateState
import com.lonx.lyrico.utils.ReplayGainError
import com.lonx.lyrico.utils.ReplayGainScanner
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.getCoverSourceType
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class EditMetadataViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val replayGainScanner: ReplayGainScanner,
    private val appLogRepository: AppLogRepository,
    private val editFieldVisibilityRepository: EditFieldVisibilityRepository,
    private val searchSourceProvider: SearchSourceProvider
) : ViewModel() {

    private val TAG = "EditMetadataVM"
    private val metadataFieldResolver = MetadataFieldResolver()

    val limitLyricsInputLines = settingsRepository.limitLyricsInputLines.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsDefaults.LIMIT_LYRICS_INPUT_LINES
    )
    private val metadataFieldWriteRules = settingsRepository.metadataFieldWriteRules.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    private val searchSources = searchSourceProvider.observeAllSources().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )
    private var currentSong: SongEntity? = null

    // 存储当前正在操作的 URI 字符串
    private var currentSongUri: String? = null
    private var preOffsetLyrics: String? = null
    private var scanJob: Job? = null
    // 记录当前的累计偏移量，供 UI 显示
    private val _currentShiftOffset = MutableStateFlow(0L)
    val currentShiftOffset: StateFlow<Long> = _currentShiftOffset.asStateFlow()
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    val visibleFieldGroups: StateFlow<List<VisibleEditFieldGroup>> =
        editFieldVisibilityRepository.configFlow
            .map { config ->
                config.visibleGroupsForScene(EditFieldScene.SingleEdit)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun readMetadata(uriString: String) {
        currentSongUri = uriString

        viewModelScope.launch {
            try {
                // 1. 获取数据库实体
                val song = songRepository.getSongByUri(uriString)
                currentSong = song

                // 2. 读取文件标签
                val audioTagData = songRepository.readAudioTagData(uriString)
                val firstPicture = audioTagData.pictures.firstOrNull()?.data
                val displayFileName = song?.fileName ?: audioTagData.fileName

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
                        // 如果当前没有在编辑，才重置 editingTagData
                        editingTagData = if (state.isEditing) state.editingTagData else audioTagData,

                        picture = audioTagData.pictures.firstOrNull(),
                        originalCover = if (state.isEditing) state.originalCover else firstPicture,
                        coverUri = if (state.isEditing) state.coverUri else firstPicture
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

        // 1. 更新当前显示的数值
        _currentShiftOffset.value = totalOffset

        // 2. 永远基于 originalLyrics (快照) 进行偏移，避免来回计算导致的精度丢失或触底失真
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

            if (result.lyricsOnly) {
                return@update state.copy(
                    isEditing = true,
                    editingTagData = current.copy(
                        lyrics = result.lyrics?.takeIf { it.isNotBlank() } ?: current.lyrics
                    )
                )
            }
            // Resolve plugin-declared metadata fields.
            val standardTagData = current.copy(
                title = result.title?.takeIf { it.isNotBlank() } ?: current.title,
                artist = result.artist?.takeIf { it.isNotBlank() } ?: current.artist,
                album = result.album?.takeIf { it.isNotBlank() } ?: current.album,
                lyrics = result.lyrics?.takeIf { it.isNotBlank() } ?: current.lyrics,
                date = result.date?.takeIf { it.isNotBlank() } ?: current.date,
                trackNumber = result.trackerNumber?.takeIf { it.isNotBlank() }
                    ?: current.trackNumber,
                picUrl = result.picUrl?.takeIf { it.isNotBlank() } ?: current.picUrl,
                comment = result.normalizedFields()["subtitle"]?.takeIf { it.isNotBlank() } ?: current.comment,
            )
            val metadataTagData = currentSong?.let { song ->
                if (result.pluginId.isBlank()) {
                    AudioTagData()
                } else {
                    metadataFieldResolver.resolve(
                        currentSong = song,
                        scoredResults = listOf(
                            ScoredSearchResult(
                                result = SongSearchResult(
                                    id = "",
                                    pluginId = result.pluginId,
                                    pluginName = result.pluginName,
                                    title = result.title.orEmpty(),
                                    artist = result.artist.orEmpty(),
                                    album = result.album.orEmpty(),
                                    duration = 0L,
                                    date = result.date.orEmpty(),
                                    trackNumber = result.trackerNumber.orEmpty(),
                                    picUrl = result.picUrl.orEmpty(),
                                    fields = result.fields
                                ),
                                score = 1.0
                            )
                        ),
                        rules = MetadataFieldWriteRuleFactory.mergeWithDeclaredFields(
                            savedRules = metadataFieldWriteRules.value,
                            searchSources = searchSources.value
                        ),
                        currentTagData = current
                    )
                }
            } ?: AudioTagData()
            state.copy(
                isEditing = true,
                editingTagData = metadataFieldResolver.mergeNonNull(standardTagData, metadataTagData),
                coverUri = result.picUrl?.takeIf { it.isNotBlank() }
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
        // 将 Bitmap 压缩为 JPEG 格式的 ByteArray
        val byteArray = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            stream.toByteArray()
        }
        
        // 创建 AudioPicture 对象
        val audioPicture = AudioPicture(
            data = byteArray,
            mimeType = "image/jpeg",
            description = "",
            pictureType = "Front Cover"
        )
        
        _uiState.update { state ->
            state.copy(
                coverUri = byteArray,
                picture = audioPicture,
                isEditing = true,
                editingTagData = state.editingTagData?.copy(
                    pictures = listOf(audioPicture),
                    picUrl = null
                )
            )
        }
    }

    /**
     * 移除封面
     */
    fun removeCover() {
        _uiState.update { state ->
            state.copy(
                coverUri = null, // 清空 coverUri 表示被移除
                isEditing = true,
                editingTagData = state.editingTagData?.copy(picUrl = "")
            )
        }
    }

    /**
     * 导出当前封面到本地相册
     */
    fun exportCover(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val state = _uiState.value
                val coverSource = state.coverUri ?: state.originalCover ?: state.picture?.data

                if (coverSource == null) {
                    recordMetadataFailure(
                        message = "Failed to export cover: no cover source",
                        relatedId = currentSongUri,
                        detail = "No embedded cover, selected cover, or original cover is available."
                    )
                    _uiState.update { it.copy(exportCoverResult = false) }
                    return@launch
                }

                val filename = "Cover_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Covers")
                }

                val resolver = context.contentResolver
                val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (destUri != null) {
                    val wroteCover = resolver.openOutputStream(destUri)?.use { outputStream ->
                        when (getCoverSourceType(coverSource)) {
                            CoverSourceType.BYTE_ARRAY -> {
                                outputStream.write(coverSource as ByteArray)
                                true
                            }

                            CoverSourceType.NETWORK_URL -> {
                                java.net.URL(coverSource.toString().trim()).openStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                                true
                            }

                            CoverSourceType.CONTENT_OR_FILE_URI,
                            CoverSourceType.URI -> {
                                val sourceUri = when (coverSource) {
                                    is Uri -> coverSource
                                    is String -> coverSource.trim().toUri()
                                    else -> null
                                }
                                sourceUri?.let {
                                    resolver.openInputStream(it)?.use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                        true
                                    }
                                } ?: false
                            }

                            CoverSourceType.FILE_PATH -> {
                                java.io.FileInputStream(coverSource.toString().trim()).use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                                true
                            }

                            CoverSourceType.BITMAP,
                            CoverSourceType.UNSUPPORTED -> false
                        }
                    } ?: false

                    if (wroteCover) {
                        _uiState.update { it.copy(exportCoverResult = true) }
                    } else {
                        recordMetadataFailure(
                            message = "Failed to export cover: source stream unavailable",
                            relatedId = currentSongUri,
                            detail = "Source: $coverSource"
                        )
                        _uiState.update { it.copy(exportCoverResult = false) }
                    }
                } else {
                    recordMetadataFailure(
                        message = "Failed to export cover: MediaStore insert returned null",
                        relatedId = currentSongUri,
                        detail = "Destination image URI could not be created."
                    )
                    _uiState.update { it.copy(exportCoverResult = false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "导出封面失败", e)
                recordMetadataException(
                    message = "Failed to export cover",
                    relatedId = currentSongUri,
                    throwable = e
                )
                _uiState.update { it.copy(exportCoverResult = false) }
            }
        }
    }

    fun clearExportCoverStatus() {
        _uiState.update { it.copy(exportCoverResult = null) }
    }
    fun revertCover() {
        _uiState.update {
            it.copy(
                coverUri = it.originalCover,
                editingTagData = it.editingTagData?.copy(picUrl = null)
            )
        }
    }

    /**
     * 保存元数据
     */
    fun saveMetadata() {
        val state = _uiState.value
        val uriString = state.songInfo?.uriString ?: return
        val editingTagData = state.editingTagData ?: return
        val audioTagData = editingTagData.filterHiddenEditFields(
            original = state.originalTagData,
            visibleFieldCodes = currentVisibleFieldCodes(),
        )

        if (_uiState.value.isSaving) return

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
                val success = songRepository.overwriteAudioTags(uriString, audioTagData)

                if (success) {
                    val newModifiedTime = System.currentTimeMillis()

                    val updateSuccess = if (currentSong != null) {
                        songRepository.updateSongMetadata(
                            audioTagData,
                            uriString, // 传入 URI
                            newModifiedTime
                        )
                    } else {
                        true
                    }

                    if (updateSuccess) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = true,
                                isEditing = false,
                                originalTagData = audioTagData,
                                editingTagData = audioTagData,
                                originalCover = audioTagData.pictures.firstOrNull()?.data
                                    ?: audioTagData.picUrl?.takeIf { picUrl -> picUrl.isNotBlank() },
                                coverUri = audioTagData.pictures.firstOrNull()?.data
                                    ?: audioTagData.picUrl?.takeIf { picUrl -> picUrl.isNotBlank() },
                                picture = audioTagData.pictures.firstOrNull(),
                            )
                        }
                    } else {
                        val reason = "Database metadata update returned false"
                        recordSaveFailure(uriString, reason)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = false,
                                saveFailureMessage = reason,
                                saveFailureLogText = buildSaveFailureLog(uriString, reason)
                            )
                        }
                    }
                } else {
                    // 逻辑上的写入失败（非权限问题）
                    val reason = "Audio tag writer returned false"
                    recordSaveFailure(uriString, reason)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = false,
                            saveFailureMessage = reason,
                            saveFailureLogText = buildSaveFailureLog(uriString, reason)
                        )
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

    private fun currentVisibleFieldCodes(): Set<String> {
        return visibleFieldGroups.value
            .flatMap { it.fields }
            .map { it.code }
            .toSet()
    }

    private fun AudioTagData.filterHiddenEditFields(
        original: AudioTagData?,
        visibleFieldCodes: Set<String>,
    ): AudioTagData {
        val base = original ?: return this

        fun visible(code: String): Boolean = code in visibleFieldCodes

        return copy(
            title = if (visible("basic_info.title")) title else base.title,
            artist = if (visible("basic_info.artist")) artist else base.artist,
            albumArtist = if (visible("basic_info.album_artist")) albumArtist else base.albumArtist,
            album = if (visible("basic_info.album")) album else base.album,
            date = if (visible("basic_info.date")) date else base.date,
            language = if (visible("basic_info.language")) language else base.language,
            genre = if (visible("basic_info.genre")) genre else base.genre,
            trackNumber = if (visible("track_details.track_number")) trackNumber else base.trackNumber,
            discNumber = if (visible("track_details.disc_number")) discNumber else base.discNumber,
            composer = if (visible("credits_other.composer")) composer else base.composer,
            lyricist = if (visible("credits_other.lyricist")) lyricist else base.lyricist,
            copyright = if (visible("credits_other.copyright")) copyright else base.copyright,
            comment = if (visible("credits_other.comment")) comment else base.comment,
            replayGainTrackGain = if (visible("replay_gain.track_gain")) {
                replayGainTrackGain
            } else {
                base.replayGainTrackGain
            },
            replayGainTrackPeak = if (visible("replay_gain.track_peak")) {
                replayGainTrackPeak
            } else {
                base.replayGainTrackPeak
            },
            replayGainAlbumGain = if (visible("replay_gain.album_gain")) {
                replayGainAlbumGain
            } else {
                base.replayGainAlbumGain
            },
            replayGainAlbumPeak = if (visible("replay_gain.album_peak")) {
                replayGainAlbumPeak
            } else {
                base.replayGainAlbumPeak
            },
            replayGainReferenceLoudness = if (visible("replay_gain.reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                base.replayGainReferenceLoudness
            },
            customFields = if (visible("custom_tags.custom_tags")) customFields else base.customFields,
            lyrics = if (visible("lyrics.lyrics")) lyrics else base.lyrics,
            pictures = if (visible("cover.picture")) pictures else base.pictures,
            picUrl = if (visible("cover.picture")) picUrl else base.picUrl,
            rating = if (visible("cover.rating")) rating else base.rating,
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
            _uiState.update {
                it.copy(
                    isReplayGainCalculating = true,
                    replayGainCalculateProgress = 0f,
                    replayGainScanMessage = null
                )
            }

            try {
                replayGainScanner.analyze(uriString)
                    .flowOn(Dispatchers.IO) // 将解码和 DSP 计算放入 IO 线程池，不卡顿 UI
                    .collect { state ->     // 持续监听发射出来的状态
                        when (state) {
                            is ReplayGainCalculateState.Progress -> {
                                // 实时刷新进度条
                                _uiState.update {
                                    it.copy(replayGainCalculateProgress = state.percent)
                                }
                            }

                            is ReplayGainCalculateState.Success -> {
                                // 扫描成功，写入标签
                                _uiState.update { ui ->
                                    val current = ui.editingTagData ?: AudioTagData(fileName = ui.fileName.orEmpty())
                                    ui.copy(
                                        editingTagData = current.copy(
                                            replayGainTrackGain = replayGainScanner.formatGain(state.analysis),
                                            replayGainTrackPeak = replayGainScanner.formatPeak(state.analysis.peak),
                                            replayGainReferenceLoudness = "-18 LUFS"
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
                                // 根据 state.error 映射具体的错误信息
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

    /**
     * 检测歌词格式：LRC 或 TTML
     * 返回 "lrc" 或 "ttml"
     */
    private fun getLyricsMimeType(lyrics: String?): String {
        if (lyrics.isNullOrBlank()) return "lrc"
        
        // 检测 TTML 格式
        if (lyrics.contains("begin=") && lyrics.contains("end=") && lyrics.contains("<?xml")) {
            return "ttml"
        }
        
        // 检测 LRC 格式时间戳 [mm:ss.xxx] 或 <mm:ss.xxx>
        if (Regex("[\\[<]\\d{1,2}:\\d{2}\\.\\d{2,3}[>\\]]").containsMatchIn(lyrics)) {
            return "lrc"
        }
        
        // 默认返回 lrc
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

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(lyrics.toByteArray(Charsets.UTF_8))
                }
                _uiState.update { it.copy(exportLyricsResult = true) }
                Log.d(TAG, "歌词导出成功: ${uri.path}")
            } catch (e: Exception) {
                Log.e(TAG, "导出歌词失败", e)
                recordMetadataException(
                    message = "Failed to export lyrics",
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
                    updateTag { copy(lyrics = lyrics) }
                    _uiState.update { it.copy(importLyricsResult = true) }
                    Log.d(TAG, "歌词导入成功")
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
                    message = "Failed to import lyrics",
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
        updateTag { copy(lyrics = convertedLyrics) }
    }

    /**
     * 转换歌词格式
     * @param targetFormat 目标格式
     */
    fun convertLyricsFormat(targetFormat: LyricFormat) {
        val currentLyrics = _uiState.value.editingTagData?.lyrics ?: return
        if (currentLyrics.isBlank()) return

        viewModelScope.launch {
            try {
                val lyricsResult = LyricDecoder.decode(currentLyrics)
                    ?: return@launch
                if (lyricsResult.original.isEmpty()) return@launch

                // 3. 配置渲染参数
                val config = LyricRenderConfig(
                    format = targetFormat,
                    conversionMode = ConversionMode.NONE,
                    showTranslation = lyricsResult.translated != null,
                    showRomanization = lyricsResult.romanization != null,
                    removeEmptyLines = true,
                    onlyTranslationIfAvailable = false
                )

                // 4. 编码：Model → String
                val converted = LyricEncoder.encode(lyricsResult, config)
                updateTag { copy(lyrics = converted) }
            } catch (e: Exception) {
                Log.e(TAG, "歌词格式转换失败", e)
                recordMetadataException(
                    message = "Failed to convert lyrics format",
                    relatedId = currentSongUri,
                    throwable = e
                )
            }
        }
    }

    fun clearImportLyricsStatus() {
        _uiState.update { it.copy(importLyricsResult = null) }
    }

    fun play(context: Context) {
        val uriStr = currentSong?.uri ?: currentSongUri ?: return
        playbackRepository.play(context, uriStr.toUri())
    }

    /**
     * 获取同专辑歌曲封面
     * 优先使用同专辑且同歌手的查询结果作为封面
     */
    suspend fun getSameAlbumCovers(): List<Pair<String, Any?>> {
        val currentAlbum = _uiState.value.editingTagData?.album ?: return emptyList()
        val currentArtist = _uiState.value.editingTagData?.artist ?: ""

        val sameAlbumSongs = songRepository.getSongsByAlbum(currentAlbum, currentArtist)
        val covers = mutableListOf<Pair<String, Any?>>()

        for (song in sameAlbumSongs) {
            if (song.uri == currentSongUri) continue // 跳过当前歌曲
            
            try {
                val tagData = songRepository.readAudioTagData(song.uri)
                val cover = tagData.pictures.firstOrNull()?.data ?: tagData.picUrl
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
