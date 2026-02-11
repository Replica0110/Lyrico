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

    /** 所有未忽略的文件夹路径 (扫描器用于构建显示列表) */
    @Query("SELECT path FROM folders WHERE isIgnored = 0")
    suspend fun getNotIgnoredFolderPaths(): List<String>

    /** 所有已忽略的文件夹路径 (可用于扫描时标记，但根据新逻辑建议扫描全量) */
    @Query("SELECT path FROM folders WHERE isIgnored = 1")
    suspend fun getIgnoredFolderPaths(): List<String>

    /** 根据路径查找 */
    @Query("SELECT * FROM folders WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): FolderEntity?

    /** 文件夹是否被忽略 */
    @Query("SELECT isIgnored FROM folders WHERE path = :path LIMIT 1")
    suspend fun isIgnored(path: String): Boolean?


    @Insert(onConflict = OnConflictStrategy.Companion.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    /** upsert 并返回 id */
    @Transaction
    suspend fun upsertAndGetId(
        path: String,
        addedBySaf: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val existing = findByPath(path)

        return if (existing == null) {
            insert(
                FolderEntity(
                    path = path,
                    addedBySaf = addedBySaf,
                    lastScanned = now,
                    dbUpdateTime = now,
                    songCount = 0
                )
            )
        } else {
            // 如果原来不是 SAF 添加的，现在是，则更新状态
            val shouldUpdateSaf = addedBySaf && !existing.addedBySaf
            update(existing.copy(
                addedBySaf = if (shouldUpdateSaf) true else existing.addedBySaf,
                lastScanned = now,
                dbUpdateTime = now
            ))
            existing.id
        }
    }

    @Query("UPDATE folders SET isIgnored = :ignored, dbUpdateTime = :updateTime WHERE id = :folderId")
    suspend fun setIgnored(
        folderId: Long,
        ignored: Boolean,
        updateTime: Long = System.currentTimeMillis()
    )


    /**
     * 刷新所有文件夹的歌曲数量
     * 无论文件夹是否被忽略，都要反映其实际物理包含的歌曲数
     */
    @Query("""
        UPDATE folders 
        SET songCount = (SELECT COUNT(*) FROM songs WHERE songs.folderId = folders.id),
            dbUpdateTime = :updateTime
    """)
    suspend fun refreshAllSongCounts(updateTime: Long = System.currentTimeMillis())

    /**
     * 刷新单个文件夹数量
     */
    @Query("""
        UPDATE folders 
        SET songCount = (SELECT COUNT(*) FROM songs WHERE folderId = :folderId),
            dbUpdateTime = :updateTime
        WHERE id = :folderId
    """)
    suspend fun refreshSongCount(folderId: Long, updateTime: Long = System.currentTimeMillis())

    /**
     * 只有同时满足以下三个条件的文件夹才会被自动删除：
     * 1. 里面没歌 (songCount == 0)
     * 2. 用户没有手动忽略它 (isIgnored == 0)
     * 3. 不是用户手动通过选择器添加的 (addedBySaf == 0)
     */
    @Query("""
        DELETE FROM folders 
        WHERE songCount = 0 
          AND isIgnored = 0 
          AND addedBySaf = 0
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

    /**
     * 彻底删除文件夹记录 (用于用户在 UI 上点击“彻底移除”按钮)
     */
    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderPermanently(folderId: Long)
}