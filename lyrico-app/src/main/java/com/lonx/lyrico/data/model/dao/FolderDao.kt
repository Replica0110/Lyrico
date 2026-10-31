package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lonx.lyrico.data.model.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {


    /** 所有文件夹 (UI 使用，包含已忽略和未忽略) */
    @Query("SELECT * FROM folders ORDER BY path")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersOnce(): List<FolderEntity>

    @Query("""
        SELECT * FROM folders
        WHERE addedBySaf = 1
          AND treeUri IS NOT NULL
          AND treeUri != ''
    """)
    suspend fun getSafFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE addedBySaf = 1")
    suspend fun getSafFoldersForPermissionCheck(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    /** upsert 并返回 id */
    @Transaction
    suspend fun upsertAndGetId(
        path: String,
        treeUri: String? = null,
        addedBySaf: Boolean = false,
        isIgnored: Boolean? = null,
        collapseToExistingParent: Boolean = true
    ): Long {
        val now = System.currentTimeMillis()
        val normalizedPath = normalizeFolderPath(path)
        val allFolders = getAllFoldersOnce()
        val existing = allFolders.firstOrNull {
            normalizeFolderPath(it.path) == normalizedPath
        }

        if (collapseToExistingParent) {
            val existingParent = allFolders.firstOrNull {
                it.id != existing?.id &&
                        isParentFolder(
                            parentPath = normalizeFolderPath(it.path),
                            childPath = normalizedPath
                        )
            }
            if (existingParent != null) {
                return existingParent.id
            }

            val childFolderIds = allFolders
                .filter {
                    it.id != existing?.id &&
                            isParentFolder(
                                parentPath = normalizedPath,
                                childPath = normalizeFolderPath(it.path)
                            )
                }
                .map { it.id }

            if (childFolderIds.isNotEmpty()) {
                deleteFoldersPermanently(childFolderIds)
            }
        }

        return if (existing == null) {
            insert(
                FolderEntity(
                    path = normalizedPath,
                    treeUri = treeUri,
                    addedBySaf = addedBySaf,
                    isIgnored = isIgnored ?: false,
                    lastScanned = now,
                    dbUpdateTime = now,
                    songCount = 0
                )
            )
        } else {
            val shouldUpdateSaf = addedBySaf && !existing.addedBySaf
            val shouldUpdateTreeUri = !treeUri.isNullOrBlank() && treeUri != existing.treeUri
            update(existing.copy(
                treeUri = if (shouldUpdateTreeUri) treeUri else existing.treeUri,
                addedBySaf = if (shouldUpdateSaf) true else existing.addedBySaf,
                isIgnored = isIgnored ?: existing.isIgnored,
                path = normalizedPath,
                lastScanned = now,
                dbUpdateTime = now
            ))
            existing.id
        }
    }

    private fun normalizeFolderPath(path: String): String {
        val normalized = path
            .replace('\\', '/')
            .trim()
            .trimEnd('/')

        return normalized.ifBlank { path.trim() }
    }

    private fun isParentFolder(parentPath: String, childPath: String): Boolean {
        if (parentPath.isBlank() || childPath.isBlank()) return false
        if (parentPath == childPath) return false
        return childPath.startsWith("$parentPath/")
    }

    @Transaction
    suspend fun upsertScannedFolderAndGetId(path: String, isIgnored: Boolean): Long {
        return upsertAndGetId(
            path = path,
            isIgnored = isIgnored,
            collapseToExistingParent = false
        )
    }

    @Transaction
    suspend fun upsertScannedFolderTreeAndGetLeafId(
        rootPath: String,
        folderPath: String,
        isIgnored: Boolean
    ): Long {
        val normalizedRootPath = normalizeFolderPath(rootPath)
        val normalizedFolderPath = normalizeFolderPath(folderPath)
        val relativePath = normalizedFolderPath
            .removePrefix(normalizedRootPath)
            .trimStart('/')
        val existingFolders = getAllFoldersOnce().associateBy { normalizeFolderPath(it.path) }

        var currentPath = normalizedRootPath
        var inheritedIgnored = existingFolders[currentPath]?.isIgnored ?: isIgnored
        var currentId = upsertScannedFolderAndGetId(currentPath, inheritedIgnored)

        relativePath
            .split('/')
            .filter { it.isNotBlank() }
            .forEach { segment ->
                currentPath = "$currentPath/$segment"
                inheritedIgnored = existingFolders[currentPath]?.isIgnored ?: inheritedIgnored
                currentId = upsertScannedFolderAndGetId(currentPath, inheritedIgnored)
            }

        return currentId
    }

    @Transaction
    suspend fun getScanRootFoldersFor(folderIds: Set<Long>): List<FolderEntity> {
        if (folderIds.isEmpty()) return emptyList()
        val folders = getAllFoldersOnce()
        val selected = folders.filter { it.id in folderIds }
        if (selected.isEmpty()) return emptyList()

        return folders
            .filter { it.addedBySaf && !it.treeUri.isNullOrBlank() }
            .filter { root ->
                selected.any { folder ->
                    val rootPath = normalizeFolderPath(root.path)
                    val selectedPath = normalizeFolderPath(folder.path)
                    root.id == folder.id || isParentFolder(rootPath, selectedPath)
                }
            }
    }

    @Transaction
    suspend fun getFolderTreeIds(folderId: Long): List<Long> {
        val folders = getAllFoldersOnce()
        val root = folders.firstOrNull { it.id == folderId } ?: return emptyList()
        val rootPath = normalizeFolderPath(root.path)
        return folders
            .filter { folder ->
                val path = normalizeFolderPath(folder.path)
                folder.id == root.id || isParentFolder(rootPath, path)
            }
            .map { it.id }
    }

    @Transaction
    suspend fun getFolderTreeIds(folderIds: Set<Long>): List<Long> {
        return folderIds.flatMap { getFolderTreeIds(it) }.distinct()
    }

    @Transaction
    suspend fun setIgnoredRecursively(folderId: Long, ignored: Boolean) {
        getFolderTreeIds(folderId).forEach { id ->
            setIgnored(id, ignored)
        }
    }

    @Transaction
    suspend fun deleteFolderTreePermanently(folderId: Long) {
        val folderIds = getFolderTreeIds(folderId)
        if (folderIds.isNotEmpty()) {
            deleteFoldersPermanently(folderIds)
        }
    }

    @Query("UPDATE folders SET isIgnored = :ignored, dbUpdateTime = :updateTime WHERE id = :folderId")
    suspend fun setIgnored(
        folderId: Long,
        ignored: Boolean,
        updateTime: Long = System.currentTimeMillis()
    )


    /**
     * 刷新所有文件夹的歌曲数量；忽略状态也按实际物理歌曲数统计。
     */
    @Query("""
        UPDATE folders 
        SET songCount = (SELECT COUNT(*) FROM songs WHERE songs.folderId = folders.id),
            dbUpdateTime = :updateTime
    """)
    suspend fun refreshAllSongCounts(updateTime: Long = System.currentTimeMillis())

    /** 刷新单个文件夹的歌曲数量。 */
    @Query("""
        UPDATE folders 
        SET songCount = (SELECT COUNT(*) FROM songs WHERE folderId = :folderId),
            dbUpdateTime = :updateTime
        WHERE id = :folderId
    """)
    suspend fun refreshSongCount(folderId: Long, updateTime: Long = System.currentTimeMillis())

    /**
     * 自动删除文件夹需同时满足：没有歌曲、未被手动忽略、不是通过 SAF 选择器添加的。
     */
    @Query("""
        DELETE FROM folders 
        WHERE songCount = 0 
          AND isIgnored = 0 
          AND addedBySaf = 0
          AND NOT EXISTS (
              SELECT 1
              FROM folders AS child
              WHERE child.path LIKE folders.path || '/%'
          )
    """)
    suspend fun deleteEmptyFolders()

    /**
     * 扫描完成后的综合后续处理
     */
    @Transaction
    suspend fun performPostScanCleanup() {
        refreshAllSongCounts()
        deleteEmptyFolders()
    }

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    suspend fun deleteFoldersPermanently(folderIds: List<Long>)

    /**
     * 彻底删除所有文件夹记录 (用于用户在 UI 上点击“彻底移除”按钮)
     */
    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Query("""
        SELECT COUNT(*) FROM folders 
    """)
    suspend fun getFoldersCount(): Int
}
