package com.lonx.lyrico.data.model.library

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class SortOrder {
    ASC,
    DESC
}

enum class SongSortBy(
    @field:StringRes val labelRes: Int,
    val supportsIndex: Boolean
) {
    TITLE(R.string.label_title, true),
    ARTISTS(R.string.label_artists, true),
    ALBUM(R.string.label_album, true),
    DATE_MODIFIED(R.string.label_date_modified, false),
    DATE_ADDED(R.string.label_date_added, false),
    FILE_SIZE(R.string.label_file_size, false),
    DURATION(R.string.label_duration, false),
    EXTENSION(R.string.label_extension, false),
}

data class SongSortInfo(
    val sortBy: SongSortBy = SongSortBy.TITLE,
    val order: SortOrder = SortOrder.ASC
)

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
    SONG_COUNT(R.string.label_song_count, false),
    YEAR(R.string.label_year, false)
}

data class AlbumSortInfo(
    val sortBy: AlbumSortBy = AlbumSortBy.NAME,
    val order: SortOrder = SortOrder.ASC
)
