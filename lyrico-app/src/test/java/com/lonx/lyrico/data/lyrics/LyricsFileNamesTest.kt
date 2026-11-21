package com.lonx.lyrico.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsFileNamesTest {
    @Test
    fun `TTML file names use root format recognition without requiring XML declaration or timed spans`() {
        for (raw in listOf(
            """<tt xmlns="http://www.w3.org/ns/ttml"><body/></tt>""",
            """<tt:tt xmlns:tt="http://www.w3.org/ns/ttml"><tt:body/></tt:tt>""",
            """<?xml version="1.0"?><!-- comment --><tt xmlns="http://www.w3.org/ns/ttml"><body/></tt>"""
        )) assertEquals("title.ttml", LyricsFileNames.defaultName("title", raw))
    }

    @Test
    fun `LRC and plain text use lrc even when their body contains XML looking text`() {
        assertEquals("title.lrc", LyricsFileNames.defaultName("title", "[00:01.00]歌词"))
        assertEquals("title.lrc", LyricsFileNames.defaultName("title", "纯文本"))
        assertEquals("title.lrc", LyricsFileNames.defaultName("title", "[00:01.00]<?xml begin=\"x\" end=\"x\""))
    }

    @Test
    fun `base name is owned by the caller so embedded dots and absent names retain existing behavior`() {
        assertEquals("title.live.lrc", LyricsFileNames.defaultName("title.live", "lyrics"))
        assertEquals(".lrc", LyricsFileNames.defaultName(null, "lyrics"))
        assertEquals("title.lrc.lrc", LyricsFileNames.defaultName("title.lrc", "lyrics"))
    }

    @Test
    fun `missing lyrics do not produce a default export name`() {
        assertNull(LyricsFileNames.defaultName("title", null))
        assertNull(LyricsFileNames.defaultName("title", " \n "))
    }
}
