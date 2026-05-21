package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PluginSearchSourceManager(
    private val repository: SourcePluginRepository,
    private val factory: ScriptSearchSourceFactory
) : AutoCloseable {
    private val cache = mutableMapOf<String, ScriptSearchSource>()
    private val cacheVersions = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    fun observeEnabledSources(): Flow<List<SearchSource>> {
        return repository.observeEnabledPlugins().map { plugins ->
            buildSources(plugins)
        }
    }

    suspend fun getEnabledSources(): List<SearchSource> {
        return buildSources(repository.getPlugins().filter { it.enabled })
    }

    private suspend fun buildSources(plugins: List<SourcePluginEntity>): List<SearchSource> {
        return mutex.withLock {
            buildSourcesLocked(plugins)
        }
    }

    suspend fun invalidate(pluginId: String) {
        mutex.withLock {
            cache.remove(pluginId)?.close()
            cacheVersions.remove(pluginId)
        }
    }

    private suspend fun buildSourcesLocked(plugins: List<SourcePluginEntity>): List<SearchSource> {
        val activeIds = plugins.mapTo(mutableSetOf()) { it.id }
        val removedIds = cache.keys - activeIds
        removedIds.forEach { id ->
            cache.remove(id)?.close()
            cacheVersions.remove(id)
        }

        return plugins.mapNotNull { plugin ->
            runCatching {
                val existing = cache[plugin.id]
                if (existing != null && cacheVersions[plugin.id] == plugin.updatedAt) {
                    existing
                } else {
                    existing?.close()
                    factory.create(plugin).also {
                        cache[plugin.id] = it
                        cacheVersions[plugin.id] = plugin.updatedAt
                    }
                }
            }.getOrNull()
        }
    }

    override fun close() {
        cache.values.forEach { it.close() }
        cache.clear()
        cacheVersions.clear()
    }
}
