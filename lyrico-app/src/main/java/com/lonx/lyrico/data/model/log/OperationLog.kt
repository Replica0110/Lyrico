package com.lonx.lyrico.data.model.log

import android.util.Log
import com.lonx.lyrico.data.repository.AppLogRepository
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ThreadContextElement

internal class LogOperation(
    val id: String,
    val parentId: String? = null,
    val recordRoutine: Boolean = true,
) : AbstractCoroutineContextElement(Key), ThreadContextElement<LogOperation?> {
    companion object Key : CoroutineContext.Key<LogOperation> {
        val current = ThreadLocal<LogOperation?>()
    }
    override fun updateThreadContext(context: CoroutineContext): LogOperation? =
        current.get().also { current.set(this) }

    override fun restoreThreadContext(context: CoroutineContext, oldState: LogOperation?) {
        current.set(oldState)
    }
}

internal suspend fun newLogOperation(name: String) = LogOperation(
    "$name-${UUID.randomUUID().toString().take(8)}",
    currentCoroutineContext()[LogOperation]?.id,
    currentCoroutineContext()[LogOperation]?.recordRoutine ?: true,
)

internal suspend fun <T> withLogOperation(name: String, block: suspend () -> T): T =
    withContext(newLogOperation(name)) { block() }

internal fun operationLogDetail(operation: LogOperation?, detail: String?): String =
    listOfNotNull(
        operation?.let { "operationId=${it.id}" },
        operation?.parentId?.let { "parentOperationId=$it" },
        detail,
    ).joinToString("\n")

/** Logging must never turn a successful business operation into a failure. */
internal suspend fun AppLogRepository.recordOperation(
    type: AppLogType,
    tag: String,
    stage: String,
    detail: String,
    relatedId: String? = null,
    level: AppLogLevel = AppLogLevel.DEBUG,
    recordingStartedAt: Long? = null,
    occurredAt: Long = System.currentTimeMillis(),
) {
    try {
        log(level, type, tag, stage, detail, relatedId, recordingStartedAt, occurredAt)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w("OperationLog", "Unable to record $tag/$stage", error)
    }
}
