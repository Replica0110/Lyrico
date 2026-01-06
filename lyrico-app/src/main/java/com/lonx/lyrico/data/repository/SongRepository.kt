package com.lonx.lyrico.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.SearchFilter
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌曲数据存储库 - 处理数据库和文件系统交互
 */
class SongRepository(
    private val database: LyricoDatabase,
    private val context: Context,
    private val musicScanner: MusicScanner,
    private val settingsManager: SettingsManager
) {
    private val songDao = database.songDao()
    private companion object {
        const val TAG = "SongRepository"
    }
    suspend fun incrementalScan() = withContext(Dispatchers.Default) {
        val lastScanTime = settingsManager.getLastScanTime()
        val changedFiles = musicScanner.scanMusicFiles(emptyList())
            .filter { it.lastModified > lastScanTime } // 只处理修改时间更新的文件
            .toList()

        if (changedFiles.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                changedFiles.forEach { songFile ->
                    readAndSaveSongMetadata(songFile, forceUpdate = true)
                }
            }
            settingsManager.saveLastScanTime(System.currentTimeMillis())
        }
    }

    fun searchSongs(query: String, filter: SearchFilter): Flow<List<SongEntity>> {
        return when (filter) {
            SearchFilter.TITLE -> songDao.searchSongsByTitle(query)
            SearchFilter.ARTIST -> songDao.searchSongsByArtist(query)
            SearchFilter.ALBUM -> songDao.searchSongsByAlbum(query)
        }
    }

    fun searchSongsByAll(query: String): Flow<List<SongEntity>> {
        return songDao.searchSongsByAll(query)
    }

    /**
     * Get all songs as a flow.
     */
    fun getAllSongs(): Flow<List<SongEntity>> {
        return songDao.getAllSongs()
    }

    /**
     * 获取指定歌曲的详细信息（Flow）
     */
    fun getSongFlow(filePath: String): Flow<SongEntity?> = songDao.getSongByPathFlow(filePath)

    /**
     * 获取指定歌曲的详细信息
     */
    suspend fun getSong(filePath: String): SongEntity? = withContext(Dispatchers.IO) {
        songDao.getSongByPath(filePath)
    }

    /**
     * 从文件读取元数据并保存到数据库
     */
    suspend fun readAndSaveSongMetadata(
        songFile: SongFile,
        forceUpdate: Boolean = false
    ): SongEntity? = withContext(Dispatchers.IO) {
        try {
            val fileLastModified = songFile.lastModified

            if (!forceUpdate) {
                val existingSong = songDao.getSongByPath(songFile.filePath)
                if (existingSong != null && existingSong.fileLastModified == fileLastModified) {
                    return@withContext existingSong
                }
            }

            Log.d(TAG, "读取歌曲元数据: ${songFile.fileName}")

            val audioData = context.contentResolver.openFileDescriptor(
                songFile.filePath.toUri(), "r"
            )?.use { pfd ->
                AudioTagReader.read(pfd, readPictures = false)
            } ?: return@withContext null

            val songEntity = SongEntity(
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                title = audioData.title,
                artist = audioData.artist,
                album = audioData.album,
                genre = audioData.genre,
                trackerNumber = audioData.trackerNumber,
                date = audioData.date,
                lyrics = audioData.lyrics,
                durationMilliseconds = audioData.durationMilliseconds,
                bitrate = audioData.bitrate,
                sampleRate = audioData.sampleRate,
                channels = audioData.channels,
                rawProperties = audioData.rawProperties.toString(),
                fileLastModified = fileLastModified,
                dbUpdateTime = System.currentTimeMillis()
            )

            songDao.insert(songEntity)
            Log.d(TAG, "歌曲元数据已保存: ${songFile.fileName}")
            return@withContext songEntity

        } catch (e: Exception) {
            Log.e(TAG, "读取歌曲元数据失败: ${songFile.fileName}", e)
            return@withContext null
        }
    }

    suspend fun scanAndSaveSongs(
        songFiles: List<SongFile>,
        forceFullScan: Boolean = false
    ): List<SongEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SongEntity>()
        
        for (songFile in songFiles) {
            val entity = readAndSaveSongMetadata(songFile, forceUpdate = forceFullScan)
            if (entity != null) {
                results.add(entity)
            }
        }

        val existingPaths = songFiles.map { it.filePath }
        if (existingPaths.isNotEmpty()) {
            songDao.deleteNotIn(existingPaths)
        }

        Log.d(TAG, "扫描完成: ${results.size}/${songFiles.size} 歌曲")
        return@withContext results
    }

    suspend fun updateSongMetadata(audioTagData: AudioTagData, filePath: String, lastModified: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val existingSong = songDao.getSongByPath(filePath)
                    ?: return@withContext false

                val updatedSong = existingSong.copy(
                    title = audioTagData.title ?: existingSong.title,
                    artist = audioTagData.artist ?: existingSong.artist,
                    album = audioTagData.album ?: existingSong.album,
                    genre = audioTagData.genre ?: existingSong.genre,
                    trackerNumber = audioTagData.trackerNumber ?: existingSong.trackerNumber,
                    date = audioTagData.date ?: existingSong.date,
                    lyrics = audioTagData.lyrics ?: existingSong.lyrics,
                    rawProperties = audioTagData.rawProperties.toString(),
                    fileLastModified = lastModified,
                    dbUpdateTime = System.currentTimeMillis()
                )

                songDao.update(updatedSong)
                Log.d(TAG, "歌曲元数据已更新: $filePath")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "更新歌曲元数据失败: $filePath", e)
                return@withContext false
            }
        }

    private fun isUriPath(path: String): Boolean {
        return try {
            val uri = path.toUri()
            uri.scheme != null && (uri.scheme == "content" || uri.scheme == "file")
        } catch (e: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun writeAudioTagData(filePath: String, audioTagData: AudioTagData): Boolean {
        return try {
            (if (isUriPath(filePath)) {
                context.contentResolver.openFileDescriptor(filePath.toUri(), "rw")
            } else {
                android.os.ParcelFileDescriptor.open(File(filePath), android.os.ParcelFileDescriptor.MODE_READ_WRITE)
            })?.use { pfdDescriptor ->
                val updates = mutableMapOf<String, String>()
                audioTagData.title?.let { updates["TITLE"] = it }
                audioTagData.artist?.let { updates["ARTIST"] = it }
                audioTagData.album?.let { updates["ALBUM"] = it }
                audioTagData.genre?.let { updates["GENRE"] = it }
                audioTagData.date?.let { updates["DATE"] = it }
                audioTagData.trackerNumber?.let { updates["TRACKNUMBER"] = it }

                com.lonx.audiotag.rw.AudioTagWriter.writeTags(pfdDescriptor, updates)

                audioTagData.lyrics?.let { lyricsString ->
                    com.lonx.audiotag.rw.AudioTagWriter.writeLyrics(pfdDescriptor, lyricsString)
                }

                true
            } ?: false
        } catch (e: android.app.RecoverableSecurityException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败", e)
            false
        }
    }

    suspend fun readAudioTagData(filePath: String): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {
                (if (isUriPath(filePath)) {
                    context.contentResolver.openFileDescriptor(filePath.toUri(), "r")
                } else {
                    android.os.ParcelFileDescriptor.open(File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                })?.use { descriptor ->
                    AudioTagReader.read(descriptor, true)
                } ?: AudioTagData()
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $filePath", e)
                AudioTagData()
            }
        }
    }


    suspend fun deleteSong(filePath: String) = withContext(Dispatchers.IO) {
        try {
            songDao.deleteByFilePath(filePath)
            Log.d(TAG, "歌曲已删除: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "删除歌曲失败: $filePath", e)
        }
    }

    suspend fun getSongsCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongsCount()
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        songDao.clear()
        Log.d(TAG, "所有歌曲数据已清空")
    }


}

