package com.lonx.lyrico.plugin.source

import android.util.Log
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SearchSourceWithState(
    val source: SearchSource,
    val enabled: Boolean
)

class PluginSearchSourceManager(
    private val repository: SourcePluginRepository,
    private val factory: ScriptSearchSourceFactory,
    private val appLogRepository: AppLogRepository
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

    suspend fun getSourceWithState(pluginId: String): SearchSourceWithState? {
        val plugin = repository.getPlugin(pluginId) ?: return null
        val source = buildSources(listOf(plugin), pruneMissing = false).firstOrNull() ?: return null
        return SearchSourceWithState(
            source = source,
            enabled = plugin.enabled
        )
    }

    private suspend fun buildSources(
        plugins: List<SourcePluginEntity>,
        pruneMissing: Boolean = true
    ): List<SearchSource> {
        return mutex.withLock {
            buildSourcesLocked(plugins, pruneMissing)
        }
    }

    suspend fun invalidate(pluginId: String) {
        mutex.withLock {
            cache.remove(pluginId)?.close()
            cacheVersions.remove(pluginId)
        }
    }

    private suspend fun buildSourcesLocked(
        plugins: List<SourcePluginEntity>,
        pruneMissing: Boolean
    ): List<SearchSource> {
        if (pruneMissing) {
            val activeIds = plugins.mapTo(mutableSetOf()) { it.id }
            val removedIds = cache.keys - activeIds
            removedIds.forEach { id ->
                cache.remove(id)?.close()
                cacheVersions.remove(id)
            }
        }

        return plugins.mapNotNull { plugin ->
            try {
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
            } catch (throwable: Exception) {
                logSourceBuildFailure(plugin, throwable)
                null
            }
        }
    }

    private suspend fun logSourceBuildFailure(plugin: SourcePluginEntity, throwable: Throwable) {
        runCatching {
            appLogRepository.logException(
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = "Failed to build plugin search source\n" +
                        "plugin=${plugin.id}\nname=${plugin.name}\nentry=${plugin.entryFile}",
                throwable = throwable,
                relatedId = plugin.id
            )
        }.onFailure { logThrowable ->
            Log.w(TAG, "Failed to write plugin source build log", logThrowable)
        }
    }

    override fun close() {
        cache.values.forEach { it.close() }
        cache.clear()
        cacheVersions.clear()
    }

    private companion object {
        const val TAG = "PluginSearchSourceManager"
    }
}
