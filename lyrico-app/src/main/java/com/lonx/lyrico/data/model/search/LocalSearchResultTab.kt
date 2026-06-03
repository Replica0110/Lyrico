package com.lonx.lyrico.data.model.search

import com.lonx.lyrico.R

/**
 * 本地搜索结果分类标签
 * 对应结果类型：单曲/专辑/艺术家
 */
enum class LocalSearchResultTab(val labelRes: Int) {
    SONGS(R.string.search_section_songs),
    ALBUMS(R.string.search_section_albums),
    ARTISTS(R.string.search_section_artists)
}
