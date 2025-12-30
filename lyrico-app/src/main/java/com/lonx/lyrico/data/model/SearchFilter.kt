package com.lonx.lyrico.data.model

enum class SearchFilter {
    TITLE,
    ARTIST,
    ALBUM
}

// 新增数据类用于表示搜索结果的分类
data class SearchResultCategory(
    val category: String,
    val songs: List<SongEntity>
)