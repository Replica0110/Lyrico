package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.repository.SettingsDefaults

enum class SearchSourceTabStyle(
    @field:StringRes val labelRes: Int
) {
    ICON_ONLY(R.string.search_source_tab_style_icon_only),
    TEXT_ONLY(R.string.search_source_tab_style_text_only),
    ICON_AND_TEXT(R.string.search_source_tab_style_icon_and_text)
}

/**
 * 搜索相关配置，用于 SearchViewModel 等需要搜索参数的消费者
 */
data class SearchConfig(
    val separator: String = SettingsDefaults.SEPARATOR,
    val searchSourceOrder: List<String> = SettingsDefaults.SEARCH_SOURCE_ORDER,
    val enabledSearchSources: Set<String> = SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES,
    val searchPageSize: Int = SettingsDefaults.SEARCH_PAGE_SIZE,
    val searchSourceTabStyle: SearchSourceTabStyle = SettingsDefaults.SEARCH_SOURCE_TAB_STYLE
)
