package com.lonx.lyrico.plugin.source

import android.util.Log
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.plugin.runtime.PluginJsRuntime
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SearchSourceCapability
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScriptSearchSource(
    private val manifest: PluginManifest,
    private val script: String,
    private val json: Json = defaultJson,
    private val runtimeFactory: () -> PluginJsRuntime = { QuickJsRuntime() }
) : SearchSource, AutoCloseable {
    override val id: String = manifest.id
    override val name: String = manifest.name
    override val capabilities: Set<SearchSourceCapability> =
        manifest.capabilities.mapTo(mutableSetOf()) { it.toSearchSourceCapability() }
            .ifEmpty { setOf(SearchSourceCapability.SEARCH_SONGS) }
    override val configFields: List<PluginConfigField> = manifest.configFields
    override val metadataFields: List<PluginMetadataField> = manifest.metadataFields
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
        runCatching {
            if (SearchSourceCapability.SEARCH_SONGS !in capabilities) {
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
            parser.parseSongResults(
                rawJson = raw,
                pluginId = id,
                pluginName = name
            )
        }.onFailure { throwable ->
            Log.w(TAG, "search failed for plugin $id (${manifest.name})", throwable)
        }.getOrDefault(emptyList())
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(quickJsDispatcher) {
        runCatching {
            if (SearchSourceCapability.GET_LYRICS !in capabilities) {
                return@withContext null
            }

            val request = PluginGetLyricsRequest(
                song = song.toPluginSongRequest(),
                config = config.values
            )
            val raw = runtime.call(FUNCTION_GET_LYRICS, json.encodeToString(request))
            parser.parseLyrics(raw)
        }.onFailure { throwable ->
            Log.w(TAG, "getLyrics failed for plugin $id (${manifest.name})", throwable)
        }.getOrNull()
    }

    override suspend fun searchCovers(keyword: String, pageSize: Int): List<SongSearchResult> =
        withContext(quickJsDispatcher) {
            runCatching {
                if (SearchSourceCapability.SEARCH_COVERS !in capabilities) {
                    return@withContext emptyList()
                }

                val request = PluginSearchCoversRequest(
                    keyword = keyword,
                    pageSize = pageSize,
                    config = config.values
                )
                val raw = runtime.call(FUNCTION_SEARCH_COVERS, json.encodeToString(request))
                parser.parseSongResults(
                    rawJson = raw,
                    pluginId = id,
                    pluginName = name
                )
            }.onFailure { throwable ->
                Log.w(TAG, "searchCover failed for plugin $id (${manifest.name})", throwable)
            }.getOrDefault(emptyList())
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
            fields = normalizedFields()
        )
    }

    private companion object {
        const val FUNCTION_SEARCH_SONGS = "searchSongs"
        const val FUNCTION_GET_LYRICS = "getLyrics"
        const val FUNCTION_SEARCH_COVERS = "searchCovers"
        const val TAG = "PlatformPlugin"

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
