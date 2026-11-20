package com.lonx.lyrico.ui.screens.library

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons

enum class LibraryTab(
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    Songs(
        titleRes = R.string.library_tab_songs,
        icon = LucideIcons.Music,
    ),
    Artists(
        titleRes = R.string.library_tab_artists,
        icon = LucideIcons.Users,
    ),
    Albums(
        titleRes = R.string.library_tab_albums,
        icon = LucideIcons.Disc,
    )
}
