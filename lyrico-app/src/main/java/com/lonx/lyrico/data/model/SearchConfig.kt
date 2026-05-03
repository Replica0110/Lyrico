package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrics.model.Source

/**
 * 搜索相关配置，用于 SearchViewModel 等需要搜索参数的消费者
 */
data class SearchConfig(
    val separator: String = SettingsDefaults.SEPARATOR,
    val searchSourceOrder: List<Source> = SettingsDefaults.SEARCH_SOURCE_ORDER,
    val enabledSearchSources: Set<Source> = SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES,
    val searchPageSize: Int = SettingsDefaults.SEARCH_PAGE_SIZE,
    val lyricsSourceConfig: SourceUsageConfig = SourceUsageConfig(),
    val coverSourceConfig: SourceUsageConfig = SourceUsageConfig(),
    val metadataSourceConfig: SourceUsageConfig = SourceUsageConfig()
)
