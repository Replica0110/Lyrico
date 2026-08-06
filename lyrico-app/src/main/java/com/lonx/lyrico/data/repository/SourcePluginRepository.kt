package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import kotlinx.coroutines.flow.Flow

interface SourcePluginRepository {
    fun observePlugins(): Flow<List<SourcePluginEntity>>
    suspend fun getPlugins(): List<SourcePluginEntity>
    suspend fun getPlugin(id: String): SourcePluginEntity?
    suspend fun upsertPlugin(plugin: SourcePluginEntity)
    suspend fun setEnabled(id: String, sourceType: PluginSourceType, enabled: Boolean)
    suspend fun updateSortOrders(ids: List<String>, sourceType: PluginSourceType)
    suspend fun updateCustomName(id: String, customName: String?)
    suspend fun updateManifestContract(
        id: String,
        apiVersion: Int,
        minHostApiVersion: Int,
        capabilitiesJson: String
    )
    suspend fun uninstallPlugin(id: String)
}
