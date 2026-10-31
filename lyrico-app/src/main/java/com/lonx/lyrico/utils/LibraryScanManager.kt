package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.repository.LibraryFolderRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.scan.LibraryScanProgress
import com.lonx.lyrico.data.song.scan.LibraryScanRequest
import com.lonx.lyrico.data.song.scan.LibraryScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibraryScanState(
    val isScanning: Boolean = false,
    val progress: LibraryScanProgress? = null,
    val scanningFolderIds: Set<Long> = emptySet(),
    val queuedFolderIds: Set<Long> = emptySet(),
    val queuedScanCount: Int = 0,
    val error: String? = null
)

interface LibraryScanManager {
    val state: StateFlow<LibraryScanState>

    fun scanAll(
        fullRescan: Boolean = false,
        onSuccess: (suspend () -> Unit)? = null
    )
    fun scanFolders(folderIds: Set<Long>, fullRescan: Boolean = false)
    fun addFolderAndScan(path: String, treeUri: String)
    suspend fun clearLibrary(): Boolean
}

class LibraryScanManagerImpl(
    private val appScope: CoroutineScope,
    private val folderRepository: LibraryFolderRepository,
    private val settingsRepository: SettingsRepository,
    private val libraryScanRepository: LibraryScanRepository
) : LibraryScanManager {

    private val _state = MutableStateFlow(LibraryScanState())
    override val state: StateFlow<LibraryScanState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private val pendingRequests = ArrayDeque<ScanRequest>()
    private val queueLock = Any()
    private val requestMutex = Mutex()
    private var requestGeneration = 0L

    override fun scanAll(
        fullRescan: Boolean,
        onSuccess: (suspend () -> Unit)?
    ) {
        requestScan(
            ScanRequest(
                fullRescan = fullRescan,
                folderIds = null,
                onSuccessActions = listOfNotNull(onSuccess)
            )
        )
    }

    override fun scanFolders(folderIds: Set<Long>, fullRescan: Boolean) {
        if (folderIds.isEmpty()) return
        requestScan(ScanRequest(fullRescan = fullRescan, folderIds = folderIds))
    }

    override fun addFolderAndScan(path: String, treeUri: String) {
        val generation = synchronized(queueLock) { requestGeneration }
        appScope.launch {
            try {
                requestMutex.withLock {
                    if (generation != synchronized(queueLock) { requestGeneration }) return@withLock
                    val id = folderRepository.register(path, treeUri)
                    enqueueScan(ScanRequest(fullRescan = false, folderIds = setOf(id)))
                    AppLog.record(AppLogType.APP, "LibraryFolders", "Library folder registered",
                        "path=$path\ntreeUri=$treeUri\nfolderId=$id\nscanQueued=true", treeUri, AppLogLevel.INFO)
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.record(AppLogType.APP, "LibraryFolders", "Library folder registration failed",
                    "path=$path\ntreeUri=$treeUri", treeUri, AppLogLevel.ERROR, error)
                _state.update { it.copy(error = error.message ?: error::class.java.simpleName) }
            }
        }
    }

    override suspend fun clearLibrary(): Boolean {
        // Invalidate requests whose app-scope coroutines have not entered the queue yet.
        synchronized(queueLock) { requestGeneration++ }
        return requestMutex.withLock {
            val active = synchronized(queueLock) {
                pendingRequests.clear()
                scanJob.also { scanJob = null; it?.cancel() }
            }
            active?.join()
            updateQueuedState()
            val cleared = folderRepository.clear()
            _state.update { it.copy(error = null) }
            cleared
        }
    }

    private fun requestScan(request: ScanRequest) {
        val generation = synchronized(queueLock) { requestGeneration }
        appScope.launch {
            requestMutex.withLock {
                if (generation == synchronized(queueLock) { requestGeneration }) enqueueScan(request)
            }
        }
    }

    private fun enqueueScan(request: ScanRequest) {
        AppLog.record(AppLogType.APP, "LibraryScanQueue", "Scan requested",
            "roots=${request.folderIds}\nfullRescan=${request.fullRescan}\ncurrentlyScanning=${state.value.isScanning}")
        synchronized(queueLock) {
            mergePendingRequest(request)
            updateQueuedState()

            if (scanJob?.isActive != true) {
                scanJob = appScope.launch { processQueue() }
            }
        }
    }

    private fun mergePendingRequest(request: ScanRequest) {
        if (request.folderIds == null) {
            val merged = request.copy(
                fullRescan = request.fullRescan || pendingRequests.any { it.fullRescan },
                onSuccessActions = pendingRequests.flatMap { it.onSuccessActions } + request.onSuccessActions
            )
            pendingRequests.clear()
            pendingRequests.addLast(merged)
            return
        }

        val allFoldersIndex = pendingRequests.indexOfFirst { it.folderIds == null }
        if (allFoldersIndex >= 0) {
            val existing = pendingRequests[allFoldersIndex]
            pendingRequests[allFoldersIndex] = existing.copy(
                fullRescan = existing.fullRescan || request.fullRescan,
                onSuccessActions = existing.onSuccessActions + request.onSuccessActions
            )
            return
        }

        val existingIndex = pendingRequests.indexOfFirst {
            it.folderIds != null && it.fullRescan == request.fullRescan
        }
        if (existingIndex >= 0) {
            val existing = pendingRequests[existingIndex]
            pendingRequests[existingIndex] = existing.copy(
                folderIds = existing.folderIds.orEmpty() + request.folderIds,
                onSuccessActions = existing.onSuccessActions + request.onSuccessActions
            )
        } else {
            pendingRequests.addLast(request)
        }
    }

    private suspend fun processQueue() {
        while (true) {
            val request = synchronized(queueLock) {
                if (pendingRequests.isEmpty()) {
                    scanJob = null
                    null
                } else {
                    pendingRequests.removeFirst()
                }
            } ?: break

            updateQueuedState()
            _state.update {
                it.copy(
                    isScanning = true,
                    progress = null,
                    scanningFolderIds = request.folderIds.orEmpty(),
                    error = null
                )
            }

            try {
                val result = libraryScanRepository.synchronize(
                    request = LibraryScanRequest(
                        fullRescan = request.fullRescan,
                        folderIds = request.folderIds,
                        ignoreShortAudio = settingsRepository.ignoreShortAudio.first()
                    ),
                    onProgress = { progress ->
                        _state.update { it.copy(progress = progress) }
                    }
                )
                if (result.failures.isEmpty()) {
                    request.onSuccessActions.forEach { action -> action() }
                } else {
                    _state.update {
                        it.copy(error = result.failures.joinToString("\n") { failure ->
                            "${failure.fileName ?: failure.uri.orEmpty()}: ${failure.message}"
                        })
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(error = e.message ?: e::class.java.simpleName) }
            } finally {
                _state.update {
                    it.copy(
                        isScanning = false,
                        progress = null,
                        scanningFolderIds = emptySet()
                    )
                }
            }
        }
    }

    private fun updateQueuedState() {
        val (queuedCount, queuedFolderIds) = synchronized(queueLock) {
            pendingRequests.size to pendingRequests
                .flatMap { request -> request.folderIds.orEmpty() }
                .toSet()
        }
        _state.update {
            it.copy(
                queuedScanCount = queuedCount,
                queuedFolderIds = queuedFolderIds
            )
        }
    }

    private data class ScanRequest(
        val fullRescan: Boolean,
        val folderIds: Set<Long>?,
        val onSuccessActions: List<suspend () -> Unit> = emptyList()
    )
}
