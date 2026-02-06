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
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Job
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
    val currentFile: String = "",
    val loadingMessage: String = ""
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SearchSource>,
    application: Application
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val contentResolver = application.contentResolver
    private var musicContentObserver: MusicContentObserver? = null
    private val scanRequest = MutableSharedFlow<Unit>(replay = 0)
    private var batchMatchJob: Job? = null

    val sortInfo: StateFlow<SortInfo> = settingsRepository.sortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortInfo())

    private val searchSourceOrder: StateFlow<List<Source>> = settingsRepository.searchSourceOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(Source.KG, Source.QM, Source.NE))

    // 当排序信息改变时，歌曲列表自动重新加载
    val songs: StateFlow<List<SongEntity>> = sortInfo
        .flatMapLatest { sort ->
            songRepository.getAllSongsSorted(sort.sortBy, sort.order)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI 交互状态
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState = _uiState.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()

    init {
        registerMusicObserver()

        // 自动同步监听
        viewModelScope.launch {
            scanRequest.debounce(2000L).collect {
                if (!_uiState.value.isBatchMatching) triggerSync(isAuto = true)
            }
        }
    }

    /**
     * 批量匹配歌曲，初步实现，待完善匹配逻辑和数据库同步逻辑
     *
     */
    fun batchMatchLyrics() {
        val selectedIds = _selectedSongIds.value
        if (selectedIds.isEmpty()) return

        batchMatchJob = viewModelScope.launch {
            val songsToMatch = songs.value.filter { it.mediaId in selectedIds }
            val currentOrder = searchSourceOrder.value

            _uiState.update { it.copy(
                isBatchMatching = true,
                successCount = 0,
                failureCount = 0,
                loadingMessage = "准备匹配...",
                batchProgress = 0 to songsToMatch.size
            ) }

            val successFiles = mutableListOf<String>()

            songsToMatch.forEachIndexed { index, song ->
                _uiState.update { it.copy(
                    batchProgress = (index + 1) to songsToMatch.size,
                    currentFile = song.fileName
                ) }

                val isSuccess = matchSingleSong(song, currentOrder)
                if (isSuccess) {
                    successFiles.add(song.filePath)
                    _uiState.update { it.copy(successCount = it.successCount + 1) }
                } else {
                    _uiState.update { it.copy(failureCount = it.failureCount + 1) }
                }
                delay(600) // 频率限制
            }

            if (successFiles.isNotEmpty()) {
                _uiState.update { it.copy(loadingMessage = "正在更新数据库...") }
                songRepository.synchronizeWithDevice(false)

            }

            _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "匹配完成") }
        }
    }
    private suspend fun matchSingleSong(song: SongEntity, order: List<Source>): Boolean {
        val queries = MusicMatchUtils.buildSearchQueries(song)
        val (parsedTitle, parsedArtist) = MusicMatchUtils.parseFileName(song.fileName)
        val queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
        val queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist

        val scoredResults = mutableListOf<ScoredSearchResult>()
        val orderedSources = sources.sortedBy { s ->
            order.indexOf(s.sourceType).let { if (it == -1) Int.MAX_VALUE else it }
        }

        for (query in queries) {
            for (source in orderedSources) {
                try {
                    val results = source.search(query, pageSize = 2)
                    results.forEach { res ->
                        val score = MusicMatchUtils.calculateMatchScore(res, song, queryTitle, queryArtist)
                        scoredResults.add(ScoredSearchResult(res, score, source))
                    }
                } catch (e: Exception) { /* Log error */ }
            }
            if (scoredResults.any { it.score > 0.75 }) break
        }

        val bestMatch = scoredResults.maxByOrNull { it.score } ?: return false
        if (bestMatch.score < 0.35) return false

        return try {
            val lyrics = bestMatch.source.getLyrics(bestMatch.result)
            val lyricDisplayMode = settingsRepository.lyricDisplayMode.first()
            val tagData = AudioTagData(
                title = song.title?.takeIf { !it.contains("未知", true) } ?: bestMatch.result.title,
                artist = song.artist?.takeIf { !it.contains("未知", true) } ?: bestMatch.result.artist,
                lyrics = lyrics?.let { LyricsUtils.formatLrcResult(it, lineByLine = lyricDisplayMode == com.lonx.lyrico.data.model.LyricDisplayMode.LINE_BY_LINE) },
                picUrl = bestMatch.result.picUrl
            )
            val oldTime = song.fileLastModified
            if (songRepository.writeAudioTagData(song.filePath, tagData)) {
                File(song.filePath).setLastModified(oldTime) // 恢复时间戳避免触发系统全量扫描
                true
            } else false
        } catch (e: Exception) { false }
    }


    fun onSortChange(newSortInfo: SortInfo) {
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
    fun toggleSelection(mediaId: Long) {
        if (!_isSelectionMode.value) _isSelectionMode.value = true
        _selectedSongIds.update { if (it.contains(mediaId)) it - mediaId else it + mediaId }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongIds.value = emptySet()
    }

    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            val message = if (isAuto) "检测到文件变化，正在更新..." else "正在扫描歌曲..."
            _uiState.update { it.copy(isLoading = true, loadingMessage = message) }
            try {
                songRepository.synchronizeWithDevice(false)
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
            } finally {
                delay(500L)
                _uiState.update { it.copy(isLoading = false, loadingMessage = "") }
            }
        }
    }

    fun refreshSongs() {
        if (_uiState.value.isLoading) return
        Log.d(TAG, "用户手动刷新歌曲列表")
        triggerSync(isAuto = false)
    }
    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            viewModelScope.launch { scanRequest.emit(Unit) }
        }
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, musicContentObserver!!)
    }

    /**
     * 单选某首歌曲（用于详情展示）
     */
    fun selectedSong(song: SongEntity) {
        _uiState.update { it.copy(selectedSongs = song) }
    }

    fun clearSelectedSong() {
        _uiState.update { it.copy(selectedSongs = null) }
    }
    /**
     * 中止批量匹配
     */
    fun abortBatchMatch() {
        batchMatchJob?.cancel()
        batchMatchJob = null
        _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "已中止") }
    }
    fun closeBatchMatchDialog() {
        _uiState.update {
            it.copy(
                batchProgress = null,
                currentFile = "",
                loadingMessage = ""
            )
        }
        exitSelectionMode()
    }
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongIds.value = songs.map { it.mediaId }.toSet()
    }
    override fun onCleared() {
        musicContentObserver?.let { contentResolver.unregisterContentObserver(it) }
        batchMatchJob?.cancel()
        super.onCleared()
    }

    private data class ScoredSearchResult(
        val result: com.lonx.lyrics.model.SongSearchResult,
        val score: Double,
        val source: SearchSource
    )
}
