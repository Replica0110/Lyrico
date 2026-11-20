package com.lonx.lyrico.data.model.search

import com.lonx.lyrico.data.db.entity.SongEntity

data class LocalLyricSearchResult(
    val song: SongEntity,
    val lyricLine: String
)
