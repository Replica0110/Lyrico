package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.lyrics.LyricsOperation
import com.lonx.lyrico.data.model.lyrics.LyricsFormatConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricsProcessingOptions
import com.lonx.lyrico.data.model.lyrics.LyricsColumnMapping
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.worker.BatchTaskScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class LyricsSongDraft(
    val uri: String,
    val title: String,
)

data class BatchLyricsFormatUiState(
    val operation: LyricsOperation = LyricsOperation.CONVERT,
    val isRunning: Boolean = false,
    val concurrency: Int = 3,
    val targetFormat: LyricFormat? = null,
    val formatLineOrder: Boolean = false,
    val removeTagLines: Boolean = false,
    val tagLineKeywords: List<String> = LyricsProcessingOptions.DefaultTagLineKeywords,
    val removeEmptyLines: Boolean = false,
    val twoColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(2),
    val threeColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(3),
    val progress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeMillis: Long = 0,
    val showConfigDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val isSuccess: Boolean = false,
    val currentTaskId: String? = null,
    val currentFile: String? = null,
)

class BatchLyricsFormatViewModel(
    private val songLibraryRepository: SongLibraryRepository,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchLyricsFormatUiState())
    val uiState: StateFlow<BatchLyricsFormatUiState> = _uiState.asStateFlow()

    private var selectedUris: List<String> = emptyList()
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.CONVERT_LYRICS_FORMAT)
            if (runningTask != null) {
                resumeObservingTask(runningTask.taskId)
            }
        }
        viewModelScope.launch {
            settingsRepository.lyricsTagLineKeywords.collect { keywords ->
                _uiState.update { it.copy(tagLineKeywords = keywords) }
            }
        }
    }

    private fun resumeObservingTask(taskId: String) {
        observeJob?.cancel()
        _uiState.update {
            it.copy(
                currentTaskId = taskId,
                showProgressDialog = true,
                isRunning = true,
                progress = 0 to 0
            )
        }
        observeJob = viewModelScope.launch {
            batchTaskRepository.observeTask(taskId).collect { task ->
                if (task == null) return@collect
                val config = task.configJson?.let { raw -> runCatching { Json.decodeFromString<LyricsFormatConfig>(raw) }.getOrNull() }
                _uiState.update {
                    it.copy(
                        operation = config?.operation ?: it.operation,
                        targetFormat = config?.targetFormat ?: it.targetFormat,
                        twoColumnMapping = config?.twoColumnMapping ?: it.twoColumnMapping,
                        threeColumnMapping = config?.threeColumnMapping ?: it.threeColumnMapping,
                        progress = task.current to task.total,
                        successCount = task.successCount,
                        failureCount = task.failureCount,
                        skippedCount = task.skippedCount,
                        currentFile = task.currentFile,
                        isRunning = task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED,
                        isSuccess = task.status == BatchTaskStatus.SUCCEEDED && task.failureCount == 0
                    )
                }
                if (task.status == BatchTaskStatus.SUCCEEDED ||
                    task.status == BatchTaskStatus.FAILED ||
                    task.status == BatchTaskStatus.CANCELLED
                ) {
                    val duration = if (task.startedAt != null && task.finishedAt != null) {
                        task.finishedAt - task.startedAt
                    } else 0L
                    _uiState.update { it.copy(isRunning = false, totalTimeMillis = duration) }
                    observeJob?.cancel()
                }
            }
        }
    }

    fun setSelectionUris(uris: List<String>) {
        selectedUris = uris
    }

    fun openConfig(initialConcurrency: Int, operation: LyricsOperation = LyricsOperation.CONVERT) {
        _uiState.update {
            it.copy(concurrency = initialConcurrency.coerceIn(1, 5), operation = operation,
                targetFormat = LyricFormat.PLAIN_LRC, formatLineOrder = false,
                removeEmptyLines = operation == LyricsOperation.REMOVE_EMPTY,
                removeTagLines = operation == LyricsOperation.REMOVE_TAGS,
                twoColumnMapping = LyricsColumnMapping.identity(2),
                threeColumnMapping = LyricsColumnMapping.identity(3),
                showConfigDialog = true)
        }
    }

    fun closeConfig() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun setConcurrency(count: Int) {
        _uiState.update { it.copy(concurrency = count.coerceIn(1, 5)) }
    }

    fun setTargetFormat(targetFormat: LyricFormat?) {
        _uiState.update { it.copy(targetFormat = targetFormat) }
    }

    fun setTwoColumnMapping(mapping: LyricsColumnMapping) { _uiState.update { it.copy(twoColumnMapping = mapping) } }
    fun setThreeColumnMapping(mapping: LyricsColumnMapping) { _uiState.update { it.copy(threeColumnMapping = mapping) } }

    fun startBatchConvert() {
        val state = _uiState.value
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        val concurrency = state.concurrency
        val options = LyricsProcessingOptions(
            targetFormat = if (state.operation == LyricsOperation.CONVERT) state.targetFormat else null,
            formatLineOrder = state.operation == LyricsOperation.SORT || state.formatLineOrder,
            removeTagLines = _uiState.value.removeTagLines,
            tagLineKeywords = _uiState.value.tagLineKeywords,
            removeEmptyLines = _uiState.value.removeEmptyLines,
            twoColumnMapping = state.twoColumnMapping,
            threeColumnMapping = state.threeColumnMapping
        )
        if (options.targetFormat == null && !options.hasTextOperations()) return

        _uiState.update {
            it.copy(
                showConfigDialog = false,
                showProgressDialog = true,
                isRunning = true,
                progress = 0 to 0,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0,
                totalTimeMillis = 0L,
                isSuccess = false
            )
        }

        viewModelScope.launch {
            val songs = uris.mapNotNull { uri ->
                songLibraryRepository.getSongByUri(uri)
            }
            if (songs.isEmpty()) {
                _uiState.update { it.copy(isRunning = false, showProgressDialog = false) }
                return@launch
            }

            val configJson = Json.encodeToString(
                LyricsFormatConfig.serializer(),
                LyricsFormatConfig(
                    targetFormat = options.targetFormat,
                    concurrency = concurrency,
                    formatLineOrder = options.formatLineOrder,
                    removeTagLines = options.removeTagLines,
                    tagLineKeywords = options.tagLineKeywords,
                    removeEmptyLines = options.removeEmptyLines,
                    twoColumnMapping = options.twoColumnMapping,
                    threeColumnMapping = options.threeColumnMapping,
                    operation = state.operation
                )
            )
            val taskId = batchTaskRepository.createTask(
                type = BatchTaskType.CONVERT_LYRICS_FORMAT,
                songs = songs,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId)
        }
    }

    fun abortBatchConvert() {
        val taskId = _uiState.value.currentTaskId
        if (taskId != null) {
            batchTaskScheduler.cancel(taskId)
            viewModelScope.launch {
                batchTaskRepository.markCancelled(taskId)
            }
        }
    }

    fun closeProgressDialog() {
        _uiState.update {
            it.copy(
                showProgressDialog = false
            )
        }
    }
    fun clearProgressDialog() {
        _uiState.update {
            it.copy(
                progress = null,
                isRunning = false
            )
        }
    }
}
