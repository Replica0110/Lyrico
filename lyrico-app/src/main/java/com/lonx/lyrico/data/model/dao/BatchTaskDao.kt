package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchTaskDao {

    @Insert
    suspend fun insertTask(task: BatchTaskEntity)

    @Insert
    suspend fun insertItems(items: List<BatchTaskItemEntity>)

    @Transaction
    suspend fun createTask(task: BatchTaskEntity, items: List<BatchTaskItemEntity>) {
        insertTask(task)
        insertItems(items)
    }

    @Query("SELECT * FROM batch_tasks ORDER BY createdAt DESC")
    fun observeTasks(): Flow<List<BatchTaskEntity>>

    @Query("SELECT * FROM batch_tasks WHERE taskId = :taskId")
    fun observeTask(taskId: String): Flow<BatchTaskEntity?>

    @Query("SELECT * FROM batch_tasks WHERE taskId = :taskId")
    suspend fun getTask(taskId: String): BatchTaskEntity?

    @Query("SELECT * FROM batch_task_items WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun observeItems(taskId: String): Flow<List<BatchTaskItemEntity>>

    @Query("SELECT * FROM batch_task_items WHERE taskId = :taskId AND status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getItemsByStatus(taskId: String, statuses: List<BatchTaskStatus>): List<BatchTaskItemEntity>

    @Query("SELECT * FROM batch_task_items WHERE taskId = :taskId AND status = :status")
    suspend fun getItemsByStatus(taskId: String, status: BatchTaskStatus): List<BatchTaskItemEntity>

    @Query("UPDATE batch_tasks SET status = :status, updatedAt = :updatedAt, startedAt = COALESCE(startedAt, :updatedAt) WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: BatchTaskStatus, updatedAt: Long)

    @Query("UPDATE batch_tasks SET current = :current, successCount = :success, failureCount = :failure, skippedCount = :skipped, currentFile = :currentFile, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun updateTaskProgress(taskId: String, current: Int, success: Int, failure: Int, skipped: Int, currentFile: String?, updatedAt: Long)

    @Query("UPDATE batch_task_items SET status = :status, resultJson = :resultJson, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE itemId = :itemId")
    suspend fun updateItemStatus(itemId: String, status: BatchTaskStatus, resultJson: String?, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE batch_task_items SET filePath = :filePath, fileName = :fileName, updatedAt = :updatedAt WHERE itemId = :itemId")
    suspend fun updateItemFileInfo(itemId: String, filePath: String, fileName: String, updatedAt: Long)

    @Query("UPDATE batch_task_items SET progress = :progress, updatedAt = :updatedAt WHERE itemId = :itemId")
    suspend fun updateItemProgress(itemId: String, progress: Float, updatedAt: Long)

    @Query("UPDATE batch_tasks SET workId = :workId, updatedAt = :updatedAt WHERE taskId = :taskId")
    suspend fun bindWorkId(taskId: String, workId: String, updatedAt: Long)

    @Query("SELECT * FROM batch_tasks WHERE status IN (:statuses)")
    suspend fun getTasksByStatuses(statuses: List<BatchTaskStatus>): List<BatchTaskEntity>

    @Query("SELECT * FROM batch_tasks WHERE type = :type AND status IN (:statuses) ORDER BY createdAt DESC LIMIT 1")
    suspend fun getTaskByTypeAndStatuses(type: BatchTaskType, statuses: List<BatchTaskStatus>): BatchTaskEntity?

    @Query("UPDATE batch_tasks SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt, finishedAt = COALESCE(finishedAt, :updatedAt) WHERE taskId = :taskId")
    suspend fun updateTaskStatusWithError(taskId: String, status: BatchTaskStatus, errorMessage: String?, updatedAt: Long)

    @Query("DELETE FROM batch_task_items WHERE taskId = :taskId")
    suspend fun deleteItemsByTaskId(taskId: String)

    @Query("DELETE FROM batch_task_items WHERE taskId IN (:taskIds)")
    suspend fun deleteItemsByTaskIds(taskIds: List<String>)

    @Query("DELETE FROM batch_tasks WHERE taskId = :taskId")
    suspend fun deleteTask(taskId: String)

    @Query("DELETE FROM batch_tasks WHERE taskId IN (:taskIds)")
    suspend fun deleteTasks(taskIds: List<String>)

    @Query("DELETE FROM batch_task_items WHERE taskId IN (SELECT taskId FROM batch_tasks WHERE status NOT IN (:activeStatuses))")
    suspend fun deleteItemsExceptStatuses(activeStatuses: List<BatchTaskStatus>)

    @Query("DELETE FROM batch_tasks WHERE status NOT IN (:activeStatuses)")
    suspend fun deleteTasksExceptStatuses(activeStatuses: List<BatchTaskStatus>)

    @Transaction
    suspend fun deleteTaskWithItems(taskId: String) {
        deleteItemsByTaskId(taskId)
        deleteTask(taskId)
    }

    @Transaction
    suspend fun deleteTasksWithItems(taskIds: List<String>) {
        if (taskIds.isEmpty()) return
        deleteItemsByTaskIds(taskIds)
        deleteTasks(taskIds)
    }

    @Transaction
    suspend fun deleteFinishedTasks() {
        val activeStatuses = listOf(BatchTaskStatus.QUEUED, BatchTaskStatus.RUNNING)
        deleteItemsExceptStatuses(activeStatuses)
        deleteTasksExceptStatuses(activeStatuses)
    }
}
