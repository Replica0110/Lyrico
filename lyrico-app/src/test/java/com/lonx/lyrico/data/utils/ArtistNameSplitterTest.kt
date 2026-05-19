package com.lonx.lyrico.data.utils

import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
import com.lonx.lyrico.data.model.artist.CustomNoSplitArtist
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNameSplitterTest {
    @Test
    fun splitArtists_splitsSlashSeparatedArtists() {
        assertEquals(
            listOf("A", "B"),
            ArtistNameSplitter.splitArtists("A / B", ArtistSplitConfig())
        )
    }

    @Test
    fun splitArtists_keepsCustomNoSplitArtistBeforeSeparators() {
        assertEquals(
            listOf("Simon & Garfunkel"),
            ArtistNameSplitter.splitArtists(
                "Simon & Garfunkel",
                ArtistSplitConfig(
                    builtinSeparatorOverrides = mapOf("ampersand" to true),
                    customNoSplitArtists = listOf(CustomNoSplitArtist("Simon & Garfunkel"))
                )
            )
        )
    }

    @Test
    fun splitArtists_respectsDisabledBuiltinSeparator() {
        assertEquals(
            listOf("A / B"),
            ArtistNameSplitter.splitArtists(
                "A / B",
                ArtistSplitConfig(
                    builtinSeparatorOverrides = mapOf("slash" to false)
                )
            )
        )
    }

    @Test
    fun splitArtists_splitsEnabledAmpersand() {
        assertEquals(
            listOf("A", "B"),
            ArtistNameSplitter.splitArtists(
                "A & B",
                ArtistSplitConfig(
                    builtinSeparatorOverrides = mapOf("ampersand" to true)
                )
            )
        )
    }

    @Test
    fun splitArtists_respectsDeletedBuiltinSeparator() {
        assertEquals(
            listOf("A / B"),
            ArtistNameSplitter.splitArtists(
                "A / B",
                ArtistSplitConfig(
                    hiddenBuiltinSeparatorIds = setOf("slash")
                )
            )
        )
    }

    @Test
    fun splitArtists_splitsCustomSeparator() {
        assertEquals(
            listOf("A", "B"),
            ArtistNameSplitter.splitArtists(
                "A feat. B",
                ArtistSplitConfig(
                    customSeparators = listOf(CustomArtistSeparator(" feat. "))
                )
            )
        )
    }

    @Test
    fun splitArtists_respectsEnabledCustomNoSplitArtist() {
        assertEquals(
            listOf("A & B"),
            ArtistNameSplitter.splitArtists(
                "A & B",
                ArtistSplitConfig(
                    builtinSeparatorOverrides = mapOf("ampersand" to true),
                    customNoSplitArtists = listOf(CustomNoSplitArtist("A & B"))
                )
            )
        )
    }
}
