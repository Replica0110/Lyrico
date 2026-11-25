package com.lonx.lyrico.data.song.search

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.room.withTransaction
import com.lonx.lyrico.data.db.LyricoDatabase
import com.lonx.lyrico.data.db.dao.AlbumSearchRow
import com.lonx.lyrico.data.db.dao.ArtistSearchRow
import com.lonx.lyrico.data.db.dao.SongDao
import com.lonx.lyrico.data.db.dao.SongFieldValue
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.domain.lyrics.LyricsSearchTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SongSearchRepositoryImpl(
    private val database: LyricoDatabase
) : SongSearchRepository {
    private val songDao = database.songDao()

    override fun searchSongs(query: String, type: LocalSearchType): Flow<List<SongEntity>> {
        return songDao.searchSongsByType(query, type.value)
    }

    override fun searchSongsForLocalSearch(query: String): Flow<List<SongEntity>> {
        return songDao.searchSongsForLocalSearch(query)
    }

    override fun searchLyricsForLocalSearch(query: String): Flow<List<LocalLyricSearchResult>> {
        val ftsQuery = LyricFtsIndexer.buildQuery(query) ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return songDao.searchLyricFtsForLocalSearch(
            SimpleSQLiteQuery(
                """
                SELECT
                    fts.lineText AS matchedLine,
                    s.id AS id,
                    s.folderId AS folderId,
                    s.mediaId AS mediaId,
                    s.source AS source,
                    s.filePath AS filePath,
                    s.fileName AS fileName,
                    s.fileSize AS fileSize,
                    s.fileExtension AS fileExtension,
                    s.title AS title,
                    s.artist AS artist,
                    s.albumArtist AS albumArtist,
                    s.discNumber AS discNumber,
                    s.composer AS composer,
                    s.lyricist AS lyricist,
                    s.comment AS comment,
                    s.album AS album,
                    s.genre AS genre,
                    s.language AS language,
                    s.trackerNumber AS trackerNumber,
                    s.date AS date,
                    s.copyright AS copyright,
                    s.rating AS rating,
                    s.replayGainTrackGain AS replayGainTrackGain,
                    s.replayGainTrackPeak AS replayGainTrackPeak,
                    s.replayGainAlbumGain AS replayGainAlbumGain,
                    s.replayGainAlbumPeak AS replayGainAlbumPeak,
                    s.replayGainReferenceLoudness AS replayGainReferenceLoudness,
                    s.durationMilliseconds AS durationMilliseconds,
                    s.bitrate AS bitrate,
                    s.sampleRate AS sampleRate,
                    s.channels AS channels,
                    s.fileLastModified AS fileLastModified,
                    s.fileAdded AS fileAdded,
                    s.dbUpdateTime AS dbUpdateTime,
                    s.titleGroupKey AS titleGroupKey,
                    s.titleSortKey AS titleSortKey,
                    s.artistGroupKey AS artistGroupKey,
                    s.artistSortKey AS artistSortKey,
                    s.albumGroupKey AS albumGroupKey,
                    s.albumSortKey AS albumSortKey,
                    s.uri AS uri
                FROM (
                    SELECT
                        songUri,
                        MIN(CAST(lineIndex AS INTEGER)) AS firstMatchIndex
                    FROM song_lyric_lines_fts
                    WHERE song_lyric_lines_fts MATCH ?
                    GROUP BY songUri
                ) AS best
                INNER JOIN song_lyric_lines_fts AS fts
                    ON fts.songUri = best.songUri
                    AND CAST(fts.lineIndex AS INTEGER) = best.firstMatchIndex
                INNER JOIN songs AS s ON s.uri = fts.songUri
                INNER JOIN folders AS f ON s.folderId = f.id
                WHERE f.isIgnored = 0
                ORDER BY s.title ASC, s.fileName ASC, fts.lineIndex ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf<Any>(ftsQuery, LOCAL_LYRIC_SEARCH_LIMIT)
            )
        )
            .map { rows ->
                rows
                    .map { row ->
                        LocalLyricSearchResult(
                            song = row.toSongEntity(),
                            lyricLine = row.matchedLine
                        )
                    }
            }
    }

    override suspend fun rebuildMissingLyricSearchTextIndex() = withContext(Dispatchers.IO) {
        songDao.getSongsNeedingLyricSearchTextIndex().forEach { song ->
            songDao.updateLyricSearchText(
                uri = song.uri,
                lyricSearchText = LyricsSearchTextExtractor.toSearchText(song.lyrics)
            )
        }
        if (songDao.getLyricFtsRowCount() == 0) {
            database.withTransaction {
                songDao.clearLyricFts()
                var lastId = 0L
                while (true) {
                    val rows = songDao.getSongLyricsForFtsAfterId(
                        lastId = lastId,
                        limit = FTS_REBUILD_BATCH_SIZE
                    )
                    if (rows.isEmpty()) break
                    LyricFtsIndexer.replaceSongLyrics(songDao, rows)
                    lastId = rows.last().id
                }
            }
        }
    }

    override fun searchAlbumsForLocalSearch(query: String): Flow<List<AlbumSearchRow>> {
        return songDao.searchAlbumsForLocalSearch(query)
    }

    override fun searchArtistsForLocalSearch(query: String): Flow<List<ArtistSearchRow>> {
        return songDao.searchArtistsForLocalSearch(query)
    }

    override fun observeSongsByAlbumForSearch(
        album: String,
        albumArtist: String?
    ): Flow<List<SongEntity>> {
        return songDao.observeSongsByAlbumForSearch(album, albumArtist)
    }

    override fun observeSongsByArtistForSearch(artist: String): Flow<List<SongEntity>> {
        return songDao.observeSongsByArtistForSearch(artist)
    }

    override fun observeAlbumsByArtistForSearch(artist: String): Flow<List<AlbumSearchRow>> {
        return songDao.observeAlbumsByArtistForSearch(artist)
    }

    override suspend fun getDistinctSongFieldValues(
        uris: List<String>,
        fieldColumn: String
    ): List<SongFieldValue> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyList()

        val valueExpression = when (fieldColumn) {
            "title",
            "artist",
            "albumArtist",
            "album",
            "date",
            "genre",
            "trackerNumber",
            "composer",
            "lyricist",
            "copyright",
            "comment",
            "lyrics",
            "replayGainTrackGain",
            "replayGainTrackPeak",
            "replayGainAlbumGain",
            "replayGainAlbumPeak",
            "replayGainReferenceLoudness" -> fieldColumn
            "discNumber" -> "CAST(discNumber AS TEXT)"
            else -> return@withContext emptyList()
        }

        val placeholders = uris.joinToString(",") { "?" }
        val selectionOrder = uris.indices.joinToString(" ") { index ->
            "WHEN ? THEN $index"
        }
        val sql = """
            SELECT
                MIN(uri) AS sourceUri,
                TRIM($valueExpression) AS value
            FROM songs
            WHERE uri IN ($placeholders)
                AND $valueExpression IS NOT NULL
                AND TRIM($valueExpression) != ''
            GROUP BY TRIM($valueExpression)
            ORDER BY MIN(CASE uri $selectionOrder ELSE ${uris.size} END)
        """.trimIndent()

        songDao.getDistinctSongFieldValues(
            SimpleSQLiteQuery(sql, (uris + uris).toTypedArray())
        )
    }

    private companion object {
        const val LOCAL_LYRIC_SEARCH_LIMIT = 500
        const val FTS_REBUILD_BATCH_SIZE = 50
    }
}
