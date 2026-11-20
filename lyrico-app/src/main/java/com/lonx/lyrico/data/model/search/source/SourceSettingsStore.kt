package com.lonx.lyrico.data.model.search.source

import kotlinx.serialization.Serializable

@Serializable
data class SourceSettingsStore(
    val values: Map<String, Map<String, String>> = emptyMap()
)
