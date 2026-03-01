package com.lonx.lyrico.viewmodel

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import android.util.Log
import com.lonx.lyrico.plugin.ConfigField
import com.lonx.lyrico.plugin.FieldType
import com.lonx.lyrico.plugin.PluginHandle
import com.lonx.lyrico.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PluginUiModel(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val isSearchSource: Boolean = false,
    val isLyricSource: Boolean = false
)
data class ConfigUiState(
    val pluginId: String,
    val pluginName: String,
    val schema: List<ConfigField> = emptyList(),
    val currentSettings: Bundle = Bundle(),
    val isLoading: Boolean = false,
    val error: String? = null
)
class PluginListViewModel(
    private val pluginManager: PluginManager
) : ViewModel() {
    private val _formState = MutableStateFlow<Map<String, Any>>(emptyMap())
    val formState: StateFlow<Map<String, Any>> = _formState.asStateFlow()
    /**
     * 插件列表（基于 PluginHandle）
     */
    val pluginList: StateFlow<List<PluginUiModel>> =
        pluginManager.plugins
            .map { handles ->
                handles.mapNotNull { handle ->
                    runCatching {
                        val info = handle.info
                        PluginUiModel(
                            id = handle.packageName,
                            name = info.name,
                            author = info.author,
                            version = info.versionName,
                            description = info.description,
                            isSearchSource = handle.search != null,
                            isLyricSource = handle.lyric != null
                        )
                    }.getOrNull()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    private val _configUiState = MutableStateFlow<ConfigUiState?>(null)
    val configUiState: StateFlow<ConfigUiState?> = _configUiState.asStateFlow()



    private fun findHandle(pluginId: String): PluginHandle? {
        return pluginManager.plugins.value.find { it.packageName == pluginId }
    }

    fun updateField(key: String, value: Any) {
        _formState.update { old ->
            old.toMutableMap().apply { put(key, value) }
        }
    }

    fun loadConfig(pluginId: String, pluginName: String) {
        viewModelScope.launch {

            _configUiState.value = ConfigUiState(
                pluginId = pluginId,
                pluginName = pluginName,
                isLoading = true
            )

            try {
                val handle = findHandle(pluginId)
                    ?: error("Plugin not found")

                val schema = withContext(Dispatchers.IO) {
                    handle.plugin.configSchema ?: emptyList()
                }

                val settings = withContext(Dispatchers.IO) {
                    handle.plugin.settings ?: Bundle()
                }

                _formState.value = buildFormState(schema, settings)

                _configUiState.value = ConfigUiState(
                    pluginId = pluginId,
                    pluginName = pluginName,
                    schema = schema,
                    currentSettings = settings,
                    isLoading = false
                )

            } catch (e: Exception) {
                _configUiState.value = _configUiState.value?.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun buildFormState(
        schema: List<ConfigField>,
        bundle: Bundle
    ): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        schema.forEach { field ->
            val value: Any = when (field.type) {
                FieldType.SWITCH ->
                    bundle.getBoolean(
                        field.key,
                        field.defaultValue.toBooleanStrictOrNull() ?: false
                    )

                else ->
                    bundle.getString(field.key) ?: field.defaultValue
            }

            map[field.key] = value
        }

        return map
    }
    fun saveConfig() {
        val current = _configUiState.value ?: return
        val form = _formState.value

        viewModelScope.launch {
            try {
                val bundle = Bundle().apply {
                    form.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            else -> putString(key, value.toString())
                        }
                    }
                }

                findHandle(current.pluginId)?.let { handle ->
                    withContext(Dispatchers.IO) {
                        handle.plugin.updateSettings(bundle)
                    }
                }

            } catch (e: Exception) {
                Log.e("PluginViewModel", "Save failed", e)
            }
        }
    }


    fun refresh() {
        pluginManager.stopDiscovery()
        pluginManager.startDiscovery()
    }

}