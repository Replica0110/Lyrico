package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.AlbumSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.toAlbumSearchResult
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ArtistDetailViewModel(
    songRepository: SongRepository,
    artist: String
) : ViewModel() {

    val songs: StateFlow<List<SongEntity>> = songRepository
        .observeSongsByArtistForSearch(artist)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val albums: StateFlow<List<AlbumSearchResult>> = songRepository
        .observeAlbumsByArtistForSearch(artist)
        .map { rows -> rows.map { it.toAlbumSearchResult() } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
}
