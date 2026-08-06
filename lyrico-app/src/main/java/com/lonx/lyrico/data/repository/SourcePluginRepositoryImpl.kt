package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.dao.SourcePluginDao
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import kotlinx.coroutines.flow.Flow

class SourcePluginRepositoryImpl(
    private val dao: SourcePluginDao
) : SourcePluginRepository {
    override fun observePlugins(): Flow<List<SourcePluginEntity>> {
        return dao.observeAll()
    }

    override suspend fun getPlugins(): List<SourcePluginEntity> {
        return dao.getAll()
    }

    override suspend fun getPlugin(id: String): SourcePluginEntity? {
        return dao.getById(id)
    }

    override suspend fun upsertPlugin(plugin: SourcePluginEntity) {
        dao.upsert(plugin)
    }

    override suspend fun setEnabled(
        id: String,
        sourceType: PluginSourceType,
        enabled: Boolean
    ) {
        val updatedAt = System.currentTimeMillis()
        when (sourceType) {
            PluginSourceType.AGGREGATED -> dao.setEnabled(id, enabled, updatedAt)
            PluginSourceType.METADATA -> dao.setMetadataEnabled(id, enabled, updatedAt)
            PluginSourceType.LYRICS -> dao.setLyricsEnabled(id, enabled, updatedAt)
            PluginSourceType.COVER -> dao.setCoverEnabled(id, enabled, updatedAt)
        }
    }

    override suspend fun updateSortOrders(
        ids: List<String>,
        sourceType: PluginSourceType
    ) {
        val updatedAt = System.currentTimeMillis()
        when (sourceType) {
            PluginSourceType.AGGREGATED -> dao.updateAggregatedSortOrders(ids, updatedAt)
            PluginSourceType.METADATA -> dao.updateMetadataSortOrders(ids, updatedAt)
            PluginSourceType.LYRICS -> dao.updateLyricsSortOrders(ids, updatedAt)
            PluginSourceType.COVER -> dao.updateCoverSortOrders(ids, updatedAt)
        }
    }

    override suspend fun updateCustomName(id: String, customName: String?) {
        dao.updateCustomName(
            id = id,
            customName = customName?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateManifestContract(
        id: String,
        apiVersion: Int,
        minHostApiVersion: Int,
        capabilitiesJson: String
    ) {
        dao.updateManifestContract(
            id = id,
            apiVersion = apiVersion,
            minHostApiVersion = minHostApiVersion,
            capabilitiesJson = capabilitiesJson,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun uninstallPlugin(id: String) {
        dao.delete(id)
    }
}
