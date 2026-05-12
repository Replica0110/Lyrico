package com.lonx.lyrico.ui.components.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lonx.lyrico.ui.components.song.SongListSectionHeader

@Composable
fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    SongListSectionHeader(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        action = action
    )
}