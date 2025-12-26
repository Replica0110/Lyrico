package com.lonx.lyrico.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.SongDao

@Database(entities = [SongEntity::class], version = 2, exportSchema = false)
abstract class LyricoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: LyricoDatabase? = null

        fun getInstance(context: Context): LyricoDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    LyricoDatabase::class.java,
                    "lyrico_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build().also { INSTANCE = it }
            }
        }

        fun destroy() {
            INSTANCE = null
        }
    }
}
