package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.worker.BatchTaskScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class BatchLyricsFormatUiState(
    val isRunning: Boolean = false,
    val concurrency: Int = 3,
    val targetFormat: LyricFormat = LyricFormat.VERBATIM_LRC,
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
    private val songRepository: SongRepository,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
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
                _uiState.update {
                    it.copy(
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

    fun openConfig(initialConcurrency: Int) {
        _uiState.update {
            it.copy(
                concurrency = initialConcurrency.coerceIn(1, 5),
                showConfigDialog = true
            )
        }
    }

    fun closeConfig() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun setConcurrency(count: Int) {
        _uiState.update { it.copy(concurrency = count.coerceIn(1, 5)) }
    }

    fun setTargetFormat(targetFormat: LyricFormat) {
        _uiState.update { it.copy(targetFormat = targetFormat) }
    }

    fun startBatchConvert() {
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        val concurrency = _uiState.value.concurrency
        val targetFormat = _uiState.value.targetFormat

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
                songRepository.getSongByUri(uri)
            }
            if (songs.isEmpty()) {
                _uiState.update { it.copy(isRunning = false, showProgressDialog = false) }
                return@launch
            }

            val configJson = Json.encodeToString(
                LyricsFormatConfig.serializer(),
                LyricsFormatConfig(targetFormat = targetFormat, concurrency = concurrency)
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

@kotlinx.serialization.Serializable
data class LyricsFormatConfig(
    val targetFormat: LyricFormat,
    val concurrency: Int
)
