package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.MetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.plugin.source.PluginSearchSourceManager
import com.lonx.lyrico.plugin.source.PluginImportSession
import com.lonx.lyrico.plugin.source.PluginVersionConflict
import com.lonx.lyrico.plugin.source.SourcePluginInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

data class PluginUiState(
    val isBusy: Boolean = false,
    val message: String = "",
    val messageVersion: Long = 0,
    val smokeResult: String = "",
    val pendingImport: PluginImportSession? = null,
    val selectedImportRoots: Set<String> = emptySet()
)

class PluginViewModel(
    private val repository: SourcePluginRepository,
    private val settingsRepository: SettingsRepository,
    private val installer: SourcePluginInstaller,
    private val pluginManager: PluginSearchSourceManager,
    private val appLogRepository: AppLogRepository
) : ViewModel() {
    val plugins: StateFlow<List<SourcePluginEntity>> =
        repository.observePlugins()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()


    fun importPlugin(context: Context, uri: Uri) {
        runBusy("Plugin package scanned") {
            _uiState.value.pendingImport?.let { installer.discardImport(it) }
            val installRoot = File(context.filesDir, "plugins/sources")
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open selected file")
            input.use {
                val session = installer.prepareImport(
                    input = it,
                    installRoot = installRoot
                )
                if (session.candidates.isEmpty()) {
                    installer.discardImport(session)
                    val failureReason = session.failed.firstOrNull()?.reason ?: "No installable plugin found"
                    logPluginError("Import failed", failureReason, session.failed.joinToString("\n") { "${it.rootPath}: ${it.reason}" })
                    error(failureReason)
                }
                _uiState.update { state ->
                    state.copy(
                        pendingImport = session,
                        selectedImportRoots = session.candidates
                            .filter { candidate -> candidate.versionConflict != PluginVersionConflict.DOWNGRADE }
                            .mapTo(mutableSetOf()) { candidate -> candidate.relativeRootInArchive }
                    )
                }
            }
        }
    }

    fun setImportCandidateSelected(rootPath: String, selected: Boolean) {
        _uiState.update { state ->
            val next = state.selectedImportRoots.toMutableSet()
            if (selected) {
                next += rootPath
            } else {
                next -= rootPath
            }
            state.copy(selectedImportRoots = next)
        }
    }

    fun installPendingImport() {
        val session = _uiState.value.pendingImport ?: run {
            publishMessage("No pending plugin import")
            return
        }
        val selectedRoots = _uiState.value.selectedImportRoots
        if (selectedRoots.isEmpty()) {
            publishMessage("No plugin selected")
            return
        }
        val allowDowngrade = session.candidates.any { candidate ->
            candidate.relativeRootInArchive in selectedRoots &&
                candidate.versionConflict == PluginVersionConflict.DOWNGRADE
        }
        _uiState.update { it.copy(pendingImport = null, selectedImportRoots = emptySet()) }
        runBusy("Plugin imported") {
            val result = installer.installPrepared(
                session = session,
                enabled = true,
                selectedRoots = selectedRoots,
                allowDowngrade = allowDowngrade
            )
            result.installed.forEach { plugin ->
                pluginManager.invalidate(plugin.id)
            }
            syncMetadataRules()
            if (result.installed.isEmpty()) {
                val failureReason = result.failed.firstOrNull()?.reason ?: "No installable plugin found"
                logPluginError("Install failed", failureReason, result.failed.joinToString("\n") { "${it.rootPath}: ${it.reason}" })
                error(failureReason)
            }
        }
    }

    fun dismissPendingImport() {
        _uiState.value.pendingImport?.let { installer.discardImport(it) }
        _uiState.update { it.copy(pendingImport = null, selectedImportRoots = emptySet()) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
            pluginManager.invalidate(id)
        }
    }

    fun setPluginOrder(plugins: List<SourcePluginEntity>) {
        viewModelScope.launch {
            plugins.forEachIndexed { index, plugin ->
                repository.updateSortOrder(plugin.id, index)
                pluginManager.invalidate(plugin.id)
            }
        }
    }


    private fun runBusy(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (actionMutex.isLocked) {
                publishMessage("Another plugin debug action is still running")
                return@launch
            }
            _uiState.update {
                it.copy(
                    isBusy = true,
                    message = "Working...",
                    messageVersion = System.nanoTime()
                )
            }
            try {
                actionMutex.withLock {
                    withTimeout(ACTION_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            block()
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = successMessage,
                        messageVersion = System.nanoTime()
                    )
                }
            } catch (e: TimeoutCancellationException) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = "Plugin debug action timed out",
                        messageVersion = System.nanoTime(),
                        smokeResult = "Timed out after ${ACTION_TIMEOUT_MS / 1000}s"
                    )
                }
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = message,
                        messageVersion = System.nanoTime(),
                        smokeResult = message
                    )
                }
            }
        }
    }

    private fun publishMessage(message: String) {
        _uiState.update {
            it.copy(
                message = message,
                messageVersion = System.nanoTime()
            )
        }
    }

    fun uninstallPlugin(id: String) {
        runBusy("Plugin deleted") {
            val plugin = repository.getPlugin(id)
            if (plugin != null) {
                repository.uninstallPlugin(id)
                pluginManager.invalidate(plugin.id)
                settingsRepository.removePluginSettings(plugin.id)
                File(plugin.pluginDir).deleteRecursively()
            }
        }
    }

    private suspend fun syncMetadataRules() {
        val sources = pluginManager.getEnabledSources()
        val mergedRules = MetadataFieldWriteRuleFactory.mergeWithDeclaredFields(
            savedRules = settingsRepository.getMetadataFieldWriteRules(),
            searchSources = sources
        )
        settingsRepository.saveMetadataFieldWriteRules(mergedRules)
    }

    private suspend fun logPluginError(message: String, detail: String, fullDetail: String? = null) {
        try {
            appLogRepository.log(
                level = AppLogLevel.ERROR,
                type = AppLogType.APP,
                tag = TAG,
                message = message,
                detail = fullDetail ?: detail
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write plugin log", e)
        }
    }

    private companion object {
        const val ACTION_TIMEOUT_MS = 30_000L
        const val TAG = "PluginViewModel"
    }
}
