package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.entity.AppLogEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.utils.UiMessage
import com.hjq.device.compat.DeviceMarketName
import com.hjq.device.compat.DeviceOs
import com.hjq.device.compat.SystemPropertyCompat
import com.lonx.lyrico.data.model.LogRetentionOption
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
data class AppLogUiState(
    val logRetentionOption: LogRetentionOption = LogRetentionOption.THIRTY_DAYS,
)

class AppLogViewModel(
    private val appLogRepository: AppLogRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val logRetentionOption: StateFlow<LogRetentionOption> = settingsRepository.logRetentionOption
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LogRetentionOption.THIRTY_DAYS
        )
    val logs: StateFlow<List<AppLogEntity>> = appLogRepository.observeLatest().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
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
                    appendLine(buildDiagnosticInfo(context))
                    appendLine()
                    append(logsText)
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(text.toByteArray(Charsets.UTF_8))
                }
                _events.emit(AppLogEvent.ShowMessage(UiMessage.StringResource(R.string.export_success)))
            } catch (e: Exception) {
                appLogRepository.logException(
                    type = AppLogType.APP,
                    tag = TAG,
                    message = "Failed to export logs",
                    throwable = e
                )
                _events.emit(
                    AppLogEvent.ShowMessage(
                        UiMessage.StringResource(R.string.export_failed, e.message ?: "Unknown error")
                    )
                )
            }
        }
    }

    private fun buildDiagnosticInfo(context: Context): String = buildString {
        appendLine("Lyrico diagnostic info")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device model: ${buildDeviceModel(context)}")
        appendLine("System version: ${buildSystemVersion()}")
        appendLine("Android version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    private fun buildDeviceModel(context: Context): String =
        DeviceMarketName.getMarketName(context).takeIfNotBlank()
            ?: listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { Build.DEVICE }

    private fun buildSystemVersion(): String {
        val osName = DeviceOs.getOsName().takeIfNotBlank()
        val osVersion = DeviceOs.getOsVersionName().takeIfNotBlank()
        return listOfNotNull(osName, osVersion)
            .joinToString(" ")
            .ifBlank { "Android ${Build.VERSION.RELEASE}" }
    }

    private fun String?.takeIfNotBlank(): String? =
        this?.trim()?.takeIf { it.isNotBlank() }

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
