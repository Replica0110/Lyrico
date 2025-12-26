package com.lonx.lyrico.viewmodel

import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.MusicScanner
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongInfo(
    val filePath: String,
    val fileName: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val loadingMessage: String? = null
)

@OptIn(FlowPreview::class)
class SongListViewModel(
    private val musicScanner: MusicScanner,
    private val songRepository: SongRepository
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _allSongs = MutableStateFlow<List<SongEntity>>(emptyList())

    val songs: StateFlow<List<SongEntity>> = combine(
        _allSongs,
        _searchQuery.debounce(300),
        _sortInfo
    ) { songs, query, sort ->
        val filteredList = if (query.isBlank()) {
            songs
        } else {
            songs.filter { song ->
                song.title?.contains(query, ignoreCase = true) == true ||
                        song.artist?.contains(query, ignoreCase = true) == true ||
                        song.album?.contains(query, ignoreCase = true) == true ||
                        song.fileName.contains(query, ignoreCase = true)
            }
        }

        val collator = Collator.getInstance()
        val sortedList = when (sort.sortBy) {
            SortBy.TITLE -> {
                val comparator = Comparator<SongEntity> { a, b ->
                    collator.compare(a.title ?: a.fileName, b.title ?: b.fileName)
                }
                filteredList.sortedWith(if (sort.order == SortOrder.ASC) comparator else comparator.reversed())
            }
            SortBy.ARTIST -> {
                val comparator = Comparator<SongEntity> { a, b ->
                    collator.compare(a.artist ?: "未知艺术家", b.artist ?: "未知艺术家")
                }
                filteredList.sortedWith(if (sort.order == SortOrder.ASC) comparator else comparator.reversed())
            }
            SortBy.DATE_MODIFIED -> {
                if (sort.order == SortOrder.ASC) {
                    filteredList.sortedBy { it.fileLastModified }
                } else {
                    filteredList.sortedByDescending { it.fileLastModified }
                }
            }
        }
        sortedList
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        Log.d(TAG, "SongListViewModel 初始化")
        // Just collect songs from the database. The UI is responsible for triggering the initial scan.
        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _allSongs.value = songList
            }
        }
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongsCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerScan(forceFullScan = true)
            }
        }
    }

    private fun triggerScan(forceFullScan: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Log.d(TAG, "开始后台扫描文件 (forceFullScan=$forceFullScan)")
                _uiState.update { it.copy(isLoading = true, loadingMessage = "正在准备扫描...") }

                if (forceFullScan) {
                    musicScanner.clearCache()
                }

                val songFiles = mutableListOf<com.lonx.lyrico.data.model.SongFile>()
                musicScanner.scanMusicFiles(emptyList())
                    .collect { songFile ->
                        songFiles.add(songFile)
                        _uiState.update { it.copy(loadingMessage = "正在扫描: ${songFile.fileName}") }
                    }

                Log.d(TAG, "扫描发现 ${songFiles.size} 个音乐文件，正在存入数据库...")
                _uiState.update { it.copy(loadingMessage = "正在将 ${songFiles.size} 首歌曲存入数据库...") }


                withContext(Dispatchers.IO) {
                    songRepository.scanAndSaveSongs(songFiles, forceFullScan = forceFullScan)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lastScanTime = System.currentTimeMillis(),
                        loadingMessage = null
                    )
                }
                Log.d(TAG, "后台扫描完成")

            } catch (e: Exception) {
                Log.e(TAG, "后台扫描失败", e)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "扫描失败: ${e.message}") }
            }
        }
    }

    fun refreshSongs(forceFullScan: Boolean = false) {
        Log.d(TAG, "用户手动刷新歌曲列表 (forceFullScan=$forceFullScan)")
        triggerScan(forceFullScan)
    }



    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "SongListViewModel 已清理")
    }
}
