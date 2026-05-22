package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import kotlinx.coroutines.flow.Flow

interface SourcePluginRepository {
    fun observePlugins(): Flow<List<SourcePluginEntity>>
    fun observeEnabledPlugins(): Flow<List<SourcePluginEntity>>
    suspend fun getPlugins(): List<SourcePluginEntity>
    suspend fun getPlugin(id: String): SourcePluginEntity?
    suspend fun upsertPlugin(plugin: SourcePluginEntity)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun updateSortOrder(id: String, sortOrder: Int)
    suspend fun uninstallPlugin(id: String)
}
