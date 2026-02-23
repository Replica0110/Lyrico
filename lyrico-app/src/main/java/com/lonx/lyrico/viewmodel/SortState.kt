package com.lonx.lyrico.viewmodel

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class SortBy(
    @field:StringRes val labelRes: Int,
    val supportsIndex: Boolean
) {
    TITLE(R.string.label_title, true),
    ARTISTS(R.string.label_artists, true),
    DATE_MODIFIED(R.string.label_date_modified, false),
    DATE_ADDED(R.string.label_date_added, false)
}

enum class SortOrder {
    ASC,
    DESC
}

data class SortInfo(
    val sortBy: SortBy = SortBy.TITLE,
    val order: SortOrder = SortOrder.ASC
)
