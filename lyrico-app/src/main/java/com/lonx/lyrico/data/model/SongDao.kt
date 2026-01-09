package com.lonx.lyrico.data.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    
    /**
     * 插入或更新单个歌曲
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)
    
    /**
     * 批量插入或更新歌曲
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)
    
    /**
     * 更新歌曲信息
     */
    @Update
    suspend fun update(song: SongEntity)
    
    /**
     * 删除单个歌曲
     */
    @Delete
    suspend fun delete(song: SongEntity)
    
    /**
     * 按文件路径删除歌曲
     */
    @Query("DELETE FROM songs WHERE filePath = :filePath")
    suspend fun deleteByFilePath(filePath: String)
    
    /**
     * 按文件路径获取歌曲
     */
    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getSongByPath(filePath: String): SongEntity?
    
    /**
     * 按文件路径获取歌曲（Flow）
     */
    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    fun getSongByPathFlow(filePath: String): Flow<SongEntity?>

    /**
     * 获取所有歌曲的文件路径
     */
    @Query("SELECT filePath FROM songs")
    suspend fun getAllFilePaths(): List<String>

    /**
     * 按文件路径批量删除歌曲
     */
    @Query("DELETE FROM songs WHERE filePath IN (:paths)")
    suspend fun deleteByFilePaths(paths: List<String>)

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%'")
    fun searchSongsByTitle(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist LIKE '%' || :query || '%'")
    fun searchSongsByArtist(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album LIKE '%' || :query || '%'")
    fun searchSongsByAlbum(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' ORDER BY CASE WHEN title LIKE '%' || :query || '%' THEN 1 WHEN artist LIKE '%' || :query || '%' THEN 2 WHEN album LIKE '%' || :query || '%' THEN 3 ELSE 4 END")
    fun searchSongsByAll(query: String): Flow<List<SongEntity>>

    /**
     * 获取所有歌曲
     */
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>
    
    /**
     * 获取未读取元数据的歌曲（fileLastModified为0）
     */
    @Query("SELECT * FROM songs WHERE fileLastModified = 0 LIMIT :limit")
    suspend fun getUnscannedSongs(limit: Int): List<SongEntity>
    
    /**
     * 获取在指定时间之后修改过的歌曲
     */
    @Query("SELECT * FROM songs WHERE fileLastModified > :timestamp")
    suspend fun getSongsModifiedAfter(timestamp: Long): List<SongEntity>
    
    /**
     * 检查歌曲是否存在
     */
    @Query("SELECT COUNT(*) FROM songs WHERE filePath = :filePath")
    suspend fun songExists(filePath: String): Int
    
    /**
     * 获取所有歌曲数量
     */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongsCount(): Int
    
    /**
     * 清空所有歌曲
     */
    @Query("DELETE FROM songs")
    suspend fun clear()
    
    /**
     * 删除不在指定列表中的歌曲（用于清理已删除的文件）
     */
    @Query("DELETE FROM songs WHERE filePath NOT IN (:filepaths)")
    suspend fun deleteNotIn(filepaths: List<String>)
}
