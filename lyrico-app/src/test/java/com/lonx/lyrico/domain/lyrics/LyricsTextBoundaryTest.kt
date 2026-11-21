package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.TtmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTextBoundaryTest {
    @Test
    fun `direct text offset preserves exact input bytes for zero offset`() {
        val raw = "\r\n[00:01.00] text <00:01.50>word\r\n"

        assertEquals(raw, LyricsTextTransform.shiftOffset(raw, 0))
    }

    @Test
    fun `direct LRC offset handles both brackets positive negative and zero clamping`() {
        val raw = "[00:01.00]line<00:01.50>word"

        assertEquals("[00:01.250]line<00:01.750>word", LyricsTextTransform.shiftOffset(raw, 250))
        assertEquals("[00:00.750]line<00:01.250>word", LyricsTextTransform.shiftOffset(raw, -250))
        assertEquals("[00:00.000]line<00:00.000>word", LyricsTextTransform.shiftOffset(raw, -2000))
    }

    @Test
    fun `direct TTML offset replaces standard timestamps without shifting duration`() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><body dur="4s"><div><p begin="00:00:01.000" end="00:00:02.500">text</p></div></body></tt>"""
        for ((offset, begin, end) in listOf(Triple(250L, "00:00:01.250", "00:00:02.750"),
            Triple(-2000L, "00:00:00.000", "00:00:00.500"))) {
            val dom = com.lonx.lyrico.domain.lyrics.document.TtmlXml.parse(LyricsTextTransform.shiftOffset(raw, offset))
            val p = dom.getElementsByTagNameNS("http://www.w3.org/ns/ttml", "p").item(0) as org.w3c.dom.Element
            assertEquals(begin, p.getAttribute("begin"))
            assertEquals(end, p.getAttribute("end"))
            assertEquals("4s", (dom.getElementsByTagNameNS("http://www.w3.org/ns/ttml", "body").item(0) as org.w3c.dom.Element).getAttribute("dur"))
        }
    }

    @Test
    fun `offset encoding starts from the original input on every render`() {
        val original = LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_PLAIN_LRC, rawPlainLrc = "[00:01.00]歌词")
        val config = LyricsRenderConfig(LyricsFormat.PLAIN_LRC, showRomanization = true,
            removeEmptyLines = false, conversionMode = ConversionMode.NONE)

        val first = LyricsRenderer.render(original, config, offset = 250)
        val second = LyricsRenderer.render(original, config, offset = 500)
        val repeated = LyricsRenderer.render(original, config, offset = 250)

        assertEquals(1250L, requireNotNull(LyricsParser.parseOrNull(first)).original.single().start)
        assertEquals(1500L, requireNotNull(LyricsParser.parseOrNull(second)).original.single().start)
        assertEquals(first, repeated)
        assertEquals("[00:01.00]歌词", original.rawPlainLrc)
    }

    @Test
    fun `document offset clamps line word and ruby times without changing body duration`() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:tts="http://www.w3.org/ns/ttml#styling"><body dur="4s"><div><p begin="1s" end="2s"><span tts:ruby="container"><span tts:ruby="base">字</span><span tts:ruby="textContainer"><span tts:ruby="text" begin="1s" end="2s">zi</span></span></span></p></div></body></tt>"""
        val result = LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_TTML, rawTtml = raw)
        val config = LyricsRenderConfig(LyricsFormat.TTML, showRomanization = true,
            removeEmptyLines = false, conversionMode = ConversionMode.NONE)
        val shifted = TtmlParser.parse(LyricsRenderer.render(result, config, offset = -1500))
        val line = shifted.tracks.single { it.type == LyricsTrackType.Original }.lines.single()

        assertEquals(0L, line.startMs)
        assertEquals(500L, line.endMs)
        assertEquals(0L, line.words.single().startMs)
        assertEquals(0L, line.words.single().ruby.single().startMs)
        assertEquals(500L, line.words.single().ruby.single().endMs)
        assertEquals("4s", shifted.bodyExtensions.attributes.entries.single { it.key.localName == "dur" }.value)
    }

    @Test
    fun `plain text index extraction trims removes blank lines and deduplicates`() {
        assertEquals(listOf("第一句", "第二句"), LyricsSearchTextExtractor.extractLines(" 第一句 \n\n第二句\n第一句"))
        assertNull(LyricsSearchTextExtractor.toSearchText(" \n "))
        assertTrue(LyricsSearchTextExtractor.extractLines(null).isEmpty())
    }

    @Test
    fun `broken TTML decoding fails safely and indexing falls back to visible text`() {
        val broken = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">可搜索歌词</p>"""

        assertNull(LyricsParser.parseOrNull(broken))
        assertEquals(listOf("可搜索歌词"), LyricsSearchTextExtractor.extractLines(broken))
    }

    @Test
    fun `timestamped index extraction includes original translation and romanization`() {
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1s" end="2s">原文<span ttm:role="x-translation">Translation</span><span ttm:role="x-roman">roman</span></p></div></body></tt>"""

        assertEquals(listOf("原文", "Translation", "roman"), LyricsSearchTextExtractor.extractLines(ttml))
    }
}
