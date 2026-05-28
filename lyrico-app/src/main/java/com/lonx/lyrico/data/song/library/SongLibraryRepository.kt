package com.lonx.lyrico.data.song.library

import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.flow.Flow

interface SongLibraryRepository {
    fun observeSongs(
        sortBy: SortBy,
        order: SortOrder,
        folderId: Long? = null
    ): Flow<List<SongEntity>>

    suspend fun getSongByUri(uri: String): SongEntity?

    suspend fun getSongsByUris(uris: List<String>): List<SongEntity>

    suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity>

    suspend fun getSongCount(): Int

    suspend fun upsertSongs(songs: List<SongEntity>)

    suspend fun updateSong(song: SongEntity)

    suspend fun updateSongs(songs: List<SongEntity>)

    suspend fun deleteSongsByUris(uris: List<String>)

    suspend fun clearAll()
}
