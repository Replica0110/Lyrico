package com.lonx.lyrico.data.song.library

import androidx.room.withTransaction
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.song.mapper.SongMetadataMapper
import com.lonx.lyrico.data.song.search.LyricFtsIndexer
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.data.model.SortBy
import com.lonx.lyrico.data.model.SortInfo
import com.lonx.lyrico.data.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SongLibraryRepositoryImpl(
    private val database: LyricoDatabase,
    private val customTagKeyRepository: CustomTagKeyRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val songMetadataMapper: SongMetadataMapper,
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

    override suspend fun synchronizeSavedTags(uri: String, tagData: AudioTagData): SongEntity? =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val song = songDao.getSongByUri(uri) ?: return@withTransaction null
                val updated = songMetadataMapper.applyAudioTagData(song, tagData)
                songDao.update(updated)
                LyricFtsIndexer.replaceSong(songDao, updated)
                customTagKeyRepository.replaceForSong(uri, tagData.customFields)
                libraryIndexRepository.reindexSong(updated)
                updated
            }
        }

    override suspend fun synchronizeRenamedSong(song: SongEntity, oldUri: String) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                songDao.update(song)
                songDao.deleteLyricFtsByUris(listOf(oldUri))
                LyricFtsIndexer.replaceSong(songDao, song)
                customTagKeyRepository.updateSongUri(oldUri, song.uri)
                libraryIndexRepository.reindexSong(song)
                database.folderDao().refreshSongCount(song.folderId)
            }
        }
    }

    override suspend fun deleteSongsByUris(uris: List<String>) {
        withContext(Dispatchers.IO) {
            if (uris.isNotEmpty()) {
                database.withTransaction {
                    uris.chunked(50).forEach { chunk ->
                        songDao.deleteByUris(chunk)
                    }
                    database.folderDao().performPostScanCleanup()
                    libraryIndexRepository.refreshAndPruneIndexes()
                }
            }
        }
    }
}
