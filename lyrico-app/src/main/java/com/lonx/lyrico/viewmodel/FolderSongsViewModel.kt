package com.lonx.lyrico.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class FolderSongsViewModel(
    savedStateHandle: SavedStateHandle,
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderId: Long =
        savedStateHandle.get<Long>("folderId")!!

    private val songDao = database.songDao()

    val songs: StateFlow<List<SongEntity>> =
        songDao.getSongsByFolderId(folderId)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )
}
