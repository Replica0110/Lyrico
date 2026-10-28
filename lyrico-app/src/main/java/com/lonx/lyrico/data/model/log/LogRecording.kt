package com.lonx.lyrico.data.model.log

import com.lonx.lyrico.data.model.entity.AppLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val NO_RECORDING_SESSION = 0L

data class LogRecordingState(
    val startedAt: Long = 0,
    val finishedAt: Long? = null,
    val count: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    val limitReached: Boolean = false,
    val omittedCount: Int = 0,
) {
    val active: Boolean get() = startedAt != 0L && finishedAt == null
}

/** Process-local recording, shared across pages; independent of the log retention setting. */
internal class LogRecording(private val scope: CoroutineScope) {
    private companion object {
        const val MAX_DURATION_MS = 10 * 60_000L
        const val MAX_ENTRIES = 1000
        const val MAX_CHARACTERS = 1_000_000
    }
    private val mutableState = MutableStateFlow(LogRecordingState())
    val state = mutableState.asStateFlow()
    private val entries = mutableListOf<AppLogEntity>()
    private var characters = 0
    private var timeout: Job? = null
    private var generation = 0L
    private var lastStartedAt = 0L

    @Synchronized fun start() {
        timeout?.cancel()
        entries.clear()
        characters = 0
        val currentGeneration = ++generation
        lastStartedAt = maxOf(System.currentTimeMillis(), lastStartedAt + 1)
        mutableState.value = LogRecordingState(startedAt = lastStartedAt)
        timeout = scope.launch {
            delay(MAX_DURATION_MS)
            stopGeneration(currentGeneration)
        }
    }

    @Synchronized private fun stopGeneration(expected: Long) {
        if (generation == expected) stop()
    }

    @Synchronized fun stop() {
        timeout?.cancel()
        if (state.value.active) mutableState.value = state.value.copy(finishedAt = System.currentTimeMillis())
    }

    @Synchronized fun clear() {
        generation++
        timeout?.cancel()
        entries.clear()
        characters = 0
        mutableState.value = LogRecordingState()
    }

    @Synchronized fun add(entry: AppLogEntity, capturedSession: Long? = null) {
        val current = state.value
        val capturedBeforeStop = capturedSession != null && capturedSession == current.startedAt &&
            current.finishedAt?.let { entry.createdAt <= it } == true
        if (!current.active && !capturedBeforeStop) return
        if (capturedSession != null && capturedSession != current.startedAt) return
        val size = entry.size()
        if (size > MAX_CHARACTERS) {
            omitted()
            return
        }
        val victims = mutableListOf<AppLogEntity>()
        var remainingCharacters = characters
        if (entries.size >= MAX_ENTRIES || characters + size > MAX_CHARACTERS) {
            val candidates = entries.filter { it.priority() <= entry.priority() }.sortedBy { it.priority() }
            for (candidate in candidates) {
                if (entries.size - victims.size < MAX_ENTRIES && remainingCharacters + size <= MAX_CHARACTERS) break
                victims += candidate
                remainingCharacters -= candidate.size()
            }
            if (entries.size - victims.size >= MAX_ENTRIES || remainingCharacters + size > MAX_CHARACTERS) {
                omitted()
                return
            }
        }
        victims.forEach { victim ->
            entries.remove(victim)
            characters -= victim.size()
            mutableState.value = state.value.copy(
                warningCount = state.value.warningCount - if (victim.level == AppLogLevel.WARNING) 1 else 0,
                errorCount = state.value.errorCount - if (victim.level == AppLogLevel.ERROR) 1 else 0,
                count = entries.size,
            )
            omitted()
        }
        entries += entry
        characters += size
        mutableState.value = state.value.copy(
            count = entries.size,
            warningCount = state.value.warningCount + if (entry.level == AppLogLevel.WARNING) 1 else 0,
            errorCount = state.value.errorCount + if (entry.level == AppLogLevel.ERROR) 1 else 0,
        )
    }

    private fun omitted() {
        mutableState.value = state.value.copy(limitReached = true, omittedCount = state.value.omittedCount + 1)
    }

    private fun AppLogEntity.size() = message.length + detail.orEmpty().length + relatedId.orEmpty().length

    private fun AppLogEntity.priority(): Int = when {
        tag == "ProblemReproduction" && message == "Reproduction recording started" -> 4
        tag == "BatchTaskWorker" && message.startsWith("Batch task ") -> 4
        else -> level.ordinal
    }

    @Synchronized fun snapshot(): Pair<LogRecordingState, List<AppLogEntity>> = state.value to entries.toList()
}
