package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.worker.BatchTaskScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BatchTaskListUiState(
    val allTasks: List<BatchTaskEntity> = emptyList(),
    val filteredTasks: List<BatchTaskEntity> = emptyList(),
    val filterType: BatchTaskType? = null,
    val filterStatus: BatchTaskStatus? = null
)

class BatchTaskListViewModel(
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(BatchTaskListUiState())
    val uiState: StateFlow<BatchTaskListUiState> = _uiState.asStateFlow()

    private val filterType = MutableStateFlow<BatchTaskType?>(null)
    private val filterStatus = MutableStateFlow<BatchTaskStatus?>(null)

    init {
        viewModelScope.launch {
            combine(
                batchTaskRepository.observeTasks(),
                filterType,
                filterStatus
            ) { tasks, type, status ->
                val filtered = tasks.filter { task ->
                    (type == null || task.type == type) &&
                    (status == null || task.status == status)
                }
                _uiState.update {
                    it.copy(
                        allTasks = tasks,
                        filteredTasks = filtered,
                        filterType = type,
                        filterStatus = status
                    )
                }
            }.collect {}
        }
    }

    fun setFilterType(type: BatchTaskType?) {
        filterType.value = type
    }

    fun setFilterStatus(status: BatchTaskStatus?) {
        filterStatus.value = status
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            batchTaskRepository.deleteTask(taskId)
        }
    }

    fun deleteTasks(taskIds: List<String>) {
        viewModelScope.launch {
            batchTaskRepository.deleteTasks(taskIds)
        }
    }

    fun clearFinishedTasks() {
        viewModelScope.launch {
            batchTaskRepository.clearFinishedTasks()
        }
    }

    fun cancelTask(taskId: String) {
        batchTaskScheduler.cancel(taskId)
        viewModelScope.launch {
            batchTaskRepository.markCancelled(taskId)
        }
    }
}
