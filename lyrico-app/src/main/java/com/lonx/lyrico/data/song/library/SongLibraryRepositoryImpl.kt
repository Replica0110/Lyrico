package com.lonx.lyrico.data.song.library

import androidx.room.withTransaction
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.song.search.LyricFtsIndexer
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SongLibraryRepositoryImpl(
    private val database: LyricoDatabase
) : SongLibraryRepository {
    private val songDao: SongDao = database.songDao()

    override fun observeSongs(
        sortBy: SortBy,
        order: SortOrder,
        folderId: Long?
    ): Flow<List<SongEntity>> {
        val query = SongQueryBuilder.build(SortInfo(sortBy, order), folderId)
        return songDao.getSongs(query)
    }

    override suspend fun getSongByUri(uri: String): SongEntity? {
        return withContext(Dispatchers.IO) {
            songDao.getSongByUri(uri)
        }
    }

    override suspend fun getSongsByUris(uris: List<String>): List<SongEntity> {
        return withContext(Dispatchers.IO) {
            if (uris.isEmpty()) emptyList() else songDao.getSongsByUris(uris)
        }
    }

    override suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity> {
        return withContext(Dispatchers.IO) {
            songDao.getSongsByAlbum(album, artist)
        }
    }

    override suspend fun getSongCount(): Int {
        return withContext(Dispatchers.IO) {
            songDao.getSongCount()
        }
    }

    override suspend fun getSongsWithoutLyrics(): List<SongEntity> {
        return withContext(Dispatchers.IO) {
            songDao.getSongsWithoutLyrics()
        }
    }

    override suspend fun getSongsForDedup(): List<SongEntity> {
        return withContext(Dispatchers.IO) {
            songDao.getSongsForDedup()
        }
    }

    override suspend fun deleteSongsByIds(songIds: List<Long>) {
        withContext(Dispatchers.IO) {
            if (songIds.isNotEmpty()) {
                database.withTransaction {
                    songDao.deleteByIds(songIds)
                }
            }
        }
    }

    override suspend fun upsertSongs(songs: List<SongEntity>) {
        withContext(Dispatchers.IO) {
            if (songs.isNotEmpty()) {
                database.withTransaction {
                    songDao.upsertAll(songs)
                    LyricFtsIndexer.replaceSongs(songDao, songs)
                }
            }
        }
    }

    override suspend fun updateSong(song: SongEntity) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                songDao.update(song)
                LyricFtsIndexer.replaceSong(songDao, song)
            }
        }
    }

    override suspend fun updateSongs(songs: List<SongEntity>) {
        withContext(Dispatchers.IO) {
            if (songs.isNotEmpty()) {
                database.withTransaction {
                    songDao.upsertAll(songs)
                    LyricFtsIndexer.replaceSongs(songDao, songs)
                }
            }
        }
    }

    override suspend fun deleteSongsByUris(uris: List<String>) {
        withContext(Dispatchers.IO) {
            if (uris.isNotEmpty()) {
                database.withTransaction {
                    songDao.deleteByUris(uris)
                    songDao.deleteLyricFtsByUris(uris)
                }
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                songDao.clear()
                songDao.clearLyricFts()
            }
        }
    }
}
