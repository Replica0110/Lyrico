package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.dao.SourcePluginDao
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import kotlinx.coroutines.flow.Flow

class SourcePluginRepositoryImpl(
    private val dao: SourcePluginDao
) : SourcePluginRepository {
    override fun observePlugins(): Flow<List<SourcePluginEntity>> {
        return dao.observeAll()
    }

    override fun observeEnabledPlugins(): Flow<List<SourcePluginEntity>> {
        return dao.observeEnabled()
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

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id = id, enabled = enabled, updatedAt = System.currentTimeMillis())
    }

    override suspend fun updateSortOrder(id: String, sortOrder: Int) {
        dao.updateSortOrder(id = id, sortOrder = sortOrder, updatedAt = System.currentTimeMillis())
    }

    override suspend fun uninstallPlugin(id: String) {
        dao.delete(id)
    }
}
