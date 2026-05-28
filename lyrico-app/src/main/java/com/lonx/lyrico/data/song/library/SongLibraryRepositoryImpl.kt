package com.lonx.lyrico.data.song.library

import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SongLibraryRepositoryImpl(
    private val songDao: SongDao
) : SongLibraryRepository {

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

    override suspend fun upsertSongs(songs: List<SongEntity>) {
        withContext(Dispatchers.IO) {
            if (songs.isNotEmpty()) songDao.upsertAll(songs)
        }
    }

    override suspend fun updateSong(song: SongEntity) {
        withContext(Dispatchers.IO) {
            songDao.update(song)
        }
    }

    override suspend fun updateSongs(songs: List<SongEntity>) {
        withContext(Dispatchers.IO) {
            if (songs.isNotEmpty()) songDao.upsertAll(songs)
        }
    }

    override suspend fun deleteSongsByUris(uris: List<String>) {
        withContext(Dispatchers.IO) {
            if (uris.isNotEmpty()) songDao.deleteByUris(uris)
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            songDao.clear()
        }
    }
}
