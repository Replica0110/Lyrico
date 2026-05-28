package com.lonx.lyrico.viewmodel

import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.search.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.search.SongSearchRepository
import com.lonx.lyrico.utils.LibraryScanManager
import com.lonx.lyrico.utils.UpdateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Parcelize
data class SongInfo(
    val uriString: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val isBatchMatching: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0,
    val searchQuery: String = "",
    val isSearching: Boolean = false
)


@OptIn(ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val songLibraryRepository: SongLibraryRepository,
    private val songSearchRepository: SongSearchRepository,
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
    private val libraryScanManager: LibraryScanManager,
    database: LyricoDatabase
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val folderDao = database.folderDao()
    private var batchMatchJob: Job? = null


    val sortInfo: StateFlow<SortInfo> = settingsRepository.sortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortInfo())


    val hasFolders: StateFlow<Boolean> = folderDao.getAllFolders()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanState = libraryScanManager.state

    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState = _uiState.asStateFlow()

    private val _searchType = MutableStateFlow(LocalSearchType.ALL)
    val searchType = _searchType.asStateFlow()
    val songs: StateFlow<List<SongEntity>> = combine(
        sortInfo,
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
        searchType
    ) { sort, query, type ->
        Triple(sort, query, type)
    }.flatMapLatest { (sort, query, type) ->
        if (query.isBlank()) {
            songLibraryRepository.observeSongs(sort.sortBy, sort.order)
        } else {
            songSearchRepository.searchSongs(query, type)
        }
    }.onEach {
        _uiState.update { it.copy(isSearching = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        _searchType.value = LocalSearchType.ALL
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val checkUpdateEnabled = settingsRepository.checkUpdateEnabled.first()
            if (checkUpdateEnabled) {
                Log.d(TAG, "检查更新")
                updateManager.checkForUpdate()
            }
        }
    }

    fun onSortChange(newSortInfo: SortInfo) {
        viewModelScope.launch {
            settingsRepository.saveSortInfo(newSortInfo)
        }
    }
    fun refreshSongs() {
        Log.d(TAG, "用户手动刷新歌曲列表")
        libraryScanManager.scanAll()
    }

    fun addSafFolderAndRefresh(path: String, treeUri: String) {
        libraryScanManager.addFolderAndScan(path, treeUri)
    }

    override fun onCleared() {
        batchMatchJob?.cancel()
        super.onCleared()
    }
}
