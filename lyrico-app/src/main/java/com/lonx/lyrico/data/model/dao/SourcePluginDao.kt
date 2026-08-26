package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourcePluginDao {
    @Query("SELECT * FROM source_plugins ORDER BY sortOrder ASC, COALESCE(NULLIF(customName, ''), name) ASC")
    fun observeAll(): Flow<List<SourcePluginEntity>>

    @Query("SELECT * FROM source_plugins WHERE id = :id")
    suspend fun getById(id: String): SourcePluginEntity?

    @Query("SELECT * FROM source_plugins ORDER BY sortOrder ASC, COALESCE(NULLIF(customName, ''), name) ASC")
    suspend fun getAll(): List<SourcePluginEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plugin: SourcePluginEntity)

    @Query("UPDATE source_plugins SET metadataEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setMetadataEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE source_plugins SET lyricsEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setLyricsEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE source_plugins SET coverEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCoverEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE source_plugins SET metadataSortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMetadataSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE source_plugins SET lyricsSortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLyricsSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE source_plugins SET coverSortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCoverSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Transaction
    suspend fun updateMetadataSortOrders(ids: List<String>, updatedAt: Long) {
        ids.forEachIndexed { index, id ->
            updateMetadataSortOrder(id, index, updatedAt)
        }
    }

    @Transaction
    suspend fun updateLyricsSortOrders(ids: List<String>, updatedAt: Long) {
        ids.forEachIndexed { index, id ->
            updateLyricsSortOrder(id, index, updatedAt)
        }
    }

    @Transaction
    suspend fun updateCoverSortOrders(ids: List<String>, updatedAt: Long) {
        ids.forEachIndexed { index, id ->
            updateCoverSortOrder(id, index, updatedAt)
        }
    }

    @Query("UPDATE source_plugins SET customName = :customName, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCustomName(id: String, customName: String?, updatedAt: Long)

    @Query(
        "UPDATE source_plugins SET apiVersion = :apiVersion, " +
            "minHostApiVersion = :minHostApiVersion, capabilitiesJson = :capabilitiesJson, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateManifestContract(
        id: String,
        apiVersion: Int,
        minHostApiVersion: Int,
        capabilitiesJson: String,
        updatedAt: Long
    )

    @Query("DELETE FROM source_plugins WHERE id = :id")
    suspend fun delete(id: String)
}
