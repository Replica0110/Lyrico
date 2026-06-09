package com.lonx.lyrico.plugin.source

import android.util.Log
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.plugin.runtime.PluginJsRuntime
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class ScriptSearchSource(
    private val manifest: PluginManifest,
    private val script: String,
    private val displayName: String = manifest.name,
    override val iconPath: String? = null,
    private val appLogRepository: AppLogRepository? = null,
    private val json: Json = defaultJson,
    private val runtimeFactory: () -> PluginJsRuntime = { QuickJsRuntime() }
) : SearchSource, AutoCloseable {
    override val id: String = manifest.id
    override val name: String = displayName
    override val capabilities: Set<PluginCapability> =
        manifest.capabilities.toSet()
            .ifEmpty { setOf(PluginCapability.SEARCH_SONGS) }
    override val configFields: List<PluginConfigField> = manifest.configFields
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            null,
            runnable,
            "QuickJS-$id",
            4L * 1024L * 1024L
        )
    }

    private val quickJsDispatcher = executor.asCoroutineDispatcher()
    private val parser = PluginJsonParser(json)
    private var config = SourceRuntimeConfig()
    private val runtimeDelegate = lazy {
        runtimeFactory().also {
            it.eval(script, manifest.entry)
        }
    }
    private val runtime: PluginJsRuntime by runtimeDelegate

    override fun applyConfig(config: SourceRuntimeConfig) {
        this.config = config
    }

    override suspend fun searchSongs(
        keyword: String,
        page: Int,
        separator: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(quickJsDispatcher) {
        try {
            if (PluginCapability.SEARCH_SONGS !in capabilities) {
                return@withContext emptyList()
            }

            val request = PluginSearchSongsRequest(
                keyword = keyword,
                page = page,
                pageSize = pageSize,
                separator = separator,
                config = config.values
            )
            val raw = runtime.call(FUNCTION_SEARCH_SONGS, json.encodeToString(request))
            val results = parser.parseSongResults(
                rawJson = raw,
                pluginId = id,
                pluginName = name
            )
            Log.d(
                TAG,
                "searchSongs plugin=$id version=${manifest.versionName}(${
                    manifest.versionCode
                }) keyword=$keyword resultCount=${results.size} " +
                        "top=${results.take(3).map { result ->
                            "${result.id}:${result.title}|keys=${result.fields.keys}|commentBlank=${result.fields["comment"].isNullOrBlank()}"
                        }} rawPreview=${raw.take(1000)}"
            )
            logPluginCall(
                level = AppLogLevel.DEBUG,
                message = "Plugin song search returned ${results.size} result(s)",
                detail = buildString {
                    appendLine("plugin=$id")
                    appendLine("name=$name")
                    appendLine("version=${manifest.versionName}(${manifest.versionCode})")
                    appendLine("keyword=$keyword")
                    appendLine("page=$page")
                    appendLine("pageSize=$pageSize")
                    appendLine("resultCount=${results.size}")
                    appendLine("top=${results.take(5).map { "${it.id}:${it.title}" }}")
                    appendLine("rawPreview=${raw.preview()}")
                }
            )
            results
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "search failed for plugin $id (${manifest.name})", throwable)
            logPluginException(
                message = "Plugin song search failed",
                throwable = throwable,
                detail = "plugin=$id\nname=$name\nkeyword=$keyword\npage=$page\npageSize=$pageSize"
            )
            emptyList()
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(quickJsDispatcher) {
        try {
            if (PluginCapability.GET_LYRICS !in capabilities) {
                return@withContext null
            }

            val request = PluginGetLyricsRequest(
                song = song.toPluginSongRequest(),
                config = config.values
            )
            val raw = runtime.call(FUNCTION_GET_LYRICS, json.encodeToString(request))
            val lyrics = parser.parseLyrics(raw)
            logPluginCall(
                level = if (lyrics == null) AppLogLevel.WARNING else AppLogLevel.DEBUG,
                message = if (lyrics == null) {
                    "Plugin lyrics call returned no usable lyrics"
                } else {
                    "Plugin lyrics call returned lyrics"
                },
                detail = buildString {
                    appendLine("plugin=$id")
                    appendLine("name=$name")
                    appendLine("song=${song.id}:${song.title}")
                    appendLine("payloadType=${lyrics?.payloadType}")
                    appendLine("rawPreview=${raw.preview()}")
                }
            )
            lyrics
        } catch (throwable: Exception) {
            if (throwable is CancellationException) throw throwable
            Log.w(TAG, "getLyrics failed for plugin $id (${manifest.name})", throwable)
            logPluginException(
                message = "Plugin lyrics call failed",
                throwable = throwable,
                detail = "plugin=$id\nname=$name\nsong=${song.id}:${song.title}"
            )
            null
        }
    }

    override suspend fun searchCovers(keyword: String, pageSize: Int): List<SongSearchResult> =
        withContext(quickJsDispatcher) {
            try {
                if (PluginCapability.SEARCH_COVERS !in capabilities) {
                    return@withContext emptyList()
                }

                val request = PluginSearchCoversRequest(
                    keyword = keyword,
                    pageSize = pageSize,
                    config = config.values
                )
                val raw = runtime.call(FUNCTION_SEARCH_COVERS, json.encodeToString(request))
                val results = parser.parseSongResults(
                    rawJson = raw,
                    pluginId = id,
                    pluginName = name
                )
                logPluginCall(
                    level = AppLogLevel.DEBUG,
                    message = "Plugin cover search returned ${results.size} result(s)",
                    detail = "plugin=$id\nname=$name\nkeyword=$keyword\npageSize=$pageSize\n" +
                            "coverCount=${results.count { it.picUrl.isNotBlank() }}\nrawPreview=${raw.preview()}"
                )
                results
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "searchCover failed for plugin $id (${manifest.name})", throwable)
                logPluginException(
                    message = "Plugin cover search failed",
                    throwable = throwable,
                    detail = "plugin=$id\nname=$name\nkeyword=$keyword\npageSize=$pageSize"
                )
                emptyList()
            }
        }

    override suspend fun searchCovers(song: SongSearchResult, pageSize: Int): List<SongSearchResult> =
        withContext(quickJsDispatcher) {
            try {
                if (PluginCapability.SEARCH_COVERS !in capabilities) {
                    return@withContext emptyList()
                }

                val request = PluginSearchCoversRequest(
                    keyword = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
                    song = song.toPluginSongRequest(),
                    pageSize = pageSize,
                    config = config.values
                )
                val raw = runtime.call(FUNCTION_SEARCH_COVERS, json.encodeToString(request))
                val results = parser.parseSongResults(
                    rawJson = raw,
                    pluginId = id,
                    pluginName = name
                )
                logPluginCall(
                    level = AppLogLevel.DEBUG,
                    message = "Plugin cover search returned ${results.size} result(s)",
                    detail = "plugin=$id\nname=$name\nsong=${song.id}:${song.title}\npageSize=$pageSize\n" +
                            "coverCount=${results.count { it.picUrl.isNotBlank() }}\nrawPreview=${raw.preview()}"
                )
                results
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
                Log.w(TAG, "searchCover failed for plugin $id (${manifest.name})", throwable)
                logPluginException(
                    message = "Plugin cover search failed",
                    throwable = throwable,
                    detail = "plugin=$id\nname=$name\nsong=${song.id}:${song.title}\npageSize=$pageSize"
                )
                emptyList()
            }
        }

    override fun close() {
        runCatching {
            if (runtimeDelegate.isInitialized()) {
                executor.submit {
                    runtime.close()
                }.get(3, TimeUnit.SECONDS)
            }
        }
        quickJsDispatcher.close()
        executor.shutdown()
    }

    private fun SongSearchResult.toPluginSongRequest(): PluginSongRequest {
        return PluginSongRequest(
            id = id,
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            sourceId = pluginId,
            pluginId = pluginId,
            fields = fields,
            internal = internal
        )
    }

    private suspend fun logPluginCall(
        level: AppLogLevel,
        message: String,
        detail: String
    ) {
        runCatching {
            appLogRepository?.log(
                level = level,
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                detail = detail.take(LOG_DETAIL_PREVIEW_LIMIT),
                relatedId = id
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to write plugin call log", throwable)
        }
    }

    private suspend fun logPluginException(
        message: String,
        throwable: Throwable,
        detail: String
    ) {
        runCatching {
            appLogRepository?.logException(
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = "$message\n$detail",
                throwable = throwable,
                relatedId = id
            )
        }.onFailure { logThrowable ->
            Log.w(TAG, "Failed to write plugin exception log", logThrowable)
        }
    }

    private fun String.preview(): String =
        replace('\n', ' ').replace('\r', ' ').take(RAW_PREVIEW_LIMIT)

    private companion object {
        const val FUNCTION_SEARCH_SONGS = "searchSongs"
        const val FUNCTION_GET_LYRICS = "getLyrics"
        const val FUNCTION_SEARCH_COVERS = "searchCovers"
        const val TAG = "PlatformPlugin"
        const val RAW_PREVIEW_LIMIT = 1_000
        const val LOG_DETAIL_PREVIEW_LIMIT = 4_000

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
