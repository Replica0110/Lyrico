package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.worker.BatchTaskScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class BatchReplayGainUiState(
    val isRunning: Boolean = false,
    val concurrency: Int = 3,
    val progress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeMillis: Long = 0,
    val showConfigDialog: Boolean = false,
    val showProgressDialog: Boolean = false,
    val isSuccess: Boolean = false,
    val resultMessage: UiMessage? = null,
    val currentTaskId: String? = null,
    val currentFile: String? = null,
    val fileProgressMap: Map<String, Float> = emptyMap(),
)

class BatchReplayGainViewModel(
    private val songLibraryRepository: SongLibraryRepository,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchReplayGainUiState())
    val uiState: StateFlow<BatchReplayGainUiState> = _uiState.asStateFlow()

    private var selectedUris: List<String> = emptyList()
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.SCAN_REPLAY_GAIN)
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
            combine(
                batchTaskRepository.observeTask(taskId),
                batchTaskRepository.observeItems(taskId)
            ) { task, items ->
                task to items
            }.collect { (task, items) ->
                if (task == null) return@collect
                val progressMap = items
                    .filter { it.status == BatchTaskStatus.RUNNING && it.progress != null }
                    .associate { it.fileName to it.progress!! }
                _uiState.update {
                    it.copy(
                        progress = task.current to task.total,
                        successCount = task.successCount,
                        failureCount = task.failureCount,
                        skippedCount = task.skippedCount,
                        currentFile = task.currentFile,
                        isRunning = task.status == BatchTaskStatus.RUNNING || task.status == BatchTaskStatus.QUEUED,
                        isSuccess = task.status == BatchTaskStatus.SUCCEEDED && task.failureCount == 0,
                        fileProgressMap = progressMap
                    )
                }
                if (task.status == BatchTaskStatus.SUCCEEDED ||
                    task.status == BatchTaskStatus.FAILED ||
                    task.status == BatchTaskStatus.CANCELLED
                ) {
                    val duration = if (task.startedAt != null && task.finishedAt != null) {
                        task.finishedAt - task.startedAt
                    } else 0L
                    _uiState.update { it.copy(isRunning = false, fileProgressMap = emptyMap(), totalTimeMillis = duration) }
                    observeJob?.cancel()
                }
            }
        }
    }

    fun setSelectionUris(uris: List<String>) {
        selectedUris = uris
    }

    fun openReplayGainConfig() {
        _uiState.update { it.copy(showConfigDialog = true) }
    }

    fun closeReplayGainConfig() {
        _uiState.update { it.copy(showConfigDialog = false) }
    }

    fun setConcurrency(count: Int) {
        _uiState.update { it.copy(concurrency = count.coerceIn(1, 5)) }
    }

    fun startBatchScan() {
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        val concurrency = _uiState.value.concurrency

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
                isSuccess = false,
                fileProgressMap = emptyMap()
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
                ReplayGainConfig.serializer(),
                ReplayGainConfig(concurrency = concurrency)
            )
            val taskId = batchTaskRepository.createTask(
                type = BatchTaskType.SCAN_REPLAY_GAIN,
                songs = songs,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId)
        }
    }

    fun abortBatchScan() {
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
                showProgressDialog = false,
                progress = null,
                isRunning = false
            )
        }
    }
}

@Serializable
data class ReplayGainConfig(
    val concurrency: Int
)
