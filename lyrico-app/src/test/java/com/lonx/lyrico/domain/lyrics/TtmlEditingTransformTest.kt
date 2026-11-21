package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.TtmlXml
import com.lonx.lyrico.domain.lyrics.document.TtmlParser
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import org.w3c.dom.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlEditingTransformTest {
    @Test
    fun `every supported TTML time expression shifts on div line word Ruby and sidecar without changing duration`() {
        for (time in listOf("1.25s", "1250ms", "1.25", "00:01.250", "00:00:01.250")) {
            val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata><span begin="$time" end="2s">roma</span></metadata></head><body dur="4s"><div begin="0.5s" end="4s"><p begin="1s" end="2s"><span begin="$time" end="2s">lyrics</span></p></div></body></tt>"""
            val shifted = TtmlXml.parse(LyricsTextTransform.shiftOffset(raw, 250))
            val spans = shifted.getElementsByTagNameNS(NS, "span")
            for (index in 0 until spans.length) assertEquals("00:00:01.500", (spans.item(index) as Element).getAttribute("begin"))
            assertEquals("00:00:00.750", (shifted.getElementsByTagNameNS(NS, "div").item(0) as Element).getAttribute("begin"))
            assertEquals("00:00:01.250", (shifted.getElementsByTagNameNS(NS, "p").item(0) as Element).getAttribute("begin"))
            assertEquals("4s", (shifted.getElementsByTagNameNS(NS, "body").item(0) as Element).getAttribute("dur"))
        }
    }

    @Test
    fun `raw renderer and editor preserve XML extensions while shifting the same absolute timeline`() {
        val raw = """<?xml version="1.0"?><tt xmlns="$NS" xmlns:extra="urn:extra"><!--keep--><head><extra:meta extra:flag="臺灣"><![CDATA[臺灣]]></extra:meta></head><body dur="4s"><div begin="0.5s" end="4s"><p begin="1s" end="2s"><span begin="1250ms" end="2s">臺灣</span><extra:item begin="9s" extra:flag="臺灣">extension</extra:item></p></div></body></tt>"""
        assertEquals(raw, LyricsTextTransform.shiftOffset(raw, 0))
        val rendered = LyricsRenderer.render(LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_TTML, rawTtml = raw),
            LyricsRenderConfig(LyricsFormat.TTML, true, removeEmptyLines = true), offset = 250)
        val shifted = TtmlXml.parse(rendered)
        val extension = shifted.getElementsByTagNameNS("urn:extra", "item").item(0) as Element
        assertEquals("9s", extension.getAttribute("begin"))
        assertEquals("臺灣", extension.getAttributeNS("urn:extra", "flag"))
        assertTrue(rendered.contains("keep"))
        assertEquals("00:00:01.500", (shifted.getElementsByTagNameNS(NS, "span").item(0) as Element).getAttribute("begin"))
    }

    @Test
    fun `editing script conversion transforms text and CDATA but never XML names attributes or time values`() {
        val raw = """<tt xmlns="$NS" xmlns:extra="urn:extra"><body><div><p begin="1s" end="2s" extra:flag="臺灣"><extra:臺灣><![CDATA[臺灣]]></extra:臺灣></p></div></body></tt>"""
        val converted = LyricsTextTransform.convertLyricsScript(raw, ConversionMode.TRADITIONAL_TO_SIMPLIFIED)
        val dom = TtmlXml.parse(converted)
        assertEquals("臺灣", (dom.getElementsByTagNameNS(NS, "p").item(0) as Element).getAttributeNS("urn:extra", "flag"))
        assertEquals("台湾", dom.getElementsByTagNameNS("urn:extra", "臺灣").item(0).textContent)
        assertEquals("1s", (dom.getElementsByTagNameNS(NS, "p").item(0) as Element).getAttribute("begin"))
        assertEquals(raw, LyricsTextTransform.convertLyricsScript(raw, ConversionMode.NONE))
    }

    @Test
    fun `model processing with script conversion offsets Ruby romanization and paragraph bounds together`() {
        val raw = """<tt xmlns="$NS" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:tts="http://www.w3.org/ns/ttml#styling" xmlns:itunes="http://music.apple.com/lyric-ttml-internal"><head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal"><transliterations><transliteration><text for="key"><span xmlns="$NS" begin="1s" end="2s">tai</span></text></transliteration></transliterations></iTunesMetadata></metadata></head><body dur="4s"><div begin="0.5s" end="3s"><p begin="1s" end="2s" itunes:key="key"><span tts:ruby="container"><span tts:ruby="base">臺</span><span tts:ruby="textContainer"><span tts:ruby="text" begin="1s" end="2s">臺</span></span></span></p></div></body></tt>"""
        val output = LyricsRenderer.render(LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_TTML, rawTtml = raw),
            LyricsRenderConfig(LyricsFormat.TTML, true, removeEmptyLines = false,
                conversionMode = ConversionMode.TRADITIONAL_TO_SIMPLIFIED), offset = 250)
        val document = TtmlParser.parse(output)
        val line = document.tracks.first { it.type == LyricsTrackType.Original }.lines.single()
        val romanization = document.tracks.first { it.type == LyricsTrackType.Romanization }.lines.single()

        assertEquals(1250L, line.startMs)
        assertEquals(2250L, line.words.single().ruby.single().endMs)
        assertEquals("台", line.words.single().ruby.single().text)
        assertEquals(1250L, romanization.words.single().startMs)
        assertEquals("750", line.extensions.attributes.entries.first { it.key.localName == "divBegin" }.value)
        assertEquals("3250", line.extensions.attributes.entries.first { it.key.localName == "divEnd" }.value)
        assertEquals("4s", document.bodyExtensions.attributes.entries.first { it.key.localName == "dur" }.value)
    }

    private companion object { const val NS = "http://www.w3.org/ns/ttml" }
}
