package com.lonx.lyrico.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.data.model.lyrics.SearchSource
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
 * 封面搜索结果
 */
data class CoverSearchResult(
    val id: String,
    val url: String,
    val source: SearchSourceUiModel,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val date: String = ""
)

/**
 * 封面搜索 UI 状态
 */
data class CoverSearchUiState(
    val searchKeyword: String = "",
    val coverResults: List<CoverSearchResult> = emptyList(),
    val availableSources: List<SearchSourceUiModel> = emptyList(),
    val selectedSource: SearchSourceUiModel? = null,
    val isSearching: Boolean = false,
    val searchErrors: Map<String, UiMessage> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap(),
    val searchSourceTabStyle: SearchSourceTabStyle = SearchSourceTabStyle.ICON_AND_TEXT,
    val isInitializing: Boolean = true
)

/**
 * 内部：封面搜索状态
 */
private data class CoverSearchState(
    val keyword: String = "",
    val selectedSourceId: String? = null,
    val resultsBySource: Map<String, List<CoverSearchResult>> = emptyMap(),
    val isSearching: Boolean = false,
    val errors: Map<String, UiMessage> = emptyMap(),
    val nextPageBySource: Map<String, Int> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap()
)

private data class CoverSourceSearchResult(
    val sourceId: String,
    val covers: List<CoverSearchResult> = emptyList(),
    val hasMore: Boolean = false,
    val error: UiMessage? = null
)

private data class MergedCoverSearchPage(
    val covers: List<CoverSearchResult>,
    val hasMore: Boolean
)

class CoverSearchViewModel(
    private val searchSourceProvider: SearchSourceProvider,
    private val settingsRepository: SettingsRepository,
    searchSourceConfigApplier: SearchSourceConfigApplier,
    private val appLogRepository: AppLogRepository
) : ViewModel() {

    private val coverSearchState = MutableStateFlow(CoverSearchState())

    private val searchConfigFlow =
        settingsRepository.searchConfigFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                null
            )
    private val observedSources =
        searchSourceProvider.observeSources(PluginSourceType.COVER)

    private val allSourcesFlow =
        observedSources
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    init {
        searchSourceConfigApplier.observeIn(viewModelScope, allSourcesFlow)
    }

    val coverUiState: StateFlow<CoverSearchUiState> =
        combine(
            coverSearchState,
            searchConfigFlow,
            allSourcesFlow
        ) { search, searchConfig, allSources ->

            val filteredSources = getSearchSources(searchConfig, allSources).map { it.toUiModel() }

            CoverSearchUiState(
                searchKeyword = search.keyword,
                coverResults = filteredSources.flatMap { source ->
                    search.resultsBySource[source.id].orEmpty()
                },
                availableSources = filteredSources,
                selectedSource = search.selectedSourceId?.let { selectedId ->
                    filteredSources.firstOrNull { it.id == selectedId }
                },
                isSearching = search.isSearching,
                searchErrors = search.errors,
                hasMoreBySource = search.hasMoreBySource,
                loadingMoreSourceIds = search.loadingMoreSourceIds,
                loadMoreErrors = search.loadMoreErrors,
                searchSourceTabStyle = searchConfig?.searchSourceTabStyle
                    ?: SearchSourceTabStyle.ICON_AND_TEXT,
                isInitializing = searchConfig == null
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            CoverSearchUiState()
        )

    private var coverSearchJob: Job? = null
    private val loadMoreJobs = mutableMapOf<String, Job>()
    private val loadMoreTokens = mutableMapOf<String, Any>()
    private var activeKeyword = ""

    /**
     * 更新搜索关键词
     */
    fun onCoverKeywordChanged(keyword: String) {
        coverSearchState.update { it.copy(keyword = keyword) }
    }

    fun onSourceSelected(source: SearchSourceUiModel) {
        coverSearchState.update { it.copy(selectedSourceId = source.id) }
        val keyword = coverSearchState.value.keyword.trim()
        if (keyword.isBlank()) return

        val current = coverSearchState.value
        if (current.isSearching) return
        if (current.resultsBySource.containsKey(source.id) || current.errors.containsKey(source.id)) {
            return
        }
        allSourcesFlow.value.firstOrNull { it.id == source.id }?.let { sourceImpl ->
            searchMissingSources(keyword, listOf(sourceImpl))
        }
    }

    fun onAllSourcesSelected() {
        coverSearchState.update { it.copy(selectedSourceId = null) }
        val keyword = coverSearchState.value.keyword.trim()
        if (keyword.isBlank()) return

        val current = coverSearchState.value
        if (current.isSearching) return
        val missingSources = allSourcesFlow.value.filterNot { source ->
            current.resultsBySource.containsKey(source.id) || current.errors.containsKey(source.id)
        }
        if (missingSources.isNotEmpty()) {
            searchMissingSources(keyword, missingSources)
        }
    }

    /**
     * 执行封面搜索
     */
    fun performCoverSearch(keywordOverride: String? = null) {
        val keyword = (keywordOverride ?: coverSearchState.value.keyword).trim()
        if (keyword.isBlank()) return

        val isNewKeyword = keyword != activeKeyword
        coverSearchJob?.cancel()
        loadMoreJobs.values.forEach(Job::cancel)
        loadMoreJobs.clear()
        loadMoreTokens.clear()
        activeKeyword = keyword
        coverSearchState.update {
            it.copy(
                keyword = keyword,
                resultsBySource = if (isNewKeyword) emptyMap() else it.resultsBySource,
                errors = if (isNewKeyword) emptyMap() else it.errors,
                nextPageBySource = if (isNewKeyword) emptyMap() else it.nextPageBySource,
                hasMoreBySource = if (isNewKeyword) emptyMap() else it.hasMoreBySource,
                loadingMoreSourceIds = emptySet(),
                loadMoreErrors = if (isNewKeyword) emptyMap() else it.loadMoreErrors,
                isSearching = true
            )
        }
        coverSearchJob = viewModelScope.launch {
            val availableSources = getSearchSources(
                searchConfig = searchConfigFlow.filterNotNull().first(),
                allSources = observedSources.first()
            )
            val selectedSourceId = coverSearchState.value.selectedSourceId
            val targets = if (selectedSourceId == null) {
                availableSources
            } else {
                availableSources.filter { it.id == selectedSourceId }
            }
            if (targets.isEmpty()) {
                coverSearchState.update {
                    it.copy(
                        errors = it.errors + (
                            NO_SOURCE_ERROR_KEY to UiMessage.StringResource(R.string.cover_source_empty)
                        ),
                        isSearching = false
                    )
                }
                return@launch
            }
            executeCoverSearch(keyword, targets, replaceExisting = true)
        }
    }

    private fun searchMissingSources(keyword: String, sources: List<SearchSource>) {
        if (sources.isEmpty()) return
        coverSearchJob?.cancel()
        coverSearchState.update { it.copy(isSearching = true) }
        coverSearchJob = viewModelScope.launch {
            executeCoverSearch(keyword, sources, replaceExisting = false)
        }
    }

    /**
     * 实际执行封面搜索逻辑
     */
    private suspend fun executeCoverSearch(
        keyword: String,
        sources: List<SearchSource>,
        replaceExisting: Boolean
    ) {
        val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
        coverSearchState.update {
            it.copy(
                resultsBySource = if (replaceExisting) it.resultsBySource - sourceIds else it.resultsBySource,
                errors = it.errors - sourceIds - NO_SOURCE_ERROR_KEY,
                nextPageBySource = if (replaceExisting) it.nextPageBySource - sourceIds else it.nextPageBySource,
                hasMoreBySource = if (replaceExisting) it.hasMoreBySource - sourceIds else it.hasMoreBySource,
                loadMoreErrors = it.loadMoreErrors - sourceIds,
                isSearching = true
            )
        }

        try {
            val pageSize = settingsRepository.searchPageSize.first()
            val sourceResults = coroutineScope {
                sources.map { sourceImpl ->
                    async {
                        try {
                            val result = searchCoverSourcePage(
                                source = sourceImpl,
                                keyword = keyword,
                                page = 1,
                                pageSize = pageSize
                            )
                            logCoverSearch(
                                level = AppLogLevel.DEBUG,
                                message = "Cover source search finished: ${result.covers.size} cover(s)",
                                detail = "keyword=$keyword\nsource=${sourceImpl.id}\npage=1\n" +
                                        "coverCount=${result.covers.size}\nhasMore=${result.hasMore}",
                                relatedId = sourceImpl.id
                            )
                            result
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            logCoverSearchException(
                                message = "Cover song candidate search failed\nkeyword=$keyword\nsource=${sourceImpl.id}",
                                throwable = e,
                                relatedId = sourceImpl.id
                            )
                            CoverSourceSearchResult(
                                sourceId = sourceImpl.id,
                                error = e.toUiMessage()
                            )
                        }
                    }
                }.awaitAll()
            }

            if (activeKeyword != keyword) return

            val allCovers = sourceResults.flatMap { it.covers }
            logCoverSearch(
                level = if (sourceResults.all { it.error != null }) AppLogLevel.WARNING else AppLogLevel.DEBUG,
                message = "Cover search finished: ${allCovers.size} cover(s)",
                detail = "keyword=$keyword\nsourceCount=${sources.size}\ncoverCount=${allCovers.size}\n" +
                        "failedSourceCount=${sourceResults.count { it.error != null }}"
            )

            coverSearchState.update {
                it.copy(
                    resultsBySource = it.resultsBySource + sourceResults.associate { result ->
                        result.sourceId to result.covers
                    },
                    nextPageBySource = it.nextPageBySource + sources.associate { it.id to 2 },
                    hasMoreBySource = it.hasMoreBySource + sourceResults.associate { result ->
                        result.sourceId to result.hasMore
                    },
                    errors = it.errors + sourceResults.mapNotNull { result ->
                        result.error?.let { result.sourceId to it }
                    },
                    isSearching = false
                )
            }

        } catch (e: Exception) {
            if (e is CancellationException) throw e
            logCoverSearchException(
                message = "Cover search failed\nkeyword=$keyword",
                throwable = e
            )
            coverSearchState.update {
                it.copy(
                    errors = it.errors + sourceIds.associateWith { e.toUiMessage() },
                    isSearching = false
                )
            }
        }
    }

    fun loadNextPage(sourceId: String? = null) {
        val keyword = activeKeyword
        if (keyword.isBlank()) return

        val current = coverSearchState.value
        val targetSourceIds = if (sourceId != null) {
            listOf(sourceId)
        } else {
            allSourcesFlow.value.map { it.id }
        }.filter { id ->
            current.hasMoreBySource[id] == true &&
                    id !in current.loadingMoreSourceIds &&
                    loadMoreJobs[id]?.isActive != true
        }

        targetSourceIds.forEach { id -> loadNextPageForSource(keyword, id) }
    }

    private fun loadNextPageForSource(keyword: String, sourceId: String) {
        val source = allSourcesFlow.value.firstOrNull { it.id == sourceId } ?: return
        val current = coverSearchState.value
        val nextPage = current.nextPageBySource[sourceId] ?: return
        if (current.hasMoreBySource[sourceId] != true) return

        coverSearchState.update {
            it.copy(
                loadingMoreSourceIds = it.loadingMoreSourceIds + sourceId,
                loadMoreErrors = it.loadMoreErrors - sourceId
            )
        }

        val requestToken = Any()
        loadMoreTokens[sourceId] = requestToken
        loadMoreJobs[sourceId] = viewModelScope.launch {
            try {
                val pageSize = settingsRepository.searchPageSize.first()
                val page = searchCoverSourcePage(source, keyword, nextPage, pageSize)
                if (activeKeyword != keyword) return@launch

                val merged = mergeCoverSearchPage(
                    existing = coverSearchState.value.resultsBySource[sourceId].orEmpty(),
                    incoming = page.covers,
                    sourceMayHaveMore = page.hasMore
                )
                logCoverSearch(
                    level = AppLogLevel.DEBUG,
                    message = "Cover source page finished: ${merged.covers.size} total cover(s)",
                    detail = "keyword=$keyword\nsource=$sourceId\npage=$nextPage\n" +
                            "receivedCount=${page.covers.size}\nhasMore=${merged.hasMore}",
                    relatedId = sourceId
                )
                coverSearchState.update {
                    it.copy(
                        resultsBySource = it.resultsBySource + (sourceId to merged.covers),
                        nextPageBySource = it.nextPageBySource + (sourceId to nextPage + 1),
                        hasMoreBySource = it.hasMoreBySource + (sourceId to merged.hasMore),
                        loadMoreErrors = it.loadMoreErrors - sourceId
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logCoverSearchException(
                    message = "Cover source page failed\nkeyword=$keyword\nsource=$sourceId\npage=$nextPage",
                    throwable = e,
                    relatedId = sourceId
                )
                if (activeKeyword == keyword) {
                    coverSearchState.update {
                        it.copy(
                            loadMoreErrors = it.loadMoreErrors + (sourceId to e.toUiMessage())
                        )
                    }
                }
            } finally {
                if (loadMoreTokens[sourceId] === requestToken) {
                    loadMoreTokens.remove(sourceId)
                    loadMoreJobs.remove(sourceId)
                    coverSearchState.update {
                        it.copy(loadingMoreSourceIds = it.loadingMoreSourceIds - sourceId)
                    }
                }
            }
        }
    }

    private suspend fun searchCoverSourcePage(
        source: SearchSource,
        keyword: String,
        page: Int,
        pageSize: Int
    ): CoverSourceSearchResult {
        val sourceModel = source.toUiModel()
        val songs = source.searchCovers(
            keyword = keyword,
            page = page,
            pageSize = pageSize
        )
        val covers = songs
            .filter { it.picUrl.isNotBlank() }
            .map { song ->
                CoverSearchResult(
                    id = song.id,
                    url = song.picUrl,
                    source = sourceModel,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    date = song.date
                )
            }
        val merged = mergeCoverSearchPage(
            existing = emptyList(),
            incoming = covers,
            sourceMayHaveMore = songs.isNotEmpty()
        )
        return CoverSourceSearchResult(
            sourceId = source.id,
            covers = merged.covers,
            hasMore = merged.hasMore
        )
    }

    private fun mergeCoverSearchPage(
        existing: List<CoverSearchResult>,
        incoming: List<CoverSearchResult>,
        sourceMayHaveMore: Boolean
    ): MergedCoverSearchPage {
        val seen = existing.mapTo(mutableSetOf()) { it.resultIdentity() }
        val uniqueIncoming = incoming.filter { seen.add(it.resultIdentity()) }
        return MergedCoverSearchPage(
            covers = existing + uniqueIncoming,
            hasMore = sourceMayHaveMore && uniqueIncoming.isNotEmpty()
        )
    }

    private fun CoverSearchResult.resultIdentity(): String =
        "${source.id}\u0000$id\u0000$url"

    private fun getSearchSources(
        searchConfig: com.lonx.lyrico.data.model.SearchConfig?,
        allSources: List<SearchSource>
    ): List<SearchSource> {
        if (searchConfig == null) return emptyList()

        // SearchSourceProvider 已按封面源优先级排序。
        return allSources
    }

    private fun Throwable.toUiMessage(): UiMessage {
        return UiMessage.DynamicString(message ?: javaClass.simpleName)
    }

    private suspend fun logCoverSearch(
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
            Log.w(TAG, "Failed to write cover search log", throwable)
        }
    }

    private suspend fun logCoverSearchException(
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
            Log.w(TAG, "Failed to write cover search exception log", logThrowable)
        }
    }

    private companion object {
        const val TAG = "CoverSearchViewModel"
        const val NO_SOURCE_ERROR_KEY = "__no_source__"
    }
}
