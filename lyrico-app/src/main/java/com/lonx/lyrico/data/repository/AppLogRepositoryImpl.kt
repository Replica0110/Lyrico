package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.LogRetentionOption
import com.lonx.lyrico.data.model.dao.AppLogDao
import com.lonx.lyrico.data.model.entity.AppLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import com.lonx.lyrico.data.model.log.LogRecording
import com.lonx.lyrico.data.model.log.LogRedactor
import com.lonx.lyrico.data.model.log.LogOperation
import com.lonx.lyrico.data.model.log.LogFileReference
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.NO_RECORDING_SESSION
import com.lonx.lyrico.data.model.log.operationLogDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogRepositoryImpl(
    private val dao: AppLogDao,
    private val settingsRepository: SettingsRepository,
    appScope: CoroutineScope,
) : AppLogRepository {
    private val recording = LogRecording(appScope)
    override val recordingState = recording.state
    override fun startRecording() = recording.start()
    override fun stopRecording() = recording.stop()
    override fun clearRecording() = recording.clear()

    override suspend fun exportRecording(expectedSessionId: Long?): String {
        AppLog.flush()
        val (state, entries) = recording.snapshot()
        check(expectedSessionId == null || state.startedAt == expectedSessionId) { "Recording session changed" }
        return redactor().redact(buildString {
            appendLine("Lyrico reproduction recording")
            appendLine("Started: ${formatTime(state.startedAt)}")
            appendLine("Finished: ${state.finishedAt?.let(::formatTime) ?: "recording"}")
            appendLine("Limit reached: ${state.limitReached}")
            appendLine("Events omitted or replaced: ${state.omittedCount}")
            appendLine("Warnings: ${state.warningCount}; errors: ${state.errorCount}")
            appendLine("Storage: memory only; cleared when the app process exits")
            appendLine()
            append(formatExportText(entries.sortedBy { it.createdAt }))
        })
    }
    @Volatile
    private var lastTrimAt: Long = 0L

    override fun observeLatest(limit: Int): Flow<List<AppLogEntity>> =
        dao.observeLatest(limit)

    override fun observeFiltered(level: AppLogLevel?, type: AppLogType?): Flow<List<AppLogEntity>> =
        dao.observeFiltered(level, type)

    override suspend fun getMatchingIds(level: AppLogLevel?, type: AppLogType?): List<Long> =
        dao.getMatchingIds(level, type)

    override fun observeByRelatedId(relatedId: String): Flow<List<AppLogEntity>> =
        dao.observeByRelatedId(relatedId)

    override suspend fun getLatest(limit: Int): List<AppLogEntity> =
        dao.getLatest(limit)

    override suspend fun getByIds(ids: List<Long>): List<AppLogEntity> =
        if (ids.isEmpty()) emptyList() else dao.getByIds(ids)

    override suspend fun exportText(limit: Int): String {
        AppLog.flush()
        val logs = getLatest(limit).asReversed()
        return redactor().redact(formatExportText(logs))
    }

    override suspend fun exportText(ids: List<Long>): String {
        AppLog.flush()
        val logs = getByIds(ids).asReversed()
        return redactor().redact(formatExportText(logs))
    }

    private fun formatExportText(logs: List<AppLogEntity>): String {
        return buildString {
            appendLine("Lyrico log export")
            appendLine("Async events omitted since process start: ${AppLog.droppedCount}")
            appendLine("Generated: ${formatTime(System.currentTimeMillis())}")
            appendLine("Count: ${logs.size}")
            appendLine()
            logs.forEach { log ->
                appendLine("${formatTime(log.createdAt)} [${log.level}/${log.type}] ${log.tag}")
                appendLine(log.message)
                log.relatedId?.let {
                    appendLine("Related: ${if (log.level <= AppLogLevel.INFO) LogFileReference.compact(it) else it}")
                }
                log.detail?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    appendLine(it)
                }
                appendLine()
                appendLine("----")
            }
        }
    }

    override suspend fun log(
        level: AppLogLevel,
        type: AppLogType,
        tag: String,
        message: String,
        detail: String?,
        relatedId: String?,
        recordingStartedAt: Long?,
        occurredAt: Long
    ) {
        val operation = currentCoroutineContext()[LogOperation]
        if (level <= AppLogLevel.INFO && operation?.recordRoutine == false) return
        val captureId = recordingStartedAt ?: recordingState.value.takeIf { it.active }?.startedAt ?: NO_RECORDING_SESSION
        val capture = captureId > 0 && captureId == recordingState.value.startedAt
        if (level == AppLogLevel.DEBUG && !capture) return
        val retain = settingsRepository.logRetentionOption.first().isRecordingEnabled
        if (!retain && !capture) return
        val redactor = redactor()
        val contextualDetail = operationLogDetail(operation, detail)
        fun text(value: String): String = redactor.redact(value).let {
            if (level <= AppLogLevel.INFO) LogFileReference.compact(it) else it
        }
        val entry = AppLogEntity(
            createdAt = occurredAt,
            level = level,
            type = type,
            tag = redactor.redact(tag).bounded(MAX_TAG_LENGTH),
            message = text(message).bounded(MAX_TEXT_LENGTH),
            detail = contextualDetail.takeIf { it.isNotBlank() }?.let { text(it).bounded(MAX_DETAIL_LENGTH) },
            relatedId = relatedId?.let(redactor::redact)
        )
        recording.add(if (level <= AppLogLevel.INFO) entry.copy(
            relatedId = entry.relatedId?.let(LogFileReference::compact),
        ) else entry, captureId)
        if (retain && level != AppLogLevel.DEBUG) {
            dao.insert(entry)
            trimIfDue()
        }
    }

    private suspend fun redactor(): LogRedactor = LogRedactor(
        settingsRepository.sourceSettingsByIdFlow.first().values.flatMap { config ->
            config.values.filterKeys(LogRedactor::isSensitiveKey).values
        }
    )

    private fun String.bounded(limit: Int): String =
        if (length <= limit) this else take(limit - 13) + "\n[truncated]"

    override suspend fun logException(
        type: AppLogType,
        tag: String,
        message: String,
        throwable: Throwable,
        relatedId: String?
    ) {
        log(
            level = AppLogLevel.ERROR,
            type = type,
            tag = tag,
            message = "$message: ${throwable.message ?: throwable::class.java.simpleName}",
            detail = throwable.stackTraceToText(),
            relatedId = relatedId
        )
    }

    override suspend fun clear() {
        dao.clear()
    }

    override suspend fun deleteByIds(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(500).forEach { dao.deleteByIds(it) }
    }

    override suspend fun trim() {
        applyRetentionPolicy()
    }

    override suspend fun applyRetentionPolicy() {
        lastTrimAt = System.currentTimeMillis()
        when (val option = settingsRepository.logRetentionOption.first()) {
            LogRetentionOption.NONE -> dao.clear()
            LogRetentionOption.FOREVER -> Unit
            else -> option.retentionMillis?.let { retentionMillis ->
                val cutoff = System.currentTimeMillis() - retentionMillis
                dao.deleteOlderThan(cutoff)
            }
        }
    }

    private suspend fun trimIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastTrimAt >= TRIM_INTERVAL_MILLIS) {
            applyRetentionPolicy()
        }
    }

    private fun Throwable.stackTraceToText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    private companion object {
        const val MAX_TAG_LENGTH = 128
        const val MAX_TEXT_LENGTH = 2_000
        const val MAX_DETAIL_LENGTH = 20_000
        const val TRIM_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
