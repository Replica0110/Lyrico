package com.lonx.lyrico.ui.screens.edit

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureEditSessionTest {
    @Test
    fun `same song reload retains ticket but switching away and back expires it`() {
        val session = PictureEditSession()
        session.changeSong("a")
        val ticket = session.ticket()
        session.changeSong("a")
        assertTrue(session.isCurrent(ticket))
        session.changeSong("b")
        session.changeSong("a")
        assertFalse(session.isCurrent(ticket))
    }

    @Test
    fun `latest selection expires only its own picture owner`() {
        val session = PictureEditSession()
        session.changeSong("a")
        val cover = session.begin("cover")
        val artistA = session.begin("artist:a")
        val artistB = session.begin("artist:b")
        session.begin("artist:a")
        assertFalse(session.isCurrent(artistA))
        assertTrue(session.isCurrent(artistB))
        assertTrue(session.isCurrent(cover))
    }

    @Test
    fun `group edits invalidate pending artists without invalidating cover or exports`() {
        val session = PictureEditSession()
        session.changeSong("a")
        val cover = session.begin("cover")
        val export = session.begin("coverExport")
        val a = session.begin("artist:a")
        val b = session.begin("artist:b")
        session.invalidateArtists()
        assertFalse(session.isCurrent(a))
        assertFalse(session.isCurrent(b))
        assertTrue(session.isCurrent(cover))
        assertTrue(session.isCurrent(export))
    }
}
