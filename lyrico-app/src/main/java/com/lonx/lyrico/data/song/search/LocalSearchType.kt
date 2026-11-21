package com.lonx.lyrico.data.song.search

import com.lonx.lyrico.R

enum class LocalSearchType(val value: String, val labelRes: Int) {
    ALL("ALL", R.string.search_type_all),
    TITLE("TITLE", R.string.search_type_title),
    ARTIST("ARTIST", R.string.search_type_artist),
    ALBUM("ALBUM", R.string.search_type_album),
    FILENAME("FILE_NAME", R.string.search_type_filename)
}