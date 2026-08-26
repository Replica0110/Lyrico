package com.lonx.lyrico.data.model.lyrics

import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginSourceType

interface SearchSource {
    val id: String
    val name: String
    val apiVersion: Int
        get() = 1
    val minHostApiVersion: Int
        get() = 1
    val iconPath: String?
        get() = null
    val capabilities: Set<PluginCapability>
        get() = setOf(PluginCapability.SEARCH_SONGS)
    val configFields: List<PluginConfigField>
        get() = emptyList()
    val metadataEnabled: Boolean
        get() = true
    val lyricsEnabled: Boolean
        get() = true
    val coverEnabled: Boolean
        get() = true
    val metadataSortOrder: Int
        get() = 0
    val lyricsSortOrder: Int
        get() = 0
    val coverSortOrder: Int
        get() = 0

    fun applyConfig(config: SourceRuntimeConfig) = Unit

    suspend fun searchSongs(keyword: String, page: Int = 1, separator: String = "/", pageSize: Int = 20): List<SongSearchResult>
    suspend fun getLyrics(song: SongSearchResult): LyricsResult?
    suspend fun getLyricsCandidates(
        song: SongSearchResult,
        page: Int = 1,
        pageSize: Int = 20
    ): List<LyricsCandidateResult> =
        getLyrics(song)?.let { lyrics ->
            listOf(LyricsCandidateResult(song = song, lyrics = lyrics))
        }.orEmpty()
    suspend fun searchCovers(keyword: String, page: Int = 1, pageSize: Int = 5): List<SongSearchResult>
    suspend fun searchCovers(song: SongSearchResult, page: Int = 1, pageSize: Int = 5): List<SongSearchResult> = searchCovers(
        keyword = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" "),
        page = page,
        pageSize = pageSize
    )
}

fun SearchSource.isEnabledFor(sourceType: PluginSourceType): Boolean = when (sourceType) {
    PluginSourceType.METADATA -> metadataEnabled
    PluginSourceType.LYRICS -> lyricsEnabled
    PluginSourceType.COVER -> coverEnabled
}
