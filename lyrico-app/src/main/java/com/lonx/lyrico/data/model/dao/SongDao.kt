package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class SongSyncInfo(
    val id: Long,
    val uri: String,
    val filePath: String,
    val fileLastModified: Long,
    val folderId: Long
)

@Dao
interface SongDao {

    // ================= 写入操作 =================

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    @Update
    suspend fun update(song: SongEntity)

    // ================= 删除操作 =================

    /**
     * 批量删除指定 URI 的歌曲 (推荐使用)
     */
    @Query("DELETE FROM songs WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    /**
     * 批量删除指定路径的歌曲 (保留作为兼容或清理手段)
     */
    @Query("DELETE FROM songs WHERE filePath IN (:paths)")
    suspend fun deleteByFilePaths(paths: List<String>)

    /**
     * 根据 URI 删除单条 (可选，但在 Repository deleteSong 中很有用)
     */
    @Query("DELETE FROM songs WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM songs")
    suspend fun clear()

    // ================= 查询操作 (单条) =================

    /**
     * 根据 URI 查询 (主要查询方式)
     */
    @Query("SELECT * FROM songs WHERE uri = :uri LIMIT 1")
    suspend fun getSongByUri(uri: String): SongEntity?

    /**
     * 根据路径查询 (辅助查询方式)
     */
    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getSongByPath(filePath: String): SongEntity?

    // ================= 查询操作 (同步与元数据) =================
    /**
     * 获取同步所需信息
     * 关键修改：确保 SELECT 的列名与 SongSyncInfo 的字段名匹配
     */
    @Query("SELECT id, uri, filePath, fileLastModified, folderId FROM songs")
    suspend fun getAllSyncInfo(): List<SongSyncInfo>

    /**
     * 获取未忽略歌曲的总数
     */
    @Query("""
        SELECT COUNT(*) FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
    """)
    suspend fun getSongsCount(): Int

    // ================= 查询操作 (列表与排序) =================

    /**
     * 全字段搜索
     * 优化：LIKE 匹配前后加 %，逻辑保持不变
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0 AND (
            s.title LIKE '%' || :query || '%' 
            OR s.artist LIKE '%' || :query || '%'
            OR s.album LIKE '%' || :query || '%'
        )
        ORDER BY 
            CASE 
                WHEN s.title LIKE :query || '%' THEN 1       -- 优先匹配：标题以此开头
                WHEN s.title LIKE '%' || :query || '%' THEN 2 -- 其次：标题包含
                WHEN s.artist LIKE '%' || :query || '%' THEN 3 -- 再次：艺术家包含
                WHEN s.album LIKE '%' || :query || '%' THEN 4  -- 最后：专辑包含
                WHEN s.fileName LIKE '%' || :query || '%' THEN 5
                ELSE 6 
            END
    """)
    fun searchSongsByAll(query: String): Flow<List<SongEntity>>

    /**
     * 获取所有歌曲 (默认排序)
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
    """)
    fun getAllSongs(): Flow<List<SongEntity>>

    /**
     * 按文件夹 ID 获取歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE s.folderId = :folderId AND f.isIgnored = 0
    """)
    fun getSongsByFolderId(folderId: Long): Flow<List<SongEntity>>


    /**
     * 使用指定的查询来获取歌曲列表
     * 使用 RawQuery，并指定 observedEntities 参数，以监听数据库变化
     */
    @RawQuery(observedEntities = [SongEntity::class])
    fun getSongs(query: SupportSQLiteQuery): Flow<List<SongEntity>>
}
