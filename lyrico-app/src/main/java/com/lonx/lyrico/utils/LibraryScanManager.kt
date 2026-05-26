package com.lonx.lyrico.utils

import android.content.Context
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.repository.LibraryScanProgress
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
}

class LibraryScanManagerImpl(
    private val appScope: CoroutineScope,
    private val context: Context,
    private val database: LyricoDatabase,
    private val songRepository: SongRepository
) : LibraryScanManager {

    private val folderDao = database.folderDao()
    private val _state = MutableStateFlow(LibraryScanState())
    override val state: StateFlow<LibraryScanState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private val pendingRequests = ArrayDeque<ScanRequest>()
    private val queueLock = Any()

    override fun scanAll(
        fullRescan: Boolean,
        onSuccess: (suspend () -> Unit)?
    ) {
        enqueueScan(
            ScanRequest(
                fullRescan = fullRescan,
                folderIds = null,
                onSuccessActions = listOfNotNull(onSuccess)
            )
        )
    }

    override fun scanFolders(folderIds: Set<Long>, fullRescan: Boolean) {
        if (folderIds.isEmpty()) return
        enqueueScan(ScanRequest(fullRescan = fullRescan, folderIds = folderIds))
    }

    override fun addFolderAndScan(path: String, treeUri: String) {
        appScope.launch {
            releaseRedundantSafPermissions(path, treeUri)
            val id = folderDao.upsertAndGetId(
                path = path,
                treeUri = treeUri,
                addedBySaf = true
            )
            folderDao.setIgnored(id, false)
            enqueueScan(ScanRequest(fullRescan = false, folderIds = setOf(id)))
        }
    }

    private suspend fun releaseRedundantSafPermissions(path: String, treeUri: String) {
        val normalizedPath = normalizeFolderPath(path)
        val allFolders = folderDao.getAllFoldersOnce()

        val existing = allFolders.firstOrNull { folder ->
            normalizeFolderPath(folder.path) == normalizedPath
        }
        if (existing != null && existing.treeUri != treeUri) {
            releaseFolderPermission(existing)
        }

        val existingParent = allFolders
            .filter { folder ->
                folder.id != existing?.id &&
                        isParentFolder(
                            parentPath = normalizeFolderPath(folder.path),
                            childPath = normalizedPath
                        )
            }
            .maxByOrNull { folder -> normalizeFolderPath(folder.path).length }
        if (existingParent != null) {
            UriUtils.releasePersistedPermission(context.contentResolver, treeUri)
            return
        }

        allFolders
            .filter { folder ->
                isParentFolder(
                    parentPath = normalizedPath,
                    childPath = normalizeFolderPath(folder.path)
                )
            }
            .forEach { folder -> releaseFolderPermission(folder) }
    }

    private fun releaseFolderPermission(folder: FolderEntity) {
        if (folder.addedBySaf) {
            UriUtils.releasePersistedPermission(context.contentResolver, folder.treeUri)
        }
    }

    private fun normalizeFolderPath(path: String): String {
        val normalized = path
            .replace('\\', '/')
            .trim()
            .trimEnd('/')

        return normalized.ifBlank { path.trim() }
    }

    private fun isParentFolder(parentPath: String, childPath: String): Boolean {
        if (parentPath.isBlank() || childPath.isBlank()) return false
        if (parentPath == childPath) return false
        return childPath.startsWith("$parentPath/")
    }

    private fun enqueueScan(request: ScanRequest) {
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
            pendingRequests.clear()
            pendingRequests.addLast(request)
            return
        }

        if (pendingRequests.any { it.folderIds == null }) return

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
                songRepository.synchronize(
                    fullRescan = request.fullRescan,
                    folderIds = request.folderIds
                ) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                request.onSuccessActions.forEach { action -> action() }
            } catch (e: Exception) {
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
