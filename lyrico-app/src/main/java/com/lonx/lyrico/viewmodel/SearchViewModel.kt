package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.utils.LyricsUtils
import com.lonx.lyrico.utils.SettingsManager
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: List<SongSearchResult> = emptyList(),
    val selectedSearchSource: Source = Source.KG,
    val availableSources: List<Source> = listOf(Source.KG, Source.QM, Source.NE),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    // 歌词预览相关
    val previewingSong: SongSearchResult? = null,
    val lyricsPreviewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val lyricsPreviewError: String? = null,
)

class SearchViewModel(
    private val sources: List<SearchSource>,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // 缓存搜索结果：Keyword -> (Source -> Results)
    private val searchResultCache =
        mutableMapOf<String, MutableMap<Source, List<SongSearchResult>>>()

    // 用于管理当前的搜索任务和歌词任务，防止并发冲突
    private var searchJob: Job? = null
    private var lyricsJob: Job? = null

    /**
     * 当输入框文字改变时调用
     * 注意：这里只更新状态，不直接触发网络搜索（避免打字时频繁请求）
     */
    fun onKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    /**
     * 切换搜索源
     * 如果当前有关键词，会尝试从缓存读取或重新发起搜索
     */
    fun switchSource(source: Source) {
        val currentKeyword = _uiState.value.searchKeyword

        // 更新选中的源
        _uiState.update { it.copy(selectedSearchSource = source) }

        if (currentKeyword.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        val cachedResults = getCachedResults(currentKeyword, source)
        if (cachedResults != null) {
            // 命中缓存，直接显示
            _uiState.update { it.copy(searchResults = cachedResults, searchError = null) }
        } else {
            // 未命中缓存，发起搜索
            search()
        }
    }

    /**
     * 执行搜索
     * @param keywordToSearch 可选参数，如果传入则更新状态并搜索，否则使用当前状态的关键词
     */
    fun search(keywordToSearch: String? = null) {
        val keyword = keywordToSearch ?: _uiState.value.searchKeyword

        if (keyword.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        // 取消上一次搜索
        searchJob?.cancel()

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null) }

            try {
                if (keywordToSearch != null) {
                    _uiState.update { it.copy(searchKeyword = keyword) }
                }

                val currentSource = _uiState.value.selectedSearchSource


                val sourceImpl = sources.firstOrNull { it.sourceType == currentSource }

                val separator = settingsManager.getSeparator().first()
                val results = sourceImpl?.search(keyword, separator = separator) ?: emptyList()

                // 写入缓存
                cacheSearchResults(keyword, currentSource, results)

                // 更新 UI
                _uiState.update { it.copy(searchResults = results, isSearching = false) }

            } catch (e: Exception) {
                // 如果是协程被取消（cancel），不应该视为错误显示给用户
                if (e is kotlinx.coroutines.CancellationException) throw e

                _uiState.update {
                    it.copy(
                        searchError = "搜索失败: ${e.message}",
                        isSearching = false
                    )
                }
            }
        }
    }


    // --- 缓存逻辑 ---
    private fun cacheSearchResults(
        keyword: String,
        source: Source,
        results: List<SongSearchResult>
    ) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[source] = results
    }

    private fun getCachedResults(keyword: String, source: Source): List<SongSearchResult>? {
        return searchResultCache[keyword]?.get(source)
    }

    // --- 歌词预览逻辑 ---
    /**
     * 歌词预览
     */
    fun fetchLyricsForPreview(song: SongSearchResult) {
        // 取消上一次的歌词加载（防止用户快速点击不同歌曲）
        lyricsJob?.cancel()

        lyricsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewingSong = song,
                    isPreviewLoading = true,
                    lyricsPreviewContent = null,
                    lyricsPreviewError = null
                )
            }

            try {
                // --- 通过接口获取对应搜索源 ---
                val sourceImpl = sources.firstOrNull { it.sourceType == song.source }
                val lyricsResult: LyricsResult? = sourceImpl?.getLyrics(song)

                val romaEnabled = settingsManager.getRomaEnabled().first()
                val lyricsText = lyricsResult?.let { LyricsUtils.formatLrcResult(result = it, romaEnabled = romaEnabled) }

                _uiState.update {
                    it.copy(
                        lyricsPreviewContent = lyricsText ?: "暂无歌词",
                        isPreviewLoading = false
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update {
                    it.copy(
                        lyricsPreviewError = "加载失败: ${e.message}",
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    /**
     * 直接获取歌词（回调）
     */
    fun fetchLyricsDirectly(song: SongSearchResult, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val sourceImpl = sources.firstOrNull { it.sourceType == song.source }
                val lyricsResult: LyricsResult? = sourceImpl?.getLyrics(song)

                val romaEnabled = settingsManager.getRomaEnabled().first()
                val lyricsText = lyricsResult?.let { LyricsUtils.formatLrcResult(result = it, romaEnabled = romaEnabled) }
                onResult(lyricsText)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun clearPreview() {
        // 关闭预览时也可以取消正在进行的加载任务
        lyricsJob?.cancel()
        _uiState.update {
            it.copy(
                previewingSong = null,
                lyricsPreviewContent = null,
                isPreviewLoading = false,
                lyricsPreviewError = null
            )
        }
    }
}