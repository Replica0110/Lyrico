package com.lonx.lyrico.ui.components.poster

import com.lonx.audiotag.model.AudioPicture
import com.lonx.lyrico.domain.poster.ArtistPictureEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistPosterMenuStateTest {
    private val artists = listOf("Artist A", "Artist B")

    @Test
    fun emptyOrExternalSlotKeepsClickedArtistAfterClosingMenu() {
        val state = ArtistPosterMenuState()
        state.open(artistName = "Artist B")
        state.closeOptions()

        assertEquals("Artist B", state.artistForAdding(artists, "Artist A"))
    }

    @Test
    fun embeddedPictureUsesItsOwner() {
        val state = ArtistPosterMenuState()
        state.open(ArtistPictureEntry(AudioPicture(byteArrayOf(1)), "Artist B", "Artist B"))

        assertEquals("Artist B", state.artistForAdding(artists, "Artist A"))
    }

    @Test
    fun generalEntryClearsPreviousTargetAndUsesCurrentSlot() {
        val state = ArtistPosterMenuState()
        state.open(artistName = "Artist A")
        state.closeOptions()
        state.open()

        assertEquals("Artist B", state.artistForAdding(artists, "Artist B"))
        assertNull(state.artistForAdding(artists, null))
    }

    @Test
    fun unmatchedOrRemovedOwnerNeedsSelectionWhenSeveralArtistsExist() {
        val state = ArtistPosterMenuState()
        state.open(artistName = "Other artist")

        assertNull(state.artistForAdding(artists, "Other artist"))
        assertEquals("Artist A", state.artistForAdding(listOf("Artist A"), "Other artist"))
        assertNull(state.artistForAdding(emptyList(), "Other artist"))
    }

    @Test
    fun knownOwnerUsesCurrentCanonicalArtistName() {
        val state = ArtistPosterMenuState()
        state.open(artistName = " artist b ")

        assertEquals("Artist B", state.artistForAdding(artists, null))
    }
}
