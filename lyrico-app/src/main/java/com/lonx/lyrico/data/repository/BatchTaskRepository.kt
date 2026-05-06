package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.Flow

interface BatchTaskRepository {
    suspend fun createTask(type: BatchTaskType, songs: List<SongEntity>, configJson: String?): String
    fun observeTasks(): Flow<List<BatchTaskEntity>>
    fun observeTask(taskId: String): Flow<BatchTaskEntity?>
    fun observeItems(taskId: String): Flow<List<BatchTaskItemEntity>>
    suspend fun getTask(taskId: String): BatchTaskEntity?
    suspend fun markRunning(taskId: String)
    suspend fun markSucceeded(taskId: String)
    suspend fun markFailed(taskId: String, error: String?)
    suspend fun markCancelled(taskId: String)
    suspend fun markItemRunning(itemId: String)
    suspend fun markItemSucceeded(itemId: String, resultJson: String? = null)
    suspend fun markItemSkipped(itemId: String, resultJson: String? = null)
    suspend fun markItemFailed(itemId: String, error: String?)
    suspend fun updateItemFileInfo(itemId: String, filePath: String, fileName: String)
    suspend fun updateItemProgress(itemId: String, progress: Float)
    suspend fun updateProgressFromItems(taskId: String, currentFile: String?)
    suspend fun bindWorkId(taskId: String, workId: String)
    suspend fun getPendingItems(taskId: String): List<BatchTaskItemEntity>
    suspend fun getOrphanedRunningTasks(): List<BatchTaskEntity>
    suspend fun markOrphanedTasksFailed()
    suspend fun deleteTask(taskId: String)
    suspend fun deleteTasks(taskIds: List<String>)
    suspend fun clearFinishedTasks()
    suspend fun getRunningTaskByType(type: BatchTaskType): BatchTaskEntity?
}
