package com.lonx.lyrico.data.song.library

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.db.entity.SongEntity
import kotlinx.coroutines.flow.Flow

interface SongLibraryRepository {
    fun observeSongs(
        sortBy: SongSortBy,
        order: SortOrder,
        folderId: Long? = null
    ): Flow<List<SongEntity>>

    suspend fun getSongByUri(uri: String): SongEntity?

    suspend fun getSongsByUris(uris: List<String>): List<SongEntity>

    suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity>

    suspend fun getSongCount(): Int

    /** Synchronize a saved file snapshot and its indexes; external files need not be in the library. */
    suspend fun synchronizeSavedTags(uri: String, tagData: AudioTagData): SongEntity?

    suspend fun synchronizeRenamedSong(song: SongEntity, oldUri: String)

    suspend fun deleteSongsByUris(uris: List<String>)
}
