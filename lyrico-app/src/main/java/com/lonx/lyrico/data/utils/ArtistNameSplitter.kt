package com.lonx.lyrico.data.utils

import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.effectiveNoSplitArtists
import com.lonx.lyrico.data.model.artist.effectiveSeparators
import com.lonx.lyrico.data.model.artist.normalizedArtistKey

object ArtistNameSplitter {
    fun splitArtists(
        rawArtist: String?,
        config: ArtistSplitConfig
    ): List<String> {
        val raw = rawArtist?.trim().orEmpty()
        if (raw.isBlank()) return emptyList()
        if (!config.enabled) return listOf(raw)

        if (raw.normalizedArtistKey() in config.effectiveNoSplitArtists()) {
            return listOf(raw)
        }

        val separators = config.effectiveSeparators()
        if (separators.isEmpty()) return listOf(raw)

        val regex = separators
            .sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
            .toRegex(RegexOption.IGNORE_CASE)

        return raw.split(regex)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.normalizedArtistKey() }
    }
}

