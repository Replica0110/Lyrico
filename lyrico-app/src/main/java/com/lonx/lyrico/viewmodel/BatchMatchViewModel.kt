package com.lonx.lyrico.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.LyricRenderConfig
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.BatchMatchHistoryRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * 批量匹配 UI 状态
 */
data class BatchMatchUiState(
    val showBatchConfigDialog: Boolean = false,
    val isBatchMatching: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0  // 批量匹配总用时（毫秒）
)

/**
 * 批量匹配 ViewModel
 * 负责处理批量匹配的完整流程，包括配置、执行、进度跟踪和历史记录保存
 */
class BatchMatchViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val batchMatchHistoryRepository: BatchMatchHistoryRepository,
    private val sources: List<SearchSource>,
    private val selectionManager: SharedSelectionManager
) : ViewModel() {

    private val TAG = "BatchMatchViewModel"

    private var batchMatchJob: Job? = null

    // 批量匹配配置
    val batchMatchConfig: StateFlow<BatchMatchConfig> = settingsRepository.batchMatchConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, BatchMatchConfigDefaults.DEFAULT_CONFIG)

    // 已启用的搜索源顺序
    private val searchSourceOrder: StateFlow<List<Source>> = settingsRepository.searchSourceOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val enabledSearchSources: StateFlow<Set<Source>> = settingsRepository.enabledSearchSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val enabledSourceOrder: StateFlow<List<Source>> = combine(
        searchSourceOrder,
        enabledSearchSources
    ) { order, enabled ->
        order.filter { it in enabled }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 分隔符
    private val separator: StateFlow<String> = settingsRepository.separator
        .stateIn(viewModelScope, SharingStarted.Eagerly, "/")

    // UI 状态
    private val _uiState = MutableStateFlow(BatchMatchUiState())
    val uiState: StateFlow<BatchMatchUiState> = _uiState.asStateFlow()

    /**
     * 打开批量匹配配置对话框
     */
    fun openBatchMatchConfig() {
        val selectedIds = selectionManager.selectedUris.value
        if (selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showBatchConfigDialog = true) }
        }
    }

    /**
     * 关闭批量匹配配置对话框
     */
    fun closeBatchMatchConfig() {
        _uiState.update { it.copy(showBatchConfigDialog = false) }
    }

    /**
     * 保存批量匹配配置
     */
    fun saveBatchMatchConfig(matchConfig: BatchMatchConfig) {
        viewModelScope.launch {
            settingsRepository.saveBatchMatchConfig(matchConfig)
        }
    }

    /**
     * 执行批量匹配
     */
    fun batchMatch(songs: List<SongEntity>) {
        val selectedIds = selectionManager.selectedUris.value
        if (selectedIds.isEmpty()) return

        val matchConfig = batchMatchConfig.value
        val separatorValue = separator.value

        // 关闭配置对话框
        closeBatchMatchConfig()

        batchMatchJob = viewModelScope.launch {
            val lyricConfig = settingsRepository.getLyricRenderConfig()
            val startTime = System.currentTimeMillis()
            val songsToMatch = songs.filter { it.uri in selectedIds }
            val currentOrder = enabledSourceOrder.value
            val total = songsToMatch.size

            _uiState.update { it.copy(
                isBatchMatching = true,
                successCount = 0,
                failureCount = 0,
                skippedCount = 0,
                batchProgress = 0 to total,
                batchTimeMillis = 0
            ) }

            val semaphore = Semaphore(matchConfig.concurrency)
            val processedCount = AtomicInteger(0)

            // 使用线程安全的列表，防止并发写入导致数据丢失或崩溃
            val matchResults = Collections.synchronizedList(mutableListOf<Pair<SongEntity, AudioTagData>>())
            val historyRecords = Collections.synchronizedList(mutableListOf<BatchMatchRecordEntity>())

            val successCounter = AtomicInteger(0)
            val failureCounter = AtomicInteger(0)
            val skippedCounter = AtomicInteger(0)

            try {
                songsToMatch.map { song ->
                    launch {
                        semaphore.withPermit {
                            _uiState.update { it.copy(currentFile = song.fileName) }

                            val result = matchAndGetTag(
                                song = song,
                                separator = separatorValue,
                                lyricConfig = lyricConfig,
                                order = currentOrder,
                                matchConfig = matchConfig
                            )

                            val currentProcessed = processedCount.incrementAndGet()

                            // 添加到历史记录
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
                                    }
                                    val s = successCounter.incrementAndGet()
                                    _uiState.update { it.copy(successCount = s) }
                                }
                                BatchMatchResult.FAILURE -> {
                                    val f = failureCounter.incrementAndGet()
                                    _uiState.update { it.copy(failureCount = f) }
                                }
                                else -> { // Skipped
                                    val s = skippedCounter.incrementAndGet()
                                    _uiState.update { it.copy(skippedCount = s) }
                                }
                            }

                            _uiState.update { it.copy(batchProgress = currentProcessed to total) }
                        }
                    }
                }.joinAll()
            } finally {
                withContext(NonCancellable) {
                    if (historyRecords.isNotEmpty()) {
                        // 更新数据库中已成功的歌曲元数据
                        if (matchResults.isNotEmpty()) {
                            songRepository.updateMetadatas(matchResults.toList())
                        }

                        val totalTime = System.currentTimeMillis() - startTime
                        val history = BatchMatchHistory(
                            timestamp = System.currentTimeMillis(),
                            successCount = successCounter.get(),
                            failureCount = failureCounter.get(),
                            skippedCount = skippedCounter.get(),
                            durationMillis = totalTime,
                        )

                        val historyId = batchMatchHistoryRepository.saveHistory(history, historyRecords.toList())

                        // 更新 UI 状态展示结果
                        _uiState.update {
                            it.copy(
                                batchHistoryId = historyId,
                                isBatchMatching = false,
                                batchTimeMillis = totalTime
                            )
                        }
                    } else {
                        // 如果刚开始就被取消，一条都没处理
                        _uiState.update { it.copy(isBatchMatching = false) }
                    }
                }
            }
        }
    }

    /**
     * 中止批量匹配
     */
    fun abortBatchMatch() {
        batchMatchJob?.cancel()
        batchMatchJob = null
        _uiState.update { it.copy(isBatchMatching = false) }
    }

    /**
     * 关闭批量匹配对话框
     */
    fun closeBatchMatchDialog() {
        _uiState.update {
            it.copy(
                batchProgress = null,
                currentFile = "",
                isBatchMatching = false,
                batchTimeMillis = 0
            )
        }
    }

    /**
     * 匹配并获取标签数据
     */
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

        val (parsedTitle, parsedArtist) = MusicMatchUtils.parseFileName(song.fileName)

        val queryTitle: String?
        val queryArtist: String?
        val queries: List<String>

        if (matchConfig.preferFileName) {
            Log.d("MatchAndGetTag", "PreferFileName")
            // 优先从文件名获取，如果没有再降级使用标签
            queryTitle = parsedTitle?.takeIf { it.isNotBlank() }
                ?: song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) }
            queryArtist = parsedArtist?.takeIf { it.isNotBlank() }
                ?: song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) }

            // 构建优先用文件名组成的搜索词
            val fileNameQuery = if (!queryTitle.isNullOrBlank() && !queryArtist.isNullOrBlank()) {
                "$queryTitle $queryArtist"
            } else {
                queryTitle ?: queryArtist
            }

            queries = if (!fileNameQuery.isNullOrBlank()) {
                listOf(fileNameQuery)
            } else {
                MusicMatchUtils.buildSearchQueries(song) // 降级处理
            }
            Log.d("MatchAndGetTag", "Queries: $queries")
        } else {
            // 原有的默认逻辑：优先用标签，标签没有再降级使用文件名
            queryTitle = song.title?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedTitle
            queryArtist = song.artist?.takeIf { it.isNotBlank() && !it.contains("未知", true) } ?: parsedArtist
            queries = MusicMatchUtils.buildSearchQueries(song)
        }

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

        val finalMatch = bestMatch ?: return@coroutineScope MatchResult(null, BatchMatchResult.FAILURE)
        if (finalMatch.score < 0.35) return@coroutineScope MatchResult(null, BatchMatchResult.FAILURE)

        try {
            val lyricsDeferred = async(Dispatchers.Default) {
                finalMatch.source.getLyrics(finalMatch.result)?.let { result ->
                    LyricsUtils.formatLrcResult(result = result, config = lyricConfig)
                }
            }
            val newLyrics = lyricsDeferred.await()

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

            // 使用增量更新方法，只写入非 null 字段，避免清空未指定的标签
            if (songRepository.patchAudioTags(song.uri, tagDataToWrite)) {
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

    override fun onCleared() {
        batchMatchJob?.cancel()
        super.onCleared()
    }

    private data class MatchResult(val tagData: AudioTagData?, val status: BatchMatchResult)

    private data class ScoredSearchResult(
        val result: SongSearchResult,
        val score: Double,
        val source: SearchSource
    )
}
