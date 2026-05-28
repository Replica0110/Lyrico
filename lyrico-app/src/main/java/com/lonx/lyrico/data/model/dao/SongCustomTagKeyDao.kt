package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lonx.lyrico.data.model.entity.SongCustomTagKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongCustomTagKeyDao {

    @Query(
        """
        SELECT `key`, COUNT(*) AS songCount
        FROM song_custom_tag_keys
        GROUP BY `key`
        ORDER BY songCount DESC, `key` COLLATE NOCASE ASC
        """
    )
    fun observeCustomTagKeyCounts(): Flow<List<CustomTagKeyCount>>

    @Query(
        """
        SELECT songUri
        FROM song_custom_tag_keys
        WHERE `key` = :key
        ORDER BY songUri ASC
        """
    )
    suspend fun getSongUrisByKey(key: String): List<String>

    @Query("DELETE FROM song_custom_tag_keys WHERE songUri = :songUri")
    suspend fun deleteForSong(songUri: String)

    @Query("DELETE FROM song_custom_tag_keys WHERE songUri IN (:songUris)")
    suspend fun deleteForSongs(songUris: List<String>)

    @Query("DELETE FROM song_custom_tag_keys")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SongCustomTagKeyEntity>)

    @Transaction
    suspend fun replaceForSong(songUri: String, keys: List<String>) {
        deleteForSong(songUri)

        val items = keys
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { key ->
                SongCustomTagKeyEntity(
                    songUri = songUri,
                    key = key,
                )
            }

        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
