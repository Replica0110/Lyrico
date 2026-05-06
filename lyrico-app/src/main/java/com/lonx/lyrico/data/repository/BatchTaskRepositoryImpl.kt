package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.dao.BatchTaskDao
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BatchTaskRepositoryImpl(
    private val batchTaskDao: BatchTaskDao
) : BatchTaskRepository {

    override suspend fun createTask(
        type: BatchTaskType,
        songs: List<SongEntity>,
        configJson: String?
    ): String {
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val task = BatchTaskEntity(
            taskId = taskId,
            type = type,
            status = BatchTaskStatus.QUEUED,
            total = songs.size,
            current = 0,
            successCount = 0,
            failureCount = 0,
            skippedCount = 0,
            currentFile = null,
            configJson = configJson,
            workId = null,
            startedAt = null,
            finishedAt = null,
            createdAt = now,
            updatedAt = now,
            errorMessage = null
        )

        val items = songs.mapIndexed { index, song ->
            BatchTaskItemEntity(
                itemId = "$taskId-$index",
                taskId = taskId,
                mediaId = song.mediaId,
                songUri = song.uri,
                filePath = song.filePath,
                fileName = song.fileName,
                status = BatchTaskStatus.QUEUED,
                progress = null,
                resultJson = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now
            )
        }

        batchTaskDao.createTask(task, items)
        return taskId
    }

    override fun observeTasks(): Flow<List<BatchTaskEntity>> =
        batchTaskDao.observeTasks()

    override fun observeTask(taskId: String): Flow<BatchTaskEntity?> =
        batchTaskDao.observeTask(taskId)

    override fun observeItems(taskId: String): Flow<List<BatchTaskItemEntity>> =
        batchTaskDao.observeItems(taskId)

    override suspend fun getTask(taskId: String): BatchTaskEntity? =
        batchTaskDao.getTask(taskId)

    override suspend fun markRunning(taskId: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateTaskStatus(taskId, BatchTaskStatus.RUNNING, now)
    }

    override suspend fun markSucceeded(taskId: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateTaskStatusWithError(taskId, BatchTaskStatus.SUCCEEDED, null, now)
    }

    override suspend fun markFailed(taskId: String, error: String?) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateTaskStatusWithError(taskId, BatchTaskStatus.FAILED, error, now)
    }

    override suspend fun markCancelled(taskId: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateTaskStatusWithError(taskId, BatchTaskStatus.CANCELLED, null, now)
    }

    override suspend fun markItemRunning(itemId: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemStatus(itemId, BatchTaskStatus.RUNNING, null, null, now)
    }

    override suspend fun markItemSucceeded(itemId: String, resultJson: String?) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemStatus(itemId, BatchTaskStatus.SUCCEEDED, resultJson, null, now)
    }

    override suspend fun markItemSkipped(itemId: String, resultJson: String?) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemStatus(itemId, BatchTaskStatus.SKIPPED, resultJson, null, now)
    }

    override suspend fun markItemFailed(itemId: String, error: String?) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemStatus(itemId, BatchTaskStatus.FAILED, null, error, now)
    }

    override suspend fun updateItemFileInfo(itemId: String, filePath: String, fileName: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemFileInfo(itemId, filePath, fileName, now)
    }

    override suspend fun updateItemProgress(itemId: String, progress: Float) {
        val now = System.currentTimeMillis()
        batchTaskDao.updateItemProgress(itemId, progress, now)
    }

    override suspend fun updateProgressFromItems(taskId: String, currentFile: String?) {
        val task = batchTaskDao.getTask(taskId) ?: return
        val succeeded = batchTaskDao.getItemsByStatus(taskId, BatchTaskStatus.SUCCEEDED)
        val failed = batchTaskDao.getItemsByStatus(taskId, BatchTaskStatus.FAILED)
        val skipped = batchTaskDao.getItemsByStatus(taskId, BatchTaskStatus.SKIPPED)
        val totalProcessed = succeeded.size + failed.size + skipped.size
        val now = System.currentTimeMillis()
        batchTaskDao.updateTaskProgress(
            taskId = taskId,
            current = totalProcessed,
            success = succeeded.size,
            failure = failed.size,
            skipped = skipped.size,
            currentFile = currentFile,
            updatedAt = now
        )
    }

    override suspend fun bindWorkId(taskId: String, workId: String) {
        val now = System.currentTimeMillis()
        batchTaskDao.bindWorkId(taskId, workId, now)
    }

    override suspend fun getPendingItems(taskId: String): List<BatchTaskItemEntity> {
        return batchTaskDao.getItemsByStatus(taskId, listOf(BatchTaskStatus.QUEUED, BatchTaskStatus.RUNNING))
    }

    override suspend fun getOrphanedRunningTasks(): List<BatchTaskEntity> {
        return batchTaskDao.getTasksByStatuses(listOf(BatchTaskStatus.RUNNING, BatchTaskStatus.QUEUED))
    }

    override suspend fun markOrphanedTasksFailed() {
        val orphaned = getOrphanedRunningTasks()
        val now = System.currentTimeMillis()
        for (task in orphaned) {
            batchTaskDao.updateTaskStatusWithError(
                task.taskId,
                BatchTaskStatus.FAILED,
                "Task interrupted by system",
                now
            )
        }
    }

    override suspend fun deleteTask(taskId: String) {
        batchTaskDao.deleteTaskWithItems(taskId)
    }

    override suspend fun deleteTasks(taskIds: List<String>) {
        batchTaskDao.deleteTasksWithItems(taskIds)
    }

    override suspend fun clearFinishedTasks() {
        batchTaskDao.deleteFinishedTasks()
    }

    override suspend fun getRunningTaskByType(type: BatchTaskType): BatchTaskEntity? {
        return batchTaskDao.getTaskByTypeAndStatuses(
            type,
            listOf(BatchTaskStatus.RUNNING, BatchTaskStatus.QUEUED)
        )
    }
}
