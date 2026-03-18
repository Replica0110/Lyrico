package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.data.repository.BatchMatchHistoryRepository
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@Parcelize
data class SongInfo(
    val filePath: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val showBatchConfigDialog: Boolean = false, // Add this
    val isBatchMatching: Boolean = false,
    val isSaving: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showBatchDeleteDialog: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val showScrollTopButton: Boolean = false,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0,  // 批量匹配总用时（毫秒）
    val searchQuery: String = "",
    val isSearching: Boolean = false
)
data class SheetUiState(
    val menuSong: SongEntity? = null,
    val detailSong: SongEntity? = null
)


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val batchMatchHistoryRepository: BatchMatchHistoryRepository,
    private val playbackRepository: PlaybackRepository,
    private val sources: List<SearchSource>,
    private val updateManager: UpdateManager,
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

    private val romaEnabled = settingsRepository.romaEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showScrollTopButton = settingsRepository.showScrollTopButton
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val translationEnabled = settingsRepository.translationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val lyricFormat = settingsRepository.lyricFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyricFormat.VERBATIM_LRC)


    // UI 交互状态
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState = _uiState.asStateFlow()

    private var preDragSelectedIds = emptyMap<Long, Long>()

    private val _selectedSongIds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val selectedSongIds = _selectedSongIds.asStateFlow()
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    private val _sheetState = MutableStateFlow(SheetUiState())
    val sheetState = _sheetState.asStateFlow()
    // 监听 sortInfo 和 搜索词(searchQuery) 的变化。
    // 使用 flatMapLatest，一旦输入新搜索词，会自动取消上一次尚未完成的数据库查询
    val songs: StateFlow<List<SongEntity>> = combine(
        sortInfo,
        _uiState.map { it.searchQuery }.distinctUntilChanged()
    ) { sort, query ->
        Pair(sort, query)
    }.flatMapLatest { (sort, query) ->
        if (query.isBlank()) {
            // 没有搜索词，返回全部歌曲（应用排序）
            songRepository.getAllSongsSorted(sort.sortBy, sort.order)
        } else {
            // 有搜索词，进行搜索
            songRepository.searchSongs(query)
        }
    }.onEach {
        // 数据库查询出结果后，关闭 isSearching 状态
        _uiState.update { it.copy(isSearching = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun showMenu(song: SongEntity) {
        _sheetState.value = SheetUiState(menuSong = song)
    }
    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun showDetail(song: SongEntity) {
        _sheetState.update {
            it.copy(detailSong = song)
        }
    }

    fun dismissDetail() {
        _sheetState.update { it.copy(detailSong = null) }
    }

    fun dismissAll() {
        _sheetState.value = SheetUiState()
    }



    init {
        registerMusicObserver()

        // 自动同步监听
        viewModelScope.launch {
            scanRequest.debounce(2000L).collect {
                if (!_uiState.value.isBatchMatching) triggerSync(isAuto = true)
            }
        }
    }
    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                // 如果搜索词不为空，立即显示加载状态，直到 songs Flow 的 onEach 将其置为 false
                isSearching = query.isNotBlank()
            )
        }
    }
    fun clearSearch() {
        onSearchQueryChanged("")
    }
    /**
     * 触发滑动选择的起点
     */
    fun startDragSelection(index: Int, songs: List<SongEntity>) {
        val song = songs.getOrNull(index) ?: return
        preDragSelectedIds = _selectedSongIds.value

        // 把当前这首歌的 ID 和 时间 存入
        val rangeIds = mapOf(song.mediaId to song.fileLastModified)

        // 异或操作：原本选中过的剔除，没选中的加入
        val result = preDragSelectedIds.toMutableMap()
        for ((k, v) in rangeIds) {
            if (result.containsKey(k)) result.remove(k) else result[k] = v
        }
        _selectedSongIds.value = result
    }

    /**
     * 拖动过程中更新选中区间
     */
    fun updateDragSelection(startIndex: Int, endIndex: Int, songs: List<SongEntity>) {
        val start = minOf(startIndex, endIndex).coerceAtLeast(0)
        val end = maxOf(startIndex, endIndex).coerceAtMost(songs.size - 1)
        if (start > end) return

        // 一次性把划过的所有歌曲 ID 和 时间 拿出来
        val rangeIds = songs.subList(start, end + 1).associate { it.mediaId to it.fileLastModified }

        val result = preDragSelectedIds.toMutableMap()
        for ((k, v) in rangeIds) {
            if (result.containsKey(k)) result.remove(k) else result[k] = v
        }
        _selectedSongIds.value = result
    }

    /**
     * 结束滑动选择
     */
    fun endDragSelection() {
        // 滑动结束，清空基准状态
        preDragSelectedIds = emptyMap()
    }
    fun onStart() {
        viewModelScope.launch {
            val checkUpdateEnabled = settingsRepository.checkUpdateEnabled.first()
            if (checkUpdateEnabled) {
                Log.d(TAG, "检查更新")
                updateManager.checkForUpdate()
            }
        }
    }
    fun openBatchMatchConfig() {
        if (_selectedSongIds.value.isNotEmpty()) {
            _uiState.update { it.copy(showBatchConfigDialog = true) }
        }
    }

    fun play(context: Context, song: SongEntity) {
        val uri = song.getUri
        playbackRepository.play(context, uri)
    }
    fun delete(song: SongEntity) {
        viewModelScope.launch {
            dismissAll()
            songRepository.deleteSong(song)
        }
    }
    fun closeBatchMatchConfig() {
        _uiState.update { it.copy(showBatchConfigDialog = false) }
    }

    /**
     * 批量匹配歌曲（支持并发控制）
     * @param matchConfig 匹配配置
     */
    fun batchMatch(matchConfig: BatchMatchConfig) {
        val selectedMap = _selectedSongIds.value
        if (selectedMap.isEmpty()) return

        val separator = separator.value
        val lyricConfig = LyricRenderConfig(
            format = lyricFormat.value,
            showRomanization = romaEnabled.value,
            showTranslation = translationEnabled.value
        )

        // 关闭配置对话框
        closeBatchMatchConfig()

        batchMatchJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val songsToMatch = songs.value.filter { selectedMap.containsKey(it.mediaId) }
            val currentOrder = searchSourceOrder.value
            val total = songsToMatch.size

            _uiState.update { it.copy(
                isBatchMatching = true,
                successCount = 0,
                isSaving =  false,
                failureCount = 0,
                skippedCount = 0,
                batchProgress = 0 to total,
                batchTimeMillis = 0
            ) }

            val semaphore = Semaphore(matchConfig.concurrency)
            val processedCount = AtomicInteger(0)
            val matchResults = Collections.synchronizedList(mutableListOf<Pair<SongEntity, AudioTagData>>())
            val historyRecords = Collections.synchronizedList(mutableListOf<BatchMatchRecordEntity>())

            val successCounter = AtomicInteger(0)
            val failureCounter = AtomicInteger(0)
            val skippedCounter = AtomicInteger(0)

            songsToMatch.map { song ->
                launch {
                    semaphore.withPermit {
                        _uiState.update { it.copy(currentFile = song.fileName) }

                        // 核心逻辑：根据 Config 决定是否跳过、如何匹配
                        val result = matchAndGetTag(
                            song = song,
                            separator = separator,
                            lyricConfig = lyricConfig,
                            order = currentOrder,
                            matchConfig = matchConfig
                        )

                        val currentProcessed = processedCount.incrementAndGet()
                        
                        historyRecords.add(
                            BatchMatchRecordEntity(
                                historyId = 0, // Pending
                                filePath = song.filePath,
                                status = result.status,
                                uri = song.uri
                            )
                        )

                        when (result.status) {
                            BatchMatchResult.SUCCESS -> {
                                if (result.tagData != null) {
                                    matchResults.add(song to result.tagData)
                                } else {
                                    val s = skippedCounter.incrementAndGet()
                                    _uiState.update { it.copy(skippedCount = s) }
                                }
                            }
                            BatchMatchResult.FAILURE -> {
                                val f = failureCounter.incrementAndGet()
                                _uiState.update { it.copy(failureCount = f) }
                            }
                            else -> {
                                val s = skippedCounter.incrementAndGet()
                                _uiState.update { it.copy(skippedCount = s) }
                            }
                        }

                        _uiState.update { it.copy(batchProgress = currentProcessed to total) }
                    }
                }
            }.joinAll()

            if (matchResults.isNotEmpty()) {
                val sortedResults = matchResults.sortedBy {
                    selectedMap[it.first.mediaId] ?: 0L
                }

                val finalWrittenResults = mutableListOf<Pair<SongEntity, AudioTagData>>()
                _uiState.update {
                    it.copy(isSaving =  true)
                }

                for ((song, tag) in sortedResults) {
                    val writeSuccess = songRepository.writeAudioTagData(song.uri, tag)
                    if (writeSuccess) {
                        finalWrittenResults.add(song to tag)
                        delay(50L)

                        val s = successCounter.incrementAndGet()
                        _uiState.update { it.copy(successCount = s) }
                    } else {
                        val f = failureCounter.incrementAndGet()
                        _uiState.update { it.copy(failureCount = f) }
                    }
                }

                if (finalWrittenResults.isNotEmpty()) {
                    songRepository.applyBatchMetadata(finalWrittenResults)
                }
            }

            // Save History
            val totalTime = System.currentTimeMillis() - startTime
            val history = BatchMatchHistory(
                timestamp = System.currentTimeMillis(),
                successCount = successCounter.get(),
                failureCount = failureCounter.get(),
                skippedCount = skippedCounter.get(),
                durationMillis = totalTime,
            )
            val historyId = batchMatchHistoryRepository.saveHistory(history, historyRecords)

            _uiState.update { it.copy(batchHistoryId = historyId, isBatchMatching = false, isSaving =  false, batchTimeMillis = totalTime) }
        }
    }

    private data class MatchResult(val tagData: AudioTagData?, val status: BatchMatchResult)

    private suspend fun matchAndGetTag(
        song: SongEntity,
        separator: String,
        lyricConfig: LyricRenderConfig,
        order: List<Source>,
        matchConfig: BatchMatchConfig
    ): MatchResult = coroutineScope {

        val needsProcessing = matchConfig.fields.any { (field, mode) ->
            if (mode == BatchMatchMode.OVERWRITE) return@any true
            
            // Supplement Mode
            when (field) {
                BatchMatchField.TITLE -> song.title.isNullOrBlank()
                BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
                BatchMatchField.ALBUM -> song.album.isNullOrBlank()
                BatchMatchField.GENRE -> song.genre.isNullOrBlank()
                BatchMatchField.DATE -> song.date.isNullOrBlank()
                BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
                BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
                BatchMatchField.COVER -> true
            }
        }

        if (!needsProcessing) return@coroutineScope MatchResult(null, BatchMatchResult.SKIPPED)

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

        val finalMatch = bestMatch ?: return@coroutineScope MatchResult(null, BatchMatchResult.FAILURE) // No match found
        if (finalMatch.score < 0.35) return@coroutineScope MatchResult(null, BatchMatchResult.FAILURE) // Score too low

        try {
            val lyricsDeferred = async { finalMatch.source.getLyrics(finalMatch.result) }
            val newLyrics = lyricsDeferred.await()?.let {
                LyricsUtils.formatLrcResult(result = it, config = lyricConfig)
            }

            val newTitle = resolveValue(matchConfig, BatchMatchField.TITLE, song.title, finalMatch.result.title)
            val newArtist = resolveValue(matchConfig, BatchMatchField.ARTIST, song.artist, finalMatch.result.artist)
            val newAlbum = resolveValue(matchConfig, BatchMatchField.ALBUM, song.album, finalMatch.result.album)
            val newDate = resolveValue(matchConfig, BatchMatchField.DATE, song.date, finalMatch.result.date)
            val newTrack = resolveValue(matchConfig, BatchMatchField.TRACK_NUMBER, song.trackerNumber, finalMatch.result.trackerNumber)
            val newGenre = resolveValue(matchConfig, BatchMatchField.GENRE, song.genre, null)
            val newLyricsResolved = resolveValue(matchConfig, BatchMatchField.LYRICS, song.lyrics, newLyrics)

            val shouldUpdateCover = shouldUpdate(matchConfig, BatchMatchField.COVER, null)
            val picUrl = if (shouldUpdateCover) finalMatch.result.picUrl else null

            val tagDataToWrite = AudioTagData(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre,
                date = newDate,
                trackNumber = newTrack,
                lyrics = newLyricsResolved,
                picUrl = picUrl
            )

            // Check if tagDataToWrite is effectively empty (no fields to update)
            val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                    newGenre == null && newDate == null && newTrack == null &&
                    newLyricsResolved == null && picUrl == null

            if (isEffectivelyEmpty) return@coroutineScope MatchResult(null, BatchMatchResult.SKIPPED)
            return@coroutineScope MatchResult(tagDataToWrite, BatchMatchResult.SUCCESS)
        } catch (e: Exception) {
            MatchResult(null, BatchMatchResult.FAILURE)
        }
    }
    
    private fun resolveValue(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?, 
        newValue: String?
    ): String? {
        if (!config.fields.containsKey(field)) return null // Not selected
        
        val mode = config.fields[field]!!
        return if (mode == BatchMatchMode.OVERWRITE) {
            newValue
        } else {
            if (currentValue.isNullOrBlank()) newValue else null
        }
    }

    private fun shouldUpdate(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?
    ): Boolean {
        if (!config.fields.containsKey(field)) return false
        val mode = config.fields[field]!!
        if (mode == BatchMatchMode.OVERWRITE) return true
        return currentValue.isNullOrBlank()
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

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongsCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerSync(isAuto = false)
            }
        }
    }
    fun toggleSelection(song: SongEntity)  {
        if (!_isSelectionMode.value) _isSelectionMode.value = true
        _selectedSongIds.update { map ->
            if (map.containsKey(song.mediaId)) {
                map - song.mediaId // 取消选中
            } else {
                map + (song.mediaId to song.fileLastModified)
            }
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongIds.value = emptyMap()
    }

    fun deselectAll() {
        _selectedSongIds.value = emptyMap()
    }
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongIds.value = songs.associate { it.mediaId to it.fileLastModified }
    }
    fun isAllSelected(songs: List<SongEntity>): Boolean {
        return songs.isNotEmpty() && _selectedSongIds.value.size == songs.size
    }

    fun showBatchDeleteDialog() {
        _uiState.update { it.copy(showBatchDeleteDialog = true) }
    }

    fun dismissBatchDeleteDialog() {
        _uiState.update { it.copy(showBatchDeleteDialog = false) }
    }

    fun batchDelete(songs: List<SongEntity>) {
        val selectedMap = _selectedSongIds.value
        val toDelete = songs.filter { selectedMap.containsKey(it.mediaId) }
        viewModelScope.launch {
            toDelete.forEach { song ->
                songRepository.deleteSong(song)
            }
            exitSelectionMode()
        }
    }

    fun batchShare(context: Context, songs: List<SongEntity>) {
        val selectedMap = _selectedSongIds.value
        val toShare = songs.filter { selectedMap.containsKey(it.mediaId) }
        val uris = toShare.map { song ->
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.mediaId
            )
        }.toCollection(ArrayList())

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(com.lonx.lyrico.R.string.share_chooser_title))
        )
    }

    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                songRepository.synchronize(false)
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
            } finally {
                delay(500L)
                _uiState.update { it.copy(isLoading = false) }
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
     * 中止批量匹配
     */
    fun abortBatchMatch() {
        batchMatchJob?.cancel()
        batchMatchJob = null
        _uiState.update { it.copy(isBatchMatching = false) }
    }
    fun closeBatchMatchDialog() {
        _uiState.update {
            it.copy(
                batchProgress = null,
                currentFile = "",
                isSaving =  false,
                isBatchMatching = false,
                batchTimeMillis = 0
            )
        }
        exitSelectionMode()
    }

    override fun onCleared() {
        musicContentObserver?.let { contentResolver.unregisterContentObserver(it) }
        batchMatchJob?.cancel()
        super.onCleared()
    }

    private data class ScoredSearchResult(
        val result: SongSearchResult,
        val score: Double,
        val source: SearchSource
    )
}
