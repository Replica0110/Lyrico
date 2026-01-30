package com.lonx.lyrics.model

interface SearchSource {
    val sourceType: Source
    suspend fun search(keyword: String, page: Int = 1,separator: String = "/", pageSize: Int = 20): List<SongSearchResult>
    suspend fun getLyrics(song: SongSearchResult): LyricsResult?
}