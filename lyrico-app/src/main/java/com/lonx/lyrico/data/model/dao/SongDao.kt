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
    val fileSize: Long,
    val folderId: Long,
    val source: String
)

data class SongFieldValue(
    val sourceUri: String,
    val value: String
)

data class AlbumSearchRow(
    val album: String,
    val albumArtist: String?,
    val songCount: Int,
    val coverSongUri: String?,
    val coverSongLastModified: Long
)

data class ArtistSearchRow(
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
    val coverSongUri: String?,
    val coverSongLastModified: Long
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
    @Query("SELECT id, uri, filePath, fileLastModified, fileSize, folderId, source FROM songs")
    suspend fun getAllSyncInfo(): List<SongSyncInfo>

    /**
     * 获取未忽略歌曲的总数
     */
    @Query("""
        SELECT COUNT(*) FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
    """)
    suspend fun getSongCount(): Int

    // ================= 查询操作 (列表与排序) =================

    /**
     * 全字段搜索
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0 AND (
            (:searchType = 'ALL' AND (
                s.title LIKE '%' || :query || '%' 
                OR s.artist LIKE '%' || :query || '%'
                OR s.album LIKE '%' || :query || '%'
                OR s.fileName LIKE '%' || :query || '%'
            ))
            OR (:searchType = 'TITLE' AND s.title LIKE '%' || :query || '%')
            OR (:searchType = 'ARTIST' AND s.artist LIKE '%' || :query || '%')
            OR (:searchType = 'ALBUM' AND s.album LIKE '%' || :query || '%')
            OR (:searchType = 'FILE_NAME' AND s.fileName LIKE '%' || :query || '%')
        )
        ORDER BY 
            CASE 
                WHEN :searchType = 'ALL' THEN
                    CASE 
                        WHEN s.title LIKE :query || '%' THEN 1
                        WHEN s.title LIKE '%' || :query || '%' THEN 2
                        WHEN s.artist LIKE '%' || :query || '%' THEN 3
                        WHEN s.album LIKE '%' || :query || '%' THEN 4
                        ELSE 5 
                    END
                WHEN :searchType = 'TITLE' THEN CASE WHEN s.title LIKE :query || '%' THEN 1 ELSE 2 END
                WHEN :searchType = 'ARTIST' THEN CASE WHEN s.artist LIKE :query || '%' THEN 1 ELSE 2 END
                WHEN :searchType = 'ALBUM' THEN CASE WHEN s.album LIKE :query || '%' THEN 1 ELSE 2 END
                WHEN :searchType = 'FILE_NAME' THEN CASE WHEN s.fileName LIKE :query || '%' THEN 1 ELSE 2 END
                ELSE 1
            END,
            s.title ASC
    """)
    fun searchSongsByType(query: String, searchType: String): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
          AND (
              s.title LIKE '%' || :query || '%'
              OR s.artist LIKE '%' || :query || '%'
              OR s.album LIKE '%' || :query || '%'
              OR s.fileName LIKE '%' || :query || '%'
          )
        ORDER BY
            CASE
                WHEN s.title LIKE :query || '%' THEN 0
                WHEN s.fileName LIKE :query || '%' THEN 1
                WHEN s.artist LIKE :query || '%' THEN 2
                WHEN s.album LIKE :query || '%' THEN 3
                ELSE 4
            END,
            s.title ASC,
            s.fileName ASC
    """)
    fun searchSongsForLocalSearch(query: String): Flow<List<SongEntity>>

    @Query("""
        SELECT
            album,
            albumArtist,
            COUNT(*) AS songCount,
            coverSongUri,
            coverSongLastModified
        FROM (
            SELECT
                TRIM(s.album) AS album,
                COALESCE(
                    NULLIF(TRIM(s.albumArtist), ''),
                    NULLIF(TRIM(s.artist), '')
                ) AS albumArtist,
                s.uri AS coverSongUri,
                s.fileLastModified AS coverSongLastModified
            FROM songs AS s
            INNER JOIN folders AS f ON s.folderId = f.id
            WHERE f.isIgnored = 0
              AND s.album IS NOT NULL
              AND TRIM(s.album) != ''
              AND s.album LIKE '%' || :query || '%'
            ORDER BY
                CASE WHEN s.album LIKE :query || '%' THEN 0 ELSE 1 END,
                s.album ASC,
                COALESCE(s.discNumber, 0) ASC,
                CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                s.title ASC,
                s.fileName ASC
        )
        GROUP BY album, albumArtist
        ORDER BY
            CASE WHEN album LIKE :query || '%' THEN 0 ELSE 1 END,
            album ASC
    """)
    fun searchAlbumsForLocalSearch(query: String): Flow<List<AlbumSearchRow>>

    @Query("""
        SELECT
            artist,
            COUNT(*) AS songCount,
            COUNT(DISTINCT NULLIF(album, '')) AS albumCount,
            coverSongUri,
            coverSongLastModified
        FROM (
            SELECT
                TRIM(s.artist) AS artist,
                TRIM(s.album) AS album,
                s.uri AS coverSongUri,
                s.fileLastModified AS coverSongLastModified
            FROM songs AS s
            INNER JOIN folders AS f ON s.folderId = f.id
            WHERE f.isIgnored = 0
              AND s.artist IS NOT NULL
              AND TRIM(s.artist) != ''
              AND s.artist LIKE '%' || :query || '%'
            ORDER BY
                CASE WHEN s.artist LIKE :query || '%' THEN 0 ELSE 1 END,
                s.artist ASC,
                s.album ASC,
                COALESCE(s.discNumber, 0) ASC,
                CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                s.title ASC,
                s.fileName ASC
        )
        GROUP BY artist
        ORDER BY
            CASE WHEN artist LIKE :query || '%' THEN 0 ELSE 1 END,
            artist ASC
    """)
    fun searchArtistsForLocalSearch(query: String): Flow<List<ArtistSearchRow>>

    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
          AND TRIM(s.album) = :album
          AND (
              :albumArtist IS NULL
              OR TRIM(s.albumArtist) = :albumArtist
              OR TRIM(s.artist) = :albumArtist
          )
        ORDER BY
            COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.title ASC,
            s.fileName ASC
    """)
    fun observeSongsByAlbumForSearch(
        album: String,
        albumArtist: String?
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0
          AND TRIM(s.artist) = :artist
        ORDER BY
            s.album ASC,
            COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.title ASC,
            s.fileName ASC
    """)
    fun observeSongsByArtistForSearch(artist: String): Flow<List<SongEntity>>

    @Query("""
        SELECT
            album,
            albumArtist,
            COUNT(*) AS songCount,
            coverSongUri,
            coverSongLastModified
        FROM (
            SELECT
                TRIM(s.album) AS album,
                COALESCE(
                    NULLIF(TRIM(s.albumArtist), ''),
                    NULLIF(TRIM(s.artist), '')
                ) AS albumArtist,
                s.uri AS coverSongUri,
                s.fileLastModified AS coverSongLastModified
            FROM songs AS s
            INNER JOIN folders AS f ON s.folderId = f.id
            WHERE f.isIgnored = 0
              AND TRIM(s.artist) = :artist
              AND s.album IS NOT NULL
              AND TRIM(s.album) != ''
            ORDER BY
                s.album ASC,
                COALESCE(s.discNumber, 0) ASC,
                CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                s.title ASC,
                s.fileName ASC
        )
        GROUP BY album, albumArtist
        ORDER BY album ASC
    """)
    fun observeAlbumsByArtistForSearch(artist: String): Flow<List<AlbumSearchRow>>

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

    @RawQuery
    suspend fun getDistinctSongFieldValues(query: SupportSQLiteQuery): List<SongFieldValue>

    /**
     * 根据专辑和艺术家获取歌曲列表
     * 优先返回同专辑且同艺术家的歌曲，然后返回同专辑的歌曲
     */
    @Query("""
        SELECT s.* FROM songs AS s
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE f.isIgnored = 0 AND s.album = :album
        ORDER BY CASE WHEN s.artist = :artist THEN 0 ELSE 1 END, s.trackerNumber ASC
    """)
    suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity>

    @Query("DELETE FROM songs WHERE folderId IN (:folderIds)")
    suspend fun deleteByFolderIds(folderIds: List<Long>)
}
