package com.lonx.lyrico.data.utils

import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.artist.CustomArtistSeparator
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
    fun splitArtists_keepsBuiltinNoSplitArtistBeforeSeparators() {
        assertEquals(
            listOf("Simon & Garfunkel"),
            ArtistNameSplitter.splitArtists(
                "Simon & Garfunkel",
                ArtistSplitConfig(
                    builtinSeparatorOverrides = mapOf("ampersand" to true)
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
}

