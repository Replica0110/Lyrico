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

data class PluginDebugUiState(
    val isBusy: Boolean = false,
    val message: String = "",
    val messageVersion: Long = 0,
    val smokeResult: String = ""
)

class PluginDebugViewModel(
    private val repository: SourcePluginRepository,
    private val settingsRepository: SettingsRepository,
    private val installer: SourcePluginInstaller,
    private val pluginManager: PluginSearchSourceManager
) : ViewModel() {
    val plugins: StateFlow<List<SourcePluginEntity>> =
        repository.observePlugins()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PluginDebugUiState())
    val uiState: StateFlow<PluginDebugUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()

    fun installMockPlugin(context: Context) {
        runBusy("Mock plugin installed") {
            val pluginDir = File(context.filesDir, "plugins/sources/debug.mock")
            writeMockPlugin(pluginDir)
            val plugin = installer.installFromDirectory(pluginDir, enabled = true)
            pluginManager.invalidate(plugin.id)
        }
    }

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

    private fun writeMockPlugin(pluginDir: File) {
        if (pluginDir.exists()) {
            pluginDir.deleteRecursively()
        }
        pluginDir.mkdirs()

        File(pluginDir, "manifest.json").writeText(MOCK_MANIFEST)
        File(pluginDir, "source.js").writeText(MOCK_SOURCE)
    }

    private companion object {
        val MOCK_MANIFEST = """
            {
              "id": "debug.mock",
              "name": "Debug Mock Source",
              "versionCode": 1,
              "versionName": "1.0.0",
              "author": "Lyrico",
              "description": "Local mock source for plugin pipeline debugging",
              "apiVersion": 1,
              "entry": "source.js",
              "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
              "configFields": [
                {
                  "key": "suffix",
                  "title": "Title suffix",
                  "type": "text",
                  "defaultValue": ""
                }
              ],
              "metadataFields": [
                {
                  "key": "title",
                  "title": "Title",
                  "group": "basic",
                  "defaultTarget": "TITLE",
                  "defaultMode": "OVERWRITE"
                },
                {
                  "key": "artist",
                  "title": "Artist",
                  "group": "basic",
                  "defaultTarget": "ARTIST",
                  "defaultMode": "OVERWRITE"
                },
                {
                  "key": "subtitle",
                  "title": "Subtitle",
                  "group": "extended",
                  "defaultTarget": "SUBTITLE",
                  "defaultMode": "SUPPLEMENT"
                },
                {
                  "key": "internal_track_id",
                  "title": "Internal Track ID",
                  "group": "internal",
                  "writeable": false,
                  "internal": true
                }
              ]
            }
        """.trimIndent()

        val MOCK_SOURCE = """
            const songs = [
              {
                id: "mock-001",
                title: "Mock Song",
                artist: "Lyrico",
                album: "Plugin Debug",
                duration: 182000,
                picUrl: "https://via.placeholder.com/480.png?text=Lyrico",
                fields: {
                  title: "Mock Song",
                  artist: "Lyrico",
                  album: "Plugin Debug",
                  date: "2026",
                  track_number: "1",
                  subtitle: "Generated by QuickJS",
                  internal_track_id: "mock-001"
                }
              },
              {
                id: "mock-002",
                title: "Another Mock Track",
                artist: "Lyrico",
                album: "Plugin Debug",
                duration: 205000,
                picUrl: "https://via.placeholder.com/480.png?text=Mock",
                fields: {
                  title: "Another Mock Track",
                  artist: "Lyrico",
                  album: "Plugin Debug",
                  date: "2026",
                  track_number: "2",
                  internal_track_id: "mock-002"
                }
              }
            ];

            function searchSongs(request) {
              const keyword = String(request.keyword || "").toLowerCase();
              const suffix = request.config && request.config.suffix ? String(request.config.suffix) : "";
              return songs
                .filter(song =>
                  !keyword ||
                  song.title.toLowerCase().includes(keyword) ||
                  song.artist.toLowerCase().includes(keyword) ||
                  "mock".includes(keyword)
                )
                .slice(0, request.pageSize || 20)
                .map(song => ({
                  id: song.id,
                  title: song.title + suffix,
                  artist: song.artist,
                  album: song.album,
                  duration: song.duration,
                  picUrl: song.picUrl,
                  fields: Object.assign({}, song.fields, { title: song.fields.title + suffix })
                }));
            }

            function getLyrics(request) {
              const title = request.song && request.song.title ? request.song.title : "Mock Song";
              return {
                type: "raw",
                tags: {
                  ti: title,
                  ar: "Lyrico"
                },
                rawPlainLrc: "[00:00.00]" + title + "\n[00:05.00]This lyric came from a QuickJS plugin"
              };
            }

            function searchCovers(request) {
              return songs
                .filter(song => song.picUrl)
                .slice(0, request.pageSize || 3)
                .map(song => ({
                  id: song.id,
                  title: song.title,
                  artist: song.artist,
                  album: song.album,
                  duration: song.duration,
                  picUrl: song.picUrl,
                  fields: Object.assign({}, song.fields, { cover_url: song.picUrl })
                }));
            }
        """.trimIndent()
        const val ACTION_TIMEOUT_MS = 30_000L
    }
}
