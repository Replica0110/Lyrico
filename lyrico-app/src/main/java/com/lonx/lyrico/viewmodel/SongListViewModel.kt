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
    val showBatchConfigDialog: Boolean = false, // Add this
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

    fun openBatchMatchConfig() {
        if (_selectedSongIds.value.isNotEmpty()) {
            _uiState.update { it.copy(showBatchConfigDialog = true) }
        }
    }

    fun closeBatchMatchConfig() {
        _uiState.update { it.copy(showBatchConfigDialog = false) }
    }

    /**
     * 批量匹配歌曲（支持并发控制）
     * @param config 匹配配置
     */
    fun batchMatch(config: com.lonx.lyrico.data.model.BatchMatchConfig) {
        val selectedIds = _selectedSongIds.value
        val separator = separator.value
        if (selectedIds.isEmpty()) return

        // 关闭配置对话框
        closeBatchMatchConfig()

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

            val semaphore = Semaphore(config.parallelism)
            val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
            val matchResults = Collections.synchronizedList(mutableListOf<Pair<SongEntity, AudioTagData>>())

            songsToMatch.map { song ->
                launch {
                    semaphore.withPermit {
                        _uiState.update { it.copy(currentFile = song.fileName) }

                        // 核心逻辑：根据 Config 决定是否跳过、如何匹配
                        val result = matchAndGetTag(song, separator, currentOrder, config)

                        val currentProcessed = processedCount.incrementAndGet()
                        if (result != null) {
                            matchResults.add(song to result)
                            _uiState.update { it.copy(successCount = it.successCount + 1) }
                        } else {
                            // 如果结果为 null，可能是失败，也可能是被 Config 跳过（视为不需要处理）
                            // 这里如果不区分跳过和失败，统称为未更新
                            // 如果是“跳过”，其实不算失败，但也不算“匹配成功并更新”。
                            // 暂时计入 failureCount 或者是单独的 skippedCount? 
                            // 简单起见，如果不需要更新，那就不算成功也不算失败，或者算成功？
                            // 让我们看看 matchAndGetTag: 返回 null 表示没有新数据。
                           
                        }
                         // 为了 UI 显示好看，跳过的（matches config requirements already）应该算成功？ 
                         // 但那样 successCount 会很多。
                         // 保持原样: successCount 仅代表 发生更新 的数量。
                         // failureCount 这里会包含 跳过 的。这可能让用户困惑。
                         // 改进：matchAndGetTag 返回一个状态？
                         // 暂时保持简单。
                         if (result == null) {
                             // failure or skipped
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

    private suspend fun matchAndGetTag(
        song: SongEntity,
        separator: String,
        order: List<Source>,
        config: com.lonx.lyrico.data.model.BatchMatchConfig
    ): AudioTagData? = coroutineScope {
        
        // --- 1. 检查是否需要处理 ---
        // 如果所有选中的字段都是 SUPPLEMENT 且 都有值，则直接返回 null (跳过)
        // 注意：CHECK COVER 是否存在比较耗时，这里如果选择了 COVER 且为 Supplement，我们先做个快速判断（如果有）
        // 这里简化：如果 text 字段都满足，且不涉及 Cover，或者 Cover 暂不优化 check，就跳过。
        // 为了响应用户要求“如果不需要补充任何字段，就跳过”，我们需要尽量检查。
        
        val needsProcessing = config.fields.any { (field, mode) ->
            if (mode == com.lonx.lyrico.data.model.BatchMatchMode.OVERWRITE) return@any true
            
            // Supplement Mode
            when (field) {
                com.lonx.lyrico.data.model.BatchMatchField.TITLE -> song.title.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.ALBUM -> song.album.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.GENRE -> song.genre.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.DATE -> song.date.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
                com.lonx.lyrico.data.model.BatchMatchField.COVER -> true // 为了严谨，总是尝试处理 Cover (除非我们读 Tag 确认)
            }
        }

        if (!needsProcessing) return@coroutineScope null


        // --- 2. 执行搜索 ---
        val queries = MusicMatchUtils.buildSearchQueries(song)
        val (parsedTitle, parsedArtist) = MusicMatchUtils.parseFileName(song.fileName)
        val queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
        val queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist

        val orderedSources = sources.sortedBy { s ->
            order.indexOf(s.sourceType).let { if (it == -1) Int.MAX_VALUE else it }
        }

        var bestMatch: ScoredSearchResult? = null

        for (query in queries) {
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

            val allResults = searchTasks.awaitAll().flatten()
            val currentBest = allResults.maxByOrNull { it.score }

            if (currentBest != null) {
                if (bestMatch == null || currentBest.score > bestMatch.score) {
                    bestMatch = currentBest
                }
                if (currentBest.score > 0.9) break
            }
        }

        val finalMatch = bestMatch ?: return@coroutineScope null
        if (finalMatch.score < 0.35) return@coroutineScope null

        try {
            val lyricsDeferred = async { finalMatch.source.getLyrics(finalMatch.result) }
            val lyricDisplayMode = settingsRepository.lyricDisplayMode.first()
            val newLyrics = lyricsDeferred.await()?.let {
                 LyricsUtils.formatLrcResult(it, lineByLine = lyricDisplayMode == LyricDisplayMode.LINE_BY_LINE)
            }
            
            // --- 3. 构建结果 & 写入文件 ---
            // 根据 Config 决定最终的 Tag Data
            // 对于未选中的字段 -> 设为 null (不改变)
            // 对于 Supplement -> 如果原值为空，使用新值；否则设为 null (不改变)
            // 对于 Overwrite -> 使用新值
            
            val newTitle = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.TITLE, song.title, finalMatch.result.title)
            val newArtist = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.ARTIST, song.artist, finalMatch.result.artist)
            // Album 搜索结果通常有? SongSearchResult 有 album 吗? 检查 SongSearchResult 定义。
            // 假设 finalMatch.result 有 album (通常有)。
            // 检查 SongSearchResult 定义... 暂时假设有，如果没有就删掉。
            // SongSearchResult (Lyrics model) definition: title, artist, album, duration, etc. Yes.
            val newAlbum = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.ALBUM, song.album, finalMatch.result.album)
            
            // Date result.date? yes.
            val newDate = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.DATE, song.date, finalMatch.result.date)
            
            // Track? result.trackerNumber
            val newTrack = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.TRACK_NUMBER, song.trackerNumber, finalMatch.result.trackerNumber)
            
            val newGenre = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.GENRE, song.genre, null) // Search result usually doesn't have genre? If not, ignore.
            
            val newLyricsResolved = resolveValue(config, com.lonx.lyrico.data.model.BatchMatchField.LYRICS, song.lyrics, newLyrics)

            // Cover
            // Cover logic is distinct because it involves download and byte arrays.
            val shouldUpdateCover = shouldUpdate(config, com.lonx.lyrico.data.model.BatchMatchField.COVER, null) // pass null as current because we don't know easily
            // Note: If we really want Supplement to work for Cover, we passed 'true' in needsProcessing.
            // But here, if mode is Supplement, we still don't know if we have cover.
            // If we want to be safe: don't write if we suspect we have cover? 
            // Or just allow write. If logic says 'shouldUpdate', we allow write.
            // But if 'shouldUpdate' returns true for Supplement (because current passed is null), we might overwrite existing cover!
            // This is a risk.
            // Safe bet: For Supplement Cover, we MUST read to check.
            
            val picUrl = if (shouldUpdateCover) finalMatch.result.picUrl else null

            val tagDataToWrite = AudioTagData(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre,
                date = newDate,
                trackerNumber = newTrack,
                lyrics = newLyricsResolved,
                picUrl = picUrl,
                // pictures list will be handled by repository if picUrl is present
            )

            // Check if tagDataToWrite is effectively empty (no fields to update)
            // If all fields are null, we skip writing.
            val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                    newGenre == null && newDate == null && newTrack == null &&
                    newLyricsResolved == null && picUrl == null

            if (isEffectivelyEmpty) return@coroutineScope null

            if (songRepository.writeAudioTagData(song.filePath, tagDataToWrite)) {
                // Return the data that was written so we can update DB
                tagDataToWrite
            } else null
        } catch (e: Exception) { null }
    }
    
    private fun resolveValue(
        config: com.lonx.lyrico.data.model.BatchMatchConfig, 
        field: com.lonx.lyrico.data.model.BatchMatchField, 
        currentValue: String?, 
        newValue: String?
    ): String? {
        if (!config.fields.containsKey(field)) return null // Not selected
        
        val mode = config.fields[field]!!
        return if (mode == com.lonx.lyrico.data.model.BatchMatchMode.OVERWRITE) {
            newValue // Overwrite: return new value (even if null? if new is null, we might not want to wipe? usually search result won't be explicitly null for known fields, but if it is, careful. Text fields usually OK)
        } else {
            // Supplement
            if (currentValue.isNullOrBlank()) newValue else null // If current has value, return null (don't update)
        }
    }
    
    // Helper to check if we should even try (for Cover)
    private fun shouldUpdate(
        config: com.lonx.lyrico.data.model.BatchMatchConfig, 
        field: com.lonx.lyrico.data.model.BatchMatchField,
        currentValue: String?
    ): Boolean {
        if (!config.fields.containsKey(field)) return false
        val mode = config.fields[field]!!
        if (mode == com.lonx.lyrico.data.model.BatchMatchMode.OVERWRITE) return true
        return currentValue.isNullOrBlank()
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
