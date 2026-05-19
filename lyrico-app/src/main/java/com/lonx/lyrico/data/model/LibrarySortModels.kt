package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.SortOrder

enum class ArtistSortBy(
    @param:StringRes val labelRes: Int,
    val supportsIndex: Boolean
) {
    NAME(R.string.label_name, true),
    SONG_COUNT(R.string.label_song_count, false),
    ALBUM_COUNT(R.string.label_album_count, false)
}

data class ArtistSortInfo(
    val sortBy: ArtistSortBy = ArtistSortBy.NAME,
    val order: SortOrder = SortOrder.ASC
)

enum class AlbumSortBy(
    @param:StringRes val labelRes: Int,
    val supportsIndex: Boolean
) {
    NAME(R.string.label_album, true),
    ALBUM_ARTIST(R.string.label_album_artist, false),
    SONG_COUNT(R.string.label_song_count, false)
}

data class AlbumSortInfo(
    val sortBy: AlbumSortBy = AlbumSortBy.NAME,
    val order: SortOrder = SortOrder.ASC
)
