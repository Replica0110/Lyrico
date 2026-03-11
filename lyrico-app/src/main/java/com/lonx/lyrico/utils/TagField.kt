package com.lonx.lyrico.utils

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class TagField(
    val index: Int,
    @field:StringRes val description: Int
) {
    TITLE(1, R.string.label_title),
    ARTIST(2, R.string.label_artists),
    ALBUM_ARTIST(3, R.string.label_album_artist),
    ALBUM(4, R.string.label_album),
    TRACK(5, R.string.label_track_number),
    DISC(6, R.string.label_disc_number),
    YEAR(7, R.string.label_date),
    GENRE(8, R.string.label_genre);

    companion object {

        private val indexMap = entries.associateBy { it.index }

        fun fromIndex(index: Int): TagField? {
            return indexMap[index]
        }

        fun placeholder(index: Int): String {
            return "@$index"
        }

        fun placeholder(field: TagField): String {
            return "@${field.index}"
        }
    }
}
