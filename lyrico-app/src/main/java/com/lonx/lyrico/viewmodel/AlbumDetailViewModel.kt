package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.normalizedArtistKey
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class AlbumDetailViewModel(
    libraryIndexRepository: LibraryIndexRepository,
    settingsRepository: SettingsRepository,
    albumId: Long
) : ViewModel() {

    val artistSplitConfig: StateFlow<ArtistSplitConfig> = settingsRepository
        .artistSplitConfigFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ArtistSplitConfig()
        )

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

    val contributingArtistIds: StateFlow<Map<String, Long>> = combine(
        songs,
        artistSplitConfig,
    ) { albumSongs, splitConfig ->
        albumSongs
            .flatMap { song -> ArtistNameSplitter.splitArtists(song.artist, splitConfig) }
            .distinctBy { it.normalizedArtistKey() }
            .mapNotNull { artistName ->
                libraryIndexRepository
                    .getArtistIdByNormalizedName(artistName.normalizedArtistKey())
                    ?.let { artistName.normalizedArtistKey() to it }
            }
            .toMap()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )
}
