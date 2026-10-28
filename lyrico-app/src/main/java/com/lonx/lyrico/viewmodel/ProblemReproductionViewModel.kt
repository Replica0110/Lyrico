package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.diagnostics.ProblemReproductionRecorder
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReproductionUiState(
    val preparingReport: Boolean = false,
    val preview: String? = null,
    val previewTruncated: Boolean = false,
)

/** Page state is local; recording and environment capture keep running when the page is left. */
class ProblemReproductionViewModel(
    private val application: Application,
    private val logs: AppLogRepository,
    private val recorder: ProblemReproductionRecorder,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private companion object {
        const val TAG = "ProblemReproduction"
        const val PREVIEW_CHARACTERS = 12_000
    }

    val recording = recorder.state
    private val state = MutableStateFlow(ReproductionUiState())
    val uiState = state.asStateFlow()
    private val messages = MutableSharedFlow<UiMessage>()
    val events = messages.asSharedFlow()
    private val shares = MutableSharedFlow<Intent>()
    val shareIntents = shares.asSharedFlow()

    fun start() {
        if (recording.value.active || state.value.preparingReport) return
        recorder.start()
        state.value = ReproductionUiState()
    }

    fun stop() = recorder.stop()

    fun preview() {
        if (state.value.preparingReport) return
        val session = recording.value.startedAt
        state.update { it.copy(preparingReport = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = recorder.report(session)
                state.update { it.copy(preview = report.take(PREVIEW_CHARACTERS), previewTruncated = report.length > PREVIEW_CHARACTERS) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportFailure(session, error)
            } finally {
                state.update { it.copy(preparingReport = false) }
            }
        }
    }

    fun dismissPreview() = state.update { it.copy(preview = null, previewTruncated = false) }

    fun clear() {
        if (state.value.preparingReport) return
        recorder.clear()
        state.value = ReproductionUiState()
    }

    fun share() {
        if (state.value.preparingReport) return
        recorder.stop()
        val session = recording.value.startedAt
        state.update { it.copy(preparingReport = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val report = recorder.report(session)
                val directory = File(application.cacheDir, "diagnostic-reports").apply {
                    check(isDirectory || mkdirs()) { "Unable to create report directory" }
                }
                val file = File(directory, "lyrico_recording_$session.log")
                file.writeText(report)
                val uri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", file)
                shares.emit(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                reportFailure(session, error)
            } finally {
                state.update { it.copy(preparingReport = false) }
            }
        }
    }

    fun export(uri: Uri, session: Long) {
        if (state.value.preparingReport) return
        state.update { it.copy(preparingReport = true) }
        appScope.launch(Dispatchers.IO) {
            try {
                val text = recorder.report(session)
                requireNotNull(application.contentResolver.openOutputStream(uri, "wt")) { "Unable to open report destination" }.use {
                    it.write(text.toByteArray(Charsets.UTF_8))
                }
                messages.emit(UiMessage.StringResource(R.string.reproduction_export_success))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logs.recordOperation(AppLogType.APP, TAG, "Reproduction report export failed",
                    "destination=$uri\n${error.stackTraceToString()}", level = AppLogLevel.ERROR)
                reportFailure(session, error)
            } finally {
                state.update { it.copy(preparingReport = false) }
            }
        }
    }

    private suspend fun reportFailure(session: Long, error: Exception) {
        messages.emit(if (session == 0L || recording.value.startedAt != session) {
            UiMessage.StringResource(R.string.reproduction_unavailable)
        } else UiMessage.StringResource(R.string.reproduction_report_failed, error.message.orEmpty()))
    }
}
