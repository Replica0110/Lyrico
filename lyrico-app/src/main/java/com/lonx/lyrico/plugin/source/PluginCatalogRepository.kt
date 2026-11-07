package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.dao.PluginSubscriptionDao
import com.lonx.lyrico.data.model.entity.PluginCatalogCacheEntity
import com.lonx.lyrico.data.model.entity.PluginSubscriptionEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.UUID

class DuplicatePluginSubscription : IllegalArgumentException()

data class PluginCatalogSnapshot(val record: PluginCatalogCacheEntity, val catalog: PluginCatalog?) {
    val releasesById: Map<String, PluginRelease> = catalog?.plugins?.associateBy { it.id }.orEmpty()
}

class PluginCatalogRepository(
    private val dao: PluginSubscriptionDao,
    val client: PluginDistributionClient = PluginDistributionClient(),
    private val now: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val mutations = Mutex()
    private val requests = mutableMapOf<String, Deferred<Result<PluginCatalog>>>()
    private val slots = Semaphore(3)
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = _refreshing.asStateFlow()
    val subscriptions = dao.observeSubscriptions().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val cache = dao.observeCache().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val snapshots = cache.map { records -> records.associate { it.url to PluginCatalogSnapshot(it, decode(it)) } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun decode(cache: PluginCatalogCacheEntity?): PluginCatalog? = cache?.catalogJson?.let {
        runCatching { Json.decodeFromString<PluginCatalog>(it) }.getOrNull()
    }

    suspend fun save(id: String?, name: String, url: String): PluginSubscriptionEntity = mutations.withLock {
        val normalized = PluginCatalogParser.requireUrl(url.trim())
        val all = dao.getSubscriptions()
        if (all.any { it.url == normalized && it.id != id }) throw DuplicatePluginSubscription()
        val existing = id?.let { key -> all.singleOrNull { it.id == key } }
        require(id == null || existing != null) { "Subscription no longer exists" }
        val result = PluginSubscriptionEntity(existing?.id ?: UUID.randomUUID().toString(),
            name.trim().ifBlank { normalized.toHttpUrl().host }, normalized, existing?.enabled ?: true,
            existing?.sortOrder ?: ((all.maxOfOrNull { it.sortOrder } ?: -1) + 1))
        dao.upsert(result)
        result
    }

    suspend fun delete(id: String) = mutations.withLock { dao.delete(id) }
    suspend fun setEnabled(id: String, enabled: Boolean) = mutations.withLock { dao.setEnabled(id, enabled) }
    suspend fun getSubscriptions() = dao.getSubscriptions()
    suspend fun getCache(url: String) = dao.getCache(url)

    /** URL identity isolates late responses from edits/deletions; callers share in-flight requests. */
    suspend fun refresh(url: String, force: Boolean = false): Result<PluginCatalog> {
        return try { refreshCatalog(url, force) } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private suspend fun refreshCatalog(url: String, force: Boolean): Result<PluginCatalog> {
        val normalized = try { PluginCatalogParser.requireUrl(url) } catch (error: Exception) { return Result.failure(error) }
        if (!force) {
            val previous = dao.getCache(normalized)
            val cached = decode(previous)
            if (cached != null && previous?.error == null && previous?.lastSuccessAt != null &&
                now() - previous.lastSuccessAt in 0 until CACHE_TTL_MS) return Result.success(cached)
        }
        val request = synchronized(lock) {
            requests[normalized] ?: scope.async(start = CoroutineStart.LAZY) {
                val previous = dao.getCache(normalized) ?: PluginCatalogCacheEntity(normalized)
                _refreshing.update { it + normalized }
                try {
                    slots.withPermit {
                        val attempt = now()
                        try {
                            val catalog = client.fetch(normalized)
                            dao.upsertCache(PluginCatalogCacheEntity(normalized, Json.encodeToString(catalog), now(), attempt))
                            Result.success(catalog)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            dao.upsertCache(previous.copy(lastAttemptAt = attempt, error = error.message ?: error.javaClass.simpleName))
                            Result.failure(error)
                        }
                    }
                } finally { _refreshing.update { it - normalized } }
            }.also { task ->
                requests[normalized] = task
                task.invokeOnCompletion { synchronized(lock) { if (requests[normalized] === task) requests.remove(normalized) } }
                task.start()
            }
        }
        return request.await()
    }

    override fun close() { scope.cancel() }
    companion object { const val CACHE_TTL_MS = 5 * 60 * 1000L }
}
