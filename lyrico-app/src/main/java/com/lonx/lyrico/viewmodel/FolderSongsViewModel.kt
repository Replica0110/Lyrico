package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.SongQueryBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FolderSongsViewModel(
    private val folderId: Long,
    private val database: LyricoDatabase
) : ViewModel() {

    private val songDao = database.songDao()

    private val _sortInfo = MutableStateFlow(SortInfo())
    val sortInfo: StateFlow<SortInfo> = _sortInfo.asStateFlow()

    val songs: StateFlow<List<SongEntity>> = _sortInfo
        .flatMapLatest { sort ->
            val query = SongQueryBuilder.build(sort, folderId)
            songDao.getSongs(query)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun onSortChange(newSortInfo: SortInfo) {
        viewModelScope.launch {
            _sortInfo.value = newSortInfo
        }
    }
}
