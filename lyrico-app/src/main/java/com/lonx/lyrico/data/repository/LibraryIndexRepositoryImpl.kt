package com.lonx.lyrico.data.repository

import androidx.room.withTransaction
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.normalizedArtistKey
import com.lonx.lyrico.data.model.dao.ArtistListItem
import com.lonx.lyrico.data.model.dao.LibraryIndexDao
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.AlbumSongCrossRef
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.ArtistSongCrossRef
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.data.utils.SortKeyUtils
import com.lonx.lyrico.data.utils.normalizedAlbumKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

class LibraryIndexRepositoryImpl(
    private val database: LyricoDatabase,
    private val songDao: SongDao,
    private val indexDao: LibraryIndexDao,
    private val settingsRepository: SettingsRepository
) : LibraryIndexRepository {

    override fun observeArtists(): Flow<List<ArtistListItem>> {
        return indexDao.observeArtists()
    }

    override fun observeArtistById(artistId: Long): Flow<ArtistListItem?> {
        return indexDao.observeArtistById(artistId)
    }

    override fun observeSongsByArtistId(artistId: Long): Flow<List<SongEntity>> {
        return indexDao.observeSongsByArtistId(artistId)
    }

    override fun observeAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>> {
        return indexDao.observeAlbumsByArtistId(artistId)
    }

    override fun observeAlbums(): Flow<List<AlbumEntity>> {
        return indexDao.observeAlbums()
    }

    override fun observeAlbumById(albumId: Long): Flow<AlbumEntity?> {
        return indexDao.observeAlbumById(albumId)
    }

    override fun observeSongsByAlbumId(albumId: Long): Flow<List<SongEntity>> {
        return indexDao.observeSongsByAlbumId(albumId)
    }

    override fun searchArtists(query: String): Flow<List<ArtistListItem>> {
        return indexDao.searchArtists(query)
    }

    override fun searchAlbums(query: String): Flow<List<AlbumEntity>> {
        return indexDao.searchAlbums(query)
    }

    override suspend fun rebuildAllIndexes() {
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()
        val songs = songDao.getAllSongsSnapshot()

        database.withTransaction {
            indexDao.clearArtistRefs()
            indexDao.clearArtists()
            indexDao.clearAlbumRefs()
            indexDao.clearAlbums()

            songs.forEach { song ->
                indexArtistsForSong(song, artistConfig)
                indexAlbumForSong(song)
            }

            refreshAndPruneIndexesInTransaction()
        }
    }

    override suspend fun rebuildArtistIndex() {
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()
        val songs = songDao.getAllSongsSnapshot()

        database.withTransaction {
            indexDao.clearArtistRefs()
            indexDao.clearArtists()

            songs.forEach { song ->
                indexArtistsForSong(song, artistConfig)
            }

            indexDao.refreshArtistStats()
            indexDao.refreshArtistCovers()
            indexDao.deleteOrphanArtists()
        }
    }

    override suspend fun rebuildAlbumIndex() {
        val songs = songDao.getAllSongsSnapshot()

        database.withTransaction {
            indexDao.clearAlbumRefs()
            indexDao.clearAlbums()

            songs.forEach { song ->
                indexAlbumForSong(song)
            }

            indexDao.refreshAlbumStats()
            indexDao.refreshAlbumCovers()
            indexDao.deleteOrphanAlbums()
        }
    }

    override suspend fun reindexSong(song: SongEntity) {
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()

        database.withTransaction {
            reindexSongInTransaction(song, artistConfig)
            refreshAndPruneIndexesInTransaction()
        }
    }

    override suspend fun reindexSongs(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()

        database.withTransaction {
            songs.forEach { song ->
                reindexSongInTransaction(song, artistConfig)
            }
            refreshAndPruneIndexesInTransaction()
        }
    }

    override suspend fun reindexSongInTransaction(song: SongEntity) {
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()
        reindexSongInTransaction(song, artistConfig)
    }

    override suspend fun reindexSongsInTransaction(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val artistConfig = settingsRepository.artistSplitConfigFlow.first()
        songs.forEach { song ->
            reindexSongInTransaction(song, artistConfig)
        }
        refreshAndPruneIndexesInTransaction()
    }

    override suspend fun removeSongIndex(songId: Long) {
        database.withTransaction {
            indexDao.deleteArtistRefsBySongId(songId)
            indexDao.deleteAlbumRefsBySongId(songId)
            refreshAndPruneIndexesInTransaction()
        }
    }

    override suspend fun refreshAndPruneIndexes() {
        database.withTransaction {
            refreshAndPruneIndexesInTransaction()
        }
    }

    override suspend fun ensureIndexesCurrent() {
        val savedVersion = settingsRepository.getLibraryIndexVersion()
        if (savedVersion >= LIBRARY_INDEX_VERSION) return

        rebuildAllIndexes()
        settingsRepository.saveLibraryIndexVersion(LIBRARY_INDEX_VERSION)
    }

    private suspend fun reindexSongInTransaction(
        song: SongEntity,
        artistConfig: ArtistSplitConfig
    ) {
        indexDao.deleteArtistRefsBySongId(song.id)
        indexDao.deleteAlbumRefsBySongId(song.id)

        indexArtistsForSong(song, artistConfig)
        indexAlbumForSong(song)
    }

    private suspend fun indexArtistsForSong(
        song: SongEntity,
        config: ArtistSplitConfig
    ) {
        val artists = ArtistNameSplitter.splitArtists(song.artist, config)

        for (artistName in artists) {
            val normalizedName = artistName.normalizedArtistKey()
            val sortKeys = SortKeyUtils.getSortKeys(artistName)

            val insertedId = indexDao.insertArtist(
                ArtistEntity(
                    name = artistName,
                    normalizedName = normalizedName,
                    groupKey = sortKeys.groupKey,
                    sortKey = sortKeys.sortKey
                )
            )

            val artistId = if (insertedId > 0) {
                insertedId
            } else {
                indexDao.getArtistIdByNormalizedName(normalizedName) ?: continue
            }

            indexDao.insertArtistSongRef(
                ArtistSongCrossRef(
                    artistId = artistId,
                    songId = song.id
                )
            )
        }
    }

    private suspend fun indexAlbumForSong(song: SongEntity) {
        val albumName = song.album?.trim().orEmpty()
        if (albumName.isBlank()) return

        val albumArtist = song.albumArtist
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val normalizedKey = normalizedAlbumKey(albumName, albumArtist)
        val sortKeys = SortKeyUtils.getSortKeys(albumName)

        val insertedId = indexDao.insertAlbum(
            AlbumEntity(
                name = albumName,
                albumArtist = albumArtist,
                normalizedKey = normalizedKey,
                groupKey = sortKeys.groupKey,
                sortKey = sortKeys.sortKey
            )
        )

        val albumId = if (insertedId > 0) {
            insertedId
        } else {
            indexDao.getAlbumIdByNormalizedKey(normalizedKey) ?: return
        }

        indexDao.insertAlbumSongRef(
            AlbumSongCrossRef(
                albumId = albumId,
                songId = song.id
            )
        )
    }

    private suspend fun refreshAndPruneIndexesInTransaction() {
        indexDao.refreshArtistStats()
        indexDao.refreshAlbumStats()
        indexDao.refreshArtistCovers()
        indexDao.refreshAlbumCovers()
        indexDao.deleteOrphanArtists()
        indexDao.deleteOrphanAlbums()
    }

    private companion object {
        const val LIBRARY_INDEX_VERSION = 2
    }
}
