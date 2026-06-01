package com.lonx.lyrico.data.model.lyrics

import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginCapability

interface SearchSource {
    val id: String
    val name: String
    val iconPath: String?
        get() = null
    val capabilities: Set<PluginCapability>
        get() = PluginCapability.entries.toSet()
    val configFields: List<PluginConfigField>
        get() = emptyList()

    fun applyConfig(config: SourceRuntimeConfig) = Unit

    suspend fun searchSongs(keyword: String, page: Int = 1, separator: String = "/", pageSize: Int = 20): List<SongSearchResult>
    suspend fun getLyrics(song: SongSearchResult): LyricsResult?
    suspend fun searchCovers(keyword: String, pageSize: Int = 5): List<SongSearchResult>
    suspend fun searchCovers(song: SongSearchResult, pageSize: Int = 5): List<SongSearchResult> = searchCovers(
        keyword = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
        pageSize = pageSize
    )
}
