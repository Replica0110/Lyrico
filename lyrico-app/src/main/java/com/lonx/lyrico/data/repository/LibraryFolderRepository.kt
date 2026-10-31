package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.LibraryOperationLock
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.utils.UriUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Folder membership, visibility and the SAF grants owned by registered library roots. */
class LibraryFolderRepository(
    private val database: LyricoDatabase,
    private val indexes: LibraryIndexRepository,
    private val context: Context,
    private val settings: SettingsRepository,
    private val operationLock: LibraryOperationLock,
) {
    private val folders = database.folderDao()

    fun observeFolders(): Flow<List<FolderEntity>> = folders.getAllFolders()

    suspend fun register(path: String, treeUri: String): Long = operationLock.withLock {
        withContext(Dispatchers.IO) {
            val (id, previousGrants) = database.withTransaction {
                val previous = folders.getAllFoldersOnce().mapNotNull { it.treeUri }
                val registeredId = folders.upsertAndGetId(path, treeUri, addedBySaf = true)
                folders.setIgnored(registeredId, false)
                indexes.refreshAndPruneIndexes()
                registeredId to previous
            }
            releaseUnusedPermissions(previousGrants + treeUri)
            id
        }
    }

    suspend fun remove(folderId: Long) = operationLock.withLock {
        withContext(Dispatchers.IO) {
            val removed = database.withTransaction {
                val ids = folders.getFolderTreeIds(folderId).toSet()
                val targets = folders.getAllFoldersOnce().filter { it.id in ids }
                folders.deleteFolderTreePermanently(folderId)
                indexes.refreshAndPruneIndexes()
                targets
            }
            releaseUnusedPermissions(removed.mapNotNull { it.treeUri })
        }
    }

    suspend fun setIgnored(folderId: Long, ignored: Boolean) = operationLock.withLock {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                folders.setIgnoredRecursively(folderId, ignored)
                indexes.refreshAndPruneIndexes()
            }
        }
    }

    suspend fun clear(): Boolean = operationLock.withLock {
        withContext(Dispatchers.IO) {
            val removed = database.withTransaction {
                val targets = folders.getAllFoldersOnce()
                folders.clearAllFolders()
                // Clear orphaned rows left by older app versions as well as live song records.
                database.songDao().clearLyricFts()
                database.songCustomTagKeyDao().clearAll()
                indexes.refreshAndPruneIndexes()
                targets
            }
            releaseUnusedPermissions(removed.mapNotNull { it.treeUri })
            true
        }
    }

    private suspend fun releaseUnusedPermissions(candidates: List<String>) {
        try {
            val retained = folders.getAllFoldersOnce().mapNotNull { it.treeUri }.toSet() +
                listOfNotNull(settings.artistPosterFolder.first())
            candidates.distinct().filterNot { it in retained }.forEach { uri ->
                if (!UriUtils.releasePersistedPermission(context.contentResolver, uri)) {
                    AppLog.record(AppLogType.APP, "LibraryFolders", "Library grant release incomplete",
                        "treeUri=$uri", uri, AppLogLevel.WARNING)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The database change has committed; retain uncertain grants and report cleanup separately.
            AppLog.record(AppLogType.APP, "LibraryFolders", "Library grant cleanup failed",
                "candidateCount=${candidates.size}", level = AppLogLevel.WARNING, error = error)
        }
    }
}
