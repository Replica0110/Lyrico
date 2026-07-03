package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.AlbumSongCrossRef
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.ArtistSongCrossRef
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class ArtistCoverCandidateRow(
    val artistId: Long,
    val uri: String,
    val lastModified: Long
)

@Dao
interface LibraryIndexDao {
    @Query("DELETE FROM artist_song WHERE songId = :songId")
    suspend fun deleteArtistRefsBySongId(songId: Long)

    @Query("DELETE FROM album_song WHERE songId = :songId")
    suspend fun deleteAlbumRefsBySongId(songId: Long)

    @Query("DELETE FROM artist_song")
    suspend fun clearArtistRefs()

    @Query("DELETE FROM artists")
    suspend fun clearArtists()

    @Query("DELETE FROM album_song")
    suspend fun clearAlbumRefs()

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtist(entity: ArtistEntity): Long

    @Query("SELECT id FROM artists WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getArtistIdByNormalizedName(normalizedName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtistSongRef(ref: ArtistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(entity: AlbumEntity): Long

    @Query("SELECT id FROM albums WHERE normalizedKey = :normalizedKey LIMIT 1")
    suspend fun getAlbumIdByNormalizedKey(normalizedKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumSongRef(ref: AlbumSongCrossRef)

    @Query("""
        UPDATE artists
        SET
            songCount = (
                SELECT COUNT(*)
                FROM artist_song AS ars
                INNER JOIN songs AS s ON s.id = ars.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE ars.artistId = artists.id
                  AND f.isIgnored = 0
            ),
            albumCount = (
                SELECT COUNT(DISTINCT NULLIF(TRIM(s.album), ''))
                FROM artist_song AS ars
                INNER JOIN songs AS s ON s.id = ars.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE ars.artistId = artists.id
                  AND f.isIgnored = 0
            ),
            updatedAt = :updatedAt
    """)
    suspend fun refreshArtistStats(updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE albums
        SET
            songCount = (
                SELECT COUNT(*)
                FROM album_song AS als
                INNER JOIN songs AS s ON s.id = als.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE als.albumId = albums.id
                  AND f.isIgnored = 0
            ),
            updatedAt = :updatedAt
    """)
    suspend fun refreshAlbumStats(updatedAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE artists
        SET
            coverSongUri = (
                SELECT s.uri
                FROM artist_song AS ars
                INNER JOIN songs AS s ON s.id = ars.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE ars.artistId = artists.id
                  AND f.isIgnored = 0
                ORDER BY s.album ASC, COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC, s.fileName ASC
                LIMIT 1
            ),
            coverSongLastModified = COALESCE((
                SELECT s.fileLastModified
                FROM artist_song AS ars
                INNER JOIN songs AS s ON s.id = ars.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE ars.artistId = artists.id
                  AND f.isIgnored = 0
                ORDER BY s.album ASC, COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC, s.fileName ASC
                LIMIT 1
            ), 0)
    """)
    suspend fun refreshArtistCovers()

    @Query("""
        UPDATE albums
        SET
            coverSongUri = (
                SELECT s.uri
                FROM album_song AS als
                INNER JOIN songs AS s ON s.id = als.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE als.albumId = albums.id
                  AND f.isIgnored = 0
                ORDER BY COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC, s.fileName ASC
                LIMIT 1
            ),
            coverSongLastModified = COALESCE((
                SELECT s.fileLastModified
                FROM album_song AS als
                INNER JOIN songs AS s ON s.id = als.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE als.albumId = albums.id
                  AND f.isIgnored = 0
                ORDER BY COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC, s.fileName ASC
                LIMIT 1
            ), 0),
            year = (
                SELECT TRIM(s.date)
                FROM album_song AS als
                INNER JOIN songs AS s ON s.id = als.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE als.albumId = albums.id
                  AND f.isIgnored = 0
                  AND TRIM(COALESCE(s.date, '')) != ''
                ORDER BY COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC,
                    s.fileName ASC
                LIMIT 1
            )
    """)
    suspend fun refreshAlbumCovers()

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artistId FROM artist_song)")
    suspend fun deleteOrphanArtists()

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT albumId FROM album_song)")
    suspend fun deleteOrphanAlbums()

    @Query("""
        SELECT *
        FROM artists
        WHERE songCount > 0
        ORDER BY sortKey ASC, name ASC
    """)
    fun observeArtists(): Flow<List<ArtistEntity>>

    @Query("""
        SELECT *
        FROM artists
        WHERE id = :artistId
        LIMIT 1
    """)
    fun observeArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("""
        SELECT
            ars.artistId AS artistId,
            s.uri AS uri,
            s.fileLastModified AS lastModified
        FROM artist_song AS ars
        INNER JOIN songs AS s ON s.id = ars.songId
        INNER JOIN folders AS f ON f.id = s.folderId
        WHERE f.isIgnored = 0
        ORDER BY ars.artistId ASC,
            s.album ASC, COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.titleSortKey ASC, s.fileName ASC
    """)
    fun observeArtistCoverCandidates(): Flow<List<ArtistCoverCandidateRow>>

    @Query("""
        SELECT s.*
        FROM songs AS s
        INNER JOIN artist_song AS ars ON s.id = ars.songId
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE ars.artistId = :artistId
          AND f.isIgnored = 0
        ORDER BY s.album ASC, COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.titleSortKey ASC, s.fileName ASC
    """)
    fun observeSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT DISTINCT a.*
        FROM albums AS a
        INNER JOIN album_song AS als ON a.id = als.albumId
        INNER JOIN artist_song AS ars ON als.songId = ars.songId
        INNER JOIN songs AS s ON s.id = als.songId
        INNER JOIN folders AS f ON f.id = s.folderId
        WHERE ars.artistId = :artistId
          AND f.isIgnored = 0
        ORDER BY a.sortKey ASC, a.name ASC
    """)
    fun observeAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("""
        SELECT 
            a.id,
            a.name,
            a.albumArtist,
            a.normalizedKey,
            a.groupKey,
            a.sortKey,
            a.songCount,
            a.coverSongUri,
            a.coverSongLastModified,
            (
                SELECT TRIM(s.date)
                FROM album_song AS als
                INNER JOIN songs AS s ON s.id = als.songId
                INNER JOIN folders AS f ON f.id = s.folderId
                WHERE als.albumId = a.id
                  AND f.isIgnored = 0
                  AND TRIM(COALESCE(s.date, '')) != ''
                ORDER BY COALESCE(s.discNumber, 0) ASC,
                    CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
                    s.titleSortKey ASC,
                    s.fileName ASC
                LIMIT 1
            ) AS year,
            a.updatedAt
        FROM albums AS a
        WHERE a.songCount > 0
        ORDER BY a.sortKey ASC, a.name ASC
    """)
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    fun observeAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("""
        SELECT s.*
        FROM songs AS s
        INNER JOIN album_song AS als ON s.id = als.songId
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE als.albumId = :albumId
          AND f.isIgnored = 0
        ORDER BY COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.titleSortKey ASC, s.fileName ASC
    """)
    fun observeSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT s.*
        FROM songs AS s
        INNER JOIN album_song AS als ON s.id = als.songId
        INNER JOIN folders AS f ON s.folderId = f.id
        WHERE als.albumId = :albumId
          AND f.isIgnored = 0
        ORDER BY COALESCE(s.discNumber, 0) ASC,
            CAST(NULLIF(s.trackerNumber, '') AS INTEGER) ASC,
            s.titleSortKey ASC, s.fileName ASC
    """)
    suspend fun getSongsByAlbumId(albumId: Long): List<SongEntity>

    @Query("""
        SELECT id, name, normalizedName, groupKey, sortKey, songCount, albumCount, coverSongUri, coverSongLastModified, updatedAt
        FROM artists
        WHERE name LIKE '%' || :query || '%'
          AND songCount > 0
        ORDER BY CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            sortKey ASC, name ASC
    """)
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Query("""
        SELECT *
        FROM albums
        WHERE name LIKE '%' || :query || '%'
          AND songCount > 0
        ORDER BY CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            sortKey ASC, name ASC
    """)
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>
}

