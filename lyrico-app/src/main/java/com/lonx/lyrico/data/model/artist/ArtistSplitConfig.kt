package com.lonx.lyrico.data.model.artist

import kotlinx.serialization.Serializable

@Serializable
data class ArtistSplitConfig(
    val enabled: Boolean = true,
    val builtinSeparatorOverrides: Map<String, Boolean> = emptyMap(),
    val customSeparators: List<CustomArtistSeparator> = emptyList(),
    val builtinNoSplitArtistOverrides: Map<String, Boolean> = emptyMap(),
    val customNoSplitArtists: List<String> = emptyList()
)

@Serializable
data class CustomArtistSeparator(
    val value: String,
    val enabled: Boolean = true
)

data class BuiltinArtistSeparator(
    val id: String,
    val value: String,
    val defaultEnabled: Boolean,
    val displayName: String = value
)

data class BuiltinNoSplitArtist(
    val id: String,
    val name: String,
    val defaultEnabled: Boolean = true
)

