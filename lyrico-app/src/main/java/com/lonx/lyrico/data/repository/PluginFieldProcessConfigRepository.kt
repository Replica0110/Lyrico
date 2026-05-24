package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import kotlinx.coroutines.flow.Flow

interface PluginFieldProcessConfigRepository {
    val configsFlow: Flow<Map<String, PluginFieldProcessConfig>>

    suspend fun getConfig(pluginId: String): PluginFieldProcessConfig

    suspend fun updateConfig(config: PluginFieldProcessConfig)

    suspend fun removeConfig(pluginId: String)
}
