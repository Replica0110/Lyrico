package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.SearchFilter
import com.lonx.lyrico.data.model.SearchResultCategory
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocalSearchUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false
)

@OptIn(FlowPreview::class)
class LocalSearchViewModel(private val songRepository: SongRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalSearchUiState())
    val uiState: StateFlow<LocalSearchUiState> = _uiState.asStateFlow()

    private val _songs = MutableStateFlow<List<SongEntity>>(emptyList())
    val songs: StateFlow<List<SongEntity>> = _songs.asStateFlow()

    private val _groupedSongs = MutableStateFlow<List<SearchResultCategory>>(emptyList())
    val groupedSongs: StateFlow<List<SearchResultCategory>> = _groupedSongs.asStateFlow()

    // 移除了自动搜索逻辑，现在只在显式调用 search() 时才执行搜索
    // 这样可以避免在用户输入时不断触发搜索

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun search() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val query = _uiState.value.searchQuery
            if (query.isBlank()) {
                _songs.value = emptyList()
                _uiState.update { it.copy(isSearching = false) }
                return@launch
            }
            songRepository.searchSongsByAll(query).collect { results ->
                _songs.value = results
                // 按匹配类型分组结果
                val groupedResults = groupResultsByMatchType(results, query)
                _groupedSongs.value = groupedResults
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun groupResultsByMatchType(results: List<SongEntity>, query: String): List<SearchResultCategory> {
        val titleMatches = results.filter { song ->
            song.title?.contains(query, ignoreCase = true) == true
        }
        val artistMatches = results.filter { song ->
            song.artist?.contains(query, ignoreCase = true) == true &&
            song !in titleMatches // 避免重复
        }
        val albumMatches = results.filter { song ->
            song.album?.contains(query, ignoreCase = true) == true &&
            song !in titleMatches && song !in artistMatches // 避免重复
        }

        val groupedResults = mutableListOf<SearchResultCategory>()
        if (titleMatches.isNotEmpty()) {
            groupedResults.add(SearchResultCategory("标题", titleMatches))
        }
        if (artistMatches.isNotEmpty()) {
            groupedResults.add(SearchResultCategory("艺术家", artistMatches))
        }
        if (albumMatches.isNotEmpty()) {
            groupedResults.add(SearchResultCategory("专辑", albumMatches))
        }
        
        return groupedResults
    }
}
