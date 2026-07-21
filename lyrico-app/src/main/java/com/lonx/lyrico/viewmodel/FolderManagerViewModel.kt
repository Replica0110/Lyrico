package com.lonx.lyrico.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderManagerUiState(
    val folders: List<FolderEntity> = emptyList(),
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
    private val libraryIndexRepository: LibraryIndexRepository,
    private val songLibraryRepository: SongLibraryRepository
) : ViewModel() {

    private companion object {
        const val TAG = "FolderManagerViewModel"
    }

    private val folderDao = database.folderDao()
    private val contentResolver = application.contentResolver

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _dedupResult = MutableStateFlow<String?>(null)
    val dedupResult: StateFlow<String?> = _dedupResult.asStateFlow()

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
                songLibraryRepository.observeSongs(sortInfo.sortBy, sortInfo.order, folderId)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

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

    /** 手动执行去重：基于 title+artist 检测并删除重复歌曲 */
    fun runDedup() {
        appScope.launch {
            val songs = songLibraryRepository.getSongsForDedup()
            val groups = songs.groupBy {
                Pair(
                    it.title?.trim()?.lowercase() ?: "",
                    it.artist?.trim()?.lowercase() ?: ""
                )
            }.filter { it.value.size > 1 }

            val toDelete = groups.flatMap { (_, list) -> list.drop(1).map { it.id } }
            if (toDelete.isNotEmpty()) {
                songLibraryRepository.deleteSongsByIds(toDelete)
                libraryIndexRepository.refreshAndPruneIndexes()
                Log.d(TAG, "Dedup: removed ${toDelete.size} duplicate songs")
            }
            _dedupResult.value = if (toDelete.isEmpty()) {
                "未发现重复歌曲"
            } else {
                "已移除 ${toDelete.size} 首重复歌曲"
            }
        }
    }

    fun clearDedupResult() {
        _dedupResult.value = null
    }
}
