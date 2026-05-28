package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.search.LocalSearchUiState
import com.lonx.lyrico.data.model.search.toAlbumSearchResult
import com.lonx.lyrico.data.model.search.toArtistSearchResult
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.song.search.SongSearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalSearchViewModel(
    private val songSearchRepository: SongSearchRepository,
    private val libraryIndexRepository: LibraryIndexRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val searchQuery: StateFlow<String> = query

    val uiState: StateFlow<LocalSearchUiState> = query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { keyword ->
            if (keyword.isBlank()) {
                flowOf(LocalSearchUiState(query = keyword))
            } else {
                combine(
                    songSearchRepository.searchSongsForLocalSearch(keyword),
                    libraryIndexRepository.searchAlbums(keyword),
                    libraryIndexRepository.searchArtists(keyword)
                ) { songs, albums, artists ->
                    LocalSearchUiState(
                        query = keyword,
                        songs = songs,
                        albums = albums.map { it.toAlbumSearchResult() },
                        artists = artists.map { it.toArtistSearchResult() }
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LocalSearchUiState()
        )

    fun onQueryChange(value: String) {
        query.value = value
    }
}
