package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class SongSyncInfo(
    val filePath: String,
    val fileLastModified: Long,
    val folderId: Long
)

@Dao
interface SongDao {

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)
    /**
     * 插入单个歌曲，如果已经存在相同 filePath 的记录，则替换
     *
     * @param song 要插入的歌曲实体
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    /**
     * 更新已有的歌曲信息
     *
     * @param song 要更新的歌曲实体（必须包含主键 filePath）
     */
    @Update
    suspend fun update(song: SongEntity)

    /**
     * 根据文件路径查询单首歌曲
     *
     * @param filePath 歌曲文件路径
     * @return 匹配的 SongEntity 或 null
     */
    @Query("SELECT * FROM songs WHERE filePath = :filePath LIMIT 1")
    suspend fun getSongByPath(filePath: String): SongEntity?

    /**
     * 批量删除指定路径的歌曲
     *
     * @param paths 要删除的文件路径列表
     */
    @Query("DELETE FROM songs WHERE filePath IN (:paths)")
    suspend fun deleteByFilePaths(paths: List<String>)

    /**
     * 全字段搜索：关联 folders 表过滤掉已忽略的文件夹
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
                WHEN s.title LIKE '%' || :query || '%' THEN 1
                WHEN s.artist LIKE '%' || :query || '%' THEN 2
                WHEN s.album LIKE '%' || :query || '%' THEN 3
                ELSE 4 
            END
    """)
    fun searchSongsByAll(query: String): Flow<List<SongEntity>>

    /**
     * 获取所有未忽略的歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
    """)
    fun getAllSongs(): Flow<List<SongEntity>>


    /**
     * 获取所有需要同步的歌曲信息
     */
    @Query("SELECT filePath, fileLastModified, folderId FROM songs")
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

    /**
     * 清空所有歌曲
     */
    @Query("DELETE FROM songs")
    suspend fun clear()


    /**
     * 按标题升序（A-Z）获取所有歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.titleSortKey ASC
    """)
    fun getAllSongsOrderByTitleAsc(): Flow<List<SongEntity>>

    /**
     * 按标题降序（Z-A）获取所有歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.titleSortKey DESC
    """)
    fun getAllSongsOrderByTitleDesc(): Flow<List<SongEntity>>

    /**
     * 按艺术家升序（A-Z）获取所有歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.artistSortKey ASC
    """)
    fun getAllSongsOrderByArtistAsc(): Flow<List<SongEntity>>


    /**
     * 按文件夹id获取歌曲
     */
    @Query("""
    SELECT * FROM songs
    WHERE folderId = :folderId
    """)
    fun getSongsByFolderId(folderId: Long): Flow<List<SongEntity>>

    /**
     * 按艺术家降序（Z-A）获取所有歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.artistSortKey DESC
    """)
    fun getAllSongsOrderByArtistDesc(): Flow<List<SongEntity>>

    /**
     * 按修改时间升序获取所有歌曲（最早修改的在前）
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.fileLastModified ASC
    """)
    fun getAllSongsOrderByDateModifiedAsc(): Flow<List<SongEntity>>

    /**
     * 按修改时间降序获取所有歌曲（最新修改的在前）
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.fileLastModified DESC
    """)
    fun getAllSongsOrderByDateModifiedDesc(): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.fileAdded ASC
    """)
    fun getAllSongsOrderByDateAddedAsc(): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
        ORDER BY s.fileAdded DESC
    """)
    fun getAllSongsOrderByDateAddedDesc(): Flow<List<SongEntity>>
}
