package com.lonx.lyrico.plugin.source

import com.lonx.lyrics.model.SearchSource
import kotlinx.coroutines.flow.Flow

class SearchSourceProvider(
    private val pluginManager: PluginSearchSourceManager
) {
    fun observeAllSources(): Flow<List<SearchSource>> {
        return pluginManager.observeEnabledSources()
    }

    suspend fun getAllSources(): List<SearchSource> {
        return pluginManager.getEnabledSources()
    }
}
