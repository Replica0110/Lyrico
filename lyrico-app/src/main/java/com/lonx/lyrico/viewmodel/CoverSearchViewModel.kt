package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.source.soda.SodaRateLimitException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * 封面搜索结果
 */
data class CoverSearchResult(
    val id: String,
    val url: String,
    val source: Source,
    val title: String = "",
    val artist: String = "",
    val album: String = ""
)

/**
 * 封面搜索 UI 状态
 */
data class CoverSearchUiState(
    val searchKeyword: String = "",
    val coverResults: List<CoverSearchResult> = emptyList(),
    val availableSources: List<Source> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: UiMessage? = null,
    val isInitializing: Boolean = true
)

/**
 * 内部：封面搜索状态
 */
private data class CoverSearchState(
    val keyword: String = "",
    val results: List<CoverSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: UiMessage? = null
)

private data class CoverSourceSearchResult(
    val covers: List<CoverSearchResult> = emptyList(),
    val error: UiMessage? = null
)

class CoverSearchViewModel(
    private val sources: List<SearchSource>,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val coverSearchState = MutableStateFlow(CoverSearchState())

    private val searchConfigFlow =
        settingsRepository.searchConfigFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )

    val coverUiState: StateFlow<CoverSearchUiState> =
        combine(
            coverSearchState,
            searchConfigFlow
        ) { search, searchConfig ->

            val sourcesOrder = searchConfig?.searchSourceOrder.orEmpty()
            val enabledSources = searchConfig?.enabledSearchSources.orEmpty()
            val filteredSources = sourcesOrder.filter { it in enabledSources }

            CoverSearchUiState(
                searchKeyword = search.keyword,
                coverResults = search.results,
                availableSources = filteredSources,
                isSearching = search.isSearching,
                searchError = search.error,
                isInitializing = searchConfig == null
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CoverSearchUiState()
        )

    private var coverSearchJob: Deferred<Unit>? = null

    /**
     * 更新搜索关键词
     */
    fun onCoverKeywordChanged(keyword: String) {
        coverSearchState.update { it.copy(keyword = keyword) }
    }

    /**
     * 执行封面搜索
     */
    fun performCoverSearch(keywordOverride: String? = null) {
        val keyword = keywordOverride ?: coverSearchState.value.keyword
        if (keyword.isBlank()) return

        coverSearchJob?.cancel()
        coverSearchJob = viewModelScope.async {
            executeCoverSearch(keyword, keywordOverride != null)
        }
    }

    /**
     * 实际执行封面搜索逻辑
     */
    private suspend fun executeCoverSearch(
        keyword: String,
        updateKeyword: Boolean
    ) {
        coverSearchState.update { 
            it.copy(
                isSearching = true, 
                error = null,
                results = emptyList()
            ) 
        }

        try {
            if (updateKeyword) {
                coverSearchState.update { it.copy(keyword = keyword) }
            }

            val searchConfig = searchConfigFlow.filterNotNull().first()
            val pageSize = settingsRepository.searchPageSize.first()
            
            val enabledSources = searchConfig.searchSourceOrder
                .filter { it in searchConfig.enabledSearchSources }

            // 并行从所有启用的源搜索封面
            val sourceResults = enabledSources.map { source ->
                viewModelScope.async {
                    try {
                        val sourceImpl = findSource(source)
                        if (sourceImpl != null) {
                            val songs = sourceImpl.searchCover(keyword, pageSize)
                            CoverSourceSearchResult(
                                covers = songs.filter { it.picUrl.isNotBlank() }.map { song ->
                                    CoverSearchResult(
                                        id = song.id,
                                        url = song.picUrl,
                                        source = source,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album
                                    )
                                }
                            )
                        } else {
                            CoverSourceSearchResult()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        CoverSourceSearchResult(
                            error = when (e) {
                                is SodaRateLimitException -> e.toUiMessage()
                                else -> null
                            }
                        )
                    }
                }
            }.awaitAll()

            val allCovers = sourceResults.flatMap { it.covers }
            val allSourcesFailed = enabledSources.isNotEmpty() &&
                sourceResults.all { it.error != null }

            coverSearchState.update {
                it.copy(
                    results = allCovers.filter { cover -> cover.url.isNotBlank() },
                    error = if (allSourcesFailed) sourceResults.firstNotNullOfOrNull { result -> result.error } else null,
                    isSearching = false
                )
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            coverSearchState.update {
                it.copy(
                    error = e.toUiMessage(),
                    isSearching = false
                )
            }
        }
    }

    /**
     * 根据 Source 类型查找对应的 SearchSource 实现
     */
    private fun findSource(source: Source): SearchSource? {
        return sources.firstOrNull { it.sourceType == source }
    }

    private fun Throwable.toUiMessage(): UiMessage {
        return when (this) {
            is SodaRateLimitException -> UiMessage.StringResource(R.string.soda_rate_limited)
            else -> UiMessage.DynamicString(message ?: javaClass.simpleName)
        }
    }
}
