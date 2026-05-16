package com.lonx.lyrico.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.worker.processor.BatchExportTaskConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class BatchExportUiState(
    val isRunning: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val totalTimeMillis: Long = 0,
    val showProgressDialog: Boolean = false,
    val isSuccess: Boolean = false,
    val currentTaskId: String? = null,
    val currentFile: String? = null,
    val taskType: BatchTaskType? = null
)

class BatchExportViewModel(
    private val songRepository: SongRepository,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchExportUiState())
    val uiState: StateFlow<BatchExportUiState> = _uiState.asStateFlow()

    private var selectedUris: List<String> = emptyList()
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.EXPORT_LYRICS)
                ?: batchTaskRepository.getRunningTaskByType(BatchTaskType.EXPORT_COVER)
            if (runningTask != null) {
                resumeObservingTask(runningTask.taskId, runningTask.type)
            }
        }
    }

    fun setSelectionUris(uris: List<String>) {
        selectedUris = uris
    }

    fun startBatchExport(taskType: BatchTaskType, destinationTreeUri: Uri) {
        if (taskType != BatchTaskType.EXPORT_LYRICS && taskType != BatchTaskType.EXPORT_COVER) return
        val uris = selectedUris.toList()
        if (uris.isEmpty()) return

        _uiState.update {
            it.copy(
                showProgressDialog = true,
                isRunning = true,
                progress = 0 to 0,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0,
                totalTimeMillis = 0L,
                isSuccess = false,
                taskType = taskType
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
                BatchExportTaskConfig.serializer(),
                BatchExportTaskConfig(destinationTreeUri = destinationTreeUri.toString())
            )
            val taskId = batchTaskRepository.createTask(
                type = taskType,
                songs = songs,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId, taskType)
        }
    }

    fun abortBatchExport() {
        val taskId = _uiState.value.currentTaskId
        if (taskId != null) {
            batchTaskScheduler.cancel(taskId)
            viewModelScope.launch {
                batchTaskRepository.markCancelled(taskId)
            }
        }
    }

    fun closeProgressDialog() {
        _uiState.update { it.copy(showProgressDialog = false) }
    }

    fun clearProgressDialog() {
        _uiState.update {
            it.copy(
                progress = null,
                isRunning = false
            )
        }
    }

    private fun resumeObservingTask(taskId: String, taskType: BatchTaskType) {
        observeJob?.cancel()
        _uiState.update {
            it.copy(
                currentTaskId = taskId,
                taskType = taskType,
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
                        taskType = task.type,
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
}
