package com.lonx.lyrico.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.LogOperation
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.model.batch.BatchTaskType
import com.lonx.lyrico.data.model.batch.BatchTaskStatus
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.worker.processor.BatchTaskProcessorFactory
import com.lonx.lyrico.worker.processor.BatchTaskSkippedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.int
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class BatchTaskWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val taskRepository: BatchTaskRepository by inject()
    private val processorFactory: BatchTaskProcessorFactory by inject()
    private val appLogRepository: AppLogRepository by inject()

    private val taskId: String = inputData.getString(KEY_TASK_ID) ?: ""

    override suspend fun doWork(): Result {
        if (taskId.isEmpty()) return Result.failure()

        val wakeLock = acquireWakeLock()

        try {
            BatchTaskNotification.ensureChannel(applicationContext)
            val task = taskRepository.getTask(taskId) ?: return Result.failure()
            if (task.status != BatchTaskStatus.QUEUED && task.status != BatchTaskStatus.RUNNING) {
                return Result.success()
            }
            val title = getTaskTitle(task.type)
            val total = task.total
            val startedAt = System.currentTimeMillis()
            val configSummary = BatchTaskLogFormatter.formatConfiguration(task.type, task.configJson)

            setForeground(createForegroundInfo(title, 0, total))

            taskRepository.markRunning(taskId)

            val processor = processorFactory.create(task.type)
            val items = taskRepository.getPendingItems(taskId)
            appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch task started",
                "task=$taskId\ntype=${task.type}\npending=${items.size}\n" +
                    "routineDetailSamples=$ROUTINE_DETAIL_SAMPLES; warnings and errors are not sampled\n$configSummary", taskId, AppLogLevel.INFO)

            if (items.isEmpty()) {
                taskRepository.updateProgressFromItems(taskId, task.currentFile)
                taskRepository.markSucceeded(taskId)
                logBatch(
                    level = AppLogLevel.INFO,
                    message = "Batch task finished: ${task.type} (0/$total processed)",
                    detail = BatchTaskLogFormatter.formatSummary(
                        taskId = taskId,
                        taskType = task.type,
                        status = "succeeded",
                        configSummary = configSummary,
                        startedAt = startedAt,
                        total = total,
                        pending = 0,
                        concurrency = 0,
                        processed = 0,
                        success = 0,
                        skipped = 0,
                        failure = 0,
                        itemDetails = emptyList()
                    ),
                    relatedId = taskId
                )
                return Result.success()
            }

            val itemTotal = items.size
            val concurrency = parseConcurrency(task.configJson)
            val semaphore = Semaphore(concurrency)
            val processedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val skippedCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            val itemDetails = ConcurrentLinkedQueue<String>()

            coroutineScope {
                val jobs = items.mapIndexed { index, item ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (isStopped) throw CancellationException("Batch worker stopped")
                            withContext(LogOperation("batch-$taskId-item-${item.itemId}", recordRoutine = index < ROUTINE_DETAIL_SAMPLES)) {
                                val itemStarted = System.nanoTime()
                                try {
                                    appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch item started",
                                        "task=$taskId\nitem=${item.itemId}\ntype=${task.type}\nuri=${item.songUri}", item.songUri)
                                    taskRepository.markItemRunning(item.itemId)
                                    val result = processor.process(task, item) { progress ->
                                        taskRepository.updateItemProgress(item.itemId, progress)
                                    }
                                    if (result.updatedFilePath != null && result.updatedFileName != null) {
                                        taskRepository.updateItemFileInfo(
                                            itemId = item.itemId,
                                            filePath = result.updatedFilePath,
                                            fileName = result.updatedFileName
                                        )
                                    }
                                    taskRepository.markItemSucceeded(item.itemId, result.resultJson)
                                    successCount.incrementAndGet()
                                    appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch item succeeded",
                                        "task=$taskId\nitem=${item.itemId}\nuri=${item.songUri}\n" +
                                            "elapsedMs=${(System.nanoTime() - itemStarted) / 1_000_000}\noutput=${result.updatedFilePath}", item.songUri)
                                } catch (e: BatchTaskSkippedException) {
                                    appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch item skipped",
                                        "task=$taskId\nitem=${item.itemId}\nuri=${item.songUri}\nreason=${e.message}", item.songUri)
                                    taskRepository.markItemSkipped(item.itemId, e.message)
                                    skippedCount.incrementAndGet()
                                    if (itemDetails.size < BatchTaskLogFormatter.MAX_ITEM_DETAILS) itemDetails.add("SKIPPED item=${item.itemId}: ${e.message ?: "No reason"}")
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch item failed",
                                        "task=$taskId\nitem=${item.itemId}\ntype=${task.type}\nuri=${item.songUri}\n" +
                                            "elapsedMs=${(System.nanoTime() - itemStarted) / 1_000_000}\n${e.stackTraceToString()}", item.songUri, AppLogLevel.ERROR)
                                    taskRepository.markItemFailed(item.itemId, e.message)
                                    failureCount.incrementAndGet()
                                    if (itemDetails.size < BatchTaskLogFormatter.MAX_ITEM_DETAILS) itemDetails.add("FAILED item=${item.itemId}: ${e.message ?: e::class.java.simpleName}")
                                } finally {
                                    val current = processedCount.incrementAndGet()
                                    if (current % LOG_PROGRESS_INTERVAL == 0 || current == itemTotal) {
                                        withContext(LogOperation("batch-$taskId")) {
                                            appLogRepository.recordOperation(AppLogType.BATCH, TAG, "Batch task progress",
                                                "task=$taskId\nprocessed=$current/$itemTotal\nsuccess=${successCount.get()}\n" +
                                                    "skipped=${skippedCount.get()}\nfailed=${failureCount.get()}\n" +
                                                    "routineDetailSamples=$ROUTINE_DETAIL_SAMPLES; warnings and errors are not sampled",
                                                taskId, AppLogLevel.INFO)
                                        }
                                    }
                                    taskRepository.updateProgressFromItems(taskId, item.fileName)
                                    try {
                                        setForeground(
                                            createForegroundInfo(title, current, itemTotal)
                                        )
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Log.w(TAG, "Failed to update foreground notification", e)
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
            taskRepository.markSucceeded(taskId)
            logBatch(
                level = if (failureCount.get() > 0) AppLogLevel.WARNING else AppLogLevel.INFO,
                message = BatchTaskLogFormatter.formatMessage(
                    task.type,
                    "finished",
                    processedCount.get(),
                    successCount.get(),
                    skippedCount.get(),
                    failureCount.get()
                ),
                detail = BatchTaskLogFormatter.formatSummary(
                    taskId = taskId,
                    taskType = task.type,
                    status = if (failureCount.get() > 0) "finished_with_errors" else "succeeded",
                    configSummary = configSummary,
                    startedAt = startedAt,
                    total = total,
                    pending = itemTotal,
                    concurrency = concurrency,
                    processed = processedCount.get(),
                    success = successCount.get(),
                    skipped = skippedCount.get(),
                    failure = failureCount.get(),
                    itemDetails = itemDetails.toList()
                ),
                relatedId = taskId
            )
            return Result.success()
        } catch (e: CancellationException) {
            // WorkManager may reschedule stopped work. User cancellation is persisted by
            // the cancellation entry points; a scheduler stop must not finish the task.
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) {
                taskRepository.markFailed(taskId, e.message)
                logBatchException(
                    message = "Batch task crashed",
                    throwable = e,
                    relatedId = taskId
                )
            }
            return Result.failure()
        } finally {
            releaseWakeLock(wakeLock)
        }
    }

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return try {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$WAKE_LOCK_TAG_PREFIX:$taskId"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MILLIS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
            null
        }
    }

    private fun releaseWakeLock(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Batch Task", 0, 0)
    }

    private fun createForegroundInfo(
        title: String,
        current: Int,
        total: Int
    ): ForegroundInfo {
        val notification = BatchTaskNotification.buildRunningNotification(
            context = applicationContext,
            taskId = taskId,
            title = title,
            currentFile = null,
            current = current,
            total = total
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                BatchTaskNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                BatchTaskNotification.NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun getTaskTitle(type: BatchTaskType): String {
        return when (type) {
            BatchTaskType.MATCH_METADATA -> applicationContext.getString(R.string.batch_task_match_tags)
            BatchTaskType.MATCH_LYRICS -> applicationContext.getString(R.string.batch_task_match_lyrics)
            BatchTaskType.MATCH_COVER -> applicationContext.getString(R.string.batch_task_match_cover)
            BatchTaskType.EDIT_TAGS -> applicationContext.getString(R.string.batch_task_edit_tags)
            BatchTaskType.RENAME_FILES -> applicationContext.getString(R.string.batch_task_rename_files)
            BatchTaskType.CONVERT_LYRICS_FORMAT -> applicationContext.getString(R.string.lyrics_process_title)
            BatchTaskType.SCAN_REPLAY_GAIN -> applicationContext.getString(R.string.batch_task_scan_replay_gain)
            BatchTaskType.EXPORT_LYRICS -> applicationContext.getString(R.string.batch_task_export_lyrics)
            BatchTaskType.EXPORT_COVER -> applicationContext.getString(R.string.batch_task_export_cover)
        }
    }

    private fun parseConcurrency(configJson: String?): Int {
        if (configJson == null) return 1
        return try {
            val obj = Json.parseToJsonElement(configJson).jsonObject
            val concurrency = obj["concurrency"]?.jsonPrimitive?.int
            concurrency?.coerceIn(1, 5) ?: 3
        } catch (e: Exception) {
            3
        }
    }

    private suspend fun logBatch(
        level: AppLogLevel,
        message: String,
        detail: String? = null,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.log(
                level = level,
                type = AppLogType.BATCH,
                tag = TAG,
                message = message,
                detail = detail,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to write batch log", e)
        }
    }

    private suspend fun logBatchException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.logException(
                type = AppLogType.BATCH,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to write batch exception log", e)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val TAG = "BatchTaskWorker"
        private const val WAKE_LOCK_TAG_PREFIX = "Lyrico:BatchTask"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L
        private const val ROUTINE_DETAIL_SAMPLES = 3
        private const val LOG_PROGRESS_INTERVAL = 50
    }
}
