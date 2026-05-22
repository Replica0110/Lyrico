package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourcePluginDao {
    @Query("SELECT * FROM source_plugins ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<SourcePluginEntity>>

    @Query("SELECT * FROM source_plugins WHERE enabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeEnabled(): Flow<List<SourcePluginEntity>>

    @Query("SELECT * FROM source_plugins WHERE id = :id")
    suspend fun getById(id: String): SourcePluginEntity?

    @Query("SELECT * FROM source_plugins ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<SourcePluginEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plugin: SourcePluginEntity)

    @Query("UPDATE source_plugins SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE source_plugins SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("DELETE FROM source_plugins WHERE id = :id")
    suspend fun delete(id: String)
}
