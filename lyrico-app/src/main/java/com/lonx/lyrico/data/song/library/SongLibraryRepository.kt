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

    /** 获取所有无歌词但有歌名的歌曲（用于一键匹配） */
    suspend fun getSongsWithoutLyrics(): List<SongEntity>

    /** 获取所有歌曲用于去重检测（按 title+artist 分组，每组按 bitrate 降序） */
    suspend fun getSongsForDedup(): List<SongEntity>

    /** 批量删除歌曲（按 ID，用于去重） */
    suspend fun deleteSongsByIds(songIds: List<Long>)

    suspend fun upsertSongs(songs: List<SongEntity>)

    suspend fun updateSong(song: SongEntity)

    suspend fun updateSongs(songs: List<SongEntity>)

    suspend fun deleteSongsByUris(uris: List<String>)

    suspend fun clearAll()
}
