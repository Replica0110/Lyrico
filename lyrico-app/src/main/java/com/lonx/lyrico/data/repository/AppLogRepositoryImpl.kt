package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.LogRetentionOption
import com.lonx.lyrico.data.model.dao.AppLogDao
import com.lonx.lyrico.data.model.entity.AppLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogRepositoryImpl(
    private val dao: AppLogDao,
    private val settingsRepository: SettingsRepository
) : AppLogRepository {
    @Volatile
    private var lastTrimAt: Long = 0L

    override fun observeLatest(limit: Int): Flow<List<AppLogEntity>> =
        dao.observeLatest(limit)

    override fun observeByRelatedId(relatedId: String): Flow<List<AppLogEntity>> =
        dao.observeByRelatedId(relatedId)

    override suspend fun getLatest(limit: Int): List<AppLogEntity> =
        dao.getLatest(limit)

    override suspend fun getByIds(ids: List<Long>): List<AppLogEntity> =
        if (ids.isEmpty()) emptyList() else dao.getByIds(ids)

    override suspend fun exportText(limit: Int): String {
        val logs = getLatest(limit).asReversed()
        return formatExportText(logs)
    }

    override suspend fun exportText(ids: List<Long>): String {
        val logs = getByIds(ids).asReversed()
        return formatExportText(logs)
    }

    private fun formatExportText(logs: List<AppLogEntity>): String {
        return buildString {
            appendLine("Lyrico log export")
            appendLine("Generated: ${formatTime(System.currentTimeMillis())}")
            appendLine("Count: ${logs.size}")
            appendLine()
            logs.forEach { log ->
                appendLine("${formatTime(log.createdAt)} [${log.level}/${log.type}] ${log.tag}")
                appendLine(log.message)
                log.relatedId?.let { appendLine("Related: $it") }
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
        relatedId: String?
    ) {
        if (!settingsRepository.logRetentionOption.first().isRecordingEnabled) return
        dao.insert(
            AppLogEntity(
                level = level,
                type = type,
                tag = tag,
                message = message.take(MAX_TEXT_LENGTH),
                detail = detail?.take(MAX_DETAIL_LENGTH),
                relatedId = relatedId
            )
        )
        trimIfDue()
    }

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
        dao.deleteByIds(ids)
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
        const val MAX_TEXT_LENGTH = 2_000
        const val MAX_DETAIL_LENGTH = 20_000
        const val TRIM_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
