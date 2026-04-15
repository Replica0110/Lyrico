package com.lonx.lyrico.data.repository

import android.app.RecoverableSecurityException
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.AudioTagKeys
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.exception.RequiresUserPermissionException
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.data.utils.SortKeyUtils
import com.lonx.lyrico.utils.MediaScanner
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 歌曲数据存储库实现类 (基于 Uri 版本)
 */
class SongRepositoryImpl(
    private val database: LyricoDatabase,
    private val context: Context,
    private val mediaScanner: MediaScanner,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
) : SongRepository {

    private val songDao = database.songDao()
    private val folderDao = database.folderDao()
    private val syncMutex = Mutex()

    private val songQueryBuilder = SongQueryBuilder
    private companion object {
        const val TAG = "SongRepository"
        const val BATCH_SIZE = 50
    }

    override suspend fun deleteSong(song: SongEntity) {
        withContext(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                // 解析存储在实体中的 URI 字符串
                val uri = song.uri.toUri()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val rowsDeleted = contentResolver.delete(uri, null, null)
                        if (rowsDeleted == 0) {
                            // 删除失败可能意味着文件不存在，但我们仍需清理数据库
                            Log.w(TAG, "系统未返回删除行数或文件已不存在: $uri")
                        }
                    } catch (e: RecoverableSecurityException) {
                        // Android 10/11+ 需要用户权限确认
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                        Log.w(TAG, "RecoverableSecurityException, 需要用户确认: $uri")
                        throw e
                    } catch (e: SecurityException) {
                        Log.e(TAG, "权限不足，无法删除: $uri", e)
                        return@withContext
                    }
                } else {
                    // Android 9 及以下
                    contentResolver.delete(uri, null, null)
                }

                songDao.deleteByUris(listOf(song.uri))
                Log.d(TAG, "已删除歌曲: ${song.title}")

            } catch (e: Exception) {
                Log.e(TAG, "删除歌曲失败: ${song.title}", e)
            }
        }
    }


    override suspend fun getSongByUri(uri: String): SongEntity? {
        return songDao.getSongByUri(uri)
    }

    override suspend fun synchronize(fullRescan: Boolean) {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                Log.d(TAG, "开始同步数据库与设备文件 (Uri模式)... (全量扫描: $fullRescan)")

                val ignoreShortAudio = settingsRepository.ignoreShortAudio.first()
                val minDuration = 60_000L

                val dbSyncInfos = songDao.getAllSyncInfo()
                val dbSongMap = dbSyncInfos.associateBy { it.uri }

                val deviceUris = mutableSetOf<String>()
                val folderIdCache = mutableMapOf<String, Long>()
                val impactedFolderIds = mutableSetOf<Long>()

                // 在内存中收集所有需要变更的数据（涉及文件 I/O，不在事务中执行）
                val songsToUpsert = mutableListOf<SongEntity>()

                suspend fun resolveFolderId(path: String): Long {
                    val folderPath = path.substringBeforeLast("/").trimEnd('/')
                    return folderIdCache.getOrPut(folderPath) {
                        folderDao.upsertAndGetId(folderPath)
                    }.also { impactedFolderIds.add(it) }
                }

                val deviceSongs = mediaScanner.querySongs()
                Log.d(TAG, "MediaStore 查询完成，共 ${deviceSongs.size} 首")

                for (deviceSong in deviceSongs) {
                    if (ignoreShortAudio && deviceSong.duration <= minDuration) {
                        continue
                    }

                    val deviceUriString = deviceSong.uri.toString()
                    deviceUris.add(deviceUriString)

                    val dbInfo = dbSongMap[deviceUriString]
                    val needsUpdate = fullRescan || dbInfo == null
                            || dbInfo.fileLastModified != deviceSong.lastModified
                            || dbInfo.filePath != deviceSong.filePath

                    if (needsUpdate) {
                        try {
                            val folderId = resolveFolderId(deviceSong.filePath)
                            val entity = extractSongMetadata(
                                deviceSong,
                                folderId,
                                existingId = dbInfo?.id ?: 0L
                            )
                            if (entity != null) {
                                songsToUpsert.add(entity)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理歌曲失败: ${deviceSong.fileName}", e)
                        }
                    }
                }

                // 计算需要删除的 URI
                val deletedUris = dbSongMap.keys - deviceUris
                if (deletedUris.isNotEmpty()) {
                    val folderIdsOfDeletedSongs = dbSyncInfos
                        .filter { it.uri in deletedUris }
                        .map { it.folderId }
                    impactedFolderIds.addAll(folderIdsOfDeletedSongs)
                }

                // 单一事务中执行所有数据库写入操作
                // Room 仅在事务提交时触发一次表失效重查询，避免 CursorWindow 的并发重查询溢出
                Log.d(TAG, "准备提交: ${songsToUpsert.size} 条更新, ${deletedUris.size} 条删除")
                database.withTransaction {
                    if (songsToUpsert.isNotEmpty()) {
                        songsToUpsert.chunked(BATCH_SIZE).forEach { chunk ->
                            songDao.upsertAll(chunk)
                        }
                    }

                    if (deletedUris.isNotEmpty()) {
                        deletedUris.chunked(BATCH_SIZE).forEach { chunk ->
                            songDao.deleteByUris(chunk.toList())
                        }
                    }

                    impactedFolderIds.forEach { folderId ->
                        folderDao.refreshSongCount(folderId)
                    }
                    folderDao.performPostScanCleanup()
                }

                settingsRepository.saveLastScanTime(System.currentTimeMillis())
                Log.d(TAG, "同步全部完成。")
            }
        }
    }

    override suspend fun updateMetadatas(updates: List<Pair<SongEntity, AudioTagData>>) {
        withContext(Dispatchers.IO) {
            if (updates.isEmpty()) return@withContext

            val updatedEntities = updates.map { (song, tag) ->
                val newModifiedTime = System.currentTimeMillis()

                song.copy(
                    title = tag.title ?: song.title,
                    artist = tag.artist ?: song.artist,
                    lyrics = tag.lyrics ?: song.lyrics,
                    date = tag.date ?: song.date,
                    trackerNumber = tag.trackNumber ?: song.trackerNumber,
                    album = tag.album ?: song.album,
                    genre = tag.genre ?: song.genre,
                    fileLastModified = newModifiedTime // 更新为当前时间
                ).withSortKeysUpdated()
            }

            database.withTransaction {
                updatedEntities.chunked(100).forEach { chunk ->
                    songDao.upsertAll(chunk)
                }
            }

            updatedEntities.map { it.folderId }.distinct().forEach { folderId ->
                folderDao.refreshSongCount(folderId)
            }
        }
    }

    private suspend fun extractSongMetadata(
        songFile: SongFile,
        folderId: Long,
        existingId: Long = 0L
    ): SongEntity? = withContext(Dispatchers.IO) {
        try {

            val audioData = context.contentResolver.openFileDescriptor(
                songFile.uri, "r"
            )?.use { pfd ->
                AudioTagReader.read(pfd, readPictures = false)
            } ?: return@withContext null

            return@withContext SongEntity(
                id = existingId,
                mediaId = songFile.mediaId,
                uri = songFile.uri.toString(),
                filePath = songFile.filePath,
                fileName = songFile.fileName,
                title = audioData.title,
                fileSize = songFile.fileSize,
                fileExtension = songFile.fileName.substringAfterLast(".").uppercase(),
                artist = audioData.artist,
                album = audioData.album,
                genre = audioData.genre,
                trackerNumber = audioData.trackNumber,
                date = audioData.date,
                lyrics = audioData.lyrics,
                composer = audioData.composer,
                lyricist = audioData.lyricist,
                comment = audioData.comment,
                discNumber = audioData.discNumber,
                copyright = audioData.copyright,
                rating = audioData.rating,
                durationMilliseconds = audioData.durationMilliseconds,
                bitrate = audioData.bitrate,
                sampleRate = audioData.sampleRate,
                channels = audioData.channels,
                rawProperties = audioData.rawProperties.toString(),
                fileLastModified = songFile.lastModified,
                fileAdded = songFile.dateAdded,
                folderId = folderId
            ).withSortKeysUpdated()
        } catch (e: Exception) {
            Log.e(TAG, "解析元数据失败: ${songFile.fileName}", e)
            null
        }
    }

    override fun searchSongs(query: String, type: LocalSearchType): Flow<List<SongEntity>> {
        return songDao.searchSongsByType(query, type.value)
    }

    override suspend fun updateSongMetadata(
        audioTagData: AudioTagData,
        contentUri: String,
        lastModified: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 使用 URI 查询
            val existingSong = songDao.getSongByUri(contentUri)
                ?: return@withContext false

            val updatedSong = existingSong.copy(
                title = audioTagData.title ?: existingSong.title,
                artist = audioTagData.artist ?: existingSong.artist,
                album = audioTagData.album ?: existingSong.album,
                albumArtist = audioTagData.albumArtist ?: existingSong.albumArtist,
                genre = audioTagData.genre ?: existingSong.genre,
                trackerNumber = audioTagData.trackNumber ?: existingSong.trackerNumber,
                discNumber = audioTagData.discNumber ?: existingSong.discNumber,
                date = audioTagData.date ?: existingSong.date,
                composer = audioTagData.composer ?: existingSong.composer,
                lyricist = audioTagData.lyricist ?: existingSong.lyricist,
                comment = audioTagData.comment ?: existingSong.comment,
                lyrics = audioTagData.lyrics ?: existingSong.lyrics,
                copyright = audioTagData.copyright ?: existingSong.copyright,
                rating = audioTagData.rating ?: existingSong.rating,
                rawProperties = audioTagData.rawProperties.toString(),
                fileLastModified = lastModified
            ).withSortKeysUpdated()

            songDao.update(updatedSong)

            Log.d(TAG, "歌曲元数据已更新: $contentUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新歌曲元数据失败: $contentUri", e)
            false
        }
    }
    override suspend fun overwriteAudioTags(contentUri: String, audioTagData: AudioTagData): Boolean {
        try {
            return writeInternal(contentUri, audioTagData)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                throw RequiresUserPermissionException(e.userAction.actionIntent.intentSender)
            }

            if (e is SecurityException) {
                Log.e("SongRepository", "权限不足无法写入: $contentUri", e)
                return false
            }

            Log.e("SongRepository", "写入失败: $contentUri", e)
            return false
        }
    }

    override suspend fun patchAudioTags(contentUri: String, audioTagData: AudioTagData): Boolean {
        try {
            return writeIncremental(contentUri, audioTagData)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                throw RequiresUserPermissionException(e.userAction.actionIntent.intentSender)
            }

            if (e is SecurityException) {
                Log.e("SongRepository", "权限不足无法写入: $contentUri", e)
                return false
            }

            Log.e("SongRepository", "增量更新失败: $contentUri", e)
            return false
        }
    }
    private suspend fun writeInternal(uriString: String, audioTagData: AudioTagData): Boolean {
        val contentUri = uriString.toUri()

        context.contentResolver.openFileDescriptor(contentUri, "rw")?.use { pfdDescriptor ->

            val updates = mutableMapOf<String, String>()

            fun updateTag(standardKey: String, value: String?, aliases: List<String>) {
                if (value != null) {
                    updates[standardKey] = value
                }

                aliases.forEach { aliasKey ->
                    updates[aliasKey] = ""
                }
            }

            updateTag("TITLE", audioTagData.title, listOf("TIT2", "TIT1"))
            updateTag("ARTIST", audioTagData.artist, listOf("TPE1"))
            updateTag("ALBUM", audioTagData.album, listOf("TALB"))
            updateTag("GENRE", audioTagData.genre, listOf("TCON", "STYLE", "SUBGENRE", "MOOD"))
            updateTag("DATE", audioTagData.date, listOf("YEAR", "TYER", "TDAT"))
            updateTag("TRACKNUMBER", audioTagData.trackNumber, listOf("TRACK", "TRCK"))

            updateTag("ALBUMARTIST", audioTagData.albumArtist, listOf("TPE2", "ALBUM ARTIST", "aART", "ALBUMARTISTSORT"))
            updateTag("DISCNUMBER", audioTagData.discNumber?.toString(), listOf("DISC", "TPOS", "DISKNUMBER"))
            updateTag("COMPOSER", audioTagData.composer, listOf("TCOM", "©wrt"))
            updateTag("COMMENT", audioTagData.comment, listOf("COMM", "DESCRIPTION"))
            updateTag("LYRICIST", audioTagData.lyricist, listOf("TEXT", "WRITER", "LYRICS BY"))
            updateTag("LYRICS", audioTagData.lyrics, listOf("UNSYNCED LYRICS", "USLT", "LYRIC", "LYRICSENG"))
            updateTag("COPYRIGHT", audioTagData.copyright, listOf("TCOP", "CPRO", "©cpy"))
            updateTag("REPLAYGAIN_TRACK_GAIN", audioTagData.replayGainTrackGain, emptyList())
            updateTag("REPLAYGAIN_TRACK_PEAK", audioTagData.replayGainTrackPeak, emptyList())
            updateTag("REPLAYGAIN_ALBUM_GAIN", audioTagData.replayGainAlbumGain, emptyList())
            updateTag("REPLAYGAIN_ALBUM_PEAK", audioTagData.replayGainAlbumPeak, emptyList())
            updateTag("REPLAYGAIN_REFERENCE_LOUDNESS", audioTagData.replayGainReferenceLoudness, emptyList())

            val ext = uriString.substringAfterLast(".").uppercase()
            val star = audioTagData.rating ?: 0
            if (star in 1..5) {
                if (ext == "MP3") {
                    val popmVal = when(star) { 1->1; 2->64; 3->128; 4->196; 5->255; else->0 }
                    updateTag("POPM", "no@email|$popmVal|0", listOf("RATING", "RATE"))
                } else if (ext == "FLAC" || ext == "OGG") {
                    updateTag("RATING", (star * 20).toString(), listOf("POPM", "RATE"))
                } else {
                    updateTag("RATE", (star * 20).toString(), listOf("RATING", "POPM"))
                }
            } else if (star == 0) {
                updateTag("POPM", null, listOf("RATING", "RATE"))
            }

            audioTagData.rawProperties
                .orEmpty()
                .keys
                .filterNot { AudioTagKeys.isReserved(it) }
                .forEach { key ->
                    updates.putIfAbsent(key, "")
                }

            audioTagData.customFields.forEach { field ->
                val key = field.key.trim()
                if (key.isEmpty() || AudioTagKeys.isReserved(key)) return@forEach
                updates[key] = field.value.trim()
            }

            AudioTagWriter.writeTags(pfdDescriptor, updates)

            // 图片写入
            val picUrl = audioTagData.picUrl
            if (picUrl != null) {
                if (picUrl.isEmpty()) {
                    AudioTagWriter.writePictures(pfdDescriptor, emptyList())
                } else {
                    val imageBytes = fetchImageBytes(picUrl)
                    if (imageBytes != null) {
                        val picture = AudioPicture(data = imageBytes)
                        AudioTagWriter.writePictures(pfdDescriptor, listOf(picture))
                    }
                }
            }

            return true
        }
        return false
    }

    /**
     * 增量写入：只更新非 null 字段，忽略 null 字段
     */
    private suspend fun writeIncremental(uriString: String, audioTagData: AudioTagData): Boolean {
        val contentUri = uriString.toUri()

        context.contentResolver.openFileDescriptor(contentUri, "rw")?.use { pfdDescriptor ->

            val updates = mutableMapOf<String, String>()

            // 增量更新：只有非 null 值才写入
            fun updateTagIfPresent(standardKey: String, value: String?, aliases: List<String>) {
                if (value != null) {
                    updates[standardKey] = value
                    aliases.forEach { aliasKey ->
                        updates[aliasKey] = ""
                    }
                }
                // 如果 value 为 null，不做任何操作，保留原有值
            }

            updateTagIfPresent("TITLE", audioTagData.title, listOf("TIT2", "TIT1"))
            updateTagIfPresent("ARTIST", audioTagData.artist, listOf("TPE1"))
            updateTagIfPresent("ALBUM", audioTagData.album, listOf("TALB"))
            updateTagIfPresent("GENRE", audioTagData.genre, listOf("TCON", "STYLE", "SUBGENRE", "MOOD"))
            updateTagIfPresent("DATE", audioTagData.date, listOf("YEAR", "TYER", "TDAT"))
            updateTagIfPresent("TRACKNUMBER", audioTagData.trackNumber, listOf("TRACK", "TRCK"))

            updateTagIfPresent("ALBUMARTIST", audioTagData.albumArtist, listOf("TPE2", "ALBUM ARTIST", "aART", "ALBUMARTISTSORT"))
            updateTagIfPresent("DISCNUMBER", audioTagData.discNumber?.toString(), listOf("DISC", "TPOS", "DISKNUMBER"))
            updateTagIfPresent("COMPOSER", audioTagData.composer, listOf("TCOM", "©wrt"))
            updateTagIfPresent("COMMENT", audioTagData.comment, listOf("COMM", "DESCRIPTION"))
            updateTagIfPresent("LYRICIST", audioTagData.lyricist, listOf("TEXT", "WRITER", "LYRICS BY"))
            updateTagIfPresent("LYRICS", audioTagData.lyrics, listOf("UNSYNCED LYRICS", "USLT", "LYRIC", "LYRICSENG"))
            updateTagIfPresent("COPYRIGHT", audioTagData.copyright, listOf("TCOP", "CPRO", "©cpy"))
            updateTagIfPresent("REPLAYGAIN_TRACK_GAIN", audioTagData.replayGainTrackGain, emptyList())
            updateTagIfPresent("REPLAYGAIN_TRACK_PEAK", audioTagData.replayGainTrackPeak, emptyList())
            updateTagIfPresent("REPLAYGAIN_ALBUM_GAIN", audioTagData.replayGainAlbumGain, emptyList())
            updateTagIfPresent("REPLAYGAIN_ALBUM_PEAK", audioTagData.replayGainAlbumPeak, emptyList())
            updateTagIfPresent("REPLAYGAIN_REFERENCE_LOUDNESS", audioTagData.replayGainReferenceLoudness, emptyList())

            // 评分处理：只有明确设置了 rating 才更新
            val star = audioTagData.rating
            if (star != null) {
                val ext = uriString.substringAfterLast(".").uppercase()
                if (star in 1..5) {
                    if (ext == "MP3") {
                        val popmVal = when(star) { 1->1; 2->64; 3->128; 4->196; 5->255; else->0 }
                        updateTagIfPresent("POPM", "no@email|$popmVal|0", listOf("RATING", "RATE"))
                    } else if (ext == "FLAC" || ext == "OGG") {
                        updateTagIfPresent("RATING", (star * 20).toString(), listOf("POPM", "RATE"))
                    } else {
                        updateTagIfPresent("RATE", (star * 20).toString(), listOf("RATING", "POPM"))
                    }
                } else if (star == 0) {
                    // 明确设置为 0，清空评分
                    updateTagIfPresent("POPM", "", listOf("RATING", "RATE"))
                }
            }
            // 如果 rating 为 null，不执行任何操作，保留原有评分

            // 只有在有实际更新时才写入
            if (updates.isNotEmpty()) {
                AudioTagWriter.writeTags(pfdDescriptor, updates)
            }

            // 图片写入：只有 picUrl 非 null 才处理
            val picUrl = audioTagData.picUrl
            if (picUrl != null) {
                if (picUrl.isEmpty()) {
                    AudioTagWriter.writePictures(pfdDescriptor, emptyList())
                } else {
                    val imageBytes = fetchImageBytes(picUrl)
                    if (imageBytes != null) {
                        val picture = AudioPicture(data = imageBytes)
                        AudioTagWriter.writePictures(pfdDescriptor, listOf(picture))
                    }
                }
            }

            return true
        }
        return false
    }

    override suspend fun readAudioTagData(contentUri: String): AudioTagData {
        return withContext(Dispatchers.IO) {
            val displayName = getDisplayName(contentUri)
            try {
                val uri = contentUri.toUri()
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    val data = AudioTagReader.read(descriptor, true)
                    data.copy(fileName = displayName)
                } ?: AudioTagData(fileName = displayName)
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $contentUri", e)
                AudioTagData(fileName = displayName)
            }
        }
    }

    override suspend fun getSongCount(): Int = withContext(Dispatchers.IO) {
        songDao.getSongCount()
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            songDao.clear()
            Log.d(TAG, "所有歌曲数据已清空")
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

    override fun observeSongs(sortBy: SortBy, order: SortOrder): Flow<List<SongEntity>> {
        val sortInfo = SortInfo(sortBy, order)
        val query = songQueryBuilder.build(sortInfo)
        return songDao.getSongs(query)
    }

    private suspend fun fetchImageBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (path.startsWith("http")) {
                val request = Request.Builder().url(path).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.bytes() else null
                }
            } else {
                context.contentResolver.openInputStream(path.toUri())?.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取图片字节流失败: $path", e)
            null
        }
    }


    override fun getDisplayName(contentUri: String): String {
        try {
            val uri = contentUri.toUri()
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
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
            } else if (uri.scheme == "file") {
                return File(uri.path ?: "").name
            }
        } catch (e: Exception) {
            Log.e(TAG, "从 URI 获取文件名失败: $contentUri", e)
        }
        // 最后的 fallback
        return contentUri.substringAfterLast("/")
    }
}
