package com.lonx.lyrico.viewmodel

enum class SortBy(val displayName: String, val dbColumn: String) {
    TITLE("歌曲名", "title"),
    ARTIST("歌手", "artist"),
    DATE_MODIFIED("修改时间", "dbUpdateTime")
}

enum class SortOrder {
    ASC,
    DESC
}

data class SortInfo(
    val sortBy: SortBy = SortBy.TITLE,
    val order: SortOrder = SortOrder.ASC
)
