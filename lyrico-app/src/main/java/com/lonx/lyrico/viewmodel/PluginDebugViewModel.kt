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

    fun installKugouPlugin(context: Context) {
        runBusy("Kugou plugin installed") {
            val pluginDir = File(context.filesDir, "plugins/sources/com.kugou.source")
            writeKugouPlugin(pluginDir)
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
                appendLine("getLyrics: ${lyrics?.rawPlainLrc?.takeIf { it.isNotBlank() } ?: "null"}")
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

    private fun writeKugouPlugin(pluginDir: File) {
        if (pluginDir.exists()) {
            pluginDir.deleteRecursively()
        }
        pluginDir.mkdirs()

        File(pluginDir, "manifest.json").writeText(KUGOU_MANIFEST)
        File(pluginDir, "source.js").writeText(KUGOU_SOURCE)
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

        val KUGOU_MANIFEST = """
            {
              "id": "com.kugou.source",
              "name": "Kugou Experimental",
              "versionCode": 1,
              "versionName": "0.1.0",
              "author": "Lyrico",
              "description": "Experimental script version of the built-in Kugou source",
              "apiVersion": 1,
              "entry": "source.js",
              "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
              "requiredHostApis": [
                "http.text",
                "crypto.md5",
                "base64",
                "bytes",
                "compression.zlib"
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
                  "key": "album",
                  "title": "Album",
                  "group": "basic",
                  "defaultTarget": "ALBUM",
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
                  "key": "hash",
                  "title": "Hash",
                  "group": "internal",
                  "writeable": false,
                  "internal": true
                }
              ]
            }
        """.trimIndent()

        val KUGOU_SOURCE = """
            const SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA";
            const KRC_KEY = [64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105];
            const DEVICE_MID = Lyrico.crypto.md5(String(Date.now()));

            function buildQuery(params) {
              return Object.keys(params)
                .sort()
                .map(key => encodeURIComponent(key) + "=" + encodeURIComponent(String(params[key])))
                .join("&");
            }

            function signParams(customParams, body, module) {
              const now = Math.floor(Date.now() / 1000);
              const params = {};
              if (module === "Lyric") {
                params.appid = "3116";
                params.clientver = "11070";
              } else {
                params.userid = "0";
                params.appid = "3116";
                params.token = "";
                params.clienttime = String(now);
                params.iscorrection = "1";
                params.uuid = "-";
                params.mid = DEVICE_MID;
                params.dfid = "-";
                params.clientver = "11070";
                params.platform = "AndroidFilter";
              }

              Object.keys(customParams || {}).forEach(key => params[key] = customParams[key]);
              const sorted = Object.keys(params)
                .sort()
                .map(key => key + "=" + params[key])
                .join("");
              params.signature = Lyrico.crypto.md5(SALT + sorted + (body || "") + SALT);
              return params;
            }

            function getJson(url, headers) {
              const text = Lyrico.http.getText(url, {
                headers: Object.assign({
                  "User-Agent": "Android14-1070-11070-201-0-SearchSong-wifi"
                }, headers || {})
              });
              return JSON.parse(text);
            }

            function normalizeImage(url) {
              return String(url || "").replace("{size}", "480").replace("http:", "https:");
            }

            function mapSong(item, separator) {
              const singers = Array.isArray(item.Singers) ? item.Singers : [];
              const artist = singers.map(s => s.name || s.Name || "").filter(Boolean).join(separator || "/");
              return {
                id: String(item.ID || ""),
                title: String(item.SongName || ""),
                artist: artist,
                album: String(item.AlbumName || ""),
                duration: Number(item.Duration || 0) * 1000,
                date: String(item.PublishDate || ""),
                picUrl: normalizeImage(item.Image),
                fields: {
                  title: String(item.SongName || ""),
                  artist: artist,
                  album: String(item.AlbumName || ""),
                  date: String(item.PublishDate || ""),
                  subtitle: String(item.Auxiliary || ""),
                  hash: String(item.FileHash || "")
                }
              };
            }

            function searchSongs(request) {
              const params = signParams({
                keyword: request.keyword || "",
                page: String(request.page || 1),
                pagesize: String(request.pageSize || 20)
              }, "", "Search");
              const url = "https://complexsearch.kugou.com/v2/search/song?" + buildQuery(params);
              const response = getJson(url, { "x-router": "complexsearch.kugou.com" });
              if (Number(response.error_code || 0) !== 0) return [];
              const list = response.data && Array.isArray(response.data.lists) ? response.data.lists : [];
              return list.map(item => mapSong(item, request.separator || "/"));
            }

            function searchCovers(request) {
              return searchSongs({
                keyword: request.keyword,
                page: 1,
                pageSize: request.pageSize || 5,
                separator: "/"
              }).filter(song => song.picUrl);
            }

            function decryptKrc(base64Content) {
              const bodyBase64 = Lyrico.base64.dropBytes(base64Content || "", 4);
              const decodedBase64 = Lyrico.bytes.xorBase64(bodyBase64, KRC_KEY);
              return Lyrico.compression.inflateBase64ToText(decodedBase64);
            }

            function formatTime(ms) {
              const total = Math.max(0, Math.floor(ms));
              const minutes = Math.floor(total / 60000);
              const seconds = Math.floor((total % 60000) / 1000);
              const centis = Math.floor((total % 1000) / 10);
              return "[" +
                String(minutes).padStart(2, "0") + ":" +
                String(seconds).padStart(2, "0") + "." +
                String(centis).padStart(2, "0") + "]";
            }

            function parseKrcToLrc(krcText) {
              return String(krcText || "")
                .split(/\r?\n/)
                .map(line => {
                  if (!line || line.indexOf("[language:") === 0) return null;
                  const match = line.match(/^\[(\d+),(\d+)](.*)$/);
                  if (!match) return null;
                  const start = Number(match[1] || 0);
                  const body = String(match[3] || "");
                  const text = body
                    .replace(/<\d+,\d+,\d+>/g, "")
                    .replace(/\[[^\]]+]/g, "")
                    .trim();
                  if (!text) return null;
                  return formatTime(start) + text;
                })
                .filter(Boolean)
                .join("\n");
            }

            function getLyrics(request) {
              const song = request.song || {};
              const fields = song.fields || {};
              const hash = fields.hash || fields.KG_HASH || "";
              if (!hash) return null;

              const searchParams = signParams({
                album_audio_id: song.id || "",
                duration: String(song.duration || 0),
                hash: hash,
                keyword: (song.artist || "") + " - " + (song.title || ""),
                lrctxt: "1",
                man: "no"
              }, "", "Lyric");
              const searchUrl = "https://lyrics.kugou.com/v1/search?" + buildQuery(searchParams);
              const searchResp = getJson(searchUrl, {});
              const candidate = searchResp.candidates && searchResp.candidates[0];
              if (!candidate) return null;

              const downloadParams = signParams({
                accesskey: candidate.accesskey,
                charset: "utf8",
                client: "mobi",
                fmt: "krc",
                id: candidate.id,
                ver: "1"
              }, "", "Lyric");
              const downloadUrl = "https://lyrics.kugou.com/download?" + buildQuery(downloadParams);
              const contentResp = getJson(downloadUrl, {});
              if (!contentResp || !contentResp.content) return null;

              const lyricText = Number(contentResp.contenttype || 0) === 2
                ? Lyrico.base64.decodeText(contentResp.content)
                : decryptKrc(contentResp.content);
              const rawPlainLrc = parseKrcToLrc(lyricText);

              return {
                type: "raw",
                tags: {
                  ti: song.title || "",
                  ar: song.artist || "",
                  al: song.album || ""
                },
                rawPlainLrc: rawPlainLrc || lyricText
              };
            }
        """.trimIndent()

        const val ACTION_TIMEOUT_MS = 30_000L
    }
}
