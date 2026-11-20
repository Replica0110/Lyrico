package com.lonx.lyrico.ui.screens.search

import com.lonx.lyrico.data.db.entity.AlbumEntity
import com.lonx.lyrico.data.db.entity.ArtistEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalLyricSearchResult

data class LocalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val lyricSearchEnabled: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val lyricMatches: List<LocalLyricSearchResult> = emptyList()
)
