package com.lonx.lyrico.data.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
     * 全字段搜索歌曲（标题 / 艺术家 / 专辑）
     * 返回结果按照匹配优先级排序：
     * 1. title 匹配最高
     * 2. artist 匹配次之
     * 3. album 匹配最低
     *
     * @param query 查询关键字
     * @return Flow 可观察的歌曲列表
     */
    @Query("""
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
        ORDER BY 
            CASE 
                WHEN title LIKE '%' || :query || '%' THEN 1
                WHEN artist LIKE '%' || :query || '%' THEN 2
                WHEN album LIKE '%' || :query || '%' THEN 3
                ELSE 4 
            END
    """)
    fun searchSongsByAll(query: String): Flow<List<SongEntity>>

    /**
     * 获取所有歌曲（无排序）
     *
     * @return Flow 可观察的所有歌曲列表
     */
    @Query("SELECT * FROM songs")
    fun getAllSongs(): Flow<List<SongEntity>>

    /**
     * 获取歌曲总数
     *
     * @return 歌曲数量
     */
    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongsCount(): Int

    /**
     * 清空所有歌曲
     */
    @Query("DELETE FROM songs")
    suspend fun clear()

    // =====================
    // 排序查询方法（按缓存字段排序）
    // =====================

    /**
     * 按标题升序（A-Z）获取所有歌曲
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY titleSortKey ASC
    """)
    fun getAllSongsOrderByTitleAsc(): Flow<List<SongEntity>>

    /**
     * 按标题降序（Z-A）获取所有歌曲
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY titleSortKey DESC
    """)
    fun getAllSongsOrderByTitleDesc(): Flow<List<SongEntity>>

    /**
     * 按艺术家升序（A-Z）获取所有歌曲
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY artistSortKey ASC
    """)
    fun getAllSongsOrderByArtistAsc(): Flow<List<SongEntity>>

    /**
     * 按艺术家降序（Z-A）获取所有歌曲
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY artistSortKey DESC
    """)
    fun getAllSongsOrderByArtistDesc(): Flow<List<SongEntity>>

    /**
     * 按修改时间升序获取所有歌曲（最早修改的在前）
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY fileLastModified ASC
    """)
    fun getAllSongsOrderByDateModifiedAsc(): Flow<List<SongEntity>>

    /**
     * 按修改时间降序获取所有歌曲（最新修改的在前）
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY fileLastModified DESC
    """)
    fun getAllSongsOrderByDateModifiedDesc(): Flow<List<SongEntity>>
    /**
     * 按添加时间升序获取所有歌曲（最早添加的在前）
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY fileAdded ASC
    """)
    fun getAllSongsOrderByDateAddedAsc(): Flow<List<SongEntity>>
    /**
     * 按添加时间降序获取所有歌曲（最新添加的在前）
     */
    @Query("""
        SELECT * FROM songs
        ORDER BY fileAdded DESC
    """)
    fun getAllSongsOrderByDateAddedDesc(): Flow<List<SongEntity>>
}
