package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import android.util.Log
import com.lonx.lyrico.plugin.PluginManager

data class PluginUiModel(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val isSearchSource: Boolean = false,
    val isLyricSource: Boolean = false
)
class PluginListViewModel(private val pluginManager: PluginManager) : ViewModel() {

    // 将两个 Source 的流合并成一个 UI 列表
    val pluginList: StateFlow<List<PluginUiModel>> = combine(
        pluginManager.connectedSearchSources,
        pluginManager.connectedLyricSources
    ) { searchSources, lyricSources ->
        val map = mutableMapOf<String, PluginUiModel>()

        // 处理搜索源
        searchSources.forEach { source ->
            try {
                val info = source.pluginInfo // 远程调用
                map[info.id] = PluginUiModel(
                    id = info.id,
                    name = info.name,
                    author = info.author,
                    version = info.versionName,
                    description = info.description,
                    isSearchSource = true
                )
            } catch (e: Exception) {
                Log.e("PluginViewModel", "获取搜索插件信息失败", e)
            }
        }

        // 处理歌词源（合并同 ID 的插件）
        lyricSources.forEach { source ->
            try {
                val info = source.pluginInfo
                val existing = map[info.id]
                if (existing != null) {
                    map[info.id] = existing.copy(isLyricSource = true)
                } else {
                    map[info.id] = PluginUiModel(
                        id = info.id,
                        name = info.name,
                        author = info.author,
                        version = info.versionName,
                        description = info.description,
                        isLyricSource = true
                    )
                }
            } catch (e: Exception) {
                Log.e("PluginViewModel", "获取歌词插件信息失败", e)
            }
        }

        map.values.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun refresh() {
        pluginManager.stopDiscovery()
        pluginManager.startDiscovery()
    }
}