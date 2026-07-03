package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.dao.ArtistCoverCandidateRow
import kotlinx.coroutines.flow.Flow

interface LibraryIndexRepository {
    fun observeArtists(): Flow<List<ArtistEntity>>
    fun observeArtistById(artistId: Long): Flow<ArtistEntity?>
    fun observeArtistCoverCandidates(): Flow<List<ArtistCoverCandidateRow>>
    fun observeSongsByArtistId(artistId: Long): Flow<List<SongEntity>>
    fun observeAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>
    fun observeAlbums(): Flow<List<AlbumEntity>>
    fun observeAlbumById(albumId: Long): Flow<AlbumEntity?>
    fun observeSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>
    suspend fun getSongsByAlbumId(albumId: Long): List<SongEntity>
    fun searchArtists(query: String): Flow<List<ArtistEntity>>
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>
    suspend fun rebuildAllIndexes()
    suspend fun rebuildArtistIndex()
    suspend fun rebuildAlbumIndex()
    suspend fun reindexSong(song: SongEntity)
    suspend fun reindexSongs(songs: List<SongEntity>)
    suspend fun reindexSongInTransaction(song: SongEntity)
    suspend fun reindexSongsInTransaction(songs: List<SongEntity>)
    suspend fun removeSongIndex(songId: Long)
    suspend fun refreshAndPruneIndexes()
    suspend fun ensureIndexesCurrent()
}
