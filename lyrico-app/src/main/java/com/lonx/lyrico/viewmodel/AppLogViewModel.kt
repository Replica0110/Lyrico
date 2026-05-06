package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.entity.AppLogEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.utils.UiMessage
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
    private val appLogRepository: AppLogRepository
) : ViewModel() {
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
                val text = if (ids == null) {
                    appLogRepository.exportText()
                } else {
                    appLogRepository.exportText(ids)
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

    companion object {
        private const val TAG = "AppLogViewModel"
    }
}
