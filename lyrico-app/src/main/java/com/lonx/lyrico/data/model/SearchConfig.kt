package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.repository.SettingsDefaults

/**
 * 搜索相关配置，用于 SearchViewModel 等需要搜索参数的消费者
 */
data class SearchConfig(
    val separator: String = SettingsDefaults.SEPARATOR,
    val searchSourceOrder: List<String> = SettingsDefaults.SEARCH_SOURCE_ORDER,
    val enabledSearchSources: Set<String> = SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES,
    val searchPageSize: Int = SettingsDefaults.SEARCH_PAGE_SIZE
)
