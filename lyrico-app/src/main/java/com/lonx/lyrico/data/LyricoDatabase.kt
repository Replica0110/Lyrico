package com.lonx.lyrico.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lonx.lyrico.data.migration.DeleteBatchMatchHistorySpec
import com.lonx.lyrico.data.model.dao.AppLogDao
import com.lonx.lyrico.data.model.dao.BatchTaskDao
import com.lonx.lyrico.data.model.dao.FolderDao
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.entity.AppLogEntity
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity

@Database(
    entities = [
        SongEntity::class, 
        FolderEntity::class,
        BatchTaskEntity::class,
        BatchTaskItemEntity::class,
        AppLogEntity::class
    ],
    version = 15,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11, spec = DeleteBatchMatchHistorySpec::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14)
    ]
)
abstract class LyricoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderDao(): FolderDao
    abstract fun batchTaskDao(): BatchTaskDao
    abstract fun appLogDao(): AppLogDao

    companion object {
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE songs SET
                        filePath = COALESCE(filePath, uri, ''),
                        fileName = COALESCE(fileName, ''),
                        durationMilliseconds = COALESCE(durationMilliseconds, 0),
                        bitrate = COALESCE(bitrate, 0),
                        sampleRate = COALESCE(sampleRate, 0),
                        channels = COALESCE(channels, 0),
                        fileLastModified = COALESCE(fileLastModified, 0),
                        fileAdded = COALESCE(fileAdded, 0),
                        dbUpdateTime = COALESCE(dbUpdateTime, 0),
                        titleGroupKey = COALESCE(NULLIF(titleGroupKey, ''), '#'),
                        titleSortKey = COALESCE(NULLIF(titleSortKey, ''), '#'),
                        artistGroupKey = COALESCE(NULLIF(artistGroupKey, ''), '#'),
                        artistSortKey = COALESCE(NULLIF(artistSortKey, ''), '#'),
                        uri = COALESCE(uri, '')
                    WHERE filePath IS NULL
                        OR fileName IS NULL
                        OR durationMilliseconds IS NULL
                        OR bitrate IS NULL
                        OR sampleRate IS NULL
                        OR channels IS NULL
                        OR fileLastModified IS NULL
                        OR fileAdded IS NULL
                        OR dbUpdateTime IS NULL
                        OR titleGroupKey IS NULL
                        OR titleGroupKey = ''
                        OR titleSortKey IS NULL
                        OR titleSortKey = ''
                        OR artistGroupKey IS NULL
                        OR artistGroupKey = ''
                        OR artistSortKey IS NULL
                        OR artistSortKey = ''
                        OR uri IS NULL
                    """.trimIndent()
                )
            }
        }
    }
}
