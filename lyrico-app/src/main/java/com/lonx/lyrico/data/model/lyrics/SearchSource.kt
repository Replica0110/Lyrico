package com.lonx.lyrico.data.model.lyrics

import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginMetadataField

interface SearchSource {
    val id: String
    val name: String
    val capabilities: Set<SearchSourceCapability>
        get() = SearchSourceCapability.entries.toSet()
    val configFields: List<PluginConfigField>
        get() = emptyList()
    val metadataFields: List<PluginMetadataField>
        get() = emptyList()

    fun applyConfig(config: SourceRuntimeConfig) = Unit

    suspend fun searchSongs(keyword: String, page: Int = 1, separator: String = "/", pageSize: Int = 20): List<SongSearchResult>
    suspend fun getLyrics(song: SongSearchResult): LyricsResult?
    suspend fun searchCovers(keyword: String, pageSize: Int = 5): List<SongSearchResult>
}

enum class SearchSourceCapability {
    SEARCH_SONGS,
    GET_LYRICS,
    SEARCH_COVERS
}
