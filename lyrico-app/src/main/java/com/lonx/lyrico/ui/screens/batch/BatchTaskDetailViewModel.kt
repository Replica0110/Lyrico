package com.lonx.lyrico.ui.screens.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.batch.BatchTaskRepository
import com.lonx.lyrico.worker.BatchTaskScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BatchTaskDetailViewModel(
    private val taskId: String,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler
) : ViewModel() {
    val task: StateFlow<BatchTaskEntity?> = batchTaskRepository.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val items: StateFlow<List<BatchTaskItemEntity>> = batchTaskRepository.observeItems(taskId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun cancelTask() {
        batchTaskScheduler.cancel(taskId)
        viewModelScope.launch {
            batchTaskRepository.markCancelled(taskId)
        }
    }
}
