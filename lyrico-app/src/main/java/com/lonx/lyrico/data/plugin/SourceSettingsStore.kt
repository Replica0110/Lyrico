package com.lonx.lyrico.data.plugin

import kotlinx.serialization.Serializable

@Serializable
data class SourceSettingsStore(
    val values: Map<String, Map<String, String>> = emptyMap()
)
