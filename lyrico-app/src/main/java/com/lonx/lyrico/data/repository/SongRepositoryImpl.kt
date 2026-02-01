package com.lonx.lyrico.data.repository

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.utils.SortKeyUtils
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 歌曲数据存储库实现类
 */
class SongRepositoryImpl(
    private val database: LyricoDatabase,
    private val context: Context,
    private val musicScanner: MusicScanner,
    private val settingsRepository: SettingsRepository
) : SongRepository {

    private val songDao = database.songDao()
    private companion object {
        const val TAG = "SongRepository"
    }

    override suspend fun synchronizeWithDevice() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "开始同步数据库与设备文件...")

            val dbSongs = songDao.getAllSongs().first()
            val dbSongMap = dbSongs.associateBy { it.filePath }
            val dbPaths = dbSongMap.keys

            val devicePaths = mutableSetOf<String>()

            musicScanner.scanMusicFiles().collect { deviceSong ->
                devicePaths.add(deviceSong.filePath)

                val dbSong = dbSongMap[deviceSong.filePath]
                if (dbSong == null || dbSong.fileLastModified != deviceSong.lastModified) {
                    readAndSaveSongMetadata(deviceSong, forceUpdate = true)
                }
            }

            val deletedPaths = dbPaths - devicePaths
            if (deletedPaths.isNotEmpty()) {
                Log.d(TAG, "发现 ${deletedPaths.size} 首已删除歌曲，正在从数据库移除...")
                songDao.deleteByFilePaths(deletedPaths.toList())
            }

            settingsRepository.saveLastScanTime(System.currentTimeMillis())
            Log.d(TAG, "数据库同步完成。")
        }
    }

    /**
     * Reads metadata from a SongFile and saves it to the database.
     */
    private suspend fun readAndSaveSongMetadata(
        songFile: SongFile,
        forceUpdate: Boolean = false
    ): SongEntity? = withContext(Dispatchers.IO) {
        try {
            if (!forceUpdate) {
                val existingSong = songDao.getSongByPath(songFile.filePath)
                if (existingSong != null && existingSong.fileLastModified == songFile.lastModified) {
                    return@withContext existingSong
                }
            }

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
                fileLastModified = songFile.lastModified,
                fileAdded = songFile.dateAdded
            ).withSortKeysUpdated()

            songDao.insert(songEntity) 

            return@withContext songEntity

        } catch (e: Exception) {
            Log.e(TAG, "读取歌曲元数据失败: ${songFile.fileName}", e)
            return@withContext null
        }
    }

    override fun getAllSongs(): Flow<List<SongEntity>> {
        return songDao.getAllSongs()
    }

    override fun searchSongs(query: String): Flow<List<SongEntity>> {
        return songDao.searchSongsByAll(query)
    }

    override suspend fun updateSongMetadata(audioTagData: AudioTagData, filePath: String, lastModified: Long): Boolean =
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
                    fileLastModified = lastModified
                ).withSortKeysUpdated()

                songDao.update(updatedSong)

                Log.d(TAG, "歌曲元数据已更新: $filePath")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "更新歌曲元数据失败: $filePath", e)
                return@withContext false
            }
        }


    override suspend fun writeAudioTagData(filePath: String, audioTagData: AudioTagData): Boolean {
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

                AudioTagWriter.writeTags(pfdDescriptor, updates)

                audioTagData.lyrics?.let { lyricsString ->
                    AudioTagWriter.writeLyrics(pfdDescriptor, lyricsString)
                }
                audioTagData.picUrl?.let { picUrl ->
                    Log.d(TAG, "写入图片: $picUrl")
                    val imageBytes = downloadImageBytes(picUrl)
                    val pictures = AudioPicture(
                        data = imageBytes
                        )
                    AudioTagWriter.writePictures(pfdDescriptor, listOf(pictures))
                }

                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "写入文件失败", e)
            false
        }
    }

    override suspend fun readAudioTagData(filePath: String): AudioTagData {
        return withContext(Dispatchers.IO) {
            val fileName = getFileName(filePath)
            try {
                (if (isUriPath(filePath)) {
                    context.contentResolver.openFileDescriptor(filePath.toUri(), "r")
                } else {
                    android.os.ParcelFileDescriptor.open(File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                })?.use { descriptor ->
                    val data = AudioTagReader.read(descriptor, true)
                    // 使用 copy 将文件名注入到返回的对象中
                    data.copy(fileName = fileName)

                } ?: AudioTagData(fileName = fileName) // 打开失败时，至少返回文件名
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $filePath", e)
                // 发生异常时，返回一个空的 Data 对象，但带上文件名
                AudioTagData(fileName = fileName)
            }
        }
    }

    override suspend fun getSongsCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongsCount()
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            songDao.clear()
            Log.d(TAG, "所有歌曲数据已清空")
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
    private fun SongEntity.withSortKeysUpdated(): SongEntity {
        val titleText = (title?.takeIf { it.isNotBlank() } ?: fileName)
        val artistText = (artist?.takeIf { it.isNotBlank() } ?: "未知艺术家")

        val titleKeys = SortKeyUtils.getSortKeys(titleText)
        val artistKeys = SortKeyUtils.getSortKeys(artistText)

        return copy(
            titleGroupKey = titleKeys.groupKey,
            titleSortKey = titleKeys.sortKey,
            artistGroupKey = artistKeys.groupKey,
            artistSortKey = artistKeys.sortKey,
            dbUpdateTime = System.currentTimeMillis()
        )
    }

    override fun getAllSongsSorted(sortBy: SortBy, order: SortOrder): Flow<List<SongEntity>> {
        return when (sortBy) {
            SortBy.TITLE -> {
                if (order == SortOrder.ASC) songDao.getAllSongsOrderByTitleAsc()
                else songDao.getAllSongsOrderByTitleDesc()
            }

            SortBy.ARTIST -> {
                if (order == SortOrder.ASC) songDao.getAllSongsOrderByArtistAsc()
                else songDao.getAllSongsOrderByArtistDesc()
            }

            SortBy.DATE_MODIFIED -> {
                if (order == SortOrder.ASC) songDao.getAllSongsOrderByDateModifiedAsc()
                else songDao.getAllSongsOrderByDateModifiedDesc()
            }

            SortBy.DATE_ADDED -> {
                if (order == SortOrder.ASC) songDao.getAllSongsOrderByDateAddedAsc()
                else songDao.getAllSongsOrderByDateAddedDesc()
            }
        }
    }
    private suspend fun downloadImageBytes(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.inputStream.use { it.readBytes() }
        }

    override fun getFileName(filePath: String): String {
        // Content URI (例如 content://media/external/...)
        if (filePath.startsWith("content://")) {
            try {
                val uri = filePath.toUri()
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME), // 只查询显示名称
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val displayName = cursor.getString(nameIndex)
                            if (!displayName.isNullOrBlank()) {
                                return displayName
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "从 URI 获取文件名失败: $filePath", e)
            }
        }

        // 普通文件路径，或者上面的查询失败了，使用 File API 回退
        return try {
            File(filePath).name
        } catch (e: Exception) {
            filePath.substringAfterLast("/")
        }
    }

}
