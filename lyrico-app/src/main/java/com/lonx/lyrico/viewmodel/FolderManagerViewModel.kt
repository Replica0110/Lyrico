package com.lonx.lyrico.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.utils.SongQueryBuilder
import com.lonx.lyrico.utils.LibraryScanManager
import com.lonx.lyrico.utils.UriUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FolderManagerUiState(
    val folders: List<FolderEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val scanningFolderIds: Set<Long> = emptySet(),
    val queuedFolderIds: Set<Long> = emptySet(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FolderManagerViewModel(
    private val database: LyricoDatabase,
    private val libraryScanManager: LibraryScanManager,
    private val application: Application,
    private val appScope: CoroutineScope,
    private val libraryIndexRepository: LibraryIndexRepository
) : ViewModel() {

    private companion object {
        const val TAG = "FolderManagerViewModel"
    }

    private val folderDao = database.folderDao()
    private val songDao = database.songDao()
    private val contentResolver = application.contentResolver

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)

    val currentFolderSongs: StateFlow<List<SongEntity>> =
        combine(
            _currentFolderId,
            _sortInfo
        ) { folderId, sortInfo ->
            folderId to sortInfo
        }.flatMapLatest { (folderId, sortInfo) ->
            if (folderId == null) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                val query = SongQueryBuilder.build(sortInfo, folderId)
                songDao.getSongs(query)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val uiState: StateFlow<FolderManagerUiState> =
        combine(
            folderDao.getAllFolders(),
            songDao.getAllSongs(),
            libraryScanManager.state
        ) { folders, songs, scanState ->
            FolderManagerUiState(
                folders = folders,
                songs = songs,
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

    fun setCurrentFolderId(folderId: Long?) {
        _currentFolderId.value = folderId
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
    }

    fun addFolder(path: String, treeUri: String) {
        libraryScanManager.addFolderAndScan(path, treeUri)
    }

    fun deleteFolder(folder: FolderEntity) {
        appScope.launch {
            val released = UriUtils.releasePersistedPermission(contentResolver, folder.treeUri)
            if (!released) {
                Log.w(TAG, "Failed to fully release persisted permission for folder: ${folder.path}")
            }
            folderDao.deleteFolderTreePermanently(folder.id)
            libraryIndexRepository.refreshAndPruneIndexes()
        }
    }

    fun refreshFolder(folder: FolderEntity) {
        libraryScanManager.scanFolders(setOf(folder.id))
    }

    fun setFolderIgnored(folder: FolderEntity, ignored: Boolean) {
        appScope.launch {
            folderDao.setIgnoredRecursively(folder.id, ignored)
            libraryIndexRepository.refreshAndPruneIndexes()
        }
    }
}