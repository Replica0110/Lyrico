package com.lonx.lyrico.screens.settings.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.utils.LibraryScanManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class LibraryMaintenanceAction { REBUILD_INDEXES, RESET_LIBRARY }

internal data class LibraryMaintenanceResult(val action: LibraryMaintenanceAction, val succeeded: Boolean)

internal data class DiagnosticsSettingsUiState(
    val runningAction: LibraryMaintenanceAction? = null,
    val result: LibraryMaintenanceResult? = null,
)

internal class DiagnosticsSettingsViewModel(
    private val libraryIndexRepository: LibraryIndexRepository,
    private val libraryScanManager: LibraryScanManager,
    private val appLogRepository: AppLogRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DiagnosticsSettingsUiState())
    val uiState = state.asStateFlow()

    fun perform(action: LibraryMaintenanceAction) {
        if (state.value.runningAction != null) return
        state.update { it.copy(runningAction = action, result = null) }
        viewModelScope.launch {
            try {
                val succeeded = withContext(Dispatchers.IO) {
                    when (action) {
                        LibraryMaintenanceAction.REBUILD_INDEXES -> {
                            libraryIndexRepository.rebuildAllIndexes()
                            true
                        }
                        LibraryMaintenanceAction.RESET_LIBRARY -> libraryScanManager.clearLibrary()
                    }
                }
                state.update { it.copy(result = LibraryMaintenanceResult(action, succeeded)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(result = LibraryMaintenanceResult(action, false)) }
                appLogRepository.recordOperation(
                    AppLogType.DATABASE,
                    "LibraryMaintenance",
                    "${action.name} failed",
                    error.stackTraceToString(),
                    level = AppLogLevel.ERROR,
                )
            } finally {
                state.update { it.copy(runningAction = null) }
            }
        }
    }

    fun clearResult() {
        state.update { it.copy(result = null) }
    }
}
