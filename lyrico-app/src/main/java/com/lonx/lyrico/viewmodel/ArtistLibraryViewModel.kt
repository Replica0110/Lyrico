package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ArtistSortBy
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.data.model.dao.ArtistListItem
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.utils.LibraryScanManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ArtistLibraryViewModel(
    libraryIndexRepository: LibraryIndexRepository,
    private val libraryScanManager: LibraryScanManager
) : ViewModel() {
    private val _sortInfo = MutableStateFlow(ArtistSortInfo())
    val sortInfo: StateFlow<ArtistSortInfo> = _sortInfo
    val scanState = libraryScanManager.state
    val artists: StateFlow<List<ArtistListItem>> =
        combine(libraryIndexRepository.observeArtists(), sortInfo) { artists, sort ->
            val sorted = when (sort.sortBy) {
                ArtistSortBy.NAME -> artists.sortedWith(
                    compareBy<ArtistListItem> { it.sortKey }.thenBy { it.name }
                )
                ArtistSortBy.SONG_COUNT -> artists.sortedWith(
                    compareByDescending<ArtistListItem> { it.songCount }.thenBy { it.sortKey }.thenBy { it.name }
                )
                ArtistSortBy.ALBUM_COUNT -> artists.sortedWith(
                    compareByDescending<ArtistListItem> { it.albumCount }.thenBy { it.sortKey }.thenBy { it.name }
                )
            }
            if (sort.order == SortOrder.ASC) sorted else sorted.asReversed()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun onSortChange(sortInfo: ArtistSortInfo) {
        _sortInfo.value = sortInfo
    }

    fun refreshSongs() {
        libraryScanManager.scanAll()
    }
}

