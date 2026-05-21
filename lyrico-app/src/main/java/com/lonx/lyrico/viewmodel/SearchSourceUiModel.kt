package com.lonx.lyrico.viewmodel

import androidx.annotation.StringRes
import com.lonx.lyrico.data.model.lyrics.SearchSource

data class SearchSourceUiModel(
    val id: String,
    val name: String,
    @param:StringRes val labelRes: Int? = null
)

fun SearchSource.toUiModel(): SearchSourceUiModel {
    return SearchSourceUiModel(
        id = id,
        name = name
    )
}
