package com.lonx.lyrico.screens.library

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class LibraryTab(
    @param:StringRes val titleRes: Int
) {
    Songs(
        titleRes = R.string.library_tab_songs
    ),
    Artists(
        titleRes = R.string.library_tab_artists
    ),
    Albums(
        titleRes = R.string.library_tab_albums
    )
}
