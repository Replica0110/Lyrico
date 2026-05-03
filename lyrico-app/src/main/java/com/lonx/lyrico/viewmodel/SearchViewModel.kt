package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchCapability
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Job
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
    val error: String? = null
)
/**
 * 搜索 UI 状态
 */
data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: Map<String, List<SongSearchResult>> = emptyMap(),
    val selectedSearchSource: Source? = null,
    val availableSources: List<Source> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val lyricsState: LyricsUiState = LyricsUiState(),
    val isInitializing: Boolean = true
)

/**
 * 内部：搜索状态拆分
 */
private data class SearchSourceState(
    val keyword: String = "",
    val results: Map<String, List<SongSearchResult>> = emptyMap(),
    val isSearching: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val sources: List<SearchSource>,
    private val settingsRepository: SettingsRepository
) : ViewModel() {


    private val searchState = MutableStateFlow(SearchSourceState())
    private val lyricsState = MutableStateFlow(LyricsUiState())

    private val selectedSourceId = MutableStateFlow<Source?>(null)

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

            val rendered = if (raw != null) {
                LyricEncoder.encode(
                    result = raw,
                    config = config
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


    val uiState: StateFlow<SearchUiState> =
        combine(
            searchState,
            searchConfigFlow,
            renderedLyricsFlow,
            selectedSourceId
        ) { search, searchConfig, renderedLyrics, selectedId ->

            val sourcesOrder = searchConfig?.lyricsSourceConfig?.sourceOrder.orEmpty()
            val enabledSources = searchConfig?.lyricsSourceConfig?.enabledSources.orEmpty()

            val filteredSources = sourcesOrder.filter { source ->
                source in enabledSources &&
                        findSource(source)?.capabilities?.contains(SearchCapability.LYRICS) != false
            }

            val selectedSource =
                filteredSources.find { it == selectedId }
                    ?: filteredSources.firstOrNull()

            SearchUiState(
                searchKeyword = search.keyword,
                searchResults = search.results,
                isSearching = search.isSearching,
                searchError = search.error,

                availableSources = filteredSources,
                selectedSearchSource = selectedSource,

                lyricsState = renderedLyrics,
                isInitializing = searchConfig == null
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchUiState()
        )


    private val searchResultCache =
        mutableMapOf<String, MutableMap<Source, List<SongSearchResult>>>()

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null



    fun onKeywordChanged(keyword: String) {
        searchState.update { it.copy(keyword = keyword) }
    }
    private suspend fun getLyricsResult(song: SongSearchResult): LyricsResult? {
        val impl = findSource(song.source) ?: return null
        return impl.getLyrics(song)
    }

    fun onSearchSourceSelected(source: Source) {
        selectedSourceId.value = source

        val keyword = searchState.value.keyword
        if (keyword.isBlank()) return

        val cached = getCachedResults(keyword, source)
        if (cached != null) {
            searchState.update {
                it.copy(
                    results = it.results + (source.name to cached),
                    error = null
                )
            }
        } else {
            performSearch()
        }
    }

    fun performSearch(keywordOverride: String? = null) {
        val keyword = keywordOverride ?: searchState.value.keyword
        if (keyword.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {

            val searchConfig = searchConfigFlow.filterNotNull().first()

            val source =
                selectedSourceId.value
                    ?: searchConfig.lyricsSourceConfig.sourceOrder.firstOrNull { source ->
                        source in searchConfig.lyricsSourceConfig.enabledSources &&
                                findSource(source)?.capabilities?.contains(SearchCapability.LYRICS) != false
                    }

            if (source == null) return@launch

            if (selectedSourceId.value == null) {
                selectedSourceId.value = source
            }

            executeSearch(keyword, source, keywordOverride != null)
        }
    }

    /**
     * 实际执行搜索逻辑
     */
    private suspend fun executeSearch(
        keyword: String,
        source: Source,
        updateKeyword: Boolean
    ) {
        searchState.update { it.copy(isSearching = true, error = null) }

        try {
            if (updateKeyword) {
                searchState.update { it.copy(keyword = keyword) }
            }

            val results = searchFromSource(keyword, source)
            cacheSearchResults(keyword, source, results)

            searchState.update {
                it.copy(
                    results = it.results + (source.name to results),
                    isSearching = false
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            searchState.update {
                it.copy(
                    error = e.message,
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
        source: Source
    ): List<SongSearchResult> {
        val sourceImpl = findSource(source) ?: return emptyList()

        val separator = settingsRepository.separator.first()
        val pageSize = settingsRepository.searchPageSize.first()

        return sourceImpl.search(
            keyword = keyword,
            page = 1,
            separator = separator,
            pageSize = pageSize
        )
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

        lyricsJob = viewModelScope.launch {
            lyricsState.value = LyricsUiState(
                song = song,
                isLoading = true
            )

            try {
                val lyricsResult = getLyricsResult(song)

                lyricsState.update {
                    it.copy(
                        lyricsResult = lyricsResult,
                        isLoading = false,
                        error = if (lyricsResult == null) "暂无歌词" else null
                    )
                }

            } catch (e: Exception) {
                lyricsState.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    /**
     * 直接获取歌词 (用于列表页"应用"按钮)
     */
    suspend fun fetchLyrics(song: SongSearchResult): String? {
        return loadFormattedLyrics(song)
    }

    /**
     * 清除当前歌词状态
     * 通常用于关闭歌词页或切换歌曲列表时
     */
    fun clearLyrics() {
        lyricsJob?.cancel()
        lyricsState.value = LyricsUiState()
    }

    /**
     * 加载并格式化歌词内容
     */
    private suspend fun loadFormattedLyrics(
        song: SongSearchResult
    ): String? {
        val sourceImpl = findSource(song.source) ?: return null
        val lyricsResult = sourceImpl.getLyrics(song) ?: return null

        val config = settingsRepository.getLyricRenderConfig()

        return LyricEncoder.encode(
            result = lyricsResult,
            config = config
        )

    }

    // -------------------------------------------------------------------------
    // 工具 & 缓存
    // -------------------------------------------------------------------------

    /**
     * 根据 Source 类型查找对应的 SearchSource 实现
     */
    private fun findSource(source: Source): SearchSource? {
        return sources.firstOrNull { it.sourceType == source }
    }

    /**
     * 缓存搜索结果
     */
    private fun cacheSearchResults(
        keyword: String,
        source: Source,
        results: List<SongSearchResult>
    ) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[source] = results
    }

    /**
     * 从缓存中读取搜索结果
     */
    private fun getCachedResults(
        keyword: String,
        source: Source
    ): List<SongSearchResult>? {
        return searchResultCache[keyword]?.get(source)
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
}
