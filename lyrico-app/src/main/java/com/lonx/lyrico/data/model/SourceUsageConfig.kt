package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.repository.SettingsDefaults
import com.lonx.lyrics.model.Source
import kotlinx.serialization.Serializable

@Serializable
data class SourceUsageConfig(
    val sourceOrder: List<Source> = SettingsDefaults.SEARCH_SOURCE_ORDER,
    val enabledSources: Set<Source> = SettingsDefaults.DEFAULT_ENABLED_SEARCH_SOURCES
) {
    val enabledSourceOrder: List<Source>
        get() = sourceOrder.filter { it in enabledSources }
}

@Serializable
enum class SourceUsage {
    LYRICS,
    COVER,
    METADATA
}
