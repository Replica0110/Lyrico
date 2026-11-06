package com.lonx.lyrico.plugin.source

import android.content.Context
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.entity.PluginCatalogCacheEntity
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID

data class PluginUpdateOption(val release: PluginRelease, val sourceUrls: List<String>, val cached: Boolean = false)
data class PluginUpdateState(
    val release: PluginRelease,
    val installedDir: String,
    val phase: Phase = Phase.AVAILABLE,
    val percent: Int = 0,
    val error: String? = null,
    val sourceUrls: List<String> = emptyList(),
    val alternatives: List<PluginUpdateOption> = emptyList(),
    val cached: Boolean = false
) {
    enum class Phase { AVAILABLE, DOWNLOADING, INSTALLING, FAILED }
    val isDownloading: Boolean get() = phase == Phase.DOWNLOADING
    val isInstalling: Boolean get() = phase == Phase.INSTALLING
}

data class PluginTransferState(
    val release: PluginRelease,
    val sourceUrl: String,
    val expectedInstalledDir: String?,
    val phase: Phase = Phase.DOWNLOADING,
    val percent: Int = 0,
    val error: String? = null,
    val taskId: String = UUID.randomUUID().toString()
) {
    enum class Phase { DOWNLOADING, VERIFYING, INSTALLING, INSTALLED, FAILED, CANCELLED }
    val active: Boolean get() = phase == Phase.DOWNLOADING || phase == Phase.VERIFYING || phase == Phase.INSTALLING
}

class PluginDistributionManager(
    context: Context,
    private val repository: SourcePluginRepository,
    private val installer: SourcePluginInstaller,
    private val logs: AppLogRepository,
    val catalogs: PluginCatalogRepository,
    val client: PluginDistributionClient = catalogs.client
) : AutoCloseable {
    private val installRoot = File(context.filesDir, "plugins/sources")
    private val downloads = File(context.cacheDir, "plugin_downloads")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val _tasks = MutableStateFlow<Map<String, PluginTransferState>>(emptyMap())
    val tasks = _tasks.asStateFlow()
    private var checkJob: Job? = null
    @Volatile var checkFailures: Int = 0
        private set
    @Volatile var checkedUpdateCount: Int = 0
        private set
    private val downloadSlots = Semaphore(3)

    val updates = combine(repository.observePlugins(), catalogs.subscriptions, catalogs.snapshots, tasks) { installed, subscriptions, cache, transfers ->
        val enabled = subscriptions.filter { it.enabled }.map { it.url }
        buildMap {
            installed.forEach { plugin ->
                val urls = (listOfNotNull(plugin.updateUrl) + enabled).mapNotNull {
                    runCatching { PluginCatalogParser.requireUrl(it) }.getOrNull()
                }.distinct()
                val resolved = resolvePluginUpdate(plugin, urls, cache)
                val task = transfers[plugin.id]
                if (task?.active == true && task.expectedInstalledDir == plugin.pluginDir) {
                    put(plugin.id, PluginUpdateState(task.release, plugin.pluginDir,
                        phase = if (task.phase == PluginTransferState.Phase.INSTALLING) PluginUpdateState.Phase.INSTALLING else PluginUpdateState.Phase.DOWNLOADING,
                        percent = task.percent, sourceUrls = listOf(task.sourceUrl)))
                } else if (resolved != null) {
                    put(plugin.id, if (task?.phase == PluginTransferState.Phase.FAILED && task.release == resolved.release &&
                        task.expectedInstalledDir == plugin.pluginDir && task.sourceUrl in resolved.sourceUrls)
                        resolved.copy(phase = PluginUpdateState.Phase.FAILED, error = task.error) else resolved)
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            combine(repository.observePlugins().map { list -> list.map { Triple(it.id, it.pluginDir, it.updateUrl) } }.distinctUntilChanged(),
                catalogs.subscriptions.map { list -> list.map { Triple(it.id, it.url, it.enabled) } }.distinctUntilChanged()) { _, _ -> Unit }
                .collect { checkUpdates() }
        }
    }

    @Synchronized
    fun checkUpdates(force: Boolean = false): Job {
        if (checkJob?.isActive == true && !force) return checkJob!!
        checkJob?.cancel()
        return scope.launch {
            installer.recover(installRoot)
            installer.synchronizeInstalledPluginManifestMetadata()
            val urls = (repository.getPlugins().mapNotNull { it.updateUrl } +
                catalogs.getSubscriptions().filter { it.enabled }.map { it.url }).map {
                runCatching { PluginCatalogParser.requireUrl(it) }.getOrDefault(it)
            }.distinct()
            val results = coroutineScope { urls.map { url -> async { catalogs.refresh(url, force) } }.awaitAll() }
            results.forEachIndexed { index, result -> result.exceptionOrNull()?.let {
                if (it is Exception) logFailure("Plugin catalog refresh failed: ${urls[index]}", it)
            } }
            val cached = urls.associateWith { catalogs.getCache(it) }.mapNotNull { (url, value) ->
                value?.let { url to PluginCatalogSnapshot(it, catalogs.decode(it)) }
            }.toMap()
            val subscribedUrls = catalogs.getSubscriptions().filter { it.enabled }.map { it.url }
            val count = repository.getPlugins().count { plugin ->
                val relevant = (listOfNotNull(plugin.updateUrl) + subscribedUrls).mapNotNull {
                    runCatching { PluginCatalogParser.requireUrl(it) }.getOrNull()
                }.distinct()
                resolvePluginUpdate(plugin, relevant, cached) != null
            }
            currentCoroutineContext().ensureActive()
            checkFailures = results.count { it.isFailure }
            checkedUpdateCount = count
        }.also { checkJob = it }
    }

    suspend fun awaitUpdateCheck(force: Boolean = true) {
        var checking = checkUpdates(force)
        while (true) {
            checking.join()
            val latest = synchronized(this) { checkJob }
            if (latest == null || latest === checking) return
            checking = latest
        }
    }

    suspend fun prepareDownloads(
        releases: List<PluginRelease>,
        onVerifying: (String) -> Unit = {},
        progress: (String, Int) -> Unit
    ): PluginImportSession {
        var staged: File? = null
        try {
            return withContext(Dispatchers.IO) {
                downloadSlots.withPermit {
                    installer.recover(installRoot)
                    val root = File(installRoot, ".import-${UUID.randomUUID()}").also { it.mkdirs() }
                    staged = root
                    val candidates = mutableListOf<PluginInstallCandidate>()
                    for (release in releases) {
                        require(release.compatible) { "Plugin requires a newer or different host API: ${release.id}" }
                        downloads.mkdirs()
                        val zip = File(downloads, "${UUID.randomUUID()}.zip")
                        try {
                            client.download(release, zip) { progress(release.id, it) }
                            onVerifying(release.id)
                            val session = zip.inputStream().use { installer.prepareImport(it, root) }
                            require(session.failed.isEmpty() && session.candidates.size == 1) {
                                "Online package must contain exactly one valid plugin"
                            }
                            val candidate = session.candidates.single()
                            release.verify(candidate.manifest)
                            candidates += candidate.copy(relativeRootInArchive = release.id)
                        } finally { zip.delete() }
                    }
                    PluginImportSession(root, installRoot, candidates, emptyList())
                }
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { staged?.deleteRecursively() }
            throw error
        }
    }

    fun update(candidate: PluginUpdateState, onFinished: (Result<SourcePluginEntity>) -> Unit = {}) {
        if (candidate.alternatives.isNotEmpty()) return
        val source = candidate.sourceUrls.firstOrNull() ?: return
        install(candidate.release, source, candidate.installedDir, onFinished = onFinished)
    }

    @Synchronized
    fun install(
        release: PluginRelease,
        sourceUrl: String,
        expectedInstalledDir: String?,
        allowDowngrade: Boolean = false,
        onFinished: (Result<SourcePluginEntity>) -> Unit = {}
    ) {
        if (_tasks.value[release.id]?.active == true) return
        val initial = PluginTransferState(release, sourceUrl, expectedInstalledDir)
        _tasks.update { it + (release.id to initial) }
        jobs[release.id] = scope.launch {
            var prepared: PluginImportSession? = null
            var committed = false
            fun change(transform: (PluginTransferState) -> PluginTransferState) {
                _tasks.update { old -> if (old[release.id]?.taskId == initial.taskId)
                    old + (release.id to transform(old.getValue(release.id))) else old }
            }
            try {
                val session = prepareDownloads(listOf(release), onVerifying = {
                    change { it.copy(phase = PluginTransferState.Phase.VERIFYING, percent = 100) }
                }) { _, percent -> change { it.copy(percent = percent) } }
                prepared = session
                currentCoroutineContext().ensureActive()
                change { it.copy(phase = PluginTransferState.Phase.INSTALLING) }
                val result = installer.installPrepared(session, enabled = true, expectedInstalledDir = expectedInstalledDir,
                    checkInstalledState = true, allowDowngrade = allowDowngrade,
                    installSourceUrl = sourceUrl, installSha256 = release.sha256,
                    onInstalled = {
                        committed = true
                        change { it.copy(phase = PluginTransferState.Phase.INSTALLED) }
                        onFinished(Result.success(it))
                    })
                check(result.installed.size == 1) { result.failed.firstOrNull()?.reason ?: "Plugin install failed" }
            } catch (error: Exception) {
                if (!committed) {
                    if (error is CancellationException && error !is TimeoutCancellationException) {
                        change { it.copy(phase = PluginTransferState.Phase.CANCELLED, error = null) }
                    } else {
                        change { it.copy(phase = PluginTransferState.Phase.FAILED, error = error.message ?: error.javaClass.simpleName) }
                        onFinished(Result.failure(error))
                        logFailure("Plugin install failed: ${release.id}", error)
                    }
                }
                if (error is CancellationException) throw error
            } finally { prepared?.let(installer::discardImport) }
        }
    }

    @Synchronized fun cancel(id: String) { jobs[id]?.cancel() }
    fun forget(id: String) { cancel(id) }
    private suspend fun logFailure(message: String, error: Exception) {
        runCatching { logs.logException(type = AppLogType.PLUGIN, tag = "PluginDistributionManager", message = message, throwable = error) }
    }
    override fun close() { scope.cancel() }
}

internal fun resolvePluginUpdate(
    plugin: SourcePluginEntity,
    urls: List<String>,
    cache: Map<String, PluginCatalogSnapshot>
): PluginUpdateState? {
    val releases = urls.mapNotNull { url ->
        cache[url]?.releasesById?.get(plugin.id)
            ?.takeIf { it.compatible && it.versionCode > plugin.versionCode }?.let { url to it }
    }
    val highest = releases.maxOfOrNull { it.second.versionCode } ?: return null
    val options = releases.filter { it.second.versionCode == highest }
        .groupBy { it.second.sha256.lowercase(java.util.Locale.ROOT) }.values.map { group ->
            val mirrors = group.sortedBy { cache[it.first]?.record?.error != null }
            PluginUpdateOption(mirrors.first().second, mirrors.map { it.first },
                mirrors.all { cache[it.first]?.record?.error != null })
        }
    val first = options.first()
    return PluginUpdateState(first.release, plugin.pluginDir, sourceUrls = first.sourceUrls,
        alternatives = options.takeIf { it.size > 1 }.orEmpty(), cached = first.cached)
}
