package com.lonx.lyrico.plugin.source

import android.content.Context
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.UUID

data class PluginUpdateState(
    val release: PluginRelease,
    val installedDir: String,
    val phase: Phase = Phase.AVAILABLE,
    val percent: Int = 0,
    val error: String? = null
) {
    enum class Phase { AVAILABLE, DOWNLOADING, INSTALLING, FAILED }
    val isDownloading: Boolean get() = phase == Phase.DOWNLOADING
    val isInstalling: Boolean get() = phase == Phase.INSTALLING
}

class PluginDistributionManager(
    context: Context,
    private val repository: SourcePluginRepository,
    private val installer: SourcePluginInstaller,
    private val logs: AppLogRepository,
    val client: PluginDistributionClient = PluginDistributionClient()
) : AutoCloseable {
    private val installRoot = File(context.filesDir, "plugins/sources")
    private val downloads = File(context.cacheDir, "plugin_downloads")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _updates = MutableStateFlow<Map<String, PluginUpdateState>>(emptyMap())
    val updates = _updates.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private var checkJob: Job? = null
    @Volatile var checkFailures: Int = 0
        private set
    private val downloadSlots = Semaphore(3)

    init {
        scope.launch {
            repository.observePlugins().map { plugins -> plugins.map { Triple(it.id, it.pluginDir, it.updateUrl) } }
                .distinctUntilChanged().collect { installed ->
                    _updates.update { old -> old.filter { (id, candidate) ->
                        installed.any { it.first == id && it.second == candidate.installedDir }
                    } }
                    checkUpdates(force = true)
                }
        }
    }

    @Synchronized
    fun checkUpdates(force: Boolean = false): Job {
        if (checkJob?.isActive == true && !force) return checkJob!!
        checkJob?.cancel()
        checkJob = scope.launch {
            var failures = 0
            installer.recover(installRoot)
            installer.synchronizeInstalledPluginManifestMetadata()
            val installed = repository.getPlugins()
            _updates.update { old -> old.filter { (id, candidate) ->
                installed.any { it.id == id && it.pluginDir == candidate.installedDir }
            } }
            installed.filter { !it.updateUrl.isNullOrBlank() }.groupBy { it.updateUrl!! }.forEach { (url, plugins) ->
                try {
                    val catalog = client.fetch(url)
                    plugins.forEach { plugin ->
                        val current = repository.getPlugin(plugin.id)
                        if (current?.pluginDir != plugin.pluginDir || current.updateUrl != url) return@forEach
                        val release = catalog.plugins.singleOrNull { it.id == plugin.id }
                        _updates.update { old ->
                            val previous = old[plugin.id]
                            if (previous?.isDownloading == true || previous?.isInstalling == true) old
                            else if (release != null && release.versionCode > plugin.versionCode) {
                                val next = previous?.takeIf { it.release == release && it.installedDir == plugin.pluginDir }
                                    ?: PluginUpdateState(release, plugin.pluginDir)
                                old + (plugin.id to next)
                            } else old - plugin.id
                        }
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    failures++
                    logFailure("Plugin update check failed", error)
                }
            }
            checkFailures = failures
        }
        return checkJob!!
    }

    suspend fun awaitUpdateCheck() {
        var checking = checkUpdates()
        while (true) {
            checking.join()
            val latest = synchronized(this) { checkJob }
            if (latest == null || latest === checking) return
            checking = latest
        }
    }

    /** Catalog links are read only; no installed plugin is bound to them. */
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

    @Synchronized
    fun update(candidate: PluginUpdateState, onFinished: (Result<SourcePluginEntity>) -> Unit = {}) {
        val id = candidate.release.id
        if (jobs[id]?.isCompleted == false) return
        jobs[id] = scope.launch {
            val task = currentCoroutineContext()[Job]!!
            var prepared: PluginImportSession? = null
            var committed = false
            _updates.update { it + (id to candidate.copy(phase = PluginUpdateState.Phase.DOWNLOADING, percent = 0, error = null)) }
            try {
                val session = prepareDownloads(listOf(candidate.release)) { _, percent ->
                    if (task.isActive) updateState(candidate) { it.copy(percent = percent) }
                }
                prepared = session
                updateState(candidate) { it.copy(phase = PluginUpdateState.Phase.INSTALLING) }
                val result = installer.installPrepared(session, enabled = true, expectedInstalledDir = candidate.installedDir,
                    onInstalled = {
                        committed = true
                        onFinished(Result.success(it))
                    })
                check(result.installed.size == 1) { result.failed.firstOrNull()?.reason ?: "Plugin install failed" }
                _updates.update { if (it[id]?.installedDir == candidate.installedDir) it - id else it }
            } catch (error: Exception) {
                if (error is CancellationException) {
                    updateState(candidate) { it.copy(phase = PluginUpdateState.Phase.AVAILABLE, percent = 0, error = null) }
                    throw error
                }
                if (!committed) onFinished(Result.failure(error))
                if (repository.getPlugin(id)?.pluginDir == candidate.installedDir) {
                    updateState(candidate) { it.copy(phase = PluginUpdateState.Phase.FAILED, error = error.message ?: "Update failed") }
                } else {
                    _updates.update { if (it[id]?.installedDir == candidate.installedDir) it - id else it }
                }
                logFailure("Plugin update failed: $id", error)
            } finally {
                prepared?.let(installer::discardImport)
            }
        }
    }

    @Synchronized
    fun cancel(id: String) { jobs[id]?.cancel() }

    fun forget(id: String) {
        cancel(id)
        _updates.update { it - id }
    }

    private fun updateState(candidate: PluginUpdateState, change: (PluginUpdateState) -> PluginUpdateState) {
        _updates.update { current ->
            val state = current[candidate.release.id]
            if (state?.installedDir == candidate.installedDir) current + (candidate.release.id to change(state)) else current
        }
    }

    private suspend fun logFailure(message: String, error: Exception) {
        runCatching { logs.logException(type = AppLogType.PLUGIN, tag = "PluginDistributionManager", message = message, throwable = error) }
    }

    override fun close() { scope.cancel() }
}
