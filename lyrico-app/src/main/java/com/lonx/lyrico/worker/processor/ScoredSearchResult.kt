package com.lonx.lyrico.worker.processor

import com.lonx.lyrico.data.song.search.SongSearchResult
import com.lonx.lyrico.data.song.search.SearchSource

internal data class ScoredSearchResult(
    val result: SongSearchResult,
    val score: Double,
    val source: SearchSource? = null
)
