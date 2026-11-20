package com.lonx.lyrico.domain.artist

import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.effectiveNoSplitArtistNames
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

        val separatorRegex = separators.toAnchoredRegex()
        val noSplitArtistRegex = config.effectiveNoSplitArtistNames()
            .filter { it.containsAny(separators) }
            .toAnchoredRegex()

        return splitPreservingNoSplitArtists(
            raw = raw,
            separatorRegex = separatorRegex,
            noSplitArtistRegex = noSplitArtistRegex
        )
    }

    private fun splitPreservingNoSplitArtists(
        raw: String,
        separatorRegex: Regex?,
        noSplitArtistRegex: Regex?
    ): List<String> {
        val artists = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0

        while (index < raw.length) {
            val noSplitMatch = noSplitArtistRegex?.anchoredMatchAt(raw, index)
            if (noSplitMatch != null) {
                current.append(noSplitMatch.value)
                index = noSplitMatch.range.last + 1
                continue
            }

            val separatorMatch = separatorRegex?.anchoredMatchAt(raw, index)
            if (separatorMatch != null) {
                current.flushTo(artists)
                index = separatorMatch.range.last + 1
                continue
            }

            current.append(raw[index])
            index++
        }

        current.flushTo(artists)
        return artists.distinctBy { it.normalizedArtistKey() }
    }

    private fun List<String>.toAnchoredRegex(): Regex? {
        if (isEmpty()) return null
        return sortedByDescending { it.length }
            .joinToString("|") { Regex.escape(it) }
            .toRegex(RegexOption.IGNORE_CASE)
    }

    private fun Regex.anchoredMatchAt(input: String, index: Int): MatchResult? {
        return find(input, index)?.takeIf { it.range.first == index }
    }

    private fun String.containsAny(values: List<String>): Boolean {
        return values.any { value -> contains(value, ignoreCase = true) }
    }

    private fun StringBuilder.flushTo(items: MutableList<String>) {
        val value = toString().trim()
        if (value.isNotEmpty()) {
            items += value
        }
        clear()
    }
}
