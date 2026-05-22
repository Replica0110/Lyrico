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
import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.MetadataWriteMode
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.worker.processor.BatchTaskProcessorFactory
import com.lonx.lyrico.worker.processor.BatchExportTaskConfig
import com.lonx.lyrico.worker.processor.BatchTaskSkippedException
import com.lonx.lyrico.worker.processor.EditTagsTaskConfig
import com.lonx.lyrico.worker.processor.MatchMetadataTaskConfig
import com.lonx.lyrico.worker.processor.RenameFilesTaskConfig
import com.lonx.lyrico.viewmodel.LyricsFormatConfig
import com.lonx.lyrico.viewmodel.ReplayGainConfig
import kotlinx.coroutines.Dispatchers
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
        BatchTaskNotification.ensureChannel(applicationContext)

        try {
            val task = taskRepository.getTask(taskId) ?: return Result.failure()
            val title = getTaskTitle(task.type)
            val total = task.total
            val startedAt = System.currentTimeMillis()
            val configSummary = buildConfigSummary(task.type, task.configJson)

            setForeground(createForegroundInfo(title, 0, total))

            taskRepository.markRunning(taskId)

            val processor = processorFactory.create(task.type)
            val items = taskRepository.getPendingItems(taskId)

            if (items.isEmpty()) {
                taskRepository.markSucceeded(taskId)
                logBatch(
                    level = AppLogLevel.INFO,
                    message = "Batch task finished: ${task.type} (0/$total processed)",
                    detail = buildBatchSummary(
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
            val concurrency = parseConcurrency(task.configJson, task.type)
            val semaphore = Semaphore(concurrency)
            val processedCount = AtomicInteger(0)
            val successCount = AtomicInteger(0)
            val skippedCount = AtomicInteger(0)
            val failureCount = AtomicInteger(0)
            val itemDetails = ConcurrentLinkedQueue<String>()

            try {
                coroutineScope {
                    val jobs = items.map { item ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                if (isStopped) return@async
                                try {
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
                                } catch (e: BatchTaskSkippedException) {
                                    taskRepository.markItemSkipped(item.itemId, e.message)
                                    skippedCount.incrementAndGet()
                                    itemDetails.add("SKIPPED ${item.fileName}: ${e.message ?: "No reason"}")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Item processing failed: ${item.fileName}", e)
                                    taskRepository.markItemFailed(item.itemId, e.message)
                                    failureCount.incrementAndGet()
                                    itemDetails.add(
                                        buildString {
                                            appendLine("FAILED ${item.fileName}: ${e.message ?: e::class.java.simpleName}")
                                            append(Log.getStackTraceString(e))
                                        }
                                    )
                                } finally {
                                    val current = processedCount.incrementAndGet()
                                    taskRepository.updateProgressFromItems(taskId, item.fileName)
                                    try {
                                        setForeground(
                                            createForegroundInfo(title, current, itemTotal)
                                        )
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to update foreground notification", e)
                                    }
                                }
                            }
                        }
                    }
                    jobs.awaitAll()
                }
            } finally {
                withContext(NonCancellable) {
                    if (isStopped) {
                        taskRepository.markCancelled(taskId)
                        logBatch(
                            level = AppLogLevel.WARNING,
                            message = buildBatchMessage(
                                task.type,
                                "cancelled",
                                processedCount.get(),
                                successCount.get(),
                                skippedCount.get(),
                                failureCount.get()
                            ),
                            detail = buildBatchSummary(
                                taskType = task.type,
                                status = "cancelled",
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
                    } else {
                        taskRepository.markSucceeded(taskId)
                        logBatch(
                            level = if (failureCount.get() > 0) AppLogLevel.WARNING else AppLogLevel.INFO,
                            message = buildBatchMessage(
                                task.type,
                                "finished",
                                processedCount.get(),
                                successCount.get(),
                                skippedCount.get(),
                                failureCount.get()
                            ),
                            detail = buildBatchSummary(
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
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            logBatchException(
                message = "Batch task crashed",
                throwable = e,
                relatedId = taskId
            )
            throw e
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
            BatchTaskType.EDIT_TAGS -> applicationContext.getString(R.string.batch_task_edit_tags)
            BatchTaskType.RENAME_FILES -> applicationContext.getString(R.string.batch_task_rename_files)
            BatchTaskType.CONVERT_LYRICS_FORMAT -> applicationContext.getString(R.string.batch_task_convert_lyrics_format)
            BatchTaskType.SCAN_REPLAY_GAIN -> applicationContext.getString(R.string.batch_task_scan_replay_gain)
            BatchTaskType.EXPORT_LYRICS -> applicationContext.getString(R.string.batch_task_export_lyrics)
            BatchTaskType.EXPORT_COVER -> applicationContext.getString(R.string.batch_task_export_cover)
        }
    }

    private fun parseConcurrency(configJson: String?, type: BatchTaskType): Int {
        if (configJson == null) return 1
        return try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(configJson).jsonObject
            val concurrency = obj["concurrency"]?.jsonPrimitive?.int
            concurrency?.coerceIn(1, 5) ?: 3
        } catch (e: Exception) {
            3
        }
    }

    private fun buildBatchSummary(
        taskType: BatchTaskType,
        status: String,
        configSummary: String,
        startedAt: Long,
        total: Int,
        pending: Int,
        concurrency: Int,
        processed: Int,
        success: Int,
        skipped: Int,
        failure: Int,
        itemDetails: List<String>
    ): String = buildString {
        val finishedAt = System.currentTimeMillis()
        appendLine("taskId=$taskId")
        appendLine("type=$taskType")
        appendLine("status=$status")
        appendLine()
        appendLine("configuration:")
        appendLine(configSummary.prependIndent("  "))
        appendLine()
        appendLine("startedAt=$startedAt")
        appendLine("finishedAt=$finishedAt")
        appendLine("durationMs=${finishedAt - startedAt}")
        appendLine("total=$total")
        appendLine("items=$pending")
        appendLine("concurrency=$concurrency")
        appendLine("processed=$processed")
        appendLine("success=$success")
        appendLine("skipped=$skipped")
        appendLine("failure=$failure")
        if (itemDetails.isNotEmpty()) {
            appendLine()
            appendLine("items:")
            itemDetails.take(MAX_ITEM_DETAILS_IN_LOG).forEach { detail ->
                appendLine(detail.trimEnd())
                appendLine()
            }
            val omitted = itemDetails.size - MAX_ITEM_DETAILS_IN_LOG
            if (omitted > 0) {
                appendLine("... $omitted more item details omitted")
            }
        }
    }

    private fun buildConfigSummary(type: BatchTaskType, configJson: String?): String {
        if (configJson.isNullOrBlank()) return "config=(none)"
        return runCatching {
            when (type) {
                BatchTaskType.MATCH_METADATA -> summarizeMatchConfig(configJson)
                BatchTaskType.RENAME_FILES -> summarizeRenameConfig(configJson)
                BatchTaskType.EDIT_TAGS -> summarizeEditTagsConfig(configJson)
                BatchTaskType.CONVERT_LYRICS_FORMAT -> summarizeLyricsFormatConfig(configJson)
                BatchTaskType.SCAN_REPLAY_GAIN -> summarizeReplayGainConfig(configJson)
                BatchTaskType.EXPORT_LYRICS,
                BatchTaskType.EXPORT_COVER -> summarizeExportConfig(configJson)
            }
        }.getOrElse { e ->
            buildString {
                appendLine("configParseError=${e.message ?: e::class.java.simpleName}")
                appendLine("rawConfig=${configJson.take(MAX_CONFIG_LOG_LENGTH)}")
            }.trimEnd()
        }
    }

    private fun summarizeMatchConfig(configJson: String): String {
        val config = Json.decodeFromString<MatchMetadataTaskConfig>(configJson)
        return buildString {
            appendLine("concurrency=${config.concurrency}")
            appendLine("separator=${config.separator}")
            appendLine("preferFileName=${config.matchConfig.preferFileName}")
            appendLine("enabledSources=${config.enabledSourceOrderIds.joinToString(" > ").ifBlank { "(default)" }}")
            appendLine("fields=${config.matchConfig.fields.toSortedMap(compareBy { it.name }).entries.joinToString(", ") { "${it.key.name}:${it.value.name}" }}")
            val metadataRules = config.metadataFieldWriteRules
                .filter { it.mode != MetadataWriteMode.DISABLED }
                .map { "${it.pluginId}.${it.normalizedKey}:${it.mode.name}" }
            appendLine("metadataFieldWriteRules=${metadataRules.joinToString(", ").ifBlank { "(none)" }}")
        }.trimEnd()
    }

    private fun summarizeRenameConfig(configJson: String): String {
        val config = Json.decodeFromString<RenameFilesTaskConfig>(configJson)
        return buildString {
            appendLine("renameFormat=${config.renameFormat}")
            appendLine("characterMappingRules=${config.characterMappingRules.size}")
            val customizedRules = config.characterMappingRules.filter { rule ->
                rule.charMappings.isNotEmpty()
            }
            appendLine("customizedMappingRules=${customizedRules.joinToString(", ") { it.name }.ifBlank { "(none)" }}")
        }.trimEnd()
    }

    private fun summarizeEditTagsConfig(configJson: String): String {
        val config = Json.decodeFromString<EditTagsTaskConfig>(configJson)
        val keep = EditTagsTaskConfig.KEEP_VALUE
        val modifiedFields = buildList {
            if (config.title != keep) add("title")
            if (config.artist != keep) add("artist")
            if (config.albumArtist != keep) add("albumArtist")
            if (config.album != keep) add("album")
            if (config.date != keep) add("date")
            if (config.genre != keep) add("genre")
            if (config.trackNumber != keep) add("trackNumber")
            if (config.discNumber != keep) add("discNumber")
            if (config.composer != keep) add("composer")
            if (config.lyricist != keep) add("lyricist")
            if (config.copyright != keep) add("copyright")
            if (config.comment != keep) add("comment")
            if (config.lyrics != keep) add("lyrics")
            if (config.ratingModified) add("rating")
            if (config.coverUri != null) add("cover")
            if (config.removeCover) add("removeCover")
            if (config.lyricsOffset.isNotBlank()) add("lyricsOffset")
            if (config.replayGainTrackGain != keep) add("replayGainTrackGain")
            if (config.replayGainTrackPeak != keep) add("replayGainTrackPeak")
            if (config.replayGainAlbumGain != keep) add("replayGainAlbumGain")
            if (config.replayGainAlbumPeak != keep) add("replayGainAlbumPeak")
            if (config.replayGainReferenceLoudness != keep) add("replayGainReferenceLoudness")
            if (config.customFields.isNotEmpty()) add("customFields")
        }

        return buildString {
            appendLine("concurrency=${config.concurrency}")
            appendLine("modifiedFields=${modifiedFields.joinToString(", ").ifBlank { "(none)" }}")
            if (config.ratingModified) appendLine("rating=${config.rating}")
            if (config.lyricsOffset.isNotBlank()) appendLine("lyricsOffset=${config.lyricsOffset}")
            appendLine("customFieldKeys=${config.customFields.map { it.key }.joinToString(", ").ifBlank { "(none)" }}")
            appendSanitizedValue("title", config.title, keep)
            appendSanitizedValue("artist", config.artist, keep)
            appendSanitizedValue("albumArtist", config.albumArtist, keep)
            appendSanitizedValue("album", config.album, keep)
            appendSanitizedValue("date", config.date, keep)
            appendSanitizedValue("genre", config.genre, keep)
            appendSanitizedValue("trackNumber", config.trackNumber, keep)
            appendSanitizedValue("discNumber", config.discNumber, keep)
            appendSanitizedValue("composer", config.composer, keep)
            appendSanitizedValue("lyricist", config.lyricist, keep)
            appendSanitizedValue("copyright", config.copyright, keep)
            appendSanitizedValue("comment", config.comment, keep)
            appendSanitizedValue("replayGainTrackGain", config.replayGainTrackGain, keep)
            appendSanitizedValue("replayGainTrackPeak", config.replayGainTrackPeak, keep)
            appendSanitizedValue("replayGainAlbumGain", config.replayGainAlbumGain, keep)
            appendSanitizedValue("replayGainAlbumPeak", config.replayGainAlbumPeak, keep)
            appendSanitizedValue("replayGainReferenceLoudness", config.replayGainReferenceLoudness, keep)
            if (config.lyrics != keep) appendLine("lyrics=(modified, ${config.lyrics.length} chars)")
            if (config.coverUri != null) appendLine("coverUri=(set)")
            if (config.removeCover) appendLine("removeCover=true")
        }.trimEnd()
    }

    private fun summarizeLyricsFormatConfig(configJson: String): String {
        val config = Json.decodeFromString<LyricsFormatConfig>(configJson)
        return buildString {
            appendLine("targetFormat=${config.targetFormat.name}")
            appendLine("concurrency=${config.concurrency}")
        }.trimEnd()
    }

    private fun summarizeReplayGainConfig(configJson: String): String {
        val config = Json.decodeFromString<ReplayGainConfig>(configJson)
        return "concurrency=${config.concurrency}"
    }

    private fun summarizeExportConfig(configJson: String): String {
        val config = Json.decodeFromString<BatchExportTaskConfig>(configJson)
        return buildString {
            appendLine("destinationTreeUri=${config.destinationTreeUri}")
            appendLine("concurrency=${config.concurrency}")
        }.trimEnd()
    }

    private fun StringBuilder.appendSanitizedValue(
        name: String,
        value: String,
        keep: String
    ) {
        if (value == keep) return
        appendLine("$name=${value.sanitizeConfigValue()}")
    }

    private fun String.sanitizeConfigValue(): String {
        val oneLine = replace("\r", "\\r").replace("\n", "\\n")
        return if (oneLine.length <= MAX_CONFIG_VALUE_LENGTH) {
            oneLine
        } else {
            "${oneLine.take(MAX_CONFIG_VALUE_LENGTH)}... (${length} chars)"
        }
    }

    private fun buildBatchMessage(
        type: BatchTaskType,
        status: String,
        processed: Int,
        success: Int,
        skipped: Int,
        failure: Int
    ): String {
        return "Batch task $status: $type (processed=$processed, success=$success, skipped=$skipped, failure=$failure)"
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
            Log.w(TAG, "Failed to write batch exception log", e)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val TAG = "BatchTaskWorker"
        private const val WAKE_LOCK_TAG_PREFIX = "Lyrico:BatchTask"
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 6 * 60 * 60 * 1000L
        private const val MAX_ITEM_DETAILS_IN_LOG = 50
        private const val MAX_CONFIG_LOG_LENGTH = 8_000
        private const val MAX_CONFIG_VALUE_LENGTH = 200
    }
}
