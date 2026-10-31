package com.lonx.lyrico.domain.poster

import com.lonx.lyrico.data.model.CharacterMappingDefaults
import java.text.Normalizer

/**
 * Matches poster file names against artist names.
 *
 * Invalid filename characters in the artist name may be dropped, replaced by one separator or use
 * their full-width form; everything else must match as-is, and a name must end where the artist
 * name ends (`ArtistExtra.jpg` never matches `Artist`). Ranks, lowest wins: `0` exact,
 * `1` after normalization, `2` suffixed variant (name followed by `_`), `null` not this artist.
 */
internal object ArtistPosterMatcher {
    /** File extensions that can be used as an artist poster. */
    val extensions = setOf("jpg", "jpeg", "png", "webp", "mp4")

    /** Characters a file name cannot hold, from the rule the app uses when renaming files. */
    internal val invalidFileNameChars: Set<Char> =
        CharacterMappingDefaults.DEFAULT_INVALID_CHARS.charMappings.keys
            .mapNotNullTo(mutableSetOf()) { it.singleOrNull() }

    /** Legal characters a file may use in place of an invalid one. */
    private const val FILE_NAME_SEPARATORS = "_- ."

    fun isPosterFile(fileName: String?): Boolean =
        fileName != null && fileName.substringAfterLast('.', "").lowercase() in extensions

    /** A suffixed match must not delete a file that matches an unselected artist more closely. */
    fun ownedBySelection(fileName: String, selected: Set<String>, libraryArtists: List<String>): Boolean {
        val selectedRank = selected.mapNotNull { rank(fileName, it) }.minOrNull() ?: return false
        val selectedKeys = selected.map { it.normalizeName() }.toSet()
        val otherRank = libraryArtists.filter { it.normalizeName() !in selectedKeys }
            .mapNotNull { rank(fileName, it) }.minOrNull()
        return otherRank == null || selectedRank < otherRank
    }

    fun rank(fileName: String, artistName: String): Int? {
        if (!isPosterFile(fileName)) return null
        val artist = artistName.normalizeName()
        if (artist.isEmpty()) return null
        return rankStem(fileName.substringBeforeLast('.').normalizeName(), artist)
    }

    /** Counts [fileNames] that belong to at least one of [artistNames]. */
    fun countMatchedFiles(fileNames: List<String>, artistNames: List<String>): Int {
        if (fileNames.isEmpty() || artistNames.isEmpty()) return 0
        val artists = artistNames.map { it.normalizeName() }.filter { it.isNotEmpty() }.distinct()
        if (artists.isEmpty()) return 0
        return fileNames.count { name ->
            if (!isPosterFile(name)) return@count false
            val stem = name.substringBeforeLast('.').normalizeName()
            artists.any { artist -> rankStem(stem, artist) != null }
        }
    }

    private fun rankStem(stem: String, artist: String): Int? {
        if (stem == artist) return 0
        val end = tolerantPrefixEnd(stem, artist)
        return when {
            end < 0 -> null
            end == stem.length -> 1
            stem[end] == '_' -> 2
            else -> null
        }
    }

    /**
     * Index in [stem] right after an [artist] match at `0`, or `-1` when it does not match;
     * characters a file name cannot hold may be missing or replaced by separators.
     */
    private fun tolerantPrefixEnd(stem: String, artist: String): Int {
        var stemIndex = 0
        var artistIndex = 0
        while (artistIndex < artist.length) {
            if (artist[artistIndex] in invalidFileNameChars) {
                while (artistIndex < artist.length && artist[artistIndex] in invalidFileNameChars) {
                    artistIndex++
                }
                while (stemIndex < stem.length && stem[stemIndex].isInvalidReplacement()) {
                    stemIndex++
                }
                continue
            }
            if (stemIndex >= stem.length || stem[stemIndex] != artist[artistIndex]) return -1
            stemIndex++
            artistIndex++
        }
        return stemIndex
    }

    /**
     * NFKC folds the full-width forms the app renames invalid characters to back to ASCII, and
     * handles the Unicode composition differences of accents.
     */
    private fun String.normalizeName(): String =
        Normalizer.normalize(trim(), Normalizer.Form.NFKC).lowercase()

    private fun Char.isInvalidReplacement(): Boolean =
        this in invalidFileNameChars || this in FILE_NAME_SEPARATORS
}
