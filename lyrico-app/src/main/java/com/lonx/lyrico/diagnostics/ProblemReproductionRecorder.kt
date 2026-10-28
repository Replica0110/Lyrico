package com.lonx.lyrico.diagnostics

import android.app.Application
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.utils.DiagnosticEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** App-scoped recording tasks are awaited before exporting, including after page navigation. */
class ProblemReproductionRecorder(
    private val application: Application,
    private val logs: AppLogRepository,
    private val settings: SettingsRepository,
    private val plugins: SourcePluginRepository,
    private val appScope: CoroutineScope,
) {
    private companion object {
        const val TAG = "ProblemReproduction"
    }

    val state = logs.recordingState
    private val tasks = mutableMapOf<Long, MutableSet<Job>>()

    fun start() {
        if (state.value.active) return
        logs.startRecording()
        val session = state.value.startedAt
        val task = appScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val snapshot = buildString {
                    appendLine(DiagnosticEnvironment.describe(application))
                    appendLine("separator=${settings.separator.first()}")
                    appendLine("searchPageSize=${settings.searchPageSize.first()}")
                    appendLine("ignoreShortAudio=${settings.ignoreShortAudio.first()}")
                    appendLine("lyricIndexEnabled=${settings.lyricIndexEnabled.first()}")
                    appendLine("replayGain=${settings.replayGainSettings.first()}")
                    appendLine("batchMatch=${settings.batchMatchConfig.first()}")
                    plugins.getPlugins().forEach { plugin ->
                        appendLine("plugin=${plugin.id} version=${plugin.versionName}(${plugin.versionCode}) API=${plugin.apiVersion} " +
                            "metadata=${plugin.metadataEnabled} lyrics=${plugin.lyricsEnabled} cover=${plugin.coverEnabled}")
                    }
                }
                if (state.value.startedAt == session) logs.recordOperation(
                    type = AppLogType.APP,
                    tag = TAG,
                    stage = "Reproduction recording started",
                    detail = snapshot,
                    level = AppLogLevel.INFO,
                    recordingStartedAt = session,
                    occurredAt = session,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logs.recordOperation(
                    type = AppLogType.APP,
                    tag = TAG,
                    stage = "Recording context unavailable",
                    detail = error.stackTraceToString(),
                    level = AppLogLevel.WARNING,
                    recordingStartedAt = session,
                    occurredAt = session,
                )
            }
        }
        track(session, task)
    }

    fun stop() = logs.stopRecording()

    fun clear() {
        val session = state.value.startedAt
        logs.clearRecording()
        val pending = synchronized(tasks) { tasks.remove(session)?.toList().orEmpty() }
        pending.forEach { it.cancel() }
    }

    suspend fun report(session: Long): String {
        check(session != 0L && state.value.startedAt == session) { "Recording session changed" }
        val pending = synchronized(tasks) { tasks[session]?.toList().orEmpty() }
        pending.joinAll()
        val report = logs.exportRecording(expectedSessionId = session)
        return DiagnosticEnvironment.describe(application) + "\n" + report
    }

    private fun track(session: Long, task: Job) {
        synchronized(tasks) { tasks.getOrPut(session) { mutableSetOf() }.add(task) }
        task.invokeOnCompletion {
            synchronized(tasks) {
                tasks[session]?.let { pending ->
                    pending.remove(task)
                    if (pending.isEmpty()) tasks.remove(session)
                }
            }
        }
        task.start()
    }
}
