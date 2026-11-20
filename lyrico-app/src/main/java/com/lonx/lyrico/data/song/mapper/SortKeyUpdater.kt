package com.lonx.lyrico.data.song.mapper

import com.lonx.lyrico.data.db.entity.SongEntity

class SortKeyUpdater {
    fun update(song: SongEntity): SongEntity {
        val titleText = song.title?.takeIf { it.isNotBlank() } ?: song.fileName
        val artistText = song.artist?.takeIf { it.isNotBlank() } ?: "未知艺术家"
        val albumText = song.album.orEmpty() + song.albumArtist.orEmpty()

        val titleKeys = SortKeyUtils.getSortKeys(titleText)
        val artistKeys = SortKeyUtils.getSortKeys(artistText)
        val albumKeys = SortKeyUtils.getSortKeys(albumText)

        return song.copy(
            titleGroupKey = titleKeys.groupKey,
            titleSortKey = titleKeys.sortKey,
            artistGroupKey = artistKeys.groupKey,
            artistSortKey = artistKeys.sortKey,
            albumGroupKey = albumKeys.groupKey,
            albumSortKey = albumKeys.sortKey,
            dbUpdateTime = System.currentTimeMillis()
        )
    }
}
