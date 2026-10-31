package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.SortOrder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.AlbumSortBy
import com.lonx.lyrico.data.model.AlbumSortInfo
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.LibraryScanManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumLibraryViewModel(
    private val libraryIndexRepository: LibraryIndexRepository,
    private val libraryScanManager: LibraryScanManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val sortInfo: StateFlow<AlbumSortInfo> = settingsRepository.albumSortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSortInfo())


    val gridColumns = settingsRepository.albumGridColumns
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    val scanState = libraryScanManager.state
    val albums: StateFlow<List<AlbumEntity>> =
        combine(libraryIndexRepository.observeAlbums(), sortInfo) { albums, sort ->
            val sorted = when (sort.sortBy) {
                AlbumSortBy.NAME -> albums.sortedWith(
                    compareBy<AlbumEntity> { it.sortKey }.thenBy { it.name }
                )

                AlbumSortBy.ALBUM_ARTIST -> albums.sortedWith(
                    compareBy<AlbumEntity> { it.albumArtist.orEmpty().uppercase() }
                        .thenBy { it.sortKey }
                        .thenBy { it.name }
                )

                AlbumSortBy.SONG_COUNT -> albums.sortedWith(
                    compareByDescending<AlbumEntity> { it.songCount }
                        .thenBy { it.sortKey }
                        .thenBy { it.name }
                )
                AlbumSortBy.YEAR -> {
                    val comparator = if (sort.order == SortOrder.ASC) {
                        compareBy<AlbumEntity> { it.year.sortableAlbumYear() ?: Int.MAX_VALUE }
                            .thenBy { it.sortKey }
                            .thenBy { it.name }
                    } else {
                        compareBy<AlbumEntity> { it.year.sortableAlbumYear() == null }
                            .thenByDescending { it.year.sortableAlbumYear() ?: Int.MIN_VALUE }
                            .thenBy { it.sortKey }
                            .thenBy { it.name }
                    }
                    return@combine albums.sortedWith(comparator)
                }
            }
            if (sort.order == SortOrder.ASC) sorted else sorted.asReversed()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun onSortChange(sortInfo: AlbumSortInfo) {
        viewModelScope.launch {
            settingsRepository.saveAlbumSortInfo(sortInfo)
        }
    }


    fun setGridColumns(columns: Int) {
        viewModelScope.launch {
            settingsRepository.saveAlbumGridColumns(columns)
        }
    }

    fun refreshSongs() {
        libraryScanManager.scanAll {
            libraryIndexRepository.rebuildAlbumIndex()
        }
    }
}

// 专辑年份来自歌曲日期标签，既可能只有年份，也可能包含月日。
internal fun String?.sortableAlbumYear(): Int? {
    val date = this?.trim() ?: return null
    if (date.length < 4 || date.take(4).any { it !in '0'..'9' }) return null
    if (date.length > 4 && date[4] !in "-/.") return null
    return date.take(4).toIntOrNull()?.takeIf { it > 0 }
}

