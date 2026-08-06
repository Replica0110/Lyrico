package com.lonx.lyrico.plugin.source

import android.util.Log
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.entity.capabilities
import com.lonx.lyrico.data.model.entity.displayName
import com.lonx.lyrico.data.model.entity.isEnabledFor
import com.lonx.lyrico.data.model.entity.isEnabledAnywhere
import com.lonx.lyrico.data.model.entity.sortOrderFor
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.supportsSourceType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.model.lyrics.SearchSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class SearchSourceWithState(
    val source: SearchSource,
    val enabled: Boolean
)

class PluginSearchSourceManager(
    private val repository: SourcePluginRepository,
    private val factory: ScriptSearchSourceFactory,
    private val installer: SourcePluginInstaller,
    private val appLogRepository: AppLogRepository
) : AutoCloseable {
    private val cache = mutableMapOf<String, ScriptSearchSource>()
    private val cachedPlugins = mutableMapOf<String, SourcePluginEntity>()
    private val mutex = Mutex()

    fun observeSources(): Flow<List<SearchSource>> {
        return repository.observePlugins()
            .map { plugins ->
                buildSources(
                    plugins = plugins.filter(SourcePluginEntity::isEnabledAnywhere),
                    retainedIds = plugins.enabledPluginIds()
                )
            }
            .onStart { installer.synchronizeInstalledPluginManifestMetadata() }
    }

    fun observeSources(sourceType: PluginSourceType): Flow<List<SearchSource>> {
        return repository.observePlugins()
            .map { plugins ->
                buildSources(
                    plugins = plugins.forSourceType(sourceType),
                    retainedIds = plugins.enabledPluginIds()
                )
            }
            .onStart { installer.synchronizeInstalledPluginManifestMetadata() }
    }

    suspend fun getSources(): List<SearchSource> {
        installer.synchronizeInstalledPluginManifestMetadata()
        val plugins = repository.getPlugins()
        return buildSources(
            plugins = plugins.filter(SourcePluginEntity::isEnabledAnywhere),
            retainedIds = plugins.enabledPluginIds()
        )
    }

    suspend fun getSources(sourceType: PluginSourceType): List<SearchSource> {
        installer.synchronizeInstalledPluginManifestMetadata()
        val plugins = repository.getPlugins()
        return buildSources(
            plugins = plugins.forSourceType(sourceType),
            retainedIds = plugins.enabledPluginIds()
        )
    }

    suspend fun getSourceWithState(pluginId: String): SearchSourceWithState? {
        installer.synchronizeInstalledPluginManifestMetadata()
        val plugin = repository.getPlugin(pluginId) ?: return null
        val source = buildSources(listOf(plugin), retainedIds = null).firstOrNull() ?: return null
        return SearchSourceWithState(
            source = source,
            enabled = plugin.isEnabledAnywhere
        )
    }

    private suspend fun buildSources(
        plugins: List<SourcePluginEntity>,
        retainedIds: Set<String>? = null
    ): List<SearchSource> {
        return mutex.withLock {
            buildSourcesLocked(plugins, retainedIds)
        }
    }

    suspend fun invalidate(pluginId: String) {
        mutex.withLock {
            cache.remove(pluginId)?.close()
            cachedPlugins.remove(pluginId)
        }
    }

    private suspend fun buildSourcesLocked(
        plugins: List<SourcePluginEntity>,
        retainedIds: Set<String>?
    ): List<SearchSource> {
        if (retainedIds != null) {
            val removedIds = cache.keys - retainedIds
            removedIds.forEach { id ->
                cache.remove(id)?.close()
                cachedPlugins.remove(id)
            }
        }

        return plugins.mapNotNull { plugin ->
            try {
                val existing = cache[plugin.id]
                if (existing != null && cachedPlugins[plugin.id] == plugin) {
                    existing
                } else {
                    existing?.close()
                    factory.create(plugin).also {
                        cache[plugin.id] = it
                        cachedPlugins[plugin.id] = plugin
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
        cachedPlugins.clear()
    }

    private companion object {
        const val TAG = "PluginSearchSourceManager"
    }
}

private fun List<SourcePluginEntity>.enabledPluginIds(): Set<String> =
    asSequence()
        .filter(SourcePluginEntity::isEnabledAnywhere)
        .mapTo(mutableSetOf()) { it.id }

private fun List<SourcePluginEntity>.forSourceType(
    sourceType: PluginSourceType
): List<SourcePluginEntity> =
    asSequence()
        .filter { plugin ->
            plugin.isEnabledFor(sourceType) &&
                plugin.capabilities.supportsSourceType(sourceType)
        }
        .sortedWith(
            compareBy<SourcePluginEntity> { it.sortOrderFor(sourceType) }
                .thenBy { it.displayName }
        )
        .toList()
