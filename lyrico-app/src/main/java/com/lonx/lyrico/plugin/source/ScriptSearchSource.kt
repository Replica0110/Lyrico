package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.plugin.PluginManifest
import com.lonx.lyrico.plugin.runtime.PluginJsRuntime
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchResultExtraField
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SearchSourceCapability
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.SourceConfigField
import com.lonx.lyrics.model.SourceRuntimeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    override val metadataFields: List<SearchResultExtraField> =
        manifest.metadataFields.map { it.toSearchResultExtraField() }
    override val extraFields: List<SearchResultExtraField>
        get() = metadataFields
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

    override fun getConfigFields(): List<SourceConfigField> {
        return manifest.configFields.map { it.toSourceConfigField() }
    }

    override fun applyConfig(config: SourceRuntimeConfig) {
        this.config = config
    }

    override suspend fun search(
        keyword: String,
        page: Int,
        separator: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(quickJsDispatcher) {
        if (SearchSourceCapability.SEARCH_SONGS !in capabilities) return@withContext emptyList()

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
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(quickJsDispatcher) {
        if (SearchSourceCapability.GET_LYRICS !in capabilities) return@withContext null

        val request = PluginGetLyricsRequest(
            song = song.toPluginSongRequest(),
            config = config.values
        )
        val raw = runtime.call(FUNCTION_GET_LYRICS, json.encodeToString(request))
        parser.parseLyrics(raw)
    }

    override suspend fun searchCover(keyword: String, pageSize: Int): List<SongSearchResult> =
        withContext(quickJsDispatcher) {
            if (SearchSourceCapability.SEARCH_COVERS !in capabilities) return@withContext emptyList()

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
        }

    override fun close() {
        if (runtimeDelegate.isInitialized()) {
            runtime.close()
        }
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

        val defaultJson: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}
