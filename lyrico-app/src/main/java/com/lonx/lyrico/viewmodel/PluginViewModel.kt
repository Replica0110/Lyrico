package com.lonx.lyrico.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SourcePluginRepository
import com.lonx.lyrico.plugin.source.PluginSearchSourceManager
import com.lonx.lyrico.plugin.source.PluginImportSession
import com.lonx.lyrico.plugin.source.PluginInstallResult
import com.lonx.lyrico.plugin.source.PluginVersionConflict
import com.lonx.lyrico.plugin.source.SourcePluginInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import com.lonx.lyrico.plugin.i18n.PluginLocales
import com.lonx.lyrico.plugin.i18n.PluginStrings
import com.lonx.lyrico.data.model.plugin.PluginManifest
import kotlinx.serialization.json.Json
import com.lonx.lyrico.plugin.source.PluginDistributionManager
import com.lonx.lyrico.plugin.source.PluginRelease
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

sealed interface PluginMessage {
    enum class Status : PluginMessage {
        PACKAGE_SCANNED, INSTALLED, DELETED, NAME_SAVED, NO_PENDING_IMPORT, NO_SELECTION,
        BUSY, WORKING, TIMED_OUT, CANNOT_OPEN_FILE, NO_INSTALLABLE_PLUGIN, CATALOG_CHANGED
    }

    data class Failure(val reason: String) : PluginMessage
    data class UpdateCheck(val count: Int, val failures: Int) : PluginMessage
    data class UpdateResult(val name: String, val version: String, val error: String? = null) : PluginMessage
}

enum class SubscriptionEditorError { DUPLICATE, INVALID_URL, SAVE_FAILED }

data class SubscriptionEditorState(
    val saving: Boolean = false,
    val error: SubscriptionEditorError? = null,
    val savedId: String? = null
)

data class PluginUiState(
    val isBusy: Boolean = false,
    val message: PluginMessage? = null,
    val messageVersion: Long = 0,
    val smokeResult: String = "",
    val pendingImport: PluginImportSession? = null,
    val selectedImportRoots: Set<String> = emptySet(),
    val importResult: PluginInstallResult? = null,
    val importError: String? = null
) {
    val importComplete: Boolean
        get() {
            val session = pendingImport ?: return false
            val result = importResult ?: return false
            val installedIds = result.installed.mapTo(mutableSetOf()) { it.id }
            return !isBusy && session.candidates.all { it.manifest.id in installedIds }
        }
}

class PluginViewModel(
    private val repository: SourcePluginRepository,
    private val settingsRepository: SettingsRepository,
    private val installer: SourcePluginInstaller,
    private val pluginManager: PluginSearchSourceManager,
    private val appLogRepository: AppLogRepository,
    val online: PluginDistributionManager
) : ViewModel() {
    private val manifestJson = Json { ignoreUnknownKeys = true }
    val plugins: StateFlow<List<SourcePluginEntity>> =
        repository.observePlugins()
            .combine(PluginLocales.preferences) { plugins, locales ->
                plugins.map { plugin ->
                    runCatching {
                        val root = File(plugin.pluginDir)
                        val manifest = manifestJson.decodeFromString<PluginManifest>(File(root, "manifest.json").readText())
                        val localized = PluginStrings.load(root, manifest).snapshot(locales).localize(manifest)
                        plugin.copy(name = localized.name, description = localized.description)
                    }.getOrElse { plugin }
                }
            }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PluginUiState())
    val uiState: StateFlow<PluginUiState> = _uiState.asStateFlow()
    private val actionMutex = Mutex()
    private var manualCheck: Job? = null

    fun checkPluginUpdates() {
        if (manualCheck?.isActive == true) return
        publishMessage(PluginMessage.Status.WORKING)
        manualCheck = viewModelScope.launch {
            online.awaitUpdateCheck()
            publishMessage(PluginMessage.UpdateCheck(online.checkedUpdateCount, online.checkFailures))
        }
    }

    fun updatePlugin(candidate: com.lonx.lyrico.plugin.source.PluginUpdateState) {
        online.update(candidate) { result ->
            publishMessage(PluginMessage.UpdateResult(
                name = candidate.release.name,
                version = candidate.release.versionName,
                error = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            ))
        }
    }

    private val _subscriptionEditor = MutableStateFlow(SubscriptionEditorState())
    val subscriptionEditor = _subscriptionEditor.asStateFlow()

    fun beginSubscriptionEdit() { if (!_subscriptionEditor.value.saving) _subscriptionEditor.value = SubscriptionEditorState() }

    fun saveSubscription(id: String?, name: String, url: String) {
        if (_subscriptionEditor.value.saving) return
        _subscriptionEditor.value = SubscriptionEditorState(saving = true)
        viewModelScope.launch {
            try {
                val source = online.catalogs.save(id, name, url)
                _subscriptionEditor.value = SubscriptionEditorState(savedId = source.id)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _subscriptionEditor.value = SubscriptionEditorState(error = when (error) {
                    is com.lonx.lyrico.plugin.source.DuplicatePluginSubscription -> SubscriptionEditorError.DUPLICATE
                    is IllegalArgumentException -> SubscriptionEditorError.INVALID_URL
                    else -> SubscriptionEditorError.SAVE_FAILED
                })
                logPluginError("Subscription save failed", error.message ?: error.javaClass.simpleName)
            }
        }
    }

    fun deleteSubscription(id: String) = subscriptionMutation { online.catalogs.delete(id) }
    fun setSubscriptionEnabled(id: String, enabled: Boolean) = subscriptionMutation { online.catalogs.setEnabled(id, enabled) }
    fun refreshSubscription(url: String) = subscriptionMutation { online.catalogs.refresh(url, force = true).getOrThrow() }
    private fun subscriptionMutation(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (error: Exception) {
                if (error is CancellationException) throw error
                publishMessage(PluginMessage.Failure(error.message ?: error.javaClass.simpleName))
            }
        }
    }

    fun installFromSubscription(id: String, release: PluginRelease, installed: SourcePluginEntity?) {
        viewModelScope.launch {
            val source = online.catalogs.getSubscriptions().singleOrNull { it.id == id && it.enabled }
            val cache = source?.let { item -> online.catalogs.cache.value.singleOrNull { it.url == item.url } }
            if (source == null || release !in online.catalogs.decode(cache)?.plugins.orEmpty()) {
                publishMessage(PluginMessage.Status.CATALOG_CHANGED)
                return@launch
            }
            online.install(release, source.url, installed?.pluginDir,
                allowDowngrade = installed != null && release.versionCode < installed.versionCode) { result ->
                publishMessage(result.fold({ PluginMessage.Status.INSTALLED }, {
                    PluginMessage.Failure(it.message ?: it.javaClass.simpleName)
                }))
            }
        }
    }

    init {
        viewModelScope.launch { installer.synchronizeInstalledPluginManifestMetadata() }
    }

    fun importPlugin(context: Context, uri: Uri) {
        runBusy(PluginMessage.Status.PACKAGE_SCANNED) {
            _uiState.value.pendingImport?.let { installer.discardImport(it) }
            val installRoot = File(context.filesDir, "plugins/sources")
            val input = context.contentResolver.openInputStream(uri)
                ?: throw PluginActionFailure(PluginMessage.Status.CANNOT_OPEN_FILE)
            input.use {
                val session = installer.prepareImport(
                    input = it,
                    installRoot = installRoot
                )
                if (session.candidates.isEmpty()) {
                    installer.discardImport(session)
                    val failureReason = session.failed.firstOrNull()?.reason ?: "No installable plugin found"
                    logPluginError("Import failed", failureReason, session.failed.joinToString("\n") { "${it.rootPath}: ${it.reason}" })
                    throw PluginActionFailure(PluginMessage.Status.NO_INSTALLABLE_PLUGIN, failureReason)
                }
                _uiState.update { state ->
                    state.copy(
                        pendingImport = session,
                        importResult = null,
                        importError = null,
                        selectedImportRoots = session.candidates
                            .filter { candidate -> candidate.versionConflict != PluginVersionConflict.DOWNGRADE }
                            .mapTo(mutableSetOf()) { candidate -> candidate.relativeRootInArchive }
                    )
                }
            }
        }
    }

    fun setImportCandidateSelected(rootPath: String, selected: Boolean) {
        _uiState.update { state ->
            val next = state.selectedImportRoots.toMutableSet()
            if (selected) {
                next += rootPath
            } else {
                next -= rootPath
            }
            state.copy(selectedImportRoots = next)
        }
    }

    fun installPendingImport() {
        val state = _uiState.value
        val session = state.pendingImport ?: run {
            publishMessage(PluginMessage.Status.NO_PENDING_IMPORT)
            return
        }
        val completedIds = state.importResult?.installed.orEmpty().mapTo(mutableSetOf()) { it.id }
        val selected = session.candidates.filter {
            it.relativeRootInArchive in state.selectedImportRoots && it.manifest.id !in completedIds
        }
        if (selected.isEmpty()) {
            publishMessage(PluginMessage.Status.NO_SELECTION)
            return
        }
        val selectedRoots = selected.mapTo(mutableSetOf()) { it.relativeRootInArchive }
        val attemptedIds = selected.mapTo(mutableSetOf()) { it.manifest.id }
        val allowDowngrade = selected.any { it.versionConflict == PluginVersionConflict.DOWNGRADE }
        _uiState.update { it.copy(importError = null) }
        runBusy(successMessage = null) {
            val committed = mutableListOf<SourcePluginEntity>()
            try {
                val result = installer.installPrepared(
                    session = session,
                    enabled = true,
                    selectedRoots = selectedRoots,
                    allowDowngrade = allowDowngrade,
                    discardSession = false,
                    onInstalled = { committed += it }
                )
                val installed = (state.importResult?.installed.orEmpty() + result.installed).distinctBy { it.id }
                val failed = (state.importResult?.failed.orEmpty().filter { it.pluginId !in attemptedIds } + result.failed)
                    .distinctBy { it.pluginId to it.rootPath }
                val combined = PluginInstallResult(installed, failed)
                if (failed.isEmpty()) {
                    installer.discardImport(session)
                    _uiState.update { it.copy(pendingImport = null, selectedImportRoots = emptySet(),
                        importResult = combined, importError = null) }
                    publishMessage(PluginMessage.Status.INSTALLED)
                } else {
                    _uiState.update { it.copy(importResult = combined) }
                    val installedIds = installed.mapTo(mutableSetOf()) { it.id }
                    val refreshed = session.candidates.map { candidate ->
                        val current = repository.getPlugin(candidate.manifest.id)
                        candidate.copy(existingPlugin = current,
                            versionConflict = PluginVersionConflict.between(current?.versionCode, candidate.manifest.versionCode))
                    }
                    val retryRoots = refreshed.filter { candidate ->
                        candidate.manifest.id !in installedIds && failed.any { it.pluginId == candidate.manifest.id } &&
                            (candidate.versionConflict != PluginVersionConflict.DOWNGRADE || allowDowngrade)
                    }.mapTo(mutableSetOf()) { it.relativeRootInArchive }
                    _uiState.update { it.copy(pendingImport = session.copy(candidates = refreshed), importResult = combined, selectedImportRoots = retryRoots,
                        importError = null, message = null, messageVersion = System.nanoTime()) }
                }
            } catch (error: Exception) {
                // A later item may time out after earlier database commits succeeded.
                val installed = (state.importResult?.installed.orEmpty() + committed).distinctBy { it.id }
                val installedIds = installed.mapTo(mutableSetOf()) { it.id }
                _uiState.update { current -> current.copy(
                    importResult = PluginInstallResult(installed,
                        state.importResult?.failed.orEmpty().filter { it.pluginId !in installedIds }),
                    selectedImportRoots = current.selectedImportRoots - session.candidates
                        .filter { it.manifest.id in installedIds }.map { it.relativeRootInArchive }.toSet()
                ) }
                if (error !is CancellationException || error is TimeoutCancellationException) {
                    _uiState.update { it.copy(importError = error.message ?: error.javaClass.simpleName) }
                }
                throw error
            }
        }
    }
    fun dismissPendingImport() {
        _uiState.value.pendingImport?.let(installer::discardImport)
        _uiState.update {
            it.copy(
                pendingImport = null,
                selectedImportRoots = emptySet(),
                importResult = null,
                importError = null
            )
        }
    }

    fun setEnabled(id: String, sourceType: PluginSourceType, enabled: Boolean) {
        viewModelScope.launch {
            installer.mutationMutex.withLock { repository.setEnabled(id, sourceType, enabled) }
        }
    }

    fun setPluginOrder(
        plugins: List<SourcePluginEntity>,
        sourceType: PluginSourceType
    ) {
        viewModelScope.launch {
            installer.mutationMutex.withLock {
                repository.updateSortOrders(ids = plugins.map { it.id }, sourceType = sourceType)
            }
        }
    }

    fun setCustomName(id: String, name: String) {
        viewModelScope.launch {
            installer.mutationMutex.withLock { repository.updateCustomName(id, name) }
            publishMessage(PluginMessage.Status.NAME_SAVED)
        }
    }


    private fun runBusy(successMessage: PluginMessage.Status?, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (actionMutex.isLocked) {
                publishMessage(PluginMessage.Status.BUSY)
                return@launch
            }
            _uiState.update {
                it.copy(
                    isBusy = true,
                    message = PluginMessage.Status.WORKING,
                    messageVersion = System.nanoTime()
                )
            }
            try {
                actionMutex.withLock {
                    withTimeout(ACTION_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            block()
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = successMessage ?: it.message,
                        messageVersion = if (successMessage != null) System.nanoTime() else it.messageVersion
                    )
                }
            } catch (e: TimeoutCancellationException) {
                logPluginError(
                    message = "Plugin action timed out",
                    detail = "Timed out after ${ACTION_TIMEOUT_MS / 1000}s",
                    fullDetail = e.stackTraceToString()
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = PluginMessage.Status.TIMED_OUT,
                        messageVersion = System.nanoTime(),
                        smokeResult = "Timed out after ${ACTION_TIMEOUT_MS / 1000}s"
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = e.message ?: e.javaClass.simpleName
                logPluginError(
                    message = "Plugin action failed",
                    detail = message,
                    fullDetail = e.stackTraceToString()
                )
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = if (e is PluginActionFailure) e.feedback else PluginMessage.Failure(message),
                        messageVersion = System.nanoTime(),
                        smokeResult = message
                    )
                }
            }
        }
    }

    private fun publishMessage(message: PluginMessage) {
        _uiState.update {
            it.copy(
                message = message,
                messageVersion = System.nanoTime()
            )
        }
    }

    fun uninstallPlugin(id: String) {
        runBusy(PluginMessage.Status.DELETED) {
            online.forget(id)
            installer.mutationMutex.withLock {
                val plugin = repository.getPlugin(id)
                if (plugin != null) {
                    pluginManager.invalidate(plugin.id)
                    repository.uninstallPlugin(id)
                    settingsRepository.removePluginSettings(plugin.id)
                    // Existing invocations may still use this directory. Recovery removes it next launch.
                }
            }
        }
    }

    private suspend fun logPluginError(message: String, detail: String, fullDetail: String? = null) {
        try {
            appLogRepository.log(
                level = AppLogLevel.ERROR,
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                detail = fullDetail ?: detail
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write plugin log", e)
        }
    }

    private companion object {
        const val ACTION_TIMEOUT_MS = 30_000L
        const val TAG = "PluginViewModel"
    }
}

private class PluginActionFailure(val feedback: PluginMessage.Status, detail: String? = null) :
    IllegalStateException(detail)
