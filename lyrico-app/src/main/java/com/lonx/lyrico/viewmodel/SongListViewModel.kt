package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.PlaybackRepository
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
    val showScrollTopButton: Boolean = false,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0,
    val searchQuery: String = "",
    val isSearching: Boolean = false
)


@OptIn(ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val updateManager: UpdateManager,
    private val libraryScanManager: LibraryScanManager,
    private val selectionManager: SharedSelectionManager,
    database: LyricoDatabase
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val folderDao = database.folderDao()
    private var batchMatchJob: Job? = null


    val sortInfo: StateFlow<SortInfo> = settingsRepository.sortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortInfo())

    val showScrollTopButton = settingsRepository.showScrollTopButton
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasFolders: StateFlow<Boolean> = folderDao.getAllFolders()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val scanState = libraryScanManager.state

    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState = _uiState.asStateFlow()

    private var preDragSelectedUris = emptySet<String>()

    private val _selectedSongUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongUris = _selectedSongUris.asStateFlow()
    private val _searchType = MutableStateFlow(LocalSearchType.ALL)
    val searchType = _searchType.asStateFlow()
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    val songs: StateFlow<List<SongEntity>> = combine(
        sortInfo,
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
        searchType
    ) { sort, query, type ->
        Triple(sort, query, type)
    }.flatMapLatest { (sort, query, type) ->
        if (query.isBlank()) {
            songRepository.observeSongs(sort.sortBy, sort.order)
        } else {
            songRepository.searchSongs(query, type)
        }
    }.onEach {
        _uiState.update { it.copy(isSearching = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    fun renameSong(song: SongEntity, newFileName: String) {
        viewModelScope.launch {
            val success = songRepository.renameSong(song, newFileName)
            if (success) {
                libraryScanManager.scanFolders(setOf(song.folderId))
            }
        }
    }


    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = query.isNotBlank()
            )
        }
    }
    fun onSearchTypeChanged(type: LocalSearchType) {
        _searchType.value = type
    }
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        _searchType.value = LocalSearchType.ALL
    }

    fun startDragSelection(index: Int, songs: List<SongEntity>) {
        val song = songs.getOrNull(index) ?: return
        preDragSelectedUris = _selectedSongUris.value

        val rangeUris = setOf(song.uri)

        _selectedSongUris.value = (preDragSelectedUris - rangeUris) + (rangeUris - preDragSelectedUris)
    }

    fun updateDragSelection(startIndex: Int, endIndex: Int, songs: List<SongEntity>) {
        val start = minOf(startIndex, endIndex).coerceAtLeast(0)
        val end = maxOf(startIndex, endIndex).coerceAtMost(songs.size - 1)
        if (start > end) return

        val rangeUris = songs.subList(start, end + 1).map { it.uri }.toSet()

        _selectedSongUris.value = (preDragSelectedUris - rangeUris) + (rangeUris - preDragSelectedUris)
    }

    fun endDragSelection() {
        preDragSelectedUris = emptySet()
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

    fun play(context: Context, song: SongEntity) {
        val uri = song.getUri
        playbackRepository.play(context, uri)
    }
    fun delete(song: SongEntity) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
        }
    }


    fun setScrollToTopButtonEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveShowScrollTopButton(enabled)
        }
    }

    fun onSortChange(newSortInfo: SortInfo) {
        viewModelScope.launch {
            settingsRepository.saveSortInfo(newSortInfo)
        }
    }

    fun toggleSelection(uri: String) {
        if (!_isSelectionMode.value) _isSelectionMode.value = true
        _selectedSongUris.update { if (it.contains(uri)) it - uri else it + uri }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongUris.value = emptySet()
    }

    fun deselectAll() {
        _selectedSongUris.value = emptySet()
    }
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongUris.value = songs.map { it.uri }.toSet()
    }



    fun batchDelete(songs: List<SongEntity>) {
        val selectedUris = _selectedSongUris.value
        val toDelete = songs.filter { it.uri in selectedUris }

        viewModelScope.launch {
            songRepository.deleteSongs(toDelete)
            exitSelectionMode()
        }
    }

    fun batchShare(context: Context, songs: List<SongEntity>) {
        val selectedUris = _selectedSongUris.value
        val toShare = songs.filter { it.uri in selectedUris }
        if (toShare.isEmpty()) return

        val uris = toShare.map { it.uri.toUri() }.toCollection(ArrayList())

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(com.lonx.lyrico.R.string.share_chooser_title))
        )
    }

    fun setSelectionUris(): Boolean {
        val selectedUris = _selectedSongUris.value
        if (selectedUris.isEmpty()) return false

        selectionManager.setUris(selectedUris)
        return true
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
