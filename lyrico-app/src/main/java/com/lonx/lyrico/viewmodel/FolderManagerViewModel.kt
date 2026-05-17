package com.lonx.lyrico.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.utils.LibraryScanManager
import com.lonx.lyrico.utils.UriUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


data class FolderManagerUiState(
    val folders: List<FolderEntity> = emptyList(),
    val scanningFolderIds: Set<Long> = emptySet(),
    val queuedFolderIds: Set<Long> = emptySet(),
    val error: String? = null
)

class FolderManagerViewModel(
    private val database: LyricoDatabase,
    private val libraryScanManager: LibraryScanManager,
    private val application: Application,
    private val appScope: CoroutineScope
) : ViewModel() {

    private companion object {
        const val TAG = "FolderManagerViewModel"
    }

    private val folderDao = database.folderDao()
    private val contentResolver = application.contentResolver

    val uiState: StateFlow<FolderManagerUiState> =
        combine(
            folderDao.getAllFolders(),
            libraryScanManager.state
        ) { folders, scanState ->
            FolderManagerUiState(
                folders = folders,
                scanningFolderIds = scanState.scanningFolderIds,
                queuedFolderIds = scanState.queuedFolderIds,
                error = scanState.error
            )
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                FolderManagerUiState()
            )

    fun addFolder(path: String, treeUri: String) {
        libraryScanManager.addFolderAndScan(path, treeUri)
    }

    fun deleteFolder(folder: FolderEntity) {
        appScope.launch {
            val released = UriUtils.releasePersistedPermission(contentResolver, folder.treeUri)
            if (!released) {
                Log.w(TAG, "Failed to fully release persisted permission for folder: ${folder.path}")
            }
            folderDao.deleteFolderPermanently(folder.id)
        }
    }

    fun refreshFolder(folder: FolderEntity) {
        libraryScanManager.scanFolders(setOf(folder.id))
    }

    fun setFolderIgnored(folder: FolderEntity, ignored: Boolean) {
        appScope.launch {
            folderDao.setIgnored(folder.id, ignored)
        }
    }
}
