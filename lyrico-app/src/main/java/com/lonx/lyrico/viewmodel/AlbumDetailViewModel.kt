package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AlbumDetailViewModel(
    songRepository: SongRepository,
    album: String,
    albumArtist: String?
) : ViewModel() {

    val songs: StateFlow<List<SongEntity>> = songRepository
        .observeSongsByAlbumForSearch(album, albumArtist)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
}
