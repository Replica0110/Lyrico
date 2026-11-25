package com.lonx.lyrico.ui.screens.edit

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldScene
import com.lonx.lyrico.data.settings.ArtistSplitConfig
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.log.recordOperation
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsFileNames
import com.lonx.lyrico.data.lyrics.LyricsFileTransfer
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions
import com.lonx.lyrico.data.metadata.MetadataApplyPolicy
import com.lonx.lyrico.data.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.metadata.MetadataWriteMode
import com.lonx.lyrico.data.metadata.StandardPluginField
import com.lonx.lyrico.data.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.song.search.LyricsSearchResult
import com.lonx.lyrico.data.song.search.sanitizeStandardFields
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.platform.player.ExternalPlayerLauncher
import com.lonx.lyrico.data.settings.SettingsDefaults
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.domain.lyrics.LyricsParser
import com.lonx.lyrico.domain.lyrics.LyricsRenderer
import com.lonx.lyrico.domain.lyrics.LyricsTextTransform
import com.lonx.lyrico.domain.lyrics.LyricsProcessor
import com.lonx.lyrico.domain.lyrics.LyricsProcessingResult
import com.lonx.lyrico.domain.metadata.SearchResultApplier
import com.lonx.lyrico.domain.metadata.preserveHiddenEditFields
import com.lonx.lyrico.domain.plugin.PluginFieldPostProcessor
import com.lonx.lyrico.domain.song.usecase.ReadAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.platform.audio.ReplayGainScanner
import com.lonx.lyrico.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SongInfo(
    val uriString: String,
    val tagData: AudioTagData?,
)

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
    val saveFailureMessage: UiMessage? = null,
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
    private val songLibraryRepository: SongLibraryRepository,
    private val readAudioTagsUseCase: ReadAudioTagsUseCase,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase,
    private val settingsRepository: SettingsRepository,
    private val externalPlayerLauncher: ExternalPlayerLauncher,
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
    private var loadedSongUri: String? = null

    /** 打开偏移面板时的歌词快照；偏移始终相对它计算，避免逐次累积误差。 */
    private var preOffsetLyrics: String? = null

    private val replayGainScan = ReplayGainScanController(
        scanner = replayGainScanner,
        settingsRepository = settingsRepository,
        scope = viewModelScope,
    )

    private val _currentShiftOffset = MutableStateFlow(0L)
    val currentShiftOffset: StateFlow<Long> = _currentShiftOffset.asStateFlow()
    private val _uiState = MutableStateFlow(EditMetadataUiState())
    val uiState: StateFlow<EditMetadataUiState> = _uiState.asStateFlow()

    internal val pictures = EditPicturesController(
        scope = viewModelScope,
        _uiState = _uiState,
        songUri = { currentSongUri.takeIf { it == loadedSongUri } },
        artistSplitConfig = { artistSplitConfig.value },
        settingsRepository = settingsRepository,
        songLibraryRepository = songLibraryRepository,
        readAudioTagsUseCase = readAudioTagsUseCase,
        appLogRepository = appLogRepository,
        recordFailure = { message, uri, detail -> recordMetadataFailure(message, uri, detail) },
        recordException = { message, uri, error -> recordMetadataException(message, uri, error) },
    )
    private var metadataReadJob: Job? = null

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
        if (currentSongUri != uriString) pendingWritePermissionUri = null
        val changedSong = currentSongUri != uriString
        val hadLoadedSong = !changedSong && loadedSongUri == uriString
        currentSongUri = uriString
        if (changedSong) {
            loadedSongUri = null
            pictures.onSongChanged(uriString)
        }
        metadataReadJob?.cancel()
        val ticket = pictures.songTicket()
        metadataReadJob = viewModelScope.launch {
            try {
                val song = songLibraryRepository.getSongByUri(uriString)
                val audioTagData = readAudioTagsUseCase(uriString)
                currentCoroutineContext().ensureActive()
                if (!pictures.isCurrent(ticket)) return@launch
                currentSong = song
                loadedSongUri = uriString
                val displayFileName = song?.fileName ?: audioTagData.fileName
                val displayPicture = audioTagData.pictures.frontCoverOrFallback()
                val displayCover = displayPicture?.data

                _uiState.update { state ->
                    val keepDraft = hadLoadedSong && state.isEditing && state.songInfo?.uriString == uriString
                    state.copy(
                        isEditing = keepDraft,
                        songInfo = SongInfo(
                            uriString = uriString,
                            tagData = audioTagData
                        ),
                        originalTagData = audioTagData,
                        fileName = displayFileName.substringBeforeLast(
                            ".",
                            missingDelimiterValue = displayFileName
                        ),
                        editingTagData = if (keepDraft) state.editingTagData else audioTagData,
                        picture = if (keepDraft) state.picture else displayPicture,
                        originalCover = if (keepDraft) state.originalCover else displayCover,
                        coverUri = if (keepDraft) state.coverUri else displayCover
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
        val shiftedLyrics = LyricsTextTransform.shiftOffset(originalLyrics, totalOffset)

        _uiState.update { state ->
            state.copy(
                editingTagData = state.editingTagData?.copy(lyrics = shiftedLyrics),
                isEditing = true
            )
        }
    }
    fun updateMetadataFromSearchResult(result: LyricsSearchResult) {
        val appliesCover = result.applyTargets.isEmpty() || MetadataFieldTarget.COVER in result.applyTargets
        if (appliesCover && !result.normalizedFields()["cover_url"].isNullOrBlank()) {
            pictures.beginCoverEdit()
        }
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
     * 保存元数据
     */
    private var saveAttempt = 0
    private var saveAttemptUri: String? = null
    private var pendingWritePermissionUri: String? = null

    fun onWritePermissionResult(granted: Boolean) {
        val uri = _uiState.value.songInfo?.uriString
        val requestedUri = pendingWritePermissionUri
        pendingWritePermissionUri = null
        val retry = granted && uri != null && uri == requestedUri
        viewModelScope.launch {
            appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Write permission result",
                "uri=$uri\nrequestedUri=$requestedUri\ngranted=$granted\npreviousAttempt=$saveAttempt\nretry=$retry", uri,
                if (granted) AppLogLevel.INFO else AppLogLevel.WARNING)
            if (retry) saveMetadata(afterPermission = true, expectedUri = requestedUri)
        }
    }

    fun saveMetadata() = saveMetadata(afterPermission = false)

    private fun saveMetadata(afterPermission: Boolean, expectedUri: String? = null) {
        val state = _uiState.value
        val uriString = state.songInfo?.uriString ?: return
        if (expectedUri != null && (uriString != expectedUri || currentSongUri != expectedUri)) return
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
                    "uri=$uriString\nattempt=$attempt\nafterPermission=$afterPermission", uriString)
                val audioTagData = editingTagData.preserveHiddenEditFields(
                    original = state.originalTagData,
                    visibleFieldCodes = editFieldConfigRepository.configFlow.first()
                        .visibleFieldCodesForScene(EditFieldScene.SingleEdit),
                )
                when (
                    val saveResult = saveAudioTagsUseCase.overwrite(
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
                        handleWritePermissionRequired(uriString, attempt, saveResult.intentSender, afterPermission)
                    }
                    is SaveAudioTagsResult.Failed -> {
                        val reason = saveResult.error.localizedMessage
                            ?: saveResult.error::class.java.simpleName
                        recordSaveFailure(uriString, reason, saveResult.error)
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = false,
                                saveFailureMessage = UiMessage.DynamicString(reason),
                                saveFailureLogText = buildSaveFailureLog(uriString, reason)
                            )
                        }
                    }
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    handleWritePermissionRequired(uriString, attempt, e.userAction.actionIntent.intentSender, afterPermission)
                } else {
                    Log.e(TAG, "保存元数据发生未知错误", e)
                    val reason = e.localizedMessage ?: e::class.java.simpleName
                    recordSaveFailure(uriString, reason, e)
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saveSuccess = false,
                            saveFailureMessage = UiMessage.DynamicString(reason),
                            saveFailureLogText = buildSaveFailureLog(uriString, reason, e)
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleWritePermissionRequired(
        uri: String,
        attempt: Int,
        sender: IntentSender,
        afterPermission: Boolean,
    ) {
        if (afterPermission) {
            val reason = "Write access is still unavailable after authorization"
            recordSaveFailure(uri, reason)
            _uiState.update { it.copy(
                isSaving = false,
                saveSuccess = false,
                permissionIntentSender = null,
                saveFailureMessage = UiMessage.StringResource(R.string.write_permission_retry_failed),
                saveFailureLogText = buildSaveFailureLog(uri, reason),
            ) }
        } else {
            pendingWritePermissionUri = uri
            appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Editor waiting for write permission",
                "uri=$uri\nattempt=$attempt", uri, AppLogLevel.WARNING)
            _uiState.update { it.copy(isSaving = false, permissionIntentSender = sender) }
        }
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
        } catch (logError: CancellationException) {
            throw logError
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
        } catch (logError: CancellationException) {
            throw logError
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

        _uiState.update {
            it.copy(
                isReplayGainCalculating = true,
                replayGainCalculateProgress = 0f,
                replayGainScanMessage = null
            )
        }
        replayGainScan.start(uriString) { event -> onReplayGainEvent(uriString, event) }
    }

    fun cancelScan() {
        replayGainScan.cancel()
    }

    /** 扫描结果落到编辑态；日志与提示文案的取舍都留在这里。 */
    private fun onReplayGainEvent(uriString: String, event: ReplayGainScanEvent) {
        when (event) {
            is ReplayGainScanEvent.Progress -> _uiState.update {
                it.copy(replayGainCalculateProgress = event.percent)
            }

            is ReplayGainScanEvent.Succeeded -> _uiState.update { ui ->
                val current = ui.editingTagData ?: AudioTagData(fileName = ui.fileName.orEmpty())
                ui.copy(
                    editingTagData = current.copy(
                        replayGainTrackGain = event.trackGain,
                        replayGainTrackPeak = event.trackPeak,
                        replayGainReferenceLoudness = event.referenceLoudness
                    ),
                    isEditing = true,
                    isReplayGainCalculating = false,
                    replayGainCalculateProgress = 1f,
                    replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_success)
                )
            }

            ReplayGainScanEvent.Cancelled -> _uiState.update {
                it.copy(
                    isReplayGainCalculating = false,
                    replayGainCalculateProgress = 0f,
                    replayGainScanMessage = UiMessage.StringResource(R.string.replay_gain_calculate_cancelled)
                )
            }

            is ReplayGainScanEvent.Failed -> {
                viewModelScope.launch {
                    recordMetadataFailure(
                        message = "ReplayGain scan failed",
                        relatedId = uriString,
                        detail = event.detail
                    )
                }
                _uiState.update {
                    it.copy(
                        isReplayGainCalculating = false,
                        replayGainCalculateProgress = 0f,
                        replayGainScanMessage = event.message
                    )
                }
            }

            is ReplayGainScanEvent.Crashed -> {
                Log.e(TAG, "扫描 ReplayGain 失败: $uriString", event.error)
                viewModelScope.launch {
                    recordMetadataException(
                        message = "ReplayGain scan crashed",
                        relatedId = uriString,
                        throwable = event.error
                    )
                }
                _uiState.update {
                    it.copy(
                        isReplayGainCalculating = false,
                        replayGainCalculateProgress = 0f,
                        replayGainScanMessage = UiMessage.StringResource(
                            R.string.replay_gain_calculate_failed,
                            event.error.localizedMessage ?: "未知错误"
                        )
                    )
                }
            }

            ReplayGainScanEvent.Finished -> _uiState.update {
                it.copy(
                    isReplayGainCalculating = false,
                    replayGainCalculateProgress = if (it.replayGainCalculateProgress == 1f) 1f else null
                )
            }
        }
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
     * 获取导出歌词的默认文件名；没有歌词时返回 null。
     */
    fun getLyricsFileName(): String? =
        LyricsFileNames.defaultName(_uiState.value.fileName, _uiState.value.editingTagData?.lyrics)

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

                LyricsFileTransfer.write(context, uri, lyrics)
                _uiState.update { it.copy(exportLyricsResult = true) }
                appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Lyrics exported",
                    "uri=$currentSongUri\ndestination=$uri\ncharacters=${lyrics.length}\nformat=${LyricsParser.detectFormat(lyrics)}", currentSongUri, AppLogLevel.INFO)
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

                LyricsFileTransfer.writeNextToAudio(
                    context = context,
                    audioUri = audioUri,
                    fileName = fileName,
                    lyrics = lyrics
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
                val lyrics = LyricsFileTransfer.read(context, uri)

                if (!lyrics.isNullOrBlank()) {
                    updateTag { copy(lyrics = lyrics) }
                    _uiState.update { it.copy(importLyricsResult = true) }
                    appLogRepository.recordOperation(AppLogType.METADATA, TAG, "Lyrics imported into draft",
                        "uri=$currentSongUri\nsource=$uri\ncharacters=${lyrics.length}\nformat=${LyricsParser.detectFormat(lyrics)}", currentSongUri)
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

        viewModelScope.launch {
            try {
                val converted = withContext(Dispatchers.Default) {
                    LyricsTextTransform.convertLyricsScript(currentLyrics, conversionMode)
                }
                if (_uiState.value.editingTagData?.lyrics == currentLyrics && converted != currentLyrics) {
                    updateTag { copy(lyrics = converted) }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                recordMetadataException("Failed to convert lyrics script", throwable = error)
            }
        }
    }

    fun processLyrics(options: LyricsProcessingOptions) {
        val currentLyrics = _uiState.value.editingTagData?.lyrics ?: return
        if (currentLyrics.isBlank()) return

        viewModelScope.launch {
            try {
                val tagLineKeywords = if (options.removeTagLines) {
                    settingsRepository.lyricsTagLineKeywords.first()
                } else {
                    emptyList()
                }
                val effectiveOptions = options.copy(tagLineKeywords = tagLineKeywords)
                val processed = withContext(Dispatchers.Default) {
                    LyricsProcessor.process(currentLyrics, effectiveOptions)
                }
                val converted = when (processed) {
                    is LyricsProcessingResult.Changed -> processed.text
                    LyricsProcessingResult.Empty,
                    LyricsProcessingResult.Unchanged,
                    is LyricsProcessingResult.Unsupported -> return@launch
                }

                // 处理期间用户可能已经改了歌词，此时不覆盖。
                if (_uiState.value.editingTagData?.lyrics == currentLyrics) {
                    updateTag { copy(lyrics = converted) }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "歌词处理失败", e)
                recordMetadataException(
                    message = "Failed to process lyrics",
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
        viewModelScope.launch { externalPlayerLauncher.launch(context, uriStr.toUri()) }
    }

    fun getPlainLyrics(
        showRomanization: Boolean = true,
        showTranslation: Boolean = true
    ): String? {
        val lyricsResult = LyricsParser.parseOrNull(_uiState.value.editingTagData?.lyrics ?: "")
            ?: return null
        if (lyricsResult.original.isEmpty()) return null
        val config = LyricsRenderConfig(
            format = LyricsFormat.PLAIN_LRC,
            conversionMode = ConversionMode.NONE,
            showTranslation = showTranslation && lyricsResult.translated != null,
            showRomanization = showRomanization && lyricsResult.romanization != null,
            removeEmptyLines = true,
            onlyTranslationIfAvailable = false
        )
        return LyricsRenderer.renderPlainText(lyricsResult, config)
    }
}
