package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository,
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

    fun deletePlugin(plugin: SourcePluginEntity) {
        runBusy("Plugin deleted") {
            repository.deletePlugin(plugin)
            pluginManager.invalidate(plugin.id)
            File(plugin.pluginDir).deleteRecursively()
        }
    }

    fun runSmokeTest(pluginId: String? = null) {
        runBusy("Smoke test finished") {
            val sources = pluginManager.getEnabledSources()
            val source = pluginId
                ?.let { id -> sources.firstOrNull { it.id == id } }
                ?: sources.firstOrNull()
                ?: error("No enabled plugin source")
            source.applyConfig(settingsRepository.getSourceSettings(source.id))

            val songs = source.search(
                keyword = "爱情转移",
                page = 1,
                separator = "/",
                pageSize = 5
            )
            val first = songs.firstOrNull()
            val lyrics = first?.let { source.getLyrics(it) }
            val covers = source.searchCover(keyword = "爱情转移", pageSize = 3)

            buildString {
                appendLine("Source: ${source.name} (${source.id})")
                appendLine("searchSongs: ${songs.size} result(s)")
                first?.let {
                    appendLine("first: ${it.title} - ${it.artist}")
                    appendLine("fields: ${it.normalizedFields()}")
                }
                appendLine(
                    "getLyrics: " + when {
                        lyrics == null -> "null"
                        lyrics.rawPlainLrc.isNotBlank() -> lyrics.rawPlainLrc
                        else -> "${lyrics.original.size} structured line(s)"
                    }
                )
                appendLine("searchCovers: ${covers.size} result(s)")
                covers.firstOrNull()?.let { appendLine("cover: ${it.picUrl}") }
            }.also { result ->
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(smokeResult = result) }
                }
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



    private companion object {
        const val ACTION_TIMEOUT_MS = 30_000L
    }
}
