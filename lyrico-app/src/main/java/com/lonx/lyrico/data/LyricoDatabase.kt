package com.lonx.lyrico.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.lonx.lyrico.data.model.dao.FolderDao
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.model.dao.BatchMatchHistoryDao

@Database(
    entities = [
        SongEntity::class, 
        FolderEntity::class,
        BatchMatchHistory::class,
        BatchMatchRecordEntity::class
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
abstract class LyricoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderDao(): FolderDao
    abstract fun batchMatchHistoryDao(): BatchMatchHistoryDao
}
