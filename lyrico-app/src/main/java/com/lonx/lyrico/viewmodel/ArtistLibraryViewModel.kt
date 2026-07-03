package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ArtistSortBy
import com.lonx.lyrico.data.model.ArtistSortInfo
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.LibraryScanManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArtistLibraryViewModel(
    private val libraryIndexRepository: LibraryIndexRepository,
    private val libraryScanManager: LibraryScanManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val sortInfo: StateFlow<ArtistSortInfo> = settingsRepository.artistSortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, ArtistSortInfo())


    val scanState = libraryScanManager.state
    val artists: StateFlow<List<ArtistEntity>> =
        combine(libraryIndexRepository.observeArtists(), sortInfo) { artists, sort ->
            val sorted = when (sort.sortBy) {
                ArtistSortBy.NAME -> artists.sortedWith(
                    compareBy<ArtistEntity> { it.sortKey }.thenBy { it.name }
                )
                ArtistSortBy.SONG_COUNT -> artists.sortedWith(
                    compareByDescending<ArtistEntity> { it.songCount }.thenBy { it.sortKey }.thenBy { it.name }
                )
                ArtistSortBy.ALBUM_COUNT -> artists.sortedWith(
                    compareByDescending<ArtistEntity> { it.albumCount }.thenBy { it.sortKey }.thenBy { it.name }
                )
            }
            if (sort.order == SortOrder.ASC) sorted else sorted.asReversed()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val artistCoverCandidates = libraryIndexRepository
        .observeArtistCoverCandidates()
        .map { candidates -> candidates.groupBy { it.artistId } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyMap()
        )

    fun onSortChange(sortInfo: ArtistSortInfo) {
        viewModelScope.launch {
            settingsRepository.saveArtistSortInfo(sortInfo)
        }
    }


    fun refreshSongs() {
        libraryScanManager.scanAll {
            libraryIndexRepository.rebuildArtistIndex()
        }
    }
}

