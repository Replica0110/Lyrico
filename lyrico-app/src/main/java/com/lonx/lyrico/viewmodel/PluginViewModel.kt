package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.plugin.source.PluginSearchSourceManager
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
    val smokeResult: String = ""
)

class PluginViewModel(
    private val repository: SourcePluginRepository,
    private val installer: SourcePluginInstaller,
    private val pluginManager: PluginSearchSourceManager
) : ViewModel() {
    val plugins: StateFlow<List<SourcePluginEntity>> =
        repository.observePlugins()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()


    fun importPlugin(context: Context, uri: Uri) {
        runBusy("Plugin imported") {
            val installRoot = File(context.filesDir, "plugins/sources")
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open selected file")
            input.use {
                val plugin = installer.installFromArchive(
                    input = it,
                    installRoot = installRoot,
                    enabled = true
                )
                pluginManager.invalidate(plugin.id)
            }
        }
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
                File(plugin.pluginDir).deleteRecursively()
            }
        }
    }


    private companion object {
        const val ACTION_TIMEOUT_MS = 30_000L
    }
}
