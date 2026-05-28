package com.lonx.lyrico.data.song.search

import androidx.sqlite.db.SimpleSQLiteQuery
import com.lonx.lyrico.data.model.dao.AlbumSearchRow
import com.lonx.lyrico.data.model.dao.ArtistSearchRow
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.dao.SongFieldValue
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.search.LocalSearchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SongSearchRepositoryImpl(
    private val songDao: SongDao
) : SongSearchRepository {

    override fun searchSongs(query: String, type: LocalSearchType): Flow<List<SongEntity>> {
        return songDao.searchSongsByType(query, type.value)
    }

    override fun searchSongsForLocalSearch(query: String): Flow<List<SongEntity>> {
        return songDao.searchSongsForLocalSearch(query)
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
}
