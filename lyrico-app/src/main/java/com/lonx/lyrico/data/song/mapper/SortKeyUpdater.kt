package com.lonx.lyrico.data.song.mapper

import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.utils.SortKeyUtils

class SortKeyUpdater {
    fun update(song: SongEntity): SongEntity {
        val titleText = song.title?.takeIf { it.isNotBlank() } ?: song.fileName
        val artistText = song.artist?.takeIf { it.isNotBlank() } ?: "未知艺术家"

        val titleKeys = SortKeyUtils.getSortKeys(titleText)
        val artistKeys = SortKeyUtils.getSortKeys(artistText)

        return song.copy(
            titleGroupKey = titleKeys.groupKey,
            titleSortKey = titleKeys.sortKey,
            artistGroupKey = artistKeys.groupKey,
            artistSortKey = artistKeys.sortKey,
            dbUpdateTime = System.currentTimeMillis()
        )
    }
}
