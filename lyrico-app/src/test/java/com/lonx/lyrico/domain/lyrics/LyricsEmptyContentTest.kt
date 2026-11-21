package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLine
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsWord
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.LyricsDocumentPipeline
import com.lonx.lyrico.domain.lyrics.document.TtmlParser
import com.lonx.lyrico.domain.lyrics.document.TtmlWriter
import com.lonx.lyrico.plugin.source.PluginJsonParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEmptyContentTest {
    @Test
    fun `writer suppresses empty metadata only whitespace and untimed documents`() {
        val cases = listOf(
            LyricsDocument(),
            LyricsDocument(metadata = LyricsMetadata(title = "title")),
            LyricsDocument(tracks = listOf(LyricsTrack(LyricsTrackType.Original,
                lines = listOf(LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "  \n "))))),
            LyricsDocument(tracks = listOf(LyricsTrack(LyricsTrackType.Original,
                lines = listOf(LyricsDocumentLine(text = "text without a writable time")))))
        )

        cases.forEach { assertEquals("", TtmlWriter.write(it)) }
    }

    @Test
    fun `head text and isolated sidecars cannot make an empty body into lyrics`() {
        val raw = ttml("", """<metadata><note>head text</note><iTunesMetadata xmlns="$ITUNES"><translations><translation><text for="missing">orphan translation</text></translation></translations></iTunesMetadata></metadata>""")

        assertEquals("", TtmlWriter.write(TtmlParser.parse(raw)))
        assertEquals(LyricsProcessingResult.Empty, LyricsFormatConverter.convert(raw, LyricsFormat.TTML))
        assertEquals("", LyricsRenderer.render(rawResult(raw), config()))
    }

    @Test
    fun `valid empty TTML is empty through processing conversion field rules and rendering`() {
        val cases = listOf(ttml(""), ttml("<p begin=\"1s\" end=\"2s\">   </p>"),
            ttml("<p end=\"2s\">no start time</p>"))

        for (raw in cases) {
            assertNull(LyricsDocumentPipeline.process(raw, LyricsFormat.TTML, LyricsFormat.TTML))
            assertEquals(LyricsProcessingResult.Empty, LyricsFormatConverter.convert(raw, LyricsFormat.TTML))
            assertEquals("", LyricsRenderer.render(rawResult(raw), config()))
        }
    }

    @Test
    fun `filtered raw output stays empty instead of returning its original XML or another source`() {
        val raw = ttml("<p begin=\"1s\" end=\"2s\"> / </p>")
        val result = rawResult(raw).copy(rawEnhancedLrc = "[00:01.00]<00:01.00>alternative<00:02.00>")

        assertEquals("", LyricsRenderer.render(result, config().copy(removeEmptyLines = true)))
        assertEquals("", LyricsRenderer.render(rawResult(raw), config().copy(removeEmptyLines = true)))
    }

    @Test
    fun `clearing one raw field retains independent valid alternatives before source selection`() {
        val result = rawResult(ttml("<p begin=\"1s\" end=\"2s\">valid TTML</p>")).copy(
            payloadType = LyricsPayloadType.RAW_ENHANCED_LRC,
            rawEnhancedLrc = "[00:01.00] / "
        )
        assertTrue(LyricsRenderer.render(result, config().copy(removeEmptyLines = true)).contains("valid TTML"))
        assertEquals("[00:01.00] / ", result.rawEnhancedLrc)
    }

    @Test
    fun `linked translation and background content keep an otherwise blank raw anchor writable`() {
        val translation = ttml("<p begin=\"1s\" end=\"2s\" itunes:key=\"anchor\"> </p>",
            """<metadata><iTunesMetadata xmlns="$ITUNES"><translations><translation><text for="anchor">translated words</text></translation></translations></iTunesMetadata></metadata>""")
        val background = ttml("""<p begin="1s" end="2s" itunes:key="anchor"><span ttm:role="x-bg">background words</span></p>""")

        for ((raw, expected) in listOf(translation to "translated words", background to "background words")) {
            assertTrue(TtmlWriter.write(TtmlParser.parse(raw)).contains(expected))
            assertTrue(LyricsRenderer.render(rawResult(raw), config()).contains(expected))
        }
    }

    @Test
    fun `structured romanization with a timed blank original anchor is rendered as lyrics`() {
        val result = LyricsResult(emptyMap(), listOf(LyricsLine(1000, 2000, emptyList())), null,
            listOf(LyricsLine(1000, 2000, listOf(LyricsWord(1000, 2000, "roman words")))))

        assertTrue(LyricsRenderer.render(result, config()).contains("roman words"))
        assertEquals("", LyricsRenderer.render(result, config().copy(showRomanization = false)))
    }

    @Test
    fun `only translation mode can render a blank timed anchor using its linked translation`() {
        val result = LyricsResult(emptyMap(), listOf(LyricsLine(1000, 2000, emptyList())),
            listOf(LyricsLine(1000, 2000, listOf(LyricsWord(1000, 2000, "translation only")))), null)

        assertTrue(LyricsRenderer.render(result, config().copy(onlyTranslationIfAvailable = true))
            .contains("translation only"))
    }

    @Test
    fun `nonempty raw TTML preserves unknown body and head extensions without operations`() {
        val raw = """<tt xmlns="$TTML" xmlns:custom="urn:test"><head><metadata><custom:note>metadata</custom:note></metadata></head><body><div><p begin="1s" end="2s"><custom:span custom:keep="yes">real lyrics</custom:span></p></div></body></tt>"""

        assertEquals(raw, LyricsRenderer.render(rawResult(raw), config()))
    }

    @Test
    fun `malformed raw direct input is preserved while transformed XML still throws`() {
        val raw = ttml("<p begin=\"1s\" end=\"2s\">lyrics</p>").removeSuffix("</tt>")

        assertEquals(raw, LyricsRenderer.render(rawResult(raw), config()))
        assertTrue(runCatching { LyricsRenderer.render(rawResult(raw), config().copy(removeEmptyLines = true)) }.isFailure)
    }

    @Test
    fun `legacy string plain text survives field processing and rendering with active settings`() {
        val raw = "第一句\n第二句"
        val result = requireNotNull(PluginJsonParser(Json).parseLyrics(JsonPrimitive(raw).toString()))
        assertEquals(raw, result.rawPlainLrc)
        assertEquals(raw, LyricsRenderer.render(result, config(LyricsFormat.PLAIN_LRC).copy(removeEmptyLines = true), offset = 250))
    }

    @Test
    fun `legacy TTML string uses its real format when active settings require parsing`() {
        val raw = ttml("<p begin=\"1s\" end=\"2s\">legacy TTML</p>")
        val result = requireNotNull(PluginJsonParser(Json).parseLyrics(JsonPrimitive(raw).toString()))
        val output = LyricsRenderer.render(result,
            config(LyricsFormat.PLAIN_LRC).copy(removeEmptyLines = true), offset = 250)

        assertTrue(output.contains("legacy TTML"))
        assertFalse(output.contains("<tt"))
        assertEquals(1250L, requireNotNull(LyricsParser.parseOrNull(output)).original.single().start)
    }

    private fun rawResult(raw: String) = LyricsResult(emptyMap(), emptyList(), null, null,
        payloadType = LyricsPayloadType.RAW_TTML, rawTtml = raw)

    private fun config(format: LyricsFormat = LyricsFormat.TTML) = LyricsRenderConfig(
        format = format, showRomanization = true, showTranslation = true,
        removeEmptyLines = false, conversionMode = ConversionMode.NONE
    )

    private fun ttml(body: String, head: String = "") =
        """<tt xmlns="$TTML" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="$ITUNES"><head>$head</head><body><div>$body</div></body></tt>"""

    private companion object {
        const val TTML = "http://www.w3.org/ns/ttml"
        const val ITUNES = "http://music.apple.com/lyric-ttml-internal"
    }
}
