package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.SortInfo
import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryFolderRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.utils.LibraryScanManager
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
    val scanningFolderIds: Set<Long> = emptySet(),
    val queuedFolderIds: Set<Long> = emptySet(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class FolderManagerViewModel(
    private val folderRepository: LibraryFolderRepository,
    private val libraryScanManager: LibraryScanManager,
    private val appScope: CoroutineScope,
    private val songLibraryRepository: SongLibraryRepository
) : ViewModel() {

    private companion object {
        const val TAG = "FolderManagerViewModel"
    }


    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _currentFolderId = MutableStateFlow<Long?>(null)
    private val operationError = MutableStateFlow<String?>(null)

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
                songLibraryRepository.observeSongs(sortInfo.sortBy, sortInfo.order, folderId)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val uiState: StateFlow<FolderManagerUiState> =
        combine(
            folderRepository.observeFolders(),
            libraryScanManager.state,
            operationError,
        ) { folders, scanState, folderError ->
            FolderManagerUiState(
                folders = folders,
                scanningFolderIds = scanState.scanningFolderIds,
                queuedFolderIds = scanState.queuedFolderIds,
                error = folderError ?: scanState.error
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
            operationError.value = null
            try {
                folderRepository.remove(folder.id)
                AppLog.record(AppLogType.DATABASE, TAG, "Library folder removed",
                    "folderId=${folder.id}\npath=${folder.path}\ntreeUri=${folder.treeUri}",
                    folder.treeUri, AppLogLevel.INFO)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.record(AppLogType.DATABASE, TAG, "Library folder removal failed",
                    "folderId=${folder.id}\npath=${folder.path}\ntreeUri=${folder.treeUri}", folder.treeUri, AppLogLevel.ERROR, error)
                operationError.value = error.message ?: error::class.java.simpleName
            }
        }
    }

    fun refreshFolder(folder: FolderEntity) {
        libraryScanManager.scanFolders(setOf(folder.id))
    }

    fun setFolderIgnored(folder: FolderEntity, ignored: Boolean) {
        appScope.launch {
            operationError.value = null
            try {
                folderRepository.setIgnored(folder.id, ignored)
                AppLog.record(AppLogType.DATABASE, TAG, "Folder visibility updated",
                    "folderId=${folder.id}\npath=${folder.path}\nignored=$ignored", folder.treeUri, AppLogLevel.INFO)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.record(AppLogType.DATABASE, TAG, "Folder visibility update failed",
                    "folderId=${folder.id}\nignored=$ignored", folder.treeUri, AppLogLevel.ERROR, error)
                operationError.value = error.message ?: error::class.java.simpleName
            }
        }
    }
}
