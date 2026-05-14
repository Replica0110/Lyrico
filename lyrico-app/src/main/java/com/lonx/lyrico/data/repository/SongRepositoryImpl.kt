package com.lonx.lyrico.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.AudioTagKeys
import com.lonx.audiotag.rw.AudioTagReader
import com.lonx.audiotag.rw.AudioTagWriter
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.exception.RequiresUserPermissionException
import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.model.dao.AlbumSearchRow
import com.lonx.lyrico.data.model.dao.ArtistSearchRow
import com.lonx.lyrico.data.model.dao.SongFieldValue
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.data.utils.SortKeyUtils
import com.lonx.lyrico.utils.MediaScanner
import com.lonx.lyrico.utils.UriUtils
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
import java.io.FileNotFoundException
import okhttp3.OkHttpClient
import okhttp3.Request

class SongRepositoryImpl(
    private val database: LyricoDatabase,
    private val context: Context,
    private val mediaScanner: MediaScanner,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient,
    private val appLogRepository: AppLogRepository
) : SongRepository {

    private val songDao = database.songDao()
    private val folderDao = database.folderDao()
    private val syncMutex = Mutex()

    private val songQueryBuilder = SongQueryBuilder
    private companion object {
        const val TAG = "SongRepository"
        const val BATCH_SIZE = 50
        const val MAX_LOG_ITEMS = 20
    }

    private suspend fun removeSafFoldersWithoutPermission(): Int {
        val foldersWithoutPermission = folderDao.getSafFoldersForPermissionCheck()
            .filterNot { folder ->
                UriUtils.hasPersistedReadPermission(context, folder.treeUri)
            }

        if (foldersWithoutPermission.isEmpty()) return 0

        database.withTransaction {
            folderDao.deleteFoldersPermanently(foldersWithoutPermission.map { it.id })
        }

        logApp(
            level = AppLogLevel.WARNING,
            message = "Removed SAF folders without persisted permission",
            detail = buildString {
                appendLine("count=${foldersWithoutPermission.size}")
                appendLimitedItems("folders", foldersWithoutPermission.map { it.path })
            }
        )
        Log.w(TAG, "已移除失去 SAF 权限的文件夹: ${foldersWithoutPermission.size} 个")
        return foldersWithoutPermission.size
    }

    private fun formatRawProperties(rawProperties: Map<String, Array<String>>?): String? {
        if (rawProperties == null) return null
        return rawProperties.entries.joinToString(
            prefix = "{",
            postfix = "}"
        ) { (key, values) ->
            "$key=${values.joinToString("; ")}"
        }
    }

    override suspend fun deleteSong(song: SongEntity) {
        withContext(Dispatchers.IO) {
            when (deleteSingleSongFile(song)) {
                DeleteFileResult.Deleted,
                DeleteFileResult.AlreadyMissing -> {
                    database.withTransaction {
                        songDao.deleteByUri(song.uri)
                        folderDao.refreshSongCount(song.folderId)
                        folderDao.performPostScanCleanup()
                    }
                }

                DeleteFileResult.Failed -> return@withContext
            }
        }
    }
    private sealed interface DeleteFileResult {
        data object Deleted : DeleteFileResult
        data object AlreadyMissing : DeleteFileResult
        data object Failed : DeleteFileResult
    }
    override suspend fun deleteSongs(songs: List<SongEntity>) {
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext

            val dbSongsToDelete = mutableListOf<SongEntity>()
            val impactedFolderIds = mutableSetOf<Long>()

            songs.forEach { song ->
                when (deleteSingleSongFile(song)) {
                    DeleteFileResult.Deleted,
                    DeleteFileResult.AlreadyMissing -> {
                        dbSongsToDelete.add(song)
                        impactedFolderIds.add(song.folderId)
                    }

                    DeleteFileResult.Failed -> {
                        // 保留 DB，避免权限失败时误删记录
                    }
                }
            }

            if (dbSongsToDelete.isEmpty()) return@withContext

            database.withTransaction {
                dbSongsToDelete
                    .map { it.uri }
                    .chunked(BATCH_SIZE)
                    .forEach { chunk ->
                        songDao.deleteByUris(chunk)
                    }

                impactedFolderIds.forEach { folderId ->
                    folderDao.refreshSongCount(folderId)
                }

                folderDao.performPostScanCleanup()
            }

            logApp(
                level = AppLogLevel.INFO,
                message = "Songs deleted",
                detail = buildString {
                    appendLine("count=${dbSongsToDelete.size}")
                    appendLimitedItems("uris", dbSongsToDelete.map { it.uri })
                }
            )

            Log.d(TAG, "已批量删除歌曲数据库记录: ${dbSongsToDelete.size} 首")
        }
    }

    private suspend fun deleteSingleSongFile(song: SongEntity): DeleteFileResult {
        return if (song.source == "SAF") {
            deleteSafSongFile(song)
        } else {
            deleteMediaStoreSongFile(song)
        }
    }

    private suspend fun deleteSafSongFile(song: SongEntity): DeleteFileResult {
        return try {
            val uri = song.uri.toUri()

            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)

            if (deleted) {
                DeleteFileResult.Deleted
            } else {
                // deleteDocument 返回 false，通常说明目标不存在或 provider 拒绝删除。
                // 再尝试打开读取，如果打不开，认为文件已经不存在，可以清 DB。
                if (isUriMissing(uri)) {
                    DeleteFileResult.AlreadyMissing
                } else {
                    DeleteFileResult.Failed
                }
            }
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "SAF 文件已不存在，清理数据库记录: ${song.uri}")
            DeleteFileResult.AlreadyMissing
        } catch (e: SecurityException) {
            Log.e(TAG, "SAF 删除权限不足: ${song.uri}", e)
            logException(
                message = "Failed to delete SAF song: permission denied",
                throwable = e,
                relatedId = song.uri
            )
            DeleteFileResult.Failed
        } catch (e: Exception) {
            val uri = song.uri.toUri()

            if (isUriMissing(uri)) {
                Log.w(TAG, "SAF 文件已不存在，清理数据库记录: ${song.uri}", e)
                DeleteFileResult.AlreadyMissing
            } else {
                Log.e(TAG, "SAF 删除失败: ${song.uri}", e)
                logException(
                    message = "Failed to delete SAF song",
                    throwable = e,
                    relatedId = song.uri
                )
                DeleteFileResult.Failed
            }
        }
    }
    private fun isUriMissing(uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                false
            } ?: true
        } catch (e: FileNotFoundException) {
            true
        } catch (e: IllegalArgumentException) {
            true
        } catch (e: SecurityException) {
            // 权限问题不能等同于文件不存在，否则会误删 DB
            false
        } catch (e: Exception) {
            false
        }
    }
    private suspend fun deleteMediaStoreSongFile(song: SongEntity): DeleteFileResult {
        return try {
            val contentResolver = context.contentResolver
            val uri = song.uri.toUri()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val rowsDeleted = contentResolver.delete(uri, null, null)

                    if (rowsDeleted > 0) {
                        DeleteFileResult.Deleted
                    } else {
                        Log.w(TAG, "MediaStore 文件已不存在或记录已不存在，清理数据库: $uri")
                        DeleteFileResult.AlreadyMissing
                    }
                } catch (e: RecoverableSecurityException) {
                    Log.w(TAG, "RecoverableSecurityException, 需要用户确认: $uri")
                    throw e
                } catch (e: SecurityException) {
                    Log.e(TAG, "权限不足，无法删除: $uri", e)
                    logException(
                        message = "Failed to delete song: permission denied",
                        throwable = e,
                        relatedId = song.uri
                    )
                    DeleteFileResult.Failed
                }
            } else {
                val rowsDeleted = contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    DeleteFileResult.Deleted
                } else {
                    DeleteFileResult.AlreadyMissing
                }
            }
        } catch (e: FileNotFoundException) {
            DeleteFileResult.AlreadyMissing
        } catch (e: Exception) {
            Log.e(TAG, "删除歌曲失败: ${song.title}", e)
            logException(
                message = "Failed to delete song: ${song.title}",
                throwable = e,
                relatedId = song.uri
            )
            DeleteFileResult.Failed
        }
    }

    override suspend fun getSongByUri(uri: String): SongEntity? {
        return songDao.getSongByUri(uri)
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

    override suspend fun synchronize(
        fullRescan: Boolean,
        folderIds: Set<Long>?,
        onProgress: (suspend (LibraryScanProgress) -> Unit)?
    ) {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "开始同步数据库与设备文件 (Uri模式)... (全量扫描: $fullRescan)")

                    onProgress?.invoke(LibraryScanProgress(stage = LibraryScanStage.LISTING_FILES))

                    val ignoreShortAudio = settingsRepository.ignoreShortAudio.first()
                    val minDuration = 60_000L

                    val removedSafFolders = removeSafFoldersWithoutPermission()
                    val dbSyncInfos = songDao.getAllSyncInfo()
                    val dbSongMap = dbSyncInfos.associateBy { it.uri }

                    val deviceUris = mutableSetOf<String>()
                    val impactedFolderIds = mutableSetOf<Long>()
                    val itemFailures = mutableListOf<String>()

                    val songsToUpsert = mutableListOf<SongEntity>()

                    val safFolders = folderDao.getSafFolders()
                        .filter { folder -> folderIds == null || folder.id in folderIds }
                    val safScanResult = mediaScanner.querySongsFromSafFolders(safFolders)
                    Log.d(TAG, "SAF 扫描完成，共 ${safScanResult.songs.size} 首, 成功文件夹=${safScanResult.successfulFolderIds.size}, 失败=${safScanResult.failedFolderIds.size}")

                    val deviceSongs = safScanResult.songs
                    Log.d(TAG, "SAF 文件夹歌曲共 ${deviceSongs.size} 首")

                    onProgress?.invoke(
                        LibraryScanProgress(
                            stage = LibraryScanStage.READING_METADATA,
                            current = 0,
                            total = deviceSongs.size
                        )
                    )

                    for ((index, scannedSong) in deviceSongs.withIndex()) {
                        val deviceSong = scannedSong.songFile

                        try {
                            if (ignoreShortAudio && deviceSong.duration > 0L && deviceSong.duration <= minDuration) {
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
                                    val folderId = scannedSong.rootFolderId
                                    impactedFolderIds.add(folderId)

                                    val entity = extractSongMetadata(
                                        songFile = deviceSong,
                                        folderId = folderId,
                                        existingId = dbInfo?.id ?: 0L,
                                        source = "SAF"
                                    )
                                    if (entity != null) {
                                        songsToUpsert.add(entity)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "处理歌曲失败: ${deviceSong.fileName}", e)
                                    itemFailures.add("${deviceSong.fileName}: ${e.message ?: e::class.java.simpleName}")
                                }
                            }
                        } finally {
                            onProgress?.invoke(
                                LibraryScanProgress(
                                    stage = LibraryScanStage.READING_METADATA,
                                    current = index + 1,
                                    total = deviceSongs.size,
                                    currentFile = deviceSong.fileName
                                )
                            )
                        }
                    }

                    val successfulSafFolderIds = safScanResult.successfulFolderIds
                    val missingSafFolderIds = safScanResult.missingFolderIds

                    val deletedUris = dbSyncInfos
                        .filter { info ->
                            when (info.source) {
                                "SAF" -> info.folderId in successfulSafFolderIds && info.uri !in deviceUris
                                else -> true
                            }
                        }
                        .map { it.uri }
                        .toSet()

                    val missingFolderSongUris = dbSyncInfos
                        .filter { info ->
                            info.source == "SAF" && info.folderId in missingSafFolderIds
                        }
                        .map { it.uri }
                        .toSet()

                    val allDeletedUris = deletedUris + missingFolderSongUris

                    if (allDeletedUris.isNotEmpty()) {
                        val folderIdsOfDeletedSongs = dbSyncInfos
                            .filter { it.uri in allDeletedUris }
                            .map { it.folderId }
                        impactedFolderIds.addAll(folderIdsOfDeletedSongs)
                    }

                    impactedFolderIds.addAll(missingSafFolderIds)

                    Log.d(TAG, "准备提交: ${songsToUpsert.size} 条更新, ${allDeletedUris.size} 条删除, ${missingSafFolderIds.size} 个缺失文件夹")
                    val databaseChanges = songsToUpsert.size + allDeletedUris.size + missingSafFolderIds.size
                    onProgress?.invoke(
                        LibraryScanProgress(
                            stage = LibraryScanStage.WRITING_DATABASE,
                            current = 0,
                            total = databaseChanges
                        )
                    )
                    database.withTransaction {
                        if (songsToUpsert.isNotEmpty()) {
                            songsToUpsert.chunked(BATCH_SIZE).forEach { chunk ->
                                songDao.upsertAll(chunk)
                            }
                        }

                        if (allDeletedUris.isNotEmpty()) {
                            allDeletedUris.chunked(BATCH_SIZE).forEach { chunk ->
                                songDao.deleteByUris(chunk.toList())
                            }
                        }

                        if (missingSafFolderIds.isNotEmpty()) {
                            folderDao.deleteFoldersPermanently(missingSafFolderIds.toList())
                        }

                        impactedFolderIds
                            .filterNot { it in missingSafFolderIds }
                            .forEach { folderId ->
                                folderDao.refreshSongCount(folderId)
                            }

                        folderDao.performPostScanCleanup()
                    }
                    onProgress?.invoke(
                        LibraryScanProgress(
                            stage = LibraryScanStage.WRITING_DATABASE,
                            current = databaseChanges,
                            total = databaseChanges
                        )
                    )

                    settingsRepository.saveLastScanTime(System.currentTimeMillis())
                    logApp(
                        level = if (itemFailures.isEmpty()) AppLogLevel.INFO else AppLogLevel.WARNING,
                        message = "Library synchronization finished",
                        detail = buildString {
                            appendLine("fullRescan=$fullRescan")
                            appendLine("safSongs=${safScanResult.songs.size}")
                            appendLine("upserted=${songsToUpsert.size}")
                            appendLine("deleted=${allDeletedUris.size}")
                            appendLine("missingSafFolders=${missingSafFolderIds.size}")
                            appendLine("removedSafFolders=$removedSafFolders")
                            appendLine("foldersUpdated=${impactedFolderIds.size}")
                            appendLine("itemFailures=${itemFailures.size}")
                            appendLine("ignoreShortAudio=$ignoreShortAudio")
                            appendLimitedItems("failures", itemFailures)
                        }
                    )
                    Log.d(TAG, "同步全部完成。")
                    onProgress?.invoke(LibraryScanProgress(stage = LibraryScanStage.FINISHED))
                } catch (e: Exception) {
                    logException(
                        message = "Library synchronization failed",
                        throwable = e
                    )
                    throw e
                }
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
                    language = tag.language ?: song.language,
                    trackerNumber = tag.trackNumber ?: song.trackerNumber,
                    album = tag.album ?: song.album,
                    genre = tag.genre ?: song.genre,
                    fileLastModified = newModifiedTime
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
        existingId: Long = 0L,
        source: String = "MEDIA_STORE"
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
                source = source,
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
                language = audioData.language,
                lyrics = audioData.lyrics,
                composer = audioData.composer,
                lyricist = audioData.lyricist,
                comment = audioData.comment,
                discNumber = audioData.discNumber,
                copyright = audioData.copyright,
                rating = audioData.rating,
                replayGainTrackGain = audioData.replayGainTrackGain,
                replayGainTrackPeak = audioData.replayGainTrackPeak,
                replayGainAlbumGain = audioData.replayGainAlbumGain,
                replayGainAlbumPeak = audioData.replayGainAlbumPeak,
                replayGainReferenceLoudness = audioData.replayGainReferenceLoudness,
                durationMilliseconds = audioData.durationMilliseconds,
                bitrate = audioData.bitrate,
                sampleRate = audioData.sampleRate,
                channels = audioData.channels,
                rawProperties = formatRawProperties(audioData.rawProperties),
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

    override suspend fun updateSongMetadata(
        audioTagData: AudioTagData,
        contentUri: String,
        lastModified: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val existingSong = songDao.getSongByUri(contentUri)
                ?: return@withContext false

            val updatedSong = existingSong.copy(
                title = audioTagData.title ?: existingSong.title,
                artist = audioTagData.artist ?: existingSong.artist,
                album = audioTagData.album ?: existingSong.album,
                albumArtist = audioTagData.albumArtist ?: existingSong.albumArtist,
                genre = audioTagData.genre ?: existingSong.genre,
                language = audioTagData.language ?: existingSong.language,
                trackerNumber = audioTagData.trackNumber ?: existingSong.trackerNumber,
                discNumber = audioTagData.discNumber ?: existingSong.discNumber,
                date = audioTagData.date ?: existingSong.date,
                composer = audioTagData.composer ?: existingSong.composer,
                lyricist = audioTagData.lyricist ?: existingSong.lyricist,
                comment = audioTagData.comment ?: existingSong.comment,
                lyrics = audioTagData.lyrics ?: existingSong.lyrics,
                copyright = audioTagData.copyright ?: existingSong.copyright,
                rating = audioTagData.rating ?: existingSong.rating,
                replayGainTrackGain = audioTagData.replayGainTrackGain
                    ?: existingSong.replayGainTrackGain,
                replayGainTrackPeak = audioTagData.replayGainTrackPeak
                    ?: existingSong.replayGainTrackPeak,
                replayGainAlbumGain = audioTagData.replayGainAlbumGain
                    ?: existingSong.replayGainAlbumGain,
                replayGainAlbumPeak = audioTagData.replayGainAlbumPeak
                    ?: existingSong.replayGainAlbumPeak,
                replayGainReferenceLoudness = audioTagData.replayGainReferenceLoudness
                    ?: existingSong.replayGainReferenceLoudness,
                rawProperties = formatRawProperties(audioTagData.rawProperties) ?: existingSong.rawProperties,
                fileLastModified = lastModified
            ).withSortKeysUpdated()

            songDao.update(updatedSong)

            Log.d(TAG, "歌曲元数据已更新: $contentUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新歌曲元数据失败: $contentUri", e)
            logMetadataException(
                message = "Failed to update song metadata in database",
                throwable = e,
                relatedId = contentUri
            )
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
                createWritePermissionIntentSender(contentUri)?.let { intentSender ->
                    throw RequiresUserPermissionException(intentSender)
                }

                Log.e("SongRepository", "权限不足无法写入: $contentUri", e)
                logMetadataException(
                    message = "Failed to overwrite audio tags: permission denied",
                    throwable = e,
                    relatedId = contentUri
                )
                return false
            }

            Log.e("SongRepository", "写入失败: $contentUri", e)
            logMetadataException(
                message = "Failed to overwrite audio tags",
                throwable = e,
                relatedId = contentUri
            )
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
                createWritePermissionIntentSender(contentUri)?.let { intentSender ->
                    throw RequiresUserPermissionException(intentSender)
                }

                Log.e("SongRepository", "权限不足无法写入: $contentUri", e)
                logMetadataException(
                    message = "Failed to patch audio tags: permission denied",
                    throwable = e,
                    relatedId = contentUri
                )
                return false
            }

            Log.e("SongRepository", "增量更新失败: $contentUri", e)
            logMetadataException(
                message = "Failed to patch audio tags",
                throwable = e,
                relatedId = contentUri
            )
            return false
        }
    }

    private fun createWritePermissionIntentSender(uriString: String): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return try {
            val uri = uriString.toUri()
            val writableUri = when {
                isMediaStoreItemUri(uri) -> uri
                uri.scheme == "content" -> findMediaStoreAudioUri(uri)
                else -> null
            } ?: return null

            MediaStore.createWriteRequest(context.contentResolver, listOf(writableUri))
                .intentSender
        } catch (e: Exception) {
            Log.w(TAG, "无法创建系统写入授权请求: $uriString", e)
            null
        }
    }

    private fun openWritableDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = uriString.toUri()

        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        }

        return tryOpenWritableContentDescriptor(uri) ?: findMediaStoreAudioUri(uri)?.let { mediaStoreUri ->
            context.contentResolver.openFileDescriptor(mediaStoreUri, "rw")
        }
    }

    private fun openReadableDescriptor(uriString: String): ParcelFileDescriptor? {
        val uri = uriString.toUri()

        if (uri.scheme == "file" || uri.scheme.isNullOrBlank()) {
            val file = if (uri.scheme == "file") {
                File(uri.path ?: throw FileNotFoundException(uriString))
            } else {
                File(uriString)
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        return try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readAudioTagDataFromUri(uriString: String, displayName: String): AudioTagData {
        openReadableDescriptor(uriString)?.use { descriptor ->
            return AudioTagReader.read(descriptor, true).copy(fileName = displayName)
        }

        return readAudioTagDataFromStreamCache(uriString, displayName)
    }

    private suspend fun readAudioTagDataFromStreamCache(
        uriString: String,
        displayName: String
    ): AudioTagData {
        val uri = uriString.toUri()
        if (uri.scheme != "content") return AudioTagData(fileName = displayName)

        val cacheDir = File(context.cacheDir, "external-audio-read-cache").apply { mkdirs() }
        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("audio-", ".tmp", cacheDir)
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return AudioTagData(fileName = displayName)

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                return AudioTagReader.read(descriptor, true).copy(fileName = displayName)
            }
        } finally {
            if (!tempFile.delete()) {
                Log.w(TAG, "无法删除临时音频缓存: ${tempFile.absolutePath}")
            }
        }
    }

    private fun tryOpenWritableContentDescriptor(uri: Uri): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "rw")
        } catch (e: SecurityException) {
            null
        } catch (e: FileNotFoundException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun findMediaStoreAudioUri(sourceUri: Uri): Uri? {
        if (isMediaStoreItemUri(sourceUri)) return sourceUri
        if (sourceUri.scheme != "content") return null

        val displayName = getOpenableDisplayName(sourceUri)
            ?: sourceUri.lastPathSegment?.let(Uri::decode)
            ?: return null
        val sourceSize = getOpenableSize(sourceUri)
        val sourceRelativePath = inferRelativePath(sourceUri)

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = buildString {
            append("${MediaStore.Audio.Media.DISPLAY_NAME} = ?")
            if (sourceSize != null) {
                append(" AND ${MediaStore.Audio.Media.SIZE} = ?")
            }
        }
        val selectionArgs = buildList {
            add(displayName)
            sourceSize?.let { add(it.toString()) }
        }.toTypedArray()

        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val relativePathIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                var fallbackUri: Uri? = null
                while (cursor.moveToNext()) {
                    val mediaUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex)
                    )
                    if (fallbackUri == null) {
                        fallbackUri = mediaUri
                    }

                    if (
                        sourceRelativePath != null &&
                        relativePathIndex >= 0 &&
                        cursor.getString(relativePathIndex)?.trimEnd('/') == sourceRelativePath.trimEnd('/')
                    ) {
                        return@use mediaUri
                    }
                }
                fallbackUri
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从 MediaStore 匹配分享文件: $sourceUri", e)
            null
        }
    }

    private fun isMediaStoreItemUri(uri: Uri): Boolean {
        if (uri.scheme != "content" || uri.authority != "media") return false
        return runCatching { ContentUris.parseId(uri) >= 0L }.getOrDefault(false)
    }

    private fun getOpenableDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getOpenableSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun inferRelativePath(uri: Uri): String? {
        val documentRelativePath = inferDocumentRelativePath(uri)
        if (documentRelativePath != null) return documentRelativePath

        val pathSegments = uri.pathSegments
        val externalRootIndex = pathSegments.indexOfFirst {
            it == "external_files" || it == "external" || it == "external_storage"
        }
        if (externalRootIndex < 0 || pathSegments.size <= externalRootIndex + 2) return null

        return pathSegments
            .drop(externalRootIndex + 1)
            .dropLast(1)
            .joinToString("/")
            .takeIf { it.isNotBlank() }
            ?.let { "$it/" }
    }

    private fun inferDocumentRelativePath(uri: Uri): String? {
        return try {
            if (!DocumentsContract.isDocumentUri(context, uri)) return null

            val documentId = DocumentsContract.getDocumentId(uri)
            val relativePathWithFile = documentId.substringAfter(':', missingDelimiterValue = "")
            if (relativePathWithFile.isBlank()) return null

            relativePathWithFile.substringBeforeLast(
                delimiter = "/",
                missingDelimiterValue = ""
            ).takeIf { it.isNotBlank() }?.let { "$it/" }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun writeInternal(uriString: String, audioTagData: AudioTagData): Boolean {
        openWritableDescriptor(uriString)?.use { pfdDescriptor ->

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
            updateTag("LANGUAGE", audioTagData.language, listOf("TLAN"))
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
            } else if (audioTagData.pictures.isNotEmpty()) {
                AudioTagWriter.writePictures(pfdDescriptor, audioTagData.pictures)
            }

            return true
        }
        return false
    }

    private suspend fun writeIncremental(uriString: String, audioTagData: AudioTagData): Boolean {
        openWritableDescriptor(uriString)?.use { pfdDescriptor ->

            val updates = mutableMapOf<String, String>()

            fun updateTagIfPresent(standardKey: String, value: String?, aliases: List<String>) {
                if (value != null) {
                    updates[standardKey] = value
                    aliases.forEach { aliasKey ->
                        updates[aliasKey] = ""
                    }
                }
            }

            updateTagIfPresent("TITLE", audioTagData.title, listOf("TIT2", "TIT1"))
            updateTagIfPresent("ARTIST", audioTagData.artist, listOf("TPE1"))
            updateTagIfPresent("ALBUM", audioTagData.album, listOf("TALB"))
            updateTagIfPresent("GENRE", audioTagData.genre, listOf("TCON", "STYLE", "SUBGENRE", "MOOD"))
            updateTagIfPresent("DATE", audioTagData.date, listOf("YEAR", "TYER", "TDAT"))
            updateTagIfPresent("LANGUAGE", audioTagData.language, listOf("TLAN"))
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
                    updateTagIfPresent("POPM", "", listOf("RATING", "RATE"))
                }
            }

            if (updates.isNotEmpty()) {
                AudioTagWriter.writeTags(pfdDescriptor, updates)
            }

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
                readAudioTagDataFromUri(contentUri, displayName)
            } catch (e: Exception) {
                Log.e(TAG, "读取音频元数据失败: $contentUri", e)
                logMetadataException(
                    message = "Failed to read audio tags",
                    throwable = e,
                    relatedId = contentUri
                )
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
        return contentUri.substringAfterLast("/")
    }

    override suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity> {
        return withContext(Dispatchers.IO) {
            songDao.getSongsByAlbum(album, artist)
        }
    }

    override suspend fun renameSong(song: SongEntity, newFileName: String): Boolean {
        return renameSongAndGetResult(song, newFileName) != null
    }

    override suspend fun renameSongAndGetResult(song: SongEntity, newFileName: String): RenameSongResult? {
        return withContext(Dispatchers.IO) {
            if (song.source == "SAF") {
                renameSafSong(song, newFileName)
            } else {
                renameFilePathSong(song, newFileName)
            }
        }
    }

    private suspend fun renameSafSong(song: SongEntity, newFileName: String): RenameSongResult? {
        val oldUri = song.uri.toUri()

        val renamedUri = try {
            DocumentsContract.renameDocument(
                context.contentResolver,
                oldUri,
                newFileName
            )
        } catch (e: Exception) {
            Log.e(TAG, "SAF 重命名失败: ${song.uri}", e)
            logException(
                message = "Failed to rename SAF song",
                throwable = e,
                relatedId = song.uri
            )
            return null
        } ?: return null

        val parentPath = song.filePath.substringBeforeLast("/", missingDelimiterValue = "")
        val newPath = if (parentPath.isBlank()) newFileName else "$parentPath/$newFileName"

        val updatedSong = song.copy(
            uri = renamedUri.toString(),
            fileName = newFileName,
            filePath = newPath,
            fileLastModified = System.currentTimeMillis()
        ).withSortKeysUpdated()

        songDao.update(updatedSong)
        folderDao.refreshSongCount(updatedSong.folderId)

        return RenameSongResult(
            oldUri = song.uri,
            newUri = renamedUri.toString(),
            newFilePath = newPath,
            newFileName = newFileName
        )
    }

    private suspend fun renameFilePathSong(song: SongEntity, newFileName: String): RenameSongResult? {
        return try {
            val oldFile = File(song.filePath)
            if (!oldFile.exists()) {
                Log.e(TAG, "重命名失败: 文件不存在 ${song.filePath}")
                return null
            }
            val newFile = File(oldFile.parent, newFileName)
            if (newFile.exists()) {
                Log.e(TAG, "重命名失败: 目标文件已存在 ${newFile.absolutePath}")
                return null
            }
            if (oldFile.renameTo(newFile)) {
                val updatedSong = song.copy(
                    uri = newFile.toURI().toString(),
                    fileName = newFileName,
                    filePath = newFile.absolutePath,
                    fileLastModified = System.currentTimeMillis()
                ).withSortKeysUpdated()

                songDao.update(updatedSong)
                folderDao.refreshSongCount(updatedSong.folderId)

                RenameSongResult(
                    oldUri = song.uri,
                    newUri = updatedSong.uri,
                    newFilePath = newFile.absolutePath,
                    newFileName = newFileName
                )
            } else {
                Log.e(TAG, "重命名失败: ${song.fileName}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "重命名异常: ${song.fileName}", e)
            null
        }
    }

    private suspend fun logApp(
        level: AppLogLevel,
        message: String,
        detail: String? = null,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.log(
                level = level,
                type = AppLogType.APP,
                tag = TAG,
                message = message,
                detail = detail,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write app log", e)
        }
    }

    private fun StringBuilder.appendLimitedItems(
        title: String,
        items: List<String>
    ) {
        if (items.isEmpty()) return
        appendLine()
        appendLine("$title:")
        items.take(MAX_LOG_ITEMS).forEach { item ->
            appendLine(item)
        }
        val omitted = items.size - MAX_LOG_ITEMS
        if (omitted > 0) {
            appendLine("... $omitted more")
        }
    }

    private suspend fun logException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.logException(
                type = AppLogType.APP,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write exception log", e)
        }
    }

    private suspend fun logMetadataException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        try {
            appLogRepository.logException(
                type = AppLogType.METADATA,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write metadata exception log", e)
        }
    }
}
