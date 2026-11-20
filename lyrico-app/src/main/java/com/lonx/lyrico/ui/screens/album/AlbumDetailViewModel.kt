package com.lonx.lyrico.ui.screens.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.db.entity.AlbumEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AlbumDetailViewModel(
    libraryIndexRepository: LibraryIndexRepository,
    albumId: Long
) : ViewModel() {

    val album: StateFlow<AlbumEntity?> = libraryIndexRepository
        .observeAlbumById(albumId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    val songs: StateFlow<List<SongEntity>> = libraryIndexRepository
        .observeSongsByAlbumId(albumId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )
}
