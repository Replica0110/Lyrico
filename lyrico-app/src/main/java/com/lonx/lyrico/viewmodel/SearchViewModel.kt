package com.lonx.lyrico.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 歌词 UI 状态
 */
data class LyricsUiState(
    val song: SongSearchResult? = null,
    val lyricsResult: LyricsResult? = null,
    val content: String? = null,
    val isLoading: Boolean = false,
    val error: UiMessage? = null
)
/**
 * 搜索 UI 状态
 */
data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: Map<String, List<SongSearchResult>> = emptyMap(),
    val searchResultPages: Map<String, List<List<SongSearchResult>>> = emptyMap(),
    val selectedSearchSource: SearchSourceUiModel? = null,
    val availableSources: List<SearchSourceUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: UiMessage? = null,
    val searchErrors: Map<String, UiMessage> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap(),
    val lyricsState: LyricsUiState = LyricsUiState(),
    val searchSourceTabStyle: SearchSourceTabStyle = SearchSourceTabStyle.ICON_AND_TEXT,
    val showAllSearchResultFields: Boolean = false,
    val isInitializing: Boolean = true
)

/**
 * 内部：搜索状态拆分
 */
private data class SearchSourceState(
    val keyword: String = "",
    val results: Map<String, List<SongSearchResult>> = emptyMap(),
    val resultPages: Map<String, List<List<SongSearchResult>>> = emptyMap(),
    val isSearching: Boolean = false,
    val errors: Map<String, UiMessage> = emptyMap(),
    val nextPageBySource: Map<String, Int> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap()
)

private data class SearchPage(
    val results: List<SongSearchResult>,
    val hasMore: Boolean
)

private data class CachedSearchResults(
    val results: List<SongSearchResult>,
    val pages: List<List<SongSearchResult>>,
    val nextPage: Int,
    val hasMore: Boolean
)

class SearchViewModel(
    private val searchSourceProvider: SearchSourceProvider,
    private val settingsRepository: SettingsRepository,
    searchSourceConfigApplier: SearchSourceConfigApplier,
    private val appLogRepository: AppLogRepository
) : ViewModel() {

    private val searchState = MutableStateFlow(SearchSourceState())
    private val lyricsState = MutableStateFlow(LyricsUiState())

    private val selectedSourceId = MutableStateFlow<String?>(null)

    val lyricConfigFlow =
        settingsRepository.lyricRenderConfigFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )
    private val renderedLyricsFlow =
        combine(
            lyricsState,
            lyricConfigFlow.filterNotNull()
        ) { lyricsState, config ->

            val raw = lyricsState.lyricsResult
            val pluginId = lyricsState.song?.pluginId

            val rendered = if (raw != null) {
                val processor = PluginFieldPostProcessor(config.toGlobalFieldProcessSettings())
                val processed = processor.processLyrics(
                    lyrics = raw,
                    config = defaultPluginFieldProcessConfig(pluginId.orEmpty())
                )
                LyricEncoder.encode(
                    result = processed,
                    config = config.copy(conversionMode = ConversionMode.NONE)
                )
            } else null

            lyricsState.copy(content = rendered)
        }
    private val searchConfigFlow =
        settingsRepository.searchConfigFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )
    private val allSourcesFlow =
        searchSourceProvider.observeSources(PluginSourceType.METADATA).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    init {
        searchSourceConfigApplier.observeIn(viewModelScope, allSourcesFlow)
    }


    val uiState: StateFlow<SearchUiState> =
        combine(
            searchState,
            searchConfigFlow,
            renderedLyricsFlow,
            selectedSourceId,
            allSourcesFlow
        ) { search, searchConfig, renderedLyrics, selectedId, allSources ->

            val sourceImpls = getSearchSources(searchConfig, allSources)
            val filteredSources = sourceImpls.map { it.toUiModel() }
            val selectedSource = selectedId?.let { id ->
                filteredSources.find { it.id == id }
            }

            SearchUiState(
                searchKeyword = search.keyword,
                searchResults = search.results,
                searchResultPages = search.resultPages,
                isSearching = search.isSearching,
                searchError = selectedSource?.let { search.errors[it.id] },
                searchErrors = search.errors,
                hasMoreBySource = search.hasMoreBySource,
                loadingMoreSourceIds = search.loadingMoreSourceIds,
                loadMoreErrors = search.loadMoreErrors,

                availableSources = filteredSources,
                selectedSearchSource = selectedSource,

                lyricsState = renderedLyrics,
                searchSourceTabStyle = searchConfig?.searchSourceTabStyle
                    ?: SearchSourceTabStyle.ICON_AND_TEXT,
                showAllSearchResultFields = searchConfig?.showAllSearchResultFields ?: false,
                isInitializing = searchConfig == null
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchUiState()
        )


    private val searchResultCache =
        mutableMapOf<String, MutableMap<String, CachedSearchResults>>()

    private var searchJob: Job? = null
    private val loadMoreJobs = mutableMapOf<String, Job>()
    private val loadMoreRequestTokens = mutableMapOf<String, Any>()
    private var lyricsJob: Job? = null
    private var activeSearchKeyword: String = ""



    fun onKeywordChanged(keyword: String) {
        searchState.update { it.copy(keyword = keyword) }
    }
    fun onSearchSourceSelected(source: SearchSourceUiModel) {
        selectedSourceId.value = source.id

        val keyword = searchState.value.keyword
        if (keyword.isBlank()) return

        val cached = getCachedResults(keyword, source.id)
        if (cached != null) {
            searchState.update {
                it.copy(
                    results = it.results + (source.id to cached.results),
                    resultPages = it.resultPages + (source.id to cached.pages),
                    errors = it.errors - source.id,
                    nextPageBySource = it.nextPageBySource + (source.id to cached.nextPage),
                    hasMoreBySource = it.hasMoreBySource + (source.id to cached.hasMore),
                    loadMoreErrors = it.loadMoreErrors - source.id
                )
            }
        } else {
            performSearch()
        }
    }

    fun onAllSourcesSelected() {
        selectedSourceId.value = null

        val keyword = searchState.value.keyword
        if (keyword.isBlank()) return

        val sourceIds = allSourcesFlow.value.map { it.id }
        val hasAllCurrentResults = sourceIds.isNotEmpty() &&
                sourceIds.all { sourceId -> searchState.value.results.containsKey(sourceId) }
        if (!hasAllCurrentResults) {
            performSearch()
        }
    }

    fun performSearch(keywordOverride: String? = null) {
        val keyword = (keywordOverride ?: searchState.value.keyword).trim()
        if (keyword.isBlank()) return

        val isNewKeyword = keyword != activeSearchKeyword
        activeSearchKeyword = keyword
        searchJob?.cancel()
        loadMoreJobs.values.forEach(Job::cancel)
        loadMoreJobs.clear()
        loadMoreRequestTokens.clear()

        searchState.update {
            it.copy(
                keyword = keyword,
                results = if (isNewKeyword) emptyMap() else it.results,
                resultPages = if (isNewKeyword) emptyMap() else it.resultPages,
                isSearching = true,
                errors = if (isNewKeyword) emptyMap() else it.errors,
                nextPageBySource = if (isNewKeyword) emptyMap() else it.nextPageBySource,
                hasMoreBySource = if (isNewKeyword) emptyMap() else it.hasMoreBySource,
                loadingMoreSourceIds = emptySet(),
                loadMoreErrors = if (isNewKeyword) emptyMap() else it.loadMoreErrors
            )
        }

        searchJob = viewModelScope.launch {

            val searchConfig = searchConfigFlow.filterNotNull().first()
            val sourceImpls = getSearchSources(
                searchConfig = searchConfig,
                allSources = allSourcesFlow.first { it.isNotEmpty() }
            )

            if (sourceImpls.isEmpty()) {
                logSearch(
                    level = AppLogLevel.WARNING,
                    message = "Search skipped: no enabled plugin source",
                    detail = "keyword=$keyword\navailableSources=${sourceImpls.map { it.id }}"
                )
                searchState.update { it.copy(isSearching = false) }
                return@launch
            }

            val source = selectedSourceId.value
            if (source == null) {
                executeSearchAll(keyword, sourceImpls, keywordOverride != null)
            } else {
                executeSearch(keyword, source, keywordOverride != null)
            }
        }
    }

    private suspend fun executeSearchAll(
        keyword: String,
        sourceImpls: List<SearchSource>,
        updateKeyword: Boolean
    ) {
        val sourceIds = sourceImpls.map { it.id }
        removeCachedResults(keyword, sourceIds)
        searchState.update {
            it.copy(
                isSearching = true,
                results = it.results - sourceIds.toSet(),
                resultPages = it.resultPages - sourceIds.toSet(),
                errors = it.errors - sourceIds.toSet(),
                nextPageBySource = it.nextPageBySource - sourceIds.toSet(),
                hasMoreBySource = it.hasMoreBySource - sourceIds.toSet(),
                loadMoreErrors = it.loadMoreErrors - sourceIds.toSet()
            )
        }

        if (updateKeyword) {
            searchState.update { it.copy(keyword = keyword) }
        }

        val results = coroutineScope {
            sourceImpls.map { source ->
                async {
                    try {
                        val sourcePage = searchFromSource(keyword, source.id, page = 1)
                        val mergedPage = mergeSearchPage(
                            existing = emptyList(),
                            incoming = sourcePage.results,
                            sourceMayHaveMore = sourcePage.hasMore
                        )
                        cacheSearchResults(
                            keyword = keyword,
                            sourceId = source.id,
                            results = mergedPage.results,
                            pages = listOf(mergedPage.results),
                            nextPage = 2,
                            hasMore = mergedPage.hasMore
                        )
                        logSearch(
                            level = AppLogLevel.DEBUG,
                            message = "Search finished: ${mergedPage.results.size} result(s)",
                            detail = "keyword=$keyword\nsource=${source.id}\npage=1\nresultCount=${mergedPage.results.size}\n" +
                                    "top=${mergedPage.results.take(5).map { "${it.id}:${it.title}" }}",
                            relatedId = source.id
                        )
                        source.id to Result.success(mergedPage)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logSearchException(
                            message = "Search failed\nkeyword=$keyword\nsource=${source.id}",
                            throwable = e,
                            relatedId = source.id
                        )
                        source.id to Result.failure<SearchPageMergeResult>(e)
                    }
                }
            }.awaitAll()
        }

        val nextResults = results
            .mapNotNull { (sourceId, result) ->
                result.getOrNull()?.let { sourceId to it.results }
            }
            .toMap()
        val nextPages = results
            .mapNotNull { (sourceId, result) -> result.getOrNull()?.let { sourceId to 2 } }
            .toMap()
        val nextResultPages = results
            .mapNotNull { (sourceId, result) ->
                result.getOrNull()?.let { sourceId to listOf(it.results) }
            }
            .toMap()
        val hasMore = results
            .mapNotNull { (sourceId, result) -> result.getOrNull()?.let { sourceId to it.hasMore } }
            .toMap()
        val nextErrors = results
            .mapNotNull { (sourceId, result) ->
                result.exceptionOrNull()?.let { sourceId to it.toUiMessage() }
            }
            .toMap()

        searchState.update {
            it.copy(
                results = it.results + nextResults,
                resultPages = it.resultPages + nextResultPages,
                errors = it.errors + nextErrors,
                nextPageBySource = it.nextPageBySource + nextPages,
                hasMoreBySource = it.hasMoreBySource + hasMore,
                isSearching = false
            )
        }
    }

    /**
     * 实际执行搜索逻辑
     */
    private suspend fun executeSearch(
        keyword: String,
        sourceId: String,
        updateKeyword: Boolean
    ) {
        removeCachedResults(keyword, listOf(sourceId))
        searchState.update {
            it.copy(
                results = it.results - sourceId,
                resultPages = it.resultPages - sourceId,
                isSearching = true,
                errors = it.errors - sourceId,
                nextPageBySource = it.nextPageBySource - sourceId,
                hasMoreBySource = it.hasMoreBySource - sourceId,
                loadMoreErrors = it.loadMoreErrors - sourceId
            )
        }

        try {
            if (updateKeyword) {
                searchState.update { it.copy(keyword = keyword) }
            }

            val sourcePage = searchFromSource(keyword, sourceId, page = 1)
            val mergedPage = mergeSearchPage(
                existing = emptyList(),
                incoming = sourcePage.results,
                sourceMayHaveMore = sourcePage.hasMore
            )
            cacheSearchResults(
                keyword = keyword,
                sourceId = sourceId,
                results = mergedPage.results,
                pages = listOf(mergedPage.results),
                nextPage = 2,
                hasMore = mergedPage.hasMore
            )
            logSearch(
                level = AppLogLevel.DEBUG,
                message = "Search finished: ${mergedPage.results.size} result(s)",
                detail = "keyword=$keyword\nsource=$sourceId\npage=1\nresultCount=${mergedPage.results.size}\n" +
                        "top=${mergedPage.results.take(5).map { "${it.id}:${it.title}" }}"
            )

            searchState.update {
                it.copy(
                    results = it.results + (sourceId to mergedPage.results),
                    resultPages = it.resultPages + (sourceId to listOf(mergedPage.results)),
                    isSearching = false,
                    errors = it.errors - sourceId,
                    nextPageBySource = it.nextPageBySource + (sourceId to 2),
                    hasMoreBySource = it.hasMoreBySource + (sourceId to mergedPage.hasMore)
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logSearchException(
                message = "Search failed\nkeyword=$keyword\nsource=$sourceId",
                throwable = e,
                relatedId = sourceId
            )
            searchState.update {
                it.copy(
                    errors = it.errors + (sourceId to e.toUiMessage()),
                    isSearching = false
                )
            }
        }
    }

    /**
     * 从指定搜索源执行搜索
     */
    private suspend fun searchFromSource(
        keyword: String,
        sourceId: String,
        page: Int
    ): SearchPage {
        val sourceImpl = findSource(sourceId) ?: run {
            logSearch(
                level = AppLogLevel.WARNING,
                message = "Search skipped: source not found",
                detail = "keyword=$keyword\nsource=$sourceId\navailableSources=${allSourcesFlow.value.map { it.id }}",
                relatedId = sourceId
            )
            return SearchPage(results = emptyList(), hasMore = false)
        }

        val separator = settingsRepository.separator.first()
        val pageSize = settingsRepository.searchPageSize.first()

        val rawResults = sourceImpl.searchSongs(
            keyword = keyword,
            page = page,
            separator = separator,
            pageSize = pageSize
        )

        val renderConfig = settingsRepository.getLyricRenderConfig()
        val processor = PluginFieldPostProcessor(renderConfig.toGlobalFieldProcessSettings())
        val processedResults = rawResults.map { result ->
            val processedFields = processor.processFields(
                pluginId = result.pluginId,
                fields = result.normalizedFields(),
                config = defaultPluginFieldProcessConfig(result.pluginId),
                fieldDefinitions = emptyList(),
                writeRules = emptyList()
            )
            result.copy(
                title = processedFields["title"] ?: result.title,
                artist = processedFields["artist"] ?: result.artist,
                album = processedFields["album"] ?: result.album,
                fields = processedFields
            )
        }
        return SearchPage(
            results = processedResults,
            // 搜索源契约没有总数或 hasNext；部分服务还会自行限制单页数量。
            // 只要本页非空就继续探测下一页，空页或重复页会终止分页。
            hasMore = rawResults.isNotEmpty()
        )
    }

    fun loadNextPage(sourceId: String? = null) {
        val keyword = activeSearchKeyword
        if (keyword.isBlank()) return

        val state = searchState.value
        val targetSourceIds = if (sourceId != null) {
            listOf(sourceId)
        } else {
            allSourcesFlow.value.map { it.id }
        }.filter { id ->
            state.hasMoreBySource[id] == true &&
                    id !in state.loadingMoreSourceIds &&
                    loadMoreJobs[id]?.isActive != true
        }

        targetSourceIds.forEach { id -> loadNextPageForSource(keyword, id) }
    }

    private fun loadNextPageForSource(keyword: String, sourceId: String) {
        val state = searchState.value
        val nextPage = state.nextPageBySource[sourceId] ?: return
        if (state.hasMoreBySource[sourceId] != true) return

        searchState.update {
            it.copy(
                loadingMoreSourceIds = it.loadingMoreSourceIds + sourceId,
                loadMoreErrors = it.loadMoreErrors - sourceId
            )
        }

        val requestToken = Any()
        loadMoreRequestTokens[sourceId] = requestToken
        loadMoreJobs[sourceId] = viewModelScope.launch {
            try {
                val sourcePage = searchFromSource(keyword, sourceId, page = nextPage)
                if (activeSearchKeyword != keyword) return@launch

                val currentResults = searchState.value.results[sourceId].orEmpty()
                val mergedPage = mergeSearchPage(
                    existing = currentResults,
                    incoming = sourcePage.results,
                    sourceMayHaveMore = sourcePage.hasMore
                )
                val newPageResults = mergedPage.results.drop(currentResults.size)
                val currentResultPages = searchState.value.resultPages[sourceId].orEmpty()
                val updatedResultPages = if (newPageResults.isEmpty()) {
                    currentResultPages
                } else {
                    currentResultPages + listOf(newPageResults)
                }
                cacheSearchResults(
                    keyword = keyword,
                    sourceId = sourceId,
                    results = mergedPage.results,
                    pages = updatedResultPages,
                    nextPage = nextPage + 1,
                    hasMore = mergedPage.hasMore
                )
                logSearch(
                    level = AppLogLevel.DEBUG,
                    message = "Search page finished: ${mergedPage.addedCount} new result(s)",
                    detail = "keyword=$keyword\nsource=$sourceId\npage=$nextPage\n" +
                            "receivedCount=${sourcePage.results.size}\n" +
                            "totalCount=${mergedPage.results.size}\nhasMore=${mergedPage.hasMore}",
                    relatedId = sourceId
                )
                searchState.update {
                    it.copy(
                        results = it.results + (sourceId to mergedPage.results),
                        resultPages = it.resultPages + (sourceId to updatedResultPages),
                        nextPageBySource = it.nextPageBySource + (sourceId to nextPage + 1),
                        hasMoreBySource = it.hasMoreBySource + (sourceId to mergedPage.hasMore),
                        loadMoreErrors = it.loadMoreErrors - sourceId
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logSearchException(
                    message = "Search page failed\nkeyword=$keyword\nsource=$sourceId\npage=$nextPage",
                    throwable = e,
                    relatedId = sourceId
                )
                if (activeSearchKeyword == keyword) {
                    searchState.update {
                        it.copy(loadMoreErrors = it.loadMoreErrors + (sourceId to e.toUiMessage()))
                    }
                }
            } finally {
                if (loadMoreRequestTokens[sourceId] === requestToken) {
                    loadMoreRequestTokens.remove(sourceId)
                    loadMoreJobs.remove(sourceId)
                    searchState.update {
                        it.copy(loadingMoreSourceIds = it.loadingMoreSourceIds - sourceId)
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 歌词逻辑
    // -------------------------------------------------------------------------

    /**
     * 加载指定歌曲的歌词
     * 同一时间只允许一个歌词加载任务
     */
    fun loadLyrics(song: SongSearchResult) {
        lyricsJob?.cancel()
        val source = findSource(song.pluginId)
        if (source == null || PluginCapability.GET_LYRICS !in source.capabilities) {
            lyricsState.value = LyricsUiState(song = song)
            return
        }

        lyricsJob = viewModelScope.launch {
            lyricsState.value = LyricsUiState(song = song, isLoading = true)
            try {
                val lyricsResult = source
                    .getLyricsCandidates(song)
                    .firstOrNull()
                    ?.lyrics
                logSearch(
                    level = if (lyricsResult == null) AppLogLevel.WARNING else AppLogLevel.DEBUG,
                    message = if (lyricsResult == null) {
                        "Lyrics load finished without usable lyrics"
                    } else {
                        "Lyrics load finished"
                    },
                    detail = "source=${song.pluginId}\nsong=${song.id}:${song.title}\n" +
                        "payloadType=${lyricsResult?.payloadType}",
                    relatedId = song.pluginId
                )
                lyricsState.update {
                    it.copy(
                        lyricsResult = lyricsResult,
                        isLoading = false,
                        error = if (lyricsResult == null) {
                            UiMessage.StringResource(R.string.lyrics_empty)
                        } else null
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logSearchException(
                    message = "Lyrics load failed\nsource=${song.pluginId}\n" +
                        "song=${song.id}:${song.title}",
                    throwable = e,
                    relatedId = song.pluginId
                )
                lyricsState.update {
                    it.copy(
                        error = e.toUiMessage(),
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 清除当前歌词状态
     * 通常用于关闭歌词页或切换歌曲列表时
     */
    fun clearLyrics() {
        lyricsJob?.cancel()
        lyricsState.value = LyricsUiState()
    }

    private fun LyricRenderConfig.toGlobalFieldProcessSettings(): GlobalFieldProcessSettings {
        return GlobalFieldProcessSettings(
            scriptConversion = conversionMode,
            removeEmptyLines = removeEmptyLines
        )
    }

    // -------------------------------------------------------------------------
    // 工具 & 缓存
    // -------------------------------------------------------------------------

    /**
     * 根据 sourceId 查找对应的 SearchSource 实现
     */
    private fun findSource(sourceId: String): SearchSource? {
        return allSourcesFlow.value.firstOrNull { it.id == sourceId }
    }

    private suspend fun logSearch(
        level: AppLogLevel,
        message: String,
        detail: String? = null,
        relatedId: String? = null
    ) {
        runCatching {
            appLogRepository.log(
                level = level,
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                detail = detail,
                relatedId = relatedId
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to write search log", throwable)
        }
    }

    private suspend fun logSearchException(
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    ) {
        runCatching {
            appLogRepository.logException(
                type = AppLogType.PLUGIN,
                tag = TAG,
                message = message,
                throwable = throwable,
                relatedId = relatedId
            )
        }.onFailure { logThrowable ->
            Log.w(TAG, "Failed to write search exception log", logThrowable)
        }
    }

    /**
     * 缓存搜索结果
     */
    private fun cacheSearchResults(
        keyword: String,
        sourceId: String,
        results: List<SongSearchResult>,
        pages: List<List<SongSearchResult>>,
        nextPage: Int,
        hasMore: Boolean
    ) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[sourceId] = CachedSearchResults(
            results = results,
            pages = pages,
            nextPage = nextPage,
            hasMore = hasMore
        )
    }

    /**
     * 从缓存中读取搜索结果
     */
    private fun getCachedResults(
        keyword: String,
        sourceId: String
    ): CachedSearchResults? {
        return searchResultCache[keyword]?.get(sourceId)
    }

    private fun removeCachedResults(keyword: String, sourceIds: Collection<String>) {
        val keywordCache = searchResultCache[keyword] ?: return
        sourceIds.forEach(keywordCache::remove)
        if (keywordCache.isEmpty()) searchResultCache.remove(keyword)
    }

    private fun getSearchSources(
        searchConfig: com.lonx.lyrico.data.model.SearchConfig?,
        allSources: List<SearchSource>
    ): List<SearchSource> {
        if (searchConfig == null) return emptyList()

        // SearchSourceProvider 已按元数据源优先级排序。
        return allSources
    }

    fun setLyricFormat(format: LyricFormat) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(format)
        }
    }

    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRomaEnabled(enabled)
        }
    }

    fun setLyricLineOrder(order: List<LyricLineTrack>) {
        viewModelScope.launch {
            settingsRepository.saveLyricLineOrder(order)
        }
    }

    fun setTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveTranslationEnabled(enabled)
        }
    }

    fun setOnlyTranslationIfAvailable(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveOnlyTranslationIfAvailable(enabled)
        }
    }

    fun setRemoveEmptyLines(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRemoveEmptyLines(enabled)
        }
    }

    fun setConversionMode(mode: ConversionMode) {
        viewModelScope.launch {
            settingsRepository.saveConversionMode(mode)
        }
    }

    private fun Throwable.toUiMessage(): UiMessage {
        return UiMessage.DynamicString(message ?: javaClass.simpleName)
    }

    private companion object {
        const val TAG = "SearchViewModel"
    }
}
