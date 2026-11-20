package com.lonx.lyrico.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.song.library.LibraryIndexRepository
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.song.search.SongSearchRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalSearchViewModel(
    private val songSearchRepository: SongSearchRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    val searchQuery: StateFlow<String> = query

    // null means the preference has not been read; tabs must not render a guessed disabled state.
    val lyricSearchEnabled: StateFlow<Boolean?> = settingsRepository.lyricIndexEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null,
    )

    val uiState: StateFlow<LocalSearchUiState> = combine(
        query.debounce { if (it.isBlank()) 0L else 250L }.distinctUntilChanged(),
        lyricSearchEnabled.filterNotNull()
    ) { keyword, enabled -> keyword to enabled }
        .flatMapLatest { (keyword, enabled) ->
            if (keyword.isBlank()) {
                flowOf(LocalSearchUiState(query = keyword, lyricSearchEnabled = enabled))
            } else {
                combine(
                    songSearchRepository.searchSongsForLocalSearch(keyword),
                    libraryIndexRepository.searchAlbums(keyword),
                    libraryIndexRepository.searchArtists(keyword),
                    if (enabled) songSearchRepository.searchLyricsForLocalSearch(keyword)
                    else flowOf(emptyList())
                ) { songs, albums, artists, lyricMatches ->
                    LocalSearchUiState(
                        query = keyword,
                        lyricSearchEnabled = enabled,
                        songs = songs,
                        albums = albums,
                        artists = artists,
                        lyricMatches = lyricMatches
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
