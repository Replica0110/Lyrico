package com.lonx.lyrico.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrico.utils.MusicScanner
import com.lonx.lyrico.utils.SettingsManager
import java.text.Collator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    private val songRepository: SongRepository,
    private val settingsManager: SettingsManager,
    application: Application
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState: StateFlow<SongListUiState> = _uiState.asStateFlow()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    private val _allSongs = MutableStateFlow<List<SongEntity>>(emptyList())

    private val contentResolver = application.contentResolver
    private var musicContentObserver: MusicContentObserver? = null
    private val incrementalScanRequest = MutableSharedFlow<Unit>(replay = 0)

    val songs: StateFlow<List<SongEntity>> = combine(
        _allSongs,
        _sortInfo
    ) { songs, sort ->
        val collator = Collator.getInstance()
        val sortedList = when (sort.sortBy) {
            SortBy.TITLE -> {
                val comparator = Comparator<SongEntity> { a, b ->
                    collator.compare(a.title ?: a.fileName, b.title ?: b.fileName)
                }
                songs.sortedWith(if (sort.order == SortOrder.ASC) comparator else comparator.reversed())
            }
            SortBy.ARTIST -> {
                val comparator = Comparator<SongEntity> { a, b ->
                    collator.compare(a.artist ?: "未知艺术家", b.artist ?: "未知艺术家")
                }
                songs.sortedWith(if (sort.order == SortOrder.ASC) comparator else comparator.reversed())
            }
            SortBy.DATE_MODIFIED -> {
                if (sort.order == SortOrder.ASC) {
                    songs.sortedBy { it.fileLastModified }
                } else {
                    songs.sortedByDescending { it.fileLastModified }
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

        viewModelScope.launch {
            settingsManager.getSortInfo().collect { savedSortInfo ->
                _sortInfo.value = savedSortInfo
            }
        }

        viewModelScope.launch {
            songRepository.getAllSongs().collect { songList ->
                _allSongs.value = songList
            }
        }
        registerMusicObserver()

        viewModelScope.launch {
            incrementalScanRequest
                .debounce(2000L) // 2秒防抖
                .collect {
                    Log.d(TAG, "防抖后触发增量扫描")
                    triggerIncrementalScan()
                }
        }
    }

    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            Log.d(TAG, "MediaStore 变更, 请求增量扫描")
            viewModelScope.launch {
                incrementalScanRequest.emit(Unit)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            musicContentObserver!!
        )
        Log.d(TAG, "MusicContentObserver registered.")
    }

    fun onSortChange(newSortInfo: SortInfo) {
        _sortInfo.value = newSortInfo
        viewModelScope.launch {
            settingsManager.saveSortInfo(newSortInfo)
        }
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

                if (forceFullScan) {
                    settingsManager.saveLastScanTime(System.currentTimeMillis())
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

    private fun triggerIncrementalScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "检测到文件变化，正在更新...") }
            songRepository.incrementalScan()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshSongs(forceFullScan: Boolean = false) {
        Log.d(TAG, "用户手动刷新歌曲列表 (forceFullScan=$forceFullScan)")
        if (forceFullScan) {
            triggerScan(true)
        } else {
            // Manually requested incremental scan should also be debounced
            viewModelScope.launch {
                incrementalScanRequest.emit(Unit)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
            Log.d(TAG, "MusicContentObserver unregistered.")
        }
        Log.d(TAG, "SongListViewModel 已清理")
    }
}
