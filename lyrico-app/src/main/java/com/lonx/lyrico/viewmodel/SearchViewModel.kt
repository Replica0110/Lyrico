package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 歌词相关 UI 状态
 * 代表“当前选中的歌曲及其歌词加载状态”
 */
data class LyricsUiState(
    val song: SongSearchResult? = null,
    val content: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)


data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: List<SongSearchResult> = emptyList(),
    val selectedSearchSource: Source? = null,
    val availableSources: List<Source> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val lyricsState: LyricsUiState = LyricsUiState(),
    val isInitializing: Boolean = true
)

class SearchViewModel(
    private val sources: List<SearchSource>,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    init {
        // 初始加载配置
        viewModelScope.launch {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        try {
            val settings = settingsRepository.settingsFlow.first()
            val sourcesOrder = settings.searchSourceOrder

            _uiState.update { it.copy(
                availableSources = sourcesOrder,
                selectedSearchSource = sourcesOrder.firstOrNull(),
                isInitializing = false
            ) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isInitializing = false, searchError = "加载配置失败") }
        }
    }
    /**
     * 搜索结果缓存：
     * Keyword -> (Source -> Results)
     */
    private val searchResultCache =
        mutableMapOf<String, MutableMap<Source, List<SongSearchResult>>>()

    /**
     * 当前搜索协程任务
     */
    private var searchJob: Job? = null

    /**
     * 当前歌词加载协程任务
     */
    private var lyricsJob: Job? = null

    // -------------------------------------------------------------------------
    // 搜索相关
    // -------------------------------------------------------------------------

    /**
     * 当搜索框关键字发生变化时调用
     * 仅更新状态，不触发实际搜索（避免频繁网络请求）
     */
    fun onKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    /**
     * 当用户选择新的搜索源时调用
     * 如果存在关键词，会优先尝试从缓存加载结果
     */
    fun onSearchSourceSelected(source: Source) {
        _uiState.update { it.copy(selectedSearchSource = source) }

        val keyword = _uiState.value.searchKeyword
        if (keyword.isNotBlank()) {
            getCachedResults(keyword, source)?.let { cached ->
                _uiState.update { it.copy(searchResults = cached, searchError = null) }
            } ?: performSearch()
        }
    }

    /**
     * 触发搜索操作
     *
     * @param keywordOverride 可选关键字：
     * - null：使用当前状态中的 keyword
     * - 非 null：更新 keyword 并执行搜索
     */
    fun performSearch(keywordOverride: String? = null) {
        val keyword = keywordOverride ?: uiState.value.searchKeyword
        if (keyword.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (_uiState.value.isInitializing) {
                _uiState.filter { !it.isInitializing }.first()
            }

            // 2. 此时获取的 source 一定是设置里的第一个（或者是用户刚切的）
            val currentSource = _uiState.value.selectedSearchSource ?: return@launch

            executeSearch(keyword, currentSource, keywordOverride != null)
        }
    }

    /**
     * 实际执行搜索逻辑
     */
    private suspend fun executeSearch(
        keyword: String,
        source: Source,
        shouldUpdateKeyword: Boolean
    ) {
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        try {
            if (shouldUpdateKeyword) {
                _uiState.update { it.copy(searchKeyword = keyword) }
            }

            val results = searchFromSource(keyword, source)
            cacheSearchResults(keyword, source, results)

            _uiState.update {
                it.copy(searchResults = results, isSearching = false)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(searchError = "搜索失败: ${e.message}", isSearching = false) }
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

    /**
     * 清空搜索结果
     */
    private fun clearSearchResults() {
        _uiState.update {
            it.copy(
                searchResults = emptyList(),
                isSearching = false,
                searchError = null
            )
        }
    }

    /**
     * 加载指定歌曲的歌词
     * 同一时间只允许一个歌词加载任务
     */
    fun loadLyrics(song: SongSearchResult) {
        lyricsJob?.cancel()

        lyricsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    lyricsState = LyricsUiState(
                        song = song,
                        isLoading = true
                    )
                )
            }

            try {
                val lyrics = loadFormattedLyrics(song)

                _uiState.update {
                    it.copy(
                        lyricsState = it.lyricsState.copy(
                            content = lyrics ?: "暂无歌词",
                            isLoading = false
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        lyricsState = it.lyricsState.copy(
                            error = "加载失败: ${e.message}",
                            isLoading = false
                        )
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
        _uiState.update {
            it.copy(lyricsState = LyricsUiState())
        }
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

        return LyricsUtils.formatLrcResult(
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
}
