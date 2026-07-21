package com.lonx.lyrico.data.song.scan

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.mapper.SongMetadataMapper
import com.lonx.lyrico.data.song.search.LyricFtsIndexer
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.utils.MediaScanner
import com.lonx.lyrico.utils.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryScanRepositoryImpl(
    private val context: Context,
    private val database: LyricoDatabase,
    private val mediaScanner: MediaScanner,
    private val settingsRepository: SettingsRepository,
    private val audioTagRepository: AudioTagRepository,
    private val songMetadataMapper: SongMetadataMapper,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val appLogRepository: AppLogRepository
) : LibraryScanRepository {

    private val songDao = database.songDao()
    private val folderDao = database.folderDao()
    private val syncMutex = Mutex()

    override suspend fun synchronize(
        request: LibraryScanRequest,
        onProgress: suspend (LibraryScanProgress) -> Unit
    ): LibraryScanResult {
        return syncMutex.withLock {
            withContext(Dispatchers.IO) {
                synchronizeLocked(request, onProgress)
            }
        }
    }

    private suspend fun synchronizeLocked(
        request: LibraryScanRequest,
        onProgress: suspend (LibraryScanProgress) -> Unit
    ): LibraryScanResult {
        try {
            Log.d(TAG, "Start library sync: fullRescan=${request.fullRescan}")
            onProgress(LibraryScanProgress(stage = LibraryScanStage.LISTING_FILES))

            val removedSafFolders = if (request.removeMissingSafFolders) {
                removeSafFoldersWithoutPermission()
            } else {
                0
            }
            val dbSyncInfos = songDao.getAllSyncInfo()
            val dbSongMap = dbSyncInfos.associateBy { it.uri }

            val deviceUris = mutableSetOf<String>()
            val impactedFolderIds = mutableSetOf<Long>()
            val failures = mutableListOf<LibraryScanFailure>()
            val songsToUpsert = mutableListOf<ScannedSongMetadata>()
            val minDuration = 60_000L

            val safFolders = if (request.folderIds == null) {
                folderDao.getSafFolders()
            } else {
                folderDao.getScanRootFoldersFor(request.folderIds)
            }
            val safFolderById = safFolders.associateBy { it.id }
            val safScanResult = mediaScanner.querySongsFromSafFolders(safFolders)
            val deviceSongs = safScanResult.songs

            onProgress(
                LibraryScanProgress(
                    stage = LibraryScanStage.READING_METADATA,
                    current = 0,
                    total = deviceSongs.size
                )
            )

            for ((index, scannedSong) in deviceSongs.withIndex()) {
                val deviceSong = scannedSong.songFile
                try {
                    val deviceUriString = deviceSong.uri.toString()
                    val dbInfo = dbSongMap[deviceUriString]
                    val needsUpdate = request.fullRescan ||
                        dbInfo == null ||
                        dbInfo.fileLastModified != deviceSong.lastModified ||
                        dbInfo.fileSize != deviceSong.fileSize ||
                        dbInfo.filePath != deviceSong.filePath
                    val knownDuration = when {
                        deviceSong.duration > 0L -> deviceSong.duration
                        !needsUpdate -> dbInfo.durationMilliseconds.toLong()
                        else -> 0L
                    }

                    if (
                        request.ignoreShortAudio &&
                        knownDuration > 0L &&
                        knownDuration <= minDuration
                    ) {
                        continue
                    }

                    if (needsUpdate) {
                        val rootFolder = safFolderById[scannedSong.rootFolderId]
                        val folderId = folderDao.upsertScannedFolderTreeAndGetLeafId(
                            rootPath = rootFolder?.path ?: scannedSong.folderPath,
                            folderPath = scannedSong.folderPath,
                            isIgnored = rootFolder?.isIgnored ?: false
                        )
                        impactedFolderIds.add(folderId)

                        val metadata = extractSongMetadata(
                            songFile = deviceSong,
                            folderId = folderId,
                            existingId = dbInfo?.id ?: 0L,
                            source = "SAF"
                        )

                        if (
                            request.ignoreShortAudio &&
                            metadata != null &&
                            metadata.entity.durationMilliseconds > 0 &&
                            metadata.entity.durationMilliseconds <= minDuration
                        ) {
                            continue
                        }

                        metadata?.let(songsToUpsert::add)
                    }

                    deviceUris.add(deviceUriString)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process song: ${deviceSong.fileName}", e)
                    failures.add(
                        LibraryScanFailure(
                            uri = deviceSong.uri.toString(),
                            fileName = deviceSong.fileName,
                            stage = LibraryScanFailureStage.ReadingMetadata,
                            message = e.message ?: e::class.java.simpleName,
                            throwable = e
                        )
                    )
                } finally {
                    onProgress(
                        LibraryScanProgress(
                            stage = LibraryScanStage.READING_METADATA,
                            current = index + 1,
                            total = deviceSongs.size,
                            currentFile = deviceSong.fileName
                        )
                    )
                }
            }

            val missingSafFolderIds = safScanResult.missingFolderIds
            val successfulScannedFolderIds = folderDao
                .getFolderTreeIds(safScanResult.successfulFolderIds)
                .toSet()

            val deletedUris = dbSyncInfos
                .filter { info ->
                    info.source == "SAF" &&
                        info.folderId in successfulScannedFolderIds &&
                        info.uri !in deviceUris
                }
                .map { it.uri }
                .toSet()

            val missingFolderSongUris = dbSyncInfos
                .filter { info -> info.source == "SAF" && info.folderId in missingSafFolderIds }
                .map { it.uri }
                .toSet()

            val allDeletedUris = deletedUris + missingFolderSongUris
            if (allDeletedUris.isNotEmpty()) {
                impactedFolderIds.addAll(
                    dbSyncInfos
                        .filter { it.uri in allDeletedUris }
                        .map { it.folderId }
                )
            }
            impactedFolderIds.addAll(missingSafFolderIds)

            val databaseChanges = songsToUpsert.size + allDeletedUris.size + missingSafFolderIds.size
            onProgress(
                LibraryScanProgress(
                    stage = LibraryScanStage.WRITING_DATABASE,
                    current = 0,
                    total = databaseChanges
                )
            )

            database.withTransaction {
                songsToUpsert.chunked(BATCH_SIZE).forEach { chunk ->
                    val songs = chunk.map { it.entity }
                    songDao.upsertAll(songs)
                    LyricFtsIndexer.replaceSongs(songDao, songs)
                    chunk.forEach { metadata ->
                        database.songCustomTagKeyDao().replaceForSong(
                            songUri = metadata.entity.uri,
                            keys = metadata.customFields.mapNotNull { field ->
                                field.key.trim().takeIf { it.isNotBlank() }?.uppercase()
                            }
                        )
                    }
                }

                allDeletedUris.chunked(BATCH_SIZE).forEach { chunk ->
                    songDao.deleteByUris(chunk.toList())
                    songDao.deleteLyricFtsByUris(chunk.toList())
                    database.songCustomTagKeyDao().deleteForSongs(chunk.toList())
                }

                missingSafFolderIds.forEach { folderId ->
                    folderDao.deleteFolderTreePermanently(folderId)
                }

                impactedFolderIds
                    .filterNot { it in missingSafFolderIds }
                    .forEach { folderId -> folderDao.refreshSongCount(folderId) }

                folderDao.performPostScanCleanup()
            }

            if (songsToUpsert.isNotEmpty()) {
                val indexedSongs = songDao.getSongsByUris(songsToUpsert.map { it.entity.uri })
                libraryIndexRepository.reindexSongs(indexedSongs)
            }
            if (allDeletedUris.isNotEmpty() || missingSafFolderIds.isNotEmpty()) {
                libraryIndexRepository.refreshAndPruneIndexes()
            }

            onProgress(
                LibraryScanProgress(
                    stage = LibraryScanStage.WRITING_DATABASE,
                    current = databaseChanges,
                    total = databaseChanges
                )
            )

            // 去重步骤：基于 title+artist 检测并删除重复歌曲
            onProgress(LibraryScanProgress(stage = LibraryScanStage.DEDUP))
            val dedupRemoved = performDedup()
            if (dedupRemoved > 0) {
                Log.d(TAG, "Dedup removed $dedupRemoved duplicate songs")
            }

            settingsRepository.saveLastScanTime(System.currentTimeMillis())
            val result = LibraryScanResult(
                scanned = deviceSongs.size,
                inserted = songsToUpsert.count { it.isInsert },
                updated = songsToUpsert.count { !it.isInsert },
                deleted = allDeletedUris.size,
                skipped = deviceSongs.size - songsToUpsert.size,
                failures = failures
            )
            logResult(request, result, removedSafFolders, impactedFolderIds.size)
            onProgress(LibraryScanProgress(stage = LibraryScanStage.FINISHED))
            return result
        } catch (e: Exception) {
            logException("Library synchronization failed", e)
            throw e
        }
    }

    private suspend fun removeSafFoldersWithoutPermission(): Int {
        val foldersWithoutPermission = folderDao.getSafFoldersForPermissionCheck()
            .filterNot { folder ->
                UriUtils.hasPersistedReadPermission(context, folder.treeUri)
            }
        val folderIds = foldersWithoutPermission.map { it.id }
        if (folderIds.isEmpty()) return 0
        database.withTransaction {
            folderIds.forEach { folderId ->
                folderDao.deleteFolderTreePermanently(folderId)
            }
        }
        return folderIds.size
    }

    private suspend fun extractSongMetadata(
        songFile: SongFile,
        folderId: Long,
        existingId: Long,
        source: String
    ): ScannedSongMetadata? {
        val audioData = audioTagRepository.read(
            uri = songFile.uri.toString(),
            options = AudioTagReadOptions(
                multiValueSeparator = settingsRepository.separator.first()
            )
        )
        val entity = songMetadataMapper.fromScannedFile(
            file = songFile,
            tag = audioData,
            folderId = folderId,
            existingId = existingId,
            source = source
        )
        return ScannedSongMetadata(
            entity = entity,
            customFields = audioData.customFields,
            isInsert = existingId == 0L
        )
    }

    private suspend fun logResult(
        request: LibraryScanRequest,
        result: LibraryScanResult,
        removedSafFolders: Int,
        foldersUpdated: Int
    ) {
        try {
            appLogRepository.log(
                level = if (result.failures.isEmpty()) AppLogLevel.INFO else AppLogLevel.WARNING,
                type = AppLogType.APP,
                tag = TAG,
                message = "Library synchronization finished",
                detail = buildString {
                    appendLine("fullRescan=${request.fullRescan}")
                    appendLine("scanned=${result.scanned}")
                    appendLine("inserted=${result.inserted}")
                    appendLine("updated=${result.updated}")
                    appendLine("deleted=${result.deleted}")
                    appendLine("removedSafFolders=$removedSafFolders")
                    appendLine("foldersUpdated=$foldersUpdated")
                    appendLine("failures=${result.failures.size}")
                    appendLine("ignoreShortAudio=${request.ignoreShortAudio}")
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write scan log", e)
        }
    }

    private suspend fun logException(message: String, throwable: Throwable) {
        try {
            appLogRepository.logException(
                type = AppLogType.APP,
                tag = TAG,
                message = message,
                throwable = throwable
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write scan exception log", e)
        }
    }

    /**
     * 执行去重：基于 (title, artist) 组合分组，保留每组中 bitrate 最高的歌曲，删除其余。
     * 仅在非忽略文件夹中执行，title 或 artist 为空的歌曲不参与去重。
     * @return 被删除的重复歌曲数量
     */
    private suspend fun performDedup(): Int {
        val allSongs = songDao.getSongsForDedup()
        // 按 (title, artist) 不区分大小写分组
        val groups = allSongs.groupBy {
            Pair(
                it.title?.trim()?.lowercase() ?: "",
                it.artist?.trim()?.lowercase() ?: ""
            )
        }.filter { it.value.size > 1 }

        // 每组已按 bitrate DESC, fileSize DESC 排序，第一条保留，其余删除
        val toDelete = groups.flatMap { (_, songs) ->
            songs.drop(1).map { it.id }
        }
        if (toDelete.isEmpty()) return 0

        val urisToDelete = allSongs.filter { it.id in toDelete }.map { it.uri }
        database.withTransaction {
            songDao.deleteByIds(toDelete)
            songDao.deleteLyricFtsByUris(urisToDelete)
            database.songCustomTagKeyDao().deleteForSongs(urisToDelete)
        }
        return toDelete.size
    }

    private data class ScannedSongMetadata(
        val entity: SongEntity,
        val customFields: List<CustomTagField>,
        val isInsert: Boolean
    )

    private companion object {
        const val TAG = "LibraryScanRepository"
        const val BATCH_SIZE = 50
    }
}
