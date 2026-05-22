package com.lonx.lyrico.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
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
    val error: UiMessage? = null
)
/**
 * 搜索 UI 状态
 */
data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: Map<String, List<SongSearchResult>> = emptyMap(),
    val selectedSearchSource: SearchSourceUiModel? = null,
    val availableSources: List<SearchSourceUiModel> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: UiMessage? = null,
    val searchErrors: Map<String, UiMessage> = emptyMap(),
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
    val errors: Map<String, UiMessage> = emptyMap()
)

class SearchViewModel(
    private val searchSourceProvider: SearchSourceProvider,
    private val settingsRepository: SettingsRepository,
    searchSourceConfigApplier: SearchSourceConfigApplier
) : ViewModel() {

    init {
        searchSourceConfigApplier.observeIn(viewModelScope)
    }


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
    private val allSourcesFlow =
        searchSourceProvider.observeAllSources()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )


    val uiState: StateFlow<SearchUiState> =
        combine(
            searchState,
            searchConfigFlow,
            renderedLyricsFlow,
            selectedSourceId,
            allSourcesFlow
        ) { search, searchConfig, renderedLyrics, selectedId, allSources ->

            val sourceImpls = buildOrderedSources(searchConfig, allSources)
            val filteredSources = sourceImpls.map { it.toUiModel() }
            val selectedSource =
                filteredSources.find { it.id == selectedId }
                    ?: filteredSources.firstOrNull()

            SearchUiState(
                searchKeyword = search.keyword,
                searchResults = search.results,
                isSearching = search.isSearching,
                searchError = selectedSource?.let { search.errors[it.id] },
                searchErrors = search.errors,

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
        mutableMapOf<String, MutableMap<String, List<SongSearchResult>>>()

    private var searchJob: Job? = null
    private var lyricsJob: Job? = null



    fun onKeywordChanged(keyword: String) {
        searchState.update { it.copy(keyword = keyword) }
    }
    private suspend fun getLyricsResult(song: SongSearchResult): LyricsResult? {
        val impl = findSource(song.pluginId) ?: return null
        return impl.getLyrics(song)
    }

    fun onSearchSourceSelected(source: SearchSourceUiModel) {
        selectedSourceId.value = source.id

        val keyword = searchState.value.keyword
        if (keyword.isBlank()) return

        val cached = getCachedResults(keyword, source.id)
        if (cached != null) {
            searchState.update {
                it.copy(
                    results = it.results + (source.id to cached),
                    errors = it.errors - source.id
                )
            }
        } else {
            performSearch()
        }
    }

    fun performSearch(keywordOverride: String? = null) {
        val keyword = (keywordOverride ?: searchState.value.keyword).trim()
        if (keyword.isBlank()) return
        searchState.update {
            it.copy(
                keyword = keyword,
                isSearching = true
            )
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {

            val searchConfig = searchConfigFlow.filterNotNull().first()
            val sourceImpls = buildOrderedSources(
                searchConfig = searchConfig,
                allSources = allSourcesFlow.first { it.isNotEmpty() }
            )

            val source =
                selectedSourceId.value
                    ?: sourceImpls.firstOrNull()?.id

            if (source == null) {
                searchState.update { it.copy(isSearching = false) }
                return@launch
            }

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
        sourceId: String,
        updateKeyword: Boolean
    ) {
        searchState.update { it.copy(isSearching = true, errors = it.errors - sourceId) }

        try {
            if (updateKeyword) {
                searchState.update { it.copy(keyword = keyword) }
            }

            val results = searchFromSource(keyword, sourceId)
            cacheSearchResults(keyword, sourceId, results)

            searchState.update {
                it.copy(
                    results = it.results + (sourceId to results),
                    isSearching = false,
                    errors = it.errors - sourceId
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
        sourceId: String
    ): List<SongSearchResult> {
        val sourceImpl = findSource(sourceId) ?: return emptyList()

        val separator = settingsRepository.separator.first()
        val pageSize = settingsRepository.searchPageSize.first()

        return sourceImpl.searchSongs(
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
                        error = if (lyricsResult == null) UiMessage.StringResource(R.string.lyrics_empty) else null
                    )
                }

            } catch (e: Exception) {
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
        val sourceImpl = findSource(song.pluginId) ?: return null
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
     * 根据 sourceId 查找对应的 SearchSource 实现
     */
    private fun findSource(sourceId: String): SearchSource? {
        return allSourcesFlow.value.firstOrNull { it.id == sourceId }
    }

    /**
     * 缓存搜索结果
     */
    private fun cacheSearchResults(
        keyword: String,
        sourceId: String,
        results: List<SongSearchResult>
    ) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[sourceId] = results
    }

    /**
     * 从缓存中读取搜索结果
     */
    private fun getCachedResults(
        keyword: String,
        sourceId: String
    ): List<SongSearchResult>? {
        return searchResultCache[keyword]?.get(sourceId)
    }

    private fun buildOrderedSources(
        searchConfig: com.lonx.lyrico.data.model.SearchConfig?,
        allSources: List<SearchSource>
    ): List<SearchSource> {
        if (searchConfig == null) return emptyList()

        return allSources.sortedBy { it.name }
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

    private fun Throwable.toUiMessage(): UiMessage {
        return UiMessage.DynamicString(message ?: javaClass.simpleName)
    }
}
