package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SongSearchResult

data class ScoredSearchResult(
    val result: SongSearchResult,
    val score: Double,
    val source: SearchSource? = null
)
