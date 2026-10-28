package com.lonx.lyrico.data.model.log

import com.lonx.lyrico.data.repository.AppLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicLong

/** Non-suspending entry for Android callbacks and shared parsers/writers. */
internal object AppLog {
    private const val MAX_PENDING_EVENTS = 128
    private const val MAX_RECENT_FAILURES = 64
    private const val REPEATED_FAILURE_WINDOW_NANOS = 5_000_000_000L
    private sealed interface Queued {
        data class Event(
            val type: AppLogType,
            val tag: String,
            val stage: String,
            val detail: String,
            val relatedId: String?,
            val level: AppLogLevel,
            val session: Long,
            val time: Long,
        ) : Queued

        data class Barrier(val completed: CompletableDeferred<Unit>) : Queued
    }
    private class Destination(val repository: AppLogRepository) {
        val wakeup = Channel<Unit>(Channel.CONFLATED)
        val pending = ArrayDeque<Queued>()
    }
    @Volatile
    private var destination: Destination? = null
    private val recentFailures = linkedMapOf<String, Long>()
    private val dropped = AtomicLong()
    val droppedCount: Long get() = dropped.get()

    fun initialize(repository: AppLogRepository, scope: CoroutineScope) {
        val target = Destination(repository)
        destination = target
        scope.launch(Dispatchers.IO) {
            for (signal in target.wakeup) {
                while (true) {
                    val item = synchronized(target) { target.pending.removeFirstOrNull() } ?: break
                    when (item) {
                        is Queued.Barrier -> item.completed.complete(Unit)
                        is Queued.Event -> try {
                            repository.log(
                                item.level, item.type, item.tag, item.stage, item.detail,
                                item.relatedId, item.session, item.time,
                            )
                        } catch (error: kotlinx.coroutines.CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            dropped.incrementAndGet()
                            android.util.Log.w("AppLog", "Unable to store diagnostic event", error)
                        }
                    }
                }
            }
        }
    }

    suspend fun flush() {
        val target = destination ?: return
        val completed = CompletableDeferred<Unit>()
        synchronized(target) {
            target.pending.addLast(Queued.Barrier(completed))
        }
        target.wakeup.trySend(Unit)
        completed.await()
    }

    fun record(
        type: AppLogType,
        tag: String,
        stage: String,
        detail: String,
        relatedId: String? = null,
        level: AppLogLevel = AppLogLevel.DEBUG,
        error: Throwable? = null,
        repeatedFailureKey: String? = null,
    ) {
        val target = destination ?: return
        if (level <= AppLogLevel.INFO && LogOperation.current.get()?.recordRoutine == false) return
        val recording = target.repository.recordingState.value
        val occurredAt = System.currentTimeMillis()
        if (level == AppLogLevel.DEBUG && !recording.active) return
        if (repeatedFailureKey != null && suppress(repeatedFailureKey)) return
        val operation = LogOperation.current.get()
        val context = operationLogDetail(operation, detail)
        val session = recording.takeIf { it.active }?.startedAt ?: NO_RECORDING_SESSION
        val event = Queued.Event(type, tag, stage,
            context + (error?.let { "\n${it.stackTraceToString()}" } ?: ""), relatedId, level,
            session, occurredAt)
        synchronized(target) {
            val events = target.pending.filterIsInstance<Queued.Event>()
            if (events.size >= MAX_PENDING_EVENTS) {
                val victim = events.minByOrNull { it.level.ordinal }
                dropped.incrementAndGet()
                if (victim == null || victim.level.ordinal > event.level.ordinal) return
                target.pending.remove(victim)
            }
            target.pending.addLast(event)
        }
        target.wakeup.trySend(Unit)
    }

    @Synchronized private fun suppress(key: String): Boolean {
        val now = System.nanoTime()
        val previous = recentFailures[key]
        if (previous != null && now - previous < REPEATED_FAILURE_WINDOW_NANOS) return true
        recentFailures[key] = now
        if (recentFailures.size > MAX_RECENT_FAILURES) recentFailures.remove(recentFailures.keys.first())
        return false
    }
}
