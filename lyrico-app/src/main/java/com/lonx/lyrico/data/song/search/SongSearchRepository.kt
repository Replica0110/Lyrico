package com.lonx.lyrico.data.song.search

import com.lonx.lyrico.data.model.dao.AlbumSearchRow
import com.lonx.lyrico.data.model.dao.ArtistSearchRow
import com.lonx.lyrico.data.model.dao.SongFieldValue
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalSearchType
import kotlinx.coroutines.flow.Flow

interface SongSearchRepository {
    fun searchSongs(
        query: String,
        type: LocalSearchType
    ): Flow<List<SongEntity>>

    fun searchSongsForLocalSearch(query: String): Flow<List<SongEntity>>

    fun searchAlbumsForLocalSearch(query: String): Flow<List<AlbumSearchRow>>

    fun searchArtistsForLocalSearch(query: String): Flow<List<ArtistSearchRow>>

    fun observeSongsByAlbumForSearch(
        album: String,
        albumArtist: String?
    ): Flow<List<SongEntity>>

    fun observeSongsByArtistForSearch(artist: String): Flow<List<SongEntity>>

    fun observeAlbumsByArtistForSearch(artist: String): Flow<List<AlbumSearchRow>>

    suspend fun getDistinctSongFieldValues(
        uris: List<String>,
        fieldColumn: String
    ): List<SongFieldValue>
}
