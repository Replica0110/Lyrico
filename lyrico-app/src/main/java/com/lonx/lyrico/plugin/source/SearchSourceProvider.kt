package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.isEnabledFor
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.supportsSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchSourceProvider(
    private val pluginManager: PluginSearchSourceManager
) {
    fun observeAllSources(): Flow<List<SearchSource>> {
        return pluginManager.observeSources()
    }

    suspend fun getAllSources(): List<SearchSource> {
        return pluginManager.getSources()
    }

    fun observeSources(sourceType: PluginSourceType): Flow<List<SearchSource>> {
        return pluginManager.observeSources(sourceType).map { sources ->
            sources.forSourceType(sourceType)
        }
    }

    suspend fun getSources(sourceType: PluginSourceType): List<SearchSource> {
        return pluginManager.getSources(sourceType).forSourceType(sourceType)
    }

    suspend fun getSourceWithState(pluginId: String): SearchSourceWithState? {
        return pluginManager.getSourceWithState(pluginId)
    }

}

internal fun List<SearchSource>.forSourceType(
    sourceType: PluginSourceType
): List<SearchSource> = filter { source ->
    source.capabilities.supportsSourceType(sourceType) && source.isEnabledFor(sourceType)
}.sortedWith(
    compareBy<SearchSource> { source ->
        when (sourceType) {
            PluginSourceType.AGGREGATED -> source.aggregatedSortOrder
            PluginSourceType.METADATA -> source.metadataSortOrder
            PluginSourceType.LYRICS -> source.lyricsSortOrder
            PluginSourceType.COVER -> source.coverSortOrder
        }
    }.thenBy { it.name }
)
