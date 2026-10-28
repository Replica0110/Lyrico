package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.DiagnosticEnvironment
import com.lonx.lyrico.data.model.log.LogRetentionOption
import com.lonx.lyrico.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AppLogEvent {
    data class ShowMessage(val message: UiMessage) : AppLogEvent()
}
class AppLogViewModel(
    private val appLogRepository: AppLogRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val logRetentionOption: StateFlow<LogRetentionOption> = settingsRepository.logRetentionOption
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LogRetentionOption.THIRTY_DAYS
        )
    fun observeLogs(level: AppLogLevel?, type: AppLogType?) =
        appLogRepository.observeFiltered(level, type)

    suspend fun getMatchingIds(level: AppLogLevel?, type: AppLogType?) =
        appLogRepository.getMatchingIds(level, type)
    private val _events = MutableSharedFlow<AppLogEvent>()
    val events = _events.asSharedFlow()

    fun deleteLogs(ids: List<Long>) {
        viewModelScope.launch {
            appLogRepository.deleteByIds(ids)
        }
    }

    fun exportLogs(context: Context, uri: Uri, ids: List<Long>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logsText = if (ids == null) {
                    appLogRepository.exportText()
                } else {
                    appLogRepository.exportText(ids)
                }
                val text = buildString {
                    appendLine(DiagnosticEnvironment.describe(context))
                    appendLine()
                    append(logsText)
                }
                requireNotNull(context.contentResolver.openOutputStream(uri)) { "Unable to open export destination" }.use { outputStream ->
                    outputStream.write(text.toByteArray(Charsets.UTF_8))
                }
                _events.emit(AppLogEvent.ShowMessage(UiMessage.StringResource(R.string.app_log_export_success)))
            } catch (e: Exception) {
                appLogRepository.logException(
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Failed to export logs",
                    throwable = e
                )
                _events.emit(
                    AppLogEvent.ShowMessage(
                        UiMessage.StringResource(R.string.app_log_export_failed, e.message ?: "Unknown error")
                    )
                )
            }
        }
    }

    fun setLogRetentionOption(option: LogRetentionOption) {
        viewModelScope.launch {
            settingsRepository.saveLogRetentionOption(option)
            appLogRepository.applyRetentionPolicy()
        }
    }

    companion object {
        private const val TAG = "AppLogViewModel"
    }
}
