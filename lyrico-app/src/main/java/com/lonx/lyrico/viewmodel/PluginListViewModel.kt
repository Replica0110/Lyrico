package com.lonx.lyrico.viewmodel

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import android.util.Log
import com.lonx.lyrico.plugin.ConfigField
import com.lonx.lyrico.plugin.LyricPluginWrapper
import com.lonx.lyrico.plugin.PluginManager
import com.lonx.lyrico.plugin.SearchPluginWrapper
import com.lonx.lyrico.plugin.UnifiedPlugin
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
class PluginListViewModel(private val pluginManager: PluginManager) : ViewModel() {

    val pluginList: StateFlow<List<PluginUiModel>> = combine(
        pluginManager.connectedSearchSources,
        pluginManager.connectedLyricSources
    ) { searchSources, lyricSources ->
        val map = mutableMapOf<String, PluginUiModel>()

        searchSources.forEach { source ->
            runCatching { source.pluginInfo }.onSuccess { info ->
                map[info.id] = PluginUiModel(
                    id = info.id, name = info.name, author = info.author,
                    version = info.versionName, description = info.description,
                    isSearchSource = true
                )
            }
        }

        lyricSources.forEach { source ->
            runCatching { source.pluginInfo }.onSuccess { info ->
                val existing = map[info.id]
                map[info.id] = existing?.copy(isLyricSource = true)
                    ?: PluginUiModel(
                        id = info.id, name = info.name, author = info.author,
                        version = info.versionName, description = info.description,
                        isLyricSource = true
                    )
            }
        }
        map.values.toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2. 当前正在编辑的配置状态
    private val _configUiState = MutableStateFlow<ConfigUiState?>(null)
    val configUiState: StateFlow<ConfigUiState?> = _configUiState.asStateFlow()

    /**
     * 寻找插件并包装成统一接口
     */
    private fun findUnifiedPlugin(id: String): UnifiedPlugin? {
        val searchSource = pluginManager.connectedSearchSources.value.find {
            runCatching { it.pluginInfo.id }.getOrNull() == id
        }
        if (searchSource != null) return SearchPluginWrapper(searchSource)

        // 2. 尝试从歌词源找
        val lyricSource = pluginManager.connectedLyricSources.value.find {
            runCatching { it.pluginInfo.id }.getOrNull() == id
        }
        if (lyricSource != null) return LyricPluginWrapper(lyricSource)

        return null
    }

    /**
     * 加载配置逻辑修改
     */
    fun loadConfig(pluginId: String, pluginName: String) {
        viewModelScope.launch {
            _configUiState.value = ConfigUiState(pluginId, pluginName, isLoading = true)

            try {
                val unified = findUnifiedPlugin(pluginId) // 使用统一包装器

                if (unified != null) {
                    val schema = withContext(Dispatchers.IO) { unified.getConfigSchema() }
                    val settings = withContext(Dispatchers.IO) { unified.getSettings() }

                    _configUiState.value = ConfigUiState(
                        pluginId = pluginId,
                        pluginName = pluginName,
                        schema = schema,
                        currentSettings = settings,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _configUiState.value = _configUiState.value?.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun saveConfig(settings: Bundle) {
        val currentState = _configUiState.value ?: return
        viewModelScope.launch {
            try {
                findUnifiedPlugin(currentState.pluginId)?.let { unified ->
                    withContext(Dispatchers.IO) { unified.updateSettings(settings) }
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

    fun dismissConfig() {
        _configUiState.value = null
    }
}