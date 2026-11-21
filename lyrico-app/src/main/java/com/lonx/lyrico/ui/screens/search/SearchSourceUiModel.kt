package com.lonx.lyrico.ui.screens.search

import androidx.annotation.StringRes
import com.lonx.lyrico.data.plugin.PluginCapability
import com.lonx.lyrico.data.song.search.SearchSource

data class SearchSourceUiModel(
    val id: String,
    val name: String,
    val iconPath: String? = null,
    val supportsLyrics: Boolean = false,
    @param:StringRes val labelRes: Int? = null
)

fun SearchSource.toUiModel(): SearchSourceUiModel {
    return SearchSourceUiModel(
        id = id,
        name = name,
        iconPath = iconPath,
        supportsLyrics = PluginCapability.GET_LYRICS in capabilities
    )
}
