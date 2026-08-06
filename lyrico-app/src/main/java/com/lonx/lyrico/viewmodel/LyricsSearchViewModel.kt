package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginCapability
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.domain.SearchSourceConfigApplier
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import com.lonx.lyrico.utils.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal fun Set<PluginCapability>.usesSongSearchForLyricsCandidates(): Boolean =
    PluginCapability.SEARCH_SONGS in this

data class LyricsSearchCandidateUi(
    val song: SongSearchResult,
    val lyrics: LyricsResult? = null,
    val formattedLyrics: String = ""
)

data class LyricsSearchUiState(
    val searchKeyword: String = "",
    val availableSources: List<SearchSourceUiModel> = emptyList(),
    val selectedSource: SearchSourceUiModel? = null,
    val results: Map<String, List<LyricsSearchCandidateUi>> = emptyMap(),
    val loadingSourceIds: Set<String> = emptySet(),
    val errors: Map<String, UiMessage> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap(),
    val loadingCandidateKeys: Set<String> = emptySet(),
    val candidateErrors: Map<String, UiMessage> = emptyMap(),
    val searchSourceTabStyle: SearchSourceTabStyle = SearchSourceTabStyle.ICON_AND_TEXT,
    val isInitializing: Boolean = true
)

private data class LyricsSearchState(
    val searchKeyword: String = "",
    val initialKeyword: String = "",
    val requestSong: SongSearchResult? = null,
    val selectedSourceId: String? = null,
    val results: Map<String, List<LyricsSearchCandidateUi>> = emptyMap(),
    val loadingSourceIds: Set<String> = emptySet(),
    val errors: Map<String, UiMessage> = emptyMap(),
    val nextPageBySource: Map<String, Int> = emptyMap(),
    val hasMoreBySource: Map<String, Boolean> = emptyMap(),
    val loadingMoreSourceIds: Set<String> = emptySet(),
    val loadMoreErrors: Map<String, UiMessage> = emptyMap(),
    val loadingCandidateKeys: Set<String> = emptySet(),
    val candidateErrors: Map<String, UiMessage> = emptyMap()
)

private data class CachedLyricsSearchResults(
    val results: List<LyricsSearchCandidateUi>,
    val nextPage: Int,
    val hasMore: Boolean
)

private data class LyricsSearchPage(
    val results: List<LyricsSearchCandidateUi>,
    val hasMore: Boolean
)

private data class MergedLyricsSearchPage(
    val results: List<LyricsSearchCandidateUi>,
    val hasMore: Boolean
)

class LyricsSearchViewModel(
    private val searchSourceProvider: SearchSourceProvider,
    private val settingsRepository: SettingsRepository,
    searchSourceConfigApplier: SearchSourceConfigApplier
) : ViewModel() {
    private val state = MutableStateFlow(LyricsSearchState())
    private val resultCache =
        mutableMapOf<String, MutableMap<String, CachedLyricsSearchResults>>()
    private val sourceSearchJobs = mutableMapOf<String, Job>()
    private val sourceSearchTokens = mutableMapOf<String, Any>()
    private val loadMoreJobs = mutableMapOf<String, Job>()
    private val loadMoreTokens = mutableMapOf<String, Any>()
    private val candidateJobs = mutableMapOf<String, Job>()
    private val candidateTokens = mutableMapOf<String, Any>()
    private var searchCoordinatorJob: Job? = null
    private var requestKey: String? = null
    private var activeSearchKeyword = ""

    private val observedSources = searchSourceProvider
        .observeSources(PluginSourceType.LYRICS)

    private val sourceFlow = observedSources
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val searchConfigFlow = settingsRepository.searchConfigFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    val lyricConfigFlow = settingsRepository.lyricRenderConfigFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null
        )

    val uiState: StateFlow<LyricsSearchUiState> = combine(
        state,
        sourceFlow,
        searchConfigFlow,
        lyricConfigFlow
    ) { current, sourceList, searchConfig, lyricConfig ->
        val sourceModels = sourceList.map { it.toUiModel() }
        LyricsSearchUiState(
            searchKeyword = current.searchKeyword,
            availableSources = sourceModels,
            selectedSource = current.selectedSourceId?.let { selectedId ->
                sourceModels.firstOrNull { it.id == selectedId }
            },
            results = if (lyricConfig == null) {
                current.results
            } else {
                current.results.mapValues { (_, candidates) ->
                    candidates.map { it.render(lyricConfig) }
                }
            },
            loadingSourceIds = current.loadingSourceIds,
            errors = current.errors,
            hasMoreBySource = current.hasMoreBySource,
            loadingMoreSourceIds = current.loadingMoreSourceIds,
            loadMoreErrors = current.loadMoreErrors,
            loadingCandidateKeys = current.loadingCandidateKeys,
            candidateErrors = current.candidateErrors,
            searchSourceTabStyle = searchConfig?.searchSourceTabStyle
                ?: SearchSourceTabStyle.ICON_AND_TEXT,
            isInitializing = searchConfig == null || lyricConfig == null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LyricsSearchUiState()
    )

    init {
        searchSourceConfigApplier.observeIn(viewModelScope, sourceFlow)
    }

    fun initialize(title: String, artist: String, album: String, date: String) {
        val key = listOf(title, artist, album, date).joinToString("\u0000")
        if (requestKey == key) return
        requestKey = key

        val initialKeyword = listOf(title, artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val requestSong = SongSearchResult(
            id = LOCAL_SONG_ID,
            pluginId = "",
            pluginName = "",
            title = title,
            artist = artist,
            album = album,
            date = date
        )
        state.value = LyricsSearchState(
            searchKeyword = initialKeyword,
            initialKeyword = initialKeyword,
            requestSong = requestSong
        )
        if (initialKeyword.isNotBlank()) {
            performSearch(initialKeyword)
        }
    }

    fun onKeywordChanged(keyword: String) {
        state.update { it.copy(searchKeyword = keyword) }
    }

    fun onSourceSelected(source: SearchSourceUiModel) {
        state.update { it.copy(selectedSourceId = source.id) }
        val keyword = state.value.searchKeyword.trim()
        if (keyword.isBlank()) return

        restoreCachedResult(keyword, source.id)
        if (!state.value.results.containsKey(source.id)) {
            sourceFlow.value.firstOrNull { it.id == source.id }?.let { sourceImpl ->
                searchSources(keyword, listOf(sourceImpl), forceRefresh = false)
            }
        }
    }

    fun onAllSourcesSelected() {
        state.update { it.copy(selectedSourceId = null) }
        val keyword = state.value.searchKeyword.trim()
        if (keyword.isBlank()) return

        sourceFlow.value.forEach { restoreCachedResult(keyword, it.id) }
        val missingSources = sourceFlow.value.filterNot { source ->
            state.value.results.containsKey(source.id)
        }
        if (missingSources.isNotEmpty()) {
            searchSources(keyword, missingSources, forceRefresh = false)
        }
    }

    fun performSearch(keywordOverride: String? = null) {
        val keyword = (keywordOverride ?: state.value.searchKeyword).trim()
        if (keyword.isBlank()) return

        sourceSearchJobs.values.forEach(Job::cancel)
        sourceSearchJobs.clear()
        loadMoreJobs.values.forEach(Job::cancel)
        loadMoreJobs.clear()
        searchCoordinatorJob?.cancel()
        candidateJobs.values.forEach(Job::cancel)
        candidateJobs.clear()

        val isNewKeyword = keyword != activeSearchKeyword
        activeSearchKeyword = keyword
        state.update {
            it.copy(
                searchKeyword = keyword,
                results = if (isNewKeyword) emptyMap() else it.results,
                errors = if (isNewKeyword) emptyMap() else it.errors,
                loadingSourceIds = emptySet(),
                nextPageBySource = if (isNewKeyword) emptyMap() else it.nextPageBySource,
                hasMoreBySource = if (isNewKeyword) emptyMap() else it.hasMoreBySource,
                loadingMoreSourceIds = emptySet(),
                loadMoreErrors = if (isNewKeyword) emptyMap() else it.loadMoreErrors,
                loadingCandidateKeys = emptySet(),
                candidateErrors = emptyMap()
            )
        }

        searchCoordinatorJob = viewModelScope.launch {
            val availableSources = observedSources.first()
            val selectedSourceId = state.value.selectedSourceId
            val targets = if (selectedSourceId == null) {
                availableSources
            } else {
                availableSources.filter { it.id == selectedSourceId }
            }
            if (targets.isEmpty()) {
                state.update {
                    it.copy(
                        errors = mapOf(
                            NO_SOURCE_ERROR_KEY to UiMessage.StringResource(
                                R.string.lyrics_source_empty
                            )
                        )
                    )
                }
                return@launch
            }
            searchSources(keyword, targets, forceRefresh = true)
        }
    }

    fun loadLyrics(sourceId: String, candidate: LyricsSearchCandidateUi) {
        if (candidate.lyrics != null) return
        val source = sourceFlow.value.firstOrNull { it.id == sourceId } ?: return
        val keyword = state.value.searchKeyword.trim()
        val candidateKey = candidateKey(sourceId, candidate.song.id)

        candidateJobs[candidateKey]?.cancel()
        val requestToken = Any()
        candidateTokens[candidateKey] = requestToken
        candidateJobs[candidateKey] = viewModelScope.launch {
            state.update {
                it.copy(
                    loadingCandidateKeys = it.loadingCandidateKeys + candidateKey,
                    candidateErrors = it.candidateErrors - candidateKey
                )
            }
            try {
                val loaded = source.getLyricsCandidates(candidate.song).firstOrNull()
                if (loaded == null) {
                    state.update {
                        it.copy(
                            candidateErrors = it.candidateErrors + (
                                candidateKey to UiMessage.StringResource(R.string.lyrics_empty)
                            )
                        )
                    }
                    return@launch
                }

                updateCandidate(keyword, sourceId, candidate.song.id) {
                    LyricsSearchCandidateUi(
                        song = loaded.song.copy(
                            id = candidate.song.id,
                            pluginId = candidate.song.pluginId,
                            pluginName = candidate.song.pluginName
                        ),
                        lyrics = loaded.lyrics
                    )
                }
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
                state.update {
                    it.copy(
                        candidateErrors = it.candidateErrors + (
                            candidateKey to throwable.toUiMessage()
                        )
                    )
                }
            } finally {
                if (candidateTokens[candidateKey] === requestToken) {
                    candidateTokens.remove(candidateKey)
                    candidateJobs.remove(candidateKey)
                    state.update {
                        it.copy(loadingCandidateKeys = it.loadingCandidateKeys - candidateKey)
                    }
                }
            }
        }
    }

    fun candidateKey(sourceId: String, songId: String): String =
        "$sourceId\u0000$songId"

    fun loadNextPage(sourceId: String? = null) {
        val keyword = activeSearchKeyword
        if (keyword.isBlank()) return

        val current = state.value
        val targetSourceIds = if (sourceId != null) {
            listOf(sourceId)
        } else {
            sourceFlow.value.map { it.id }
        }.filter { id ->
            current.hasMoreBySource[id] == true &&
                    id !in current.loadingMoreSourceIds &&
                    loadMoreJobs[id]?.isActive != true
        }

        targetSourceIds.forEach { id -> loadNextPageForSource(keyword, id) }
    }

    private fun loadNextPageForSource(keyword: String, sourceId: String) {
        val source = sourceFlow.value.firstOrNull { it.id == sourceId } ?: return
        val current = state.value
        val nextPage = current.nextPageBySource[sourceId] ?: return
        if (current.hasMoreBySource[sourceId] != true) return

        state.update {
            it.copy(
                loadingMoreSourceIds = it.loadingMoreSourceIds + sourceId,
                loadMoreErrors = it.loadMoreErrors - sourceId
            )
        }

        val requestToken = Any()
        loadMoreTokens[sourceId] = requestToken
        loadMoreJobs[sourceId] = viewModelScope.launch {
            try {
                val page = searchSource(source, requestSongFor(keyword), page = nextPage)
                if (activeSearchKeyword != keyword) return@launch

                val merged = mergeLyricsSearchPage(
                    existing = state.value.results[sourceId].orEmpty(),
                    incoming = page.results,
                    sourceMayHaveMore = page.hasMore
                )
                cacheResults(
                    keyword = keyword,
                    sourceId = sourceId,
                    results = merged.results,
                    nextPage = nextPage + 1,
                    hasMore = merged.hasMore
                )
                state.update {
                    it.copy(
                        results = it.results + (sourceId to merged.results),
                        nextPageBySource = it.nextPageBySource + (sourceId to nextPage + 1),
                        hasMoreBySource = it.hasMoreBySource + (sourceId to merged.hasMore),
                        loadMoreErrors = it.loadMoreErrors - sourceId
                    )
                }
            } catch (throwable: Exception) {
                if (throwable is CancellationException) throw throwable
                if (activeSearchKeyword == keyword) {
                    state.update {
                        it.copy(
                            loadMoreErrors = it.loadMoreErrors +
                                    (sourceId to throwable.toUiMessage())
                        )
                    }
                }
            } finally {
                if (loadMoreTokens[sourceId] === requestToken) {
                    loadMoreTokens.remove(sourceId)
                    loadMoreJobs.remove(sourceId)
                    state.update {
                        it.copy(loadingMoreSourceIds = it.loadingMoreSourceIds - sourceId)
                    }
                }
            }
        }
    }

    private fun searchSources(
        keyword: String,
        sources: List<SearchSource>,
        forceRefresh: Boolean
    ) {
        sources.forEach { source ->
            if (!forceRefresh && restoreCachedResult(keyword, source.id)) return@forEach
            if (forceRefresh) removeCachedResult(keyword, source.id)
            sourceSearchJobs[source.id]?.cancel()
            val requestToken = Any()
            sourceSearchTokens[source.id] = requestToken
            sourceSearchJobs[source.id] = viewModelScope.launch {
                state.update {
                    it.copy(
                        results = if (forceRefresh) it.results - source.id else it.results,
                        loadingSourceIds = it.loadingSourceIds + source.id,
                        errors = it.errors - source.id - NO_SOURCE_ERROR_KEY,
                        nextPageBySource = if (forceRefresh) {
                            it.nextPageBySource - source.id
                        } else {
                            it.nextPageBySource
                        },
                        hasMoreBySource = if (forceRefresh) {
                            it.hasMoreBySource - source.id
                        } else {
                            it.hasMoreBySource
                        },
                        loadMoreErrors = it.loadMoreErrors - source.id
                    )
                }
                try {
                    val page = searchSource(source, requestSongFor(keyword), page = 1)
                    if (state.value.searchKeyword.trim() != keyword) return@launch

                    cacheResults(
                        keyword = keyword,
                        sourceId = source.id,
                        results = page.results,
                        nextPage = 2,
                        hasMore = page.hasMore
                    )
                    state.update {
                        it.copy(
                            results = it.results + (source.id to page.results),
                            nextPageBySource = it.nextPageBySource + (source.id to 2),
                            hasMoreBySource = it.hasMoreBySource + (source.id to page.hasMore),
                            errors = if (page.results.isEmpty()) {
                                it.errors + (
                                    source.id to UiMessage.StringResource(R.string.cd_no_results)
                                )
                            } else {
                                it.errors - source.id
                            }
                        )
                    }
                } catch (throwable: Exception) {
                    if (throwable is CancellationException) throw throwable
                    if (state.value.searchKeyword.trim() == keyword) {
                        state.update {
                            it.copy(errors = it.errors + (source.id to throwable.toUiMessage()))
                        }
                    }
                } finally {
                    if (sourceSearchTokens[source.id] === requestToken) {
                        sourceSearchTokens.remove(source.id)
                        sourceSearchJobs.remove(source.id)
                        state.update {
                            it.copy(loadingSourceIds = it.loadingSourceIds - source.id)
                        }
                    }
                }
            }
        }
    }

    private suspend fun searchSource(
        source: SearchSource,
        requestSong: SongSearchResult,
        page: Int
    ): LyricsSearchPage {
        if (source.capabilities.usesSongSearchForLyricsCandidates()) {
            val keyword = listOf(requestSong.title, requestSong.artist)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val songs = source.searchSongs(
                keyword = keyword,
                page = page,
                separator = settingsRepository.separator.first(),
                pageSize = settingsRepository.searchPageSize.first()
            )
            return LyricsSearchPage(
                results = songs.map { LyricsSearchCandidateUi(song = it) },
                // 插件协议没有总数或 hasNext；空页或重复页会终止分页。
                hasMore = songs.isNotEmpty()
            )
        }

        val candidates = source.getLyricsCandidates(
            song = requestSong,
            page = page,
            pageSize = settingsRepository.searchPageSize.first()
        )
        return LyricsSearchPage(
            results = candidates.map { result ->
                LyricsSearchCandidateUi(
                    song = result.song,
                    lyrics = result.lyrics
                )
            },
            // API1-3 插件会忽略页码并重复返回第一页，合并时会在重复页自动终止。
            hasMore = candidates.isNotEmpty()
        )
    }

    private fun requestSongFor(keyword: String): SongSearchResult {
        val current = state.value
        val original = current.requestSong ?: SongSearchResult(
            id = LOCAL_SONG_ID,
            pluginId = "",
            pluginName = ""
        )
        return if (keyword == current.initialKeyword) {
            original
        } else {
            original.copy(
                title = keyword,
                artist = "",
                album = "",
                date = ""
            )
        }
    }

    private fun restoreCachedResult(keyword: String, sourceId: String): Boolean {
        val cached = resultCache[keyword]?.get(sourceId) ?: return false
        state.update {
            it.copy(
                results = it.results + (sourceId to cached.results),
                nextPageBySource = it.nextPageBySource + (sourceId to cached.nextPage),
                hasMoreBySource = it.hasMoreBySource + (sourceId to cached.hasMore),
                errors = it.errors - sourceId,
                loadMoreErrors = it.loadMoreErrors - sourceId
            )
        }
        return true
    }

    private fun cacheResults(
        keyword: String,
        sourceId: String,
        results: List<LyricsSearchCandidateUi>,
        nextPage: Int,
        hasMore: Boolean
    ) {
        resultCache.getOrPut(keyword) { mutableMapOf() }[sourceId] =
            CachedLyricsSearchResults(results, nextPage, hasMore)
    }

    private fun removeCachedResult(keyword: String, sourceId: String) {
        val keywordCache = resultCache[keyword] ?: return
        keywordCache.remove(sourceId)
        if (keywordCache.isEmpty()) resultCache.remove(keyword)
    }

    private fun mergeLyricsSearchPage(
        existing: List<LyricsSearchCandidateUi>,
        incoming: List<LyricsSearchCandidateUi>,
        sourceMayHaveMore: Boolean
    ): MergedLyricsSearchPage {
        val seen = existing.mapTo(mutableSetOf()) { it.song.resultIdentity() }
        val uniqueIncoming = incoming.filter { seen.add(it.song.resultIdentity()) }
        return MergedLyricsSearchPage(
            results = existing + uniqueIncoming,
            hasMore = sourceMayHaveMore && uniqueIncoming.isNotEmpty()
        )
    }

    private fun SongSearchResult.resultIdentity(): String =
        "$pluginId\u0000$id"

    private fun updateCandidate(
        keyword: String,
        sourceId: String,
        songId: String,
        replacement: (LyricsSearchCandidateUi) -> LyricsSearchCandidateUi
    ) {
        if (state.value.searchKeyword.trim() != keyword) return
        val currentResults = state.value.results[sourceId].orEmpty()
        val updatedResults = currentResults.map { candidate ->
            if (candidate.song.id == songId) replacement(candidate) else candidate
        }
        val cached = resultCache[keyword]?.get(sourceId)
        cacheResults(
            keyword = keyword,
            sourceId = sourceId,
            results = updatedResults,
            nextPage = cached?.nextPage ?: state.value.nextPageBySource[sourceId] ?: 2,
            hasMore = cached?.hasMore ?: state.value.hasMoreBySource[sourceId] == true
        )
        state.update {
            it.copy(results = it.results + (sourceId to updatedResults))
        }
    }

    private fun LyricsSearchCandidateUi.render(
        config: LyricRenderConfig
    ): LyricsSearchCandidateUi {
        val rawLyrics = lyrics ?: return copy(formattedLyrics = "")
        val processor = PluginFieldPostProcessor(config.toGlobalFieldProcessSettings())
        val processed = processor.processLyrics(
            lyrics = rawLyrics,
            config = defaultPluginFieldProcessConfig(song.pluginId)
        )
        return copy(
            formattedLyrics = LyricEncoder.encode(
                result = processed,
                config = config.copy(conversionMode = ConversionMode.NONE)
            )
        )
    }

    fun setLyricFormat(format: LyricFormat) {
        viewModelScope.launch { settingsRepository.saveLyricDisplayMode(format) }
    }

    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveRomaEnabled(enabled) }
    }

    fun setLyricLineOrder(order: List<LyricLineTrack>) {
        viewModelScope.launch { settingsRepository.saveLyricLineOrder(order) }
    }

    fun setTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveTranslationEnabled(enabled) }
    }

    fun setOnlyTranslationIfAvailable(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveOnlyTranslationIfAvailable(enabled)
        }
    }

    fun setRemoveEmptyLines(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.saveRemoveEmptyLines(enabled) }
    }

    fun setConversionMode(mode: ConversionMode) {
        viewModelScope.launch { settingsRepository.saveConversionMode(mode) }
    }

    private fun LyricRenderConfig.toGlobalFieldProcessSettings(): GlobalFieldProcessSettings =
        GlobalFieldProcessSettings(
            scriptConversion = conversionMode,
            removeEmptyLines = removeEmptyLines
        )

    private fun Throwable.toUiMessage(): UiMessage =
        UiMessage.DynamicString(message ?: javaClass.simpleName)

    private companion object {
        const val LOCAL_SONG_ID = "local-song"
        const val NO_SOURCE_ERROR_KEY = "__no_source__"
    }
}
