package com.lonx.lyrico.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfigStore
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class PluginFieldProcessConfigRepositoryImpl(
    private val context: Context,
    private val json: Json
) : PluginFieldProcessConfigRepository {
    override val configsFlow: Flow<Map<String, PluginFieldProcessConfig>>
        get() = context.settingsDataStore.data.map { preferences ->
            decodeStore(preferences[PLUGIN_FIELD_PROCESS_CONFIG_STORE]).configs
        }

    override suspend fun getConfig(pluginId: String): PluginFieldProcessConfig {
        val stablePluginId = pluginId.toStableSourceId()
        return configsFlow.first()[stablePluginId] ?: defaultPluginFieldProcessConfig(stablePluginId)
    }

    override suspend fun updateConfig(config: PluginFieldProcessConfig) {
        val stablePluginId = config.pluginId.toStableSourceId()
        context.settingsDataStore.edit { preferences ->
            val currentStore = decodeStore(preferences[PLUGIN_FIELD_PROCESS_CONFIG_STORE])
            val normalizedConfig = config.copy(pluginId = stablePluginId)
            preferences[PLUGIN_FIELD_PROCESS_CONFIG_STORE] = json.encodeToString(
                currentStore.copy(
                    configs = currentStore.configs + (stablePluginId to normalizedConfig)
                )
            )
        }
    }

    override suspend fun removeConfig(pluginId: String) {
        val stablePluginId = pluginId.toStableSourceId()
        context.settingsDataStore.edit { preferences ->
            val currentStore = decodeStore(preferences[PLUGIN_FIELD_PROCESS_CONFIG_STORE])
            preferences[PLUGIN_FIELD_PROCESS_CONFIG_STORE] = json.encodeToString(
                currentStore.copy(configs = currentStore.configs - stablePluginId)
            )
        }
    }

    private fun decodeStore(raw: String?): PluginFieldProcessConfigStore {
        return raw
            ?.takeIf { it.isNotBlank() }
            ?.let { value ->
                runCatching {
                    json.decodeFromString<PluginFieldProcessConfigStore>(value)
                }.getOrNull()
            }
            ?: PluginFieldProcessConfigStore()
    }

    private fun String.toStableSourceId(): String {
        return trim()
    }

    private companion object {
        val PLUGIN_FIELD_PROCESS_CONFIG_STORE = stringPreferencesKey("plugin_field_process_config_store")
    }
}
