package com.lonx.lyrico.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrics.model.SearchSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import kotlin.math.abs

@Parcelize
data class SongInfo(
    val filePath: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val selectedSongs: SongEntity? = null,
    val isBatchMatching: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val loadingMessage: String = ""
)

@OptIn(FlowPreview::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SearchSource>,
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
    private val scanRequest = MutableSharedFlow<Unit>(replay = 0)
    // 存储被选中的歌曲 ID (filePath 是唯一的 key)
    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: StateFlow<List<SongEntity>> =
        sortInfo.flatMapLatest { sort ->
            songRepository.getAllSongsSorted(sort.sortBy, sort.order)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )


    init {
        Log.d(TAG, "SongListViewModel 初始化")

        viewModelScope.launch {
            settingsRepository.sortInfo.collect { savedSortInfo ->
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
            scanRequest
                .debounce(2000L) // 2秒防抖
                .collect {
                    if (_uiState.value.isBatchMatching) {
                        Log.d(TAG, "正在批量匹配中，忽略自动同步请求")
                        return@collect
                    }
                    Log.d(TAG, "防抖后触发自动同步")
                    triggerSync(isAuto = true)
                }
        }
    }

    /**
     * 批量匹配歌曲，初步实现，待完善匹配逻辑和数据库同步逻辑
     *
     */
    fun batchMatchLyrics() {
        val selectedPaths = _selectedSongIds.value
        if (selectedPaths.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isBatchMatching = true, loadingMessage = "准备匹配...") }

            val songsToMatch = _allSongs.value.filter { it.mediaId in selectedPaths }

            // 记录哪些文件确实修改成功了
            val successFiles = mutableListOf<String>()

            songsToMatch.forEachIndexed { index, song ->
                _uiState.update { it.copy(batchProgress = (index + 1) to songsToMatch.size) }

                val isSuccess = matchSong(song)

                if (isSuccess) {
                    successFiles.add(song.filePath)
                    _uiState.update { it.copy(successCount = it.successCount + 1) }
                } else {
                    _uiState.update { it.copy(failureCount = it.failureCount + 1) }
                }
                delay(600) // 频率限制
            }

            if (successFiles.isNotEmpty()) {
                _uiState.update { it.copy(loadingMessage = "正在同步数据库...") }

                songRepository.synchronizeWithDevice(false)

            }

            _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "全部匹配完成") }
            delay(3000)
            exitSelectionMode()
        }
    }
    /**
     * 匹配单个歌曲，初步实现，待完善匹配逻辑和数据库同步逻辑
     *
     */
    private suspend fun matchSong(song: SongEntity): Boolean {
        val artistClean = song.artist?.let { if (it.contains("未知", ignoreCase = true)) "" else it } ?: ""
        val query = "${song.title} $artistClean".trim()
        // TODO: 目前使用搜索源的默认排序方式，如果在第一个源匹配到内容就不再搜索其他源，待完善
        for (source in sources) {
            try {
                val results = source.search(query, pageSize = 3)
                if (results.isEmpty()) continue
                val bestMatch = results.minByOrNull { abs(it.duration - song.durationMilliseconds) } ?: results.first()
                val lyricsResult = source.getLyrics(bestMatch)

                val tagData = AudioTagData(
                    title = song.title?.takeIf { !it.contains("未知", true) } ?: bestMatch.title,
                    artist = song.artist?.takeIf { !it.contains("未知", true) } ?: bestMatch.artist,
                    album = song.album?.takeIf { !it.contains("未知", true) } ?: bestMatch.album,
                    lyrics = lyricsResult?.let { LyricsUtils.formatLrcResult(it) },
                    picUrl = bestMatch.picUrl,
                    date = bestMatch.date,
                    trackerNumber = bestMatch.trackerNumber
                )

                // 记录旧时间戳
                val oldTime = song.fileLastModified

                // 写入物理文件
                val ok = songRepository.writeAudioTagData(song.filePath, tagData)

                if (ok) {
                    // 立即恢复时间戳，确保 synchronizeWithDevice 时
                    restoreFileTimestamp(song.filePath, oldTime)
                    return true
                }
            } catch (e: Exception) {
                Log.e("BatchMatch", "File write failed: ${song.title}", e)
            }
        }
        return false
    }
    private fun restoreFileTimestamp(path: String, timestamp: Long) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.setLastModified(timestamp)
            }
        } catch (e: Exception) {
            Log.e("BatchMatch", "恢复时间戳失败", e)
        }
    }

    fun toggleSelection(mediaId: Long) {
        // 如果还没进入多选模式，点击时自动进入
        if (!_isSelectionMode.value) {
            _isSelectionMode.value = true
        }

        val current = _selectedSongIds.value
        if (current.contains( mediaId)) {
            _selectedSongIds.value = current - mediaId
        } else {
            _selectedSongIds.value = current + mediaId
        }
    }

    // 全选
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongIds.value = songs.map { it.mediaId }.toSet()
    }

    // 退出多选模式
    fun exitSelectionMode() {
        _isSelectionMode.value = false      // 显式关闭模式
        _selectedSongIds.value = emptySet() // 清空选择
    }
    fun selectedSong(song: SongEntity) {
        _uiState.update { it.copy(selectedSongs = song) }
    }
    fun clearSelectedSong(){
        _uiState.update { it.copy(selectedSongs = null) }
    }
    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            Log.d(TAG, "MediaStore 变更, 请求自动同步")
            viewModelScope.launch {
                scanRequest.emit(Unit)
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
            settingsRepository.saveSortInfo(newSortInfo)
        }
    }

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongsCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerSync(isAuto = false)
            }
        }
    }

    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            val message = if (isAuto) "检测到文件变化，正在更新..." else "正在扫描歌曲..."
            _uiState.update { it.copy(isLoading = true, loadingMessage = message) }
            try {
                songRepository.synchronizeWithDevice(false)
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "同步失败: ${e.message}") }
            }
            // Add a small delay to prevent the loading indicator from disappearing too quickly
            delay(500L)
            _uiState.update { it.copy(isLoading = false, loadingMessage = "") }
        }
    }

    fun refreshSongs() {
        if (_uiState.value.isLoading) return
        Log.d(TAG, "用户手动刷新歌曲列表")
        triggerSync(isAuto = false)
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
