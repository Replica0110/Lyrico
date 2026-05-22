package com.lonx.lyrico.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lonx.lyrico.data.model.dao.AppLogDao
import com.lonx.lyrico.data.model.dao.BatchTaskDao
import com.lonx.lyrico.data.model.dao.FolderDao
import com.lonx.lyrico.data.model.dao.LibraryIndexDao
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrico.data.model.dao.SourcePluginDao
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.AlbumSongCrossRef
import com.lonx.lyrico.data.model.entity.AppLogEntity
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.ArtistSongCrossRef
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.SourcePluginEntity

@Database(
    entities = [
        SongEntity::class, 
        FolderEntity::class,
        BatchTaskEntity::class,
        BatchTaskItemEntity::class,
        AppLogEntity::class,
        ArtistEntity::class,
        ArtistSongCrossRef::class,
        AlbumEntity::class,
        AlbumSongCrossRef::class,
        SourcePluginEntity::class
    ],
    version = 13,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9)
    ]
)
abstract class LyricoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun folderDao(): FolderDao
    abstract fun batchTaskDao(): BatchTaskDao
    abstract fun appLogDao(): AppLogDao
    abstract fun libraryIndexDao(): LibraryIndexDao
    abstract fun sourcePluginDao(): SourcePluginDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGainTrackGain TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGainTrackPeak TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGainAlbumGain TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGainAlbumPeak TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN replayGainReferenceLoudness TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE songs ADD COLUMN source TEXT NOT NULL DEFAULT 'MEDIA_STORE'")
                db.execSQL("ALTER TABLE songs ADD COLUMN language TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE folders ADD COLUMN treeUri TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_addedBySaf ON folders(addedBySaf)")

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
                        uri = COALESCE(uri, ''),
                        source = COALESCE(NULLIF(source, ''), 'MEDIA_STORE')
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
                        OR source IS NULL
                        OR source = ''
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS batch_match_records")
                db.execSQL("DROP TABLE IF EXISTS batch_match_history")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS batch_tasks (
                        taskId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        total INTEGER NOT NULL,
                        current INTEGER NOT NULL,
                        successCount INTEGER NOT NULL,
                        failureCount INTEGER NOT NULL,
                        skippedCount INTEGER NOT NULL,
                        currentFile TEXT,
                        configJson TEXT,
                        workId TEXT,
                        startedAt INTEGER,
                        finishedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        errorMessage TEXT,
                        PRIMARY KEY(taskId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_tasks_status ON batch_tasks(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS batch_task_items (
                        itemId TEXT NOT NULL,
                        taskId TEXT NOT NULL,
                        mediaId INTEGER NOT NULL,
                        songUri TEXT NOT NULL,
                        filePath TEXT,
                        fileName TEXT NOT NULL,
                        status TEXT NOT NULL,
                        progress REAL,
                        resultJson TEXT,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(itemId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_task_items_taskId ON batch_task_items(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_batch_task_items_status ON batch_task_items(status)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        level TEXT NOT NULL,
                        type TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        message TEXT NOT NULL,
                        detail TEXT,
                        relatedId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_logs_createdAt ON app_logs(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_logs_level ON app_logs(level)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_logs_type ON app_logs(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_logs_relatedId ON app_logs(relatedId)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artists (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        groupKey TEXT NOT NULL,
                        sortKey TEXT NOT NULL,
                        songCount INTEGER NOT NULL,
                        albumCount INTEGER NOT NULL,
                        coverSongUri TEXT,
                        coverSongLastModified INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_artists_normalizedName ON artists(normalizedName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artists_groupKey_sortKey ON artists(groupKey, sortKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS artist_song (
                        artistId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        PRIMARY KEY(artistId, songId),
                        FOREIGN KEY(artistId) REFERENCES artists(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(songId) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artist_song_artistId ON artist_song(artistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_artist_song_songId ON artist_song(songId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS albums (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        albumArtist TEXT,
                        normalizedKey TEXT NOT NULL,
                        groupKey TEXT NOT NULL,
                        sortKey TEXT NOT NULL,
                        songCount INTEGER NOT NULL,
                        coverSongUri TEXT,
                        coverSongLastModified INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_albums_normalizedKey ON albums(normalizedKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_albums_groupKey_sortKey ON albums(groupKey, sortKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS album_song (
                        albumId INTEGER NOT NULL,
                        songId INTEGER NOT NULL,
                        PRIMARY KEY(albumId, songId),
                        FOREIGN KEY(albumId) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(songId) REFERENCES songs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_album_song_albumId ON album_song(albumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_album_song_songId ON album_song(songId)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS source_plugins (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        versionCode INTEGER NOT NULL,
                        versionName TEXT NOT NULL,
                        author TEXT NOT NULL,
                        description TEXT NOT NULL,
                        apiVersion INTEGER NOT NULL,
                        pluginDir TEXT NOT NULL,
                        entryFile TEXT NOT NULL,
                        iconPath TEXT,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        installedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE source_plugins ADD COLUMN includeDirsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
