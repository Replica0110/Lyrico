package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.SongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocalSearchUiState(
    val searchQuery: String = "",
    val songs: List<SongEntity> = emptyList(),
    val isSearching: Boolean = false
)

class LocalSearchViewModel(private val songRepository: SongRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalSearchUiState())
    val uiState: StateFlow<LocalSearchUiState> = _uiState.asStateFlow()

    // 用于管理当前的搜索任务，以便在输入新内容时取消上一次未完成的搜索
    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        // 1. 立即更新搜索框文字
        _uiState.update { it.copy(searchQuery = query) }

        // 2. 取消上一次正在进行的搜索任务（如果有的话），确保结果准确
        searchJob?.cancel()

        // 3. 执行搜索逻辑
        searchJob = viewModelScope.launch {
            // 如果输入为空，直接清空列表并停止加载
            if (query.isBlank()) {
                _uiState.update { it.copy(songs = emptyList(), isSearching = false) }
                return@launch
            }

            // 开始搜索：显示 loading
            _uiState.update { it.copy(isSearching = true) }

            // 调用仓库搜索
            songRepository.searchSongs(query).collect { results ->
                _uiState.update {
                    it.copy(
                        songs = results,
                        isSearching = false
                    )
                }
            }
        }
    }
}