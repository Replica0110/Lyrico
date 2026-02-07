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
import com.lonx.lyrico.data.model.LyricDisplayMode
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.Collections

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
    val loadingMessage: String = "",
    val batchTimeMillis: Long = 0  // 批量匹配总用时（毫秒）
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
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val separator = settingsRepository.separator
        .stateIn(viewModelScope, SharingStarted.Eagerly, "/")


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
     * 批量匹配歌曲（支持并发控制）
     * @param parallelism 并发数
     */
    fun batchMatch(parallelism: Int = 3) {
        val selectedIds = _selectedSongIds.value
        val separator = separator.value
        if (selectedIds.isEmpty()) return

        batchMatchJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val songsToMatch = songs.value.filter { it.mediaId in selectedIds }
            val currentOrder = searchSourceOrder.value
            val total = songsToMatch.size

            _uiState.update { it.copy(
                isBatchMatching = true,
                successCount = 0,
                failureCount = 0,
                batchProgress = 0 to total,
                batchTimeMillis = 0
            ) }

            val semaphore = Semaphore(parallelism)
            val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val matchResults = Collections.synchronizedList(mutableListOf<Pair<SongEntity, AudioTagData>>())

            songsToMatch.map { song ->
                launch {
                    semaphore.withPermit {
                        _uiState.update { it.copy(currentFile = song.fileName) }

                        val result = matchAndGetTag(song,separator, currentOrder)

                        val currentProcessed = processedCount.incrementAndGet()
                        if (result != null) {
                            matchResults.add(song to result)
                            _uiState.update { it.copy(successCount = it.successCount + 1) }
                        } else {
                            _uiState.update { it.copy(failureCount = it.failureCount + 1) }
                        }

                        _uiState.update { it.copy(batchProgress = currentProcessed to total) }
                    }
                }
            }.joinAll()

            if (matchResults.isNotEmpty()) {
                _uiState.update { it.copy(loadingMessage = "正在批量持久化...") }
                songRepository.applyBatchMetadata(matchResults)
            }

            val totalTime = System.currentTimeMillis() - startTime
            _uiState.update { it.copy(isBatchMatching = false, loadingMessage = "匹配完成", batchTimeMillis = totalTime) }
        }
    }
    private suspend fun matchAndGetTag(song: SongEntity,separator: String, order: List<Source>): AudioTagData? = coroutineScope {
        val queries = MusicMatchUtils.buildSearchQueries(song)
        val (parsedTitle, parsedArtist) = MusicMatchUtils.parseFileName(song.fileName)
        val queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
        val queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist

        val orderedSources = sources.sortedBy { s ->
            order.indexOf(s.sourceType).let { if (it == -1) Int.MAX_VALUE else it }
        }

        var bestMatch: ScoredSearchResult? = null

        // 策略：对每一个 Query，并行请求所有搜索源
        for (query in queries) {
            // 在一首歌内部，对多个源发起并行请求
            val searchTasks = orderedSources.map { source ->
                async {
                    try {
                        val results = source.search(query, separator = separator, pageSize = 2)
                        results.map { res ->
                            val score = MusicMatchUtils.calculateMatchScore(res, song, queryTitle, queryArtist)
                            ScoredSearchResult(res, score, source)
                        }
                    } catch (e: Exception) { emptyList() }
                }
            }

            // 等待所有源返回结果
            val allResults = searchTasks.awaitAll().flatten()
            val currentBest = allResults.maxByOrNull { it.score }

            if (currentBest != null) {
                if (bestMatch == null || currentBest.score > bestMatch.score) {
                    bestMatch = currentBest
                }
                // 强剪枝：如果当前 Query 已经找到了非常完美的匹配 ( > 0.9)
                // 直接跳过后续的 Query 搜索（不再搜索“文件名”等模糊词）
                if (currentBest.score > 0.9) break
            }
        }

        val finalMatch = bestMatch ?: return@coroutineScope null
        if (finalMatch.score < 0.35) return@coroutineScope null

        try {
            // 并行处理：获取歌词的同时下载图片
            val lyricsDeferred = async { finalMatch.source.getLyrics(finalMatch.result) }
            val lyricDisplayMode = settingsRepository.lyricDisplayMode.first()

            val tagData = AudioTagData(
                title = song.title?.takeIf { !it.contains("未知", true) } ?: finalMatch.result.title,
                artist = song.artist?.takeIf { !it.contains("未知", true) } ?: finalMatch.result.artist,
                lyrics = lyricsDeferred.await()?.let {
                    LyricsUtils.formatLrcResult(it, lineByLine = lyricDisplayMode == LyricDisplayMode.LINE_BY_LINE)
                },
                picUrl = finalMatch.result.picUrl,
                date = finalMatch.result.date,
                trackerNumber = finalMatch.result.trackerNumber
            )

            if (songRepository.writeAudioTagData(song.filePath, tagData)) {
                tagData
            } else null
        } catch (e: Exception) { null }
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
                songRepository.synchronize(false)
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
                loadingMessage = "",
                batchTimeMillis = 0
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
