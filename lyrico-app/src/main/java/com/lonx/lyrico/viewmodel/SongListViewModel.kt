package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.flow.first
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
    val showDeleteDialog: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0  // 批量匹配总用时（毫秒）
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

    private val translationEnabled = settingsRepository.translationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val lyricFormat = settingsRepository.lyricFormat
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyricFormat.VERBATIM_LRC)

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
    private val _sheetState = MutableStateFlow(SheetUiState())
    val sheetState = _sheetState.asStateFlow()
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
        val selectedIds = _selectedSongIds.value
        val separator = separator.value
        val lyricConfig = LyricRenderConfig(
            format = lyricFormat.value,
            showRomanization = romaEnabled.value,
            showTranslation = translationEnabled.value
        )
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
                skippedCount = 0,
                batchProgress = 0 to total,
                batchTimeMillis = 0
            ) }

            val semaphore = Semaphore(matchConfig.parallelism)
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
                                status = result.status
                            )
                        )

                        when (result.status) {
                            BatchMatchResult.SUCCESS if result.tagData != null -> {
                                matchResults.add(song to result.tagData)
                                val s = successCounter.incrementAndGet()
                                _uiState.update { it.copy(successCount = s) }
                            }
                            BatchMatchResult.FAILURE -> {
                                val f = failureCounter.incrementAndGet()
                                _uiState.update { it.copy(failureCount = f) }
                            }
                            else -> {
                                // Skipped
                                val s = skippedCounter.incrementAndGet()
                                _uiState.update { it.copy(skippedCount = s) }
                            }
                        }

                        _uiState.update { it.copy(batchProgress = currentProcessed to total) }
                    }
                }
            }.joinAll()

            if (matchResults.isNotEmpty()) {
                songRepository.applyBatchMetadata(matchResults)
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

            _uiState.update { it.copy(batchHistoryId = historyId,isBatchMatching = false, batchTimeMillis = totalTime) }
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
                BatchMatchField.COVER -> true // 为了严谨，总是尝试处理 Cover (除非我们读 Tag 确认)
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
                trackerNumber = newTrack,
                lyrics = newLyricsResolved,
                picUrl = picUrl
            )

            // Check if tagDataToWrite is effectively empty (no fields to update)
            val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                    newGenre == null && newDate == null && newTrack == null &&
                    newLyricsResolved == null && picUrl == null

            if (isEffectivelyEmpty) return@coroutineScope MatchResult(null, BatchMatchResult.SKIPPED)

            if (songRepository.writeAudioTagData(song.filePath, tagDataToWrite)) {
                MatchResult(tagDataToWrite, BatchMatchResult.SUCCESS)
            } else {
                MatchResult(null, BatchMatchResult.FAILURE) // Write failed
            }
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
        val result: SongSearchResult,
        val score: Double,
        val source: SearchSource
    )
}
