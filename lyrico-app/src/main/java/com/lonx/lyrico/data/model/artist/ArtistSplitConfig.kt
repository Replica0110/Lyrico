package com.lonx.lyrico.data.model.artist

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ArtistSplitConfig(
    val enabled: Boolean = true,
    val hiddenBuiltinSeparatorIds: Set<String> = emptySet(),
    val customSeparators: List<CustomArtistSeparator> = emptyList(),
    val builtinNoSplitArtistOverrides: Map<String, Boolean> = emptyMap(),
    val customNoSplitArtists: List<CustomNoSplitArtist> = emptyList()
)

@Serializable
data class CustomArtistSeparator(
    val value: String,
    val id: String = UUID.randomUUID().toString()
)

@Serializable
data class CustomNoSplitArtist(
    val name: String,
    val id: String = UUID.randomUUID().toString()
)

data class BuiltinArtistSeparator(
    val id: String,
    val value: String,
    val displayName: String = value
)

data class BuiltinNoSplitArtist(
    val id: String,
    val name: String,
    val defaultEnabled: Boolean = true
)
