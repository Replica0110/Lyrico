package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.source.kg.KgSource
import com.lonx.lyrics.source.qm.QmSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class SearchUiState(
    val searchKeyword: String = "",
    val searchResults: List<SongSearchResult> = emptyList(),
    val selectedSearchSource: Source = Source.KG,
    val availableSources: List<Source> = listOf(Source.KG, Source.QM),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    // For lyrics preview
    val previewingSong: SongSearchResult? = null,
    val lyricsPreviewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val lyricsPreviewError: String? = null
)

class SearchViewModel(
    private val kgSource: KgSource,
    private val qmSource: QmSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    // 缓存搜索结果，以关键字和搜索源为键
    private val searchResultCache = mutableMapOf<String, MutableMap<Source, List<SongSearchResult>>>()

    fun onKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    fun switchSource(source: Source) {
        // If keyword is blank, just switch the source and clear results, no search needed.
        if (_uiState.value.searchKeyword.isBlank()) {
            _uiState.update { it.copy(selectedSearchSource = source, searchResults = emptyList()) }
            return
        }

        val currentKeyword = _uiState.value.searchKeyword
        val cachedResults = getCachedResults(currentKeyword, source)

        _uiState.update { it.copy(selectedSearchSource = source) }

        if (cachedResults != null) {
            // Cache hit, just update the UI
            _uiState.update { it.copy(searchResults = cachedResults) }
        } else {
            // Cache miss for the new source, so perform a search
            search()
        }
    }

    fun search(keywordToSearch: String? = null) {
        val keyword = keywordToSearch ?: _uiState.value.searchKeyword
        if (keyword.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        // If a new keyword is passed, ensure the state is updated
        if (keywordToSearch != null) {
            _uiState.update { it.copy(searchKeyword = keyword) }
        }

        viewModelScope.launch {
            val currentSource = _uiState.value.selectedSearchSource
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            try {
                val results = when (currentSource) {
                    Source.KG -> kgSource.search(keyword)
                    Source.QM -> qmSource.search(keyword)
                    else -> emptyList()
                }
                cacheSearchResults(keyword, currentSource, results)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(searchError = "Search failed: ${e.message}", isSearching = false) }
            }
        }
    }
    
    private fun cacheSearchResults(keyword: String, source: Source, results: List<SongSearchResult>) {
        val keywordCache = searchResultCache.getOrPut(keyword) { mutableMapOf() }
        keywordCache[source] = results
    }
    
    private fun getCachedResults(keyword: String, source: Source): List<SongSearchResult>? {
        return searchResultCache[keyword]?.get(source)
    }
    
    fun clearCache() {
        searchResultCache.clear()
    }
    
    fun clearCacheForKeyword(keyword: String) {
        searchResultCache.remove(keyword)
    }

    fun fetchLyricsForPreview(song: SongSearchResult) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    previewingSong = song,
                    isPreviewLoading = true,
                    lyricsPreviewContent = null,
                    lyricsPreviewError = null
                )
            }
            try {
                val lyricsResult: LyricsResult? = when (song.source) {
                    Source.KG -> kgSource.getLyrics(song)
                    Source.QM -> qmSource.getLyrics(song)
                    else -> null
                }
                
                val lyricsText = lyricsResult?.let { formatKrcResult(it) }

                _uiState.update {
                    it.copy(
                        lyricsPreviewContent = lyricsText ?: "No lyrics found for this song.",
                        isPreviewLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        lyricsPreviewError = "Failed to load lyrics: ${e.message}",
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    fun fetchLyricsDirectly(song: SongSearchResult, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val lyricsResult: LyricsResult? = when (song.source) {
                    Source.KG -> kgSource.getLyrics(song)
                    Source.QM -> qmSource.getLyrics(song)
                    else -> null
                }
                
                val lyricsText = lyricsResult?.let { formatKrcResult(it) }
                onResult(lyricsText)
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    fun clearPreview() {
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

private fun formatTimestamp(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val ms = millis % 1000
    return String.format("%02d:%02d.%03d", minutes, seconds, ms)
}

private fun formatKrcResult(result: LyricsResult): String {
    val builder = StringBuilder()
    val originalLines = result.original
    val translatedLines = result.translated

    val translatedMap = translatedLines?.associateBy { it.start } ?: emptyMap()

    originalLines.forEach { originalLine ->
        val formattedOriginalLine = originalLine.words.joinToString("") { word ->
            "[${formatTimestamp(word.start)}]${word.text}"
        }
        builder.append(formattedOriginalLine)
        builder.append("\n")

        // Find a matching translated line. A tolerance of 500ms is used to match lines.
        val matchedTranslation = findMatchingTranslatedLine(originalLine, translatedMap)

        if (matchedTranslation != null) {
            val formattedTranslatedLine = "[${formatTimestamp(matchedTranslation.start)}]${matchedTranslation.words.joinToString(" ") { it.text }}"
            builder.append(formattedTranslatedLine)
            builder.append("\n")
        }
    }
    return builder.toString().trim()
}

private fun findMatchingTranslatedLine(originalLine: LyricsLine, translatedMap: Map<Long, LyricsLine>): LyricsLine? {
    // Exact match
    var matched = translatedMap[originalLine.start]
    if (matched != null) return matched

    // Fuzzy match within a tolerance
    return translatedMap.entries.find { abs(it.key - originalLine.start) < 500 }?.value
}
