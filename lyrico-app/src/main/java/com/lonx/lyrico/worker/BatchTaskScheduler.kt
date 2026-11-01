package com.lonx.lyrico.worker

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.await
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.BatchTaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class BatchTaskScheduler(
    private val context: Context,
    private val taskRepository: BatchTaskRepository
) {
    private val schedulingLock = Mutex()

    suspend fun schedule(type: BatchTaskType, songs: List<SongEntity>, configJson: String?): String =
        schedulingLock.withLock {
            // Keep the committed ID available for cleanup if the caller leaves mid-creation.
            val taskId = withContext(NonCancellable) {
                taskRepository.createTask(type, songs, configJson)
            }
            try {
                enqueue(taskId)
                taskId
            } catch (error: Exception) {
                withContext(NonCancellable) {
                    WorkManager.getInstance(context).cancelAllWorkByTag("batch_task:$taskId").await()
                    if (error is CancellationException) {
                        taskRepository.markCancelled(taskId)
                    } else {
                        taskRepository.markFailed(taskId, error.message)
                    }
                }
                throw error
            }
        }

    private suspend fun enqueue(taskId: String) {
        val request = OneTimeWorkRequestBuilder<BatchTaskWorker>()
            .setInputData(
                Data.Builder()
                    .putString(BatchTaskWorker.KEY_TASK_ID, taskId)
                    .build()
            )
            .addTag("batch_task")
            .addTag("batch_task:$taskId")
            .build()

        taskRepository.bindWorkId(taskId, request.id.toString())

        WorkManager.getInstance(context).enqueueUniqueWork(
            "batch_task_$taskId",
            ExistingWorkPolicy.KEEP,
            request
        ).await()
    }

    suspend fun restoreTaskStates() = schedulingLock.withLock {
        val workManager = WorkManager.getInstance(context)
        for (task in taskRepository.getActiveTasks()) {
            val workId = task.workId?.let { id ->
                try { UUID.fromString(id) } catch (_: IllegalArgumentException) { null }
            }
            val work = workId?.let { workManager.getWorkInfoByIdFlow(it).first() }
            when (work?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> Unit
                WorkInfo.State.SUCCEEDED -> {
                    taskRepository.updateProgressFromItems(task.taskId, task.currentFile)
                    taskRepository.markSucceeded(task.taskId)
                }
                WorkInfo.State.CANCELLED -> taskRepository.markCancelled(task.taskId)
                WorkInfo.State.FAILED, null -> taskRepository.markFailed(
                    task.taskId, "Task interrupted by system"
                )
            }
        }
    }

    fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("batch_task:$taskId")
    }
}
