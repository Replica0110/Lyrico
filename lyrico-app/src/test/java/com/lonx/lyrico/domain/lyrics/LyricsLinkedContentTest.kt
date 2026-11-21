package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLine
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsWord
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.domain.lyrics.document.RemoveEmptyLinesPostProcessor
import com.lonx.lyrico.domain.lyrics.document.RemoveTagLinesPostProcessor
import com.lonx.lyrico.domain.lyrics.document.TtmlWriter
import com.lonx.lyrico.domain.lyrics.document.toLyricsDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLinkedContentTest {
    @Test
    fun `plain preview independently hides timed romanization and translation from imported enhanced LRC`() {
        val raw = requireNotNull(javaClass.getResource("/lyrics/enhanced-lrc-three-tracks.lrc")).readText()
        val parsed = requireNotNull(LyricsParser.parseOrNull(raw))
        assertEquals(1, parsed.original.size)
        assertEquals(1, parsed.romanization!!.size)
        assertEquals(1, parsed.translated!!.size)
        val original = "「形には意味などないから」"
        val translated = "学者在电视上说"
        for (showRomanization in listOf(true, false)) {
            for (showTranslation in listOf(true, false)) {
                val plain = LyricsRenderer.renderPlainText(parsed, config().copy(
                    showRomanization = showRomanization, showTranslation = showTranslation))
                assertTrue(plain.contains(original))
                assertEquals(showRomanization, plain.contains("ka ta chi"))
                assertEquals(showTranslation, plain.contains(translated))
                assertFalse(LyricsTimestamp.lrcTimePattern.containsMatchIn(plain))
                if (!showRomanization && showTranslation) assertEquals("$original\n$translated", plain)
                if (!showRomanization && !showTranslation) assertEquals(original, plain)
            }
        }
        assertTrue(raw.contains("<00:16.591>"))
    }

    @Test
    fun `real field processing retains linked lyrics across raw structured TTML LRC and plain output`() {
        for (input in inputs()) {
            for (format in listOf(LyricsFormat.TTML, LyricsFormat.PLAIN_LRC)) {
                val output = LyricsRenderer.render(input, config(format))
                assertTrue("$format: $output", output.contains("translation"))
                assertTrue("$format: $output", output.contains("ni"))
                if (format == LyricsFormat.PLAIN_LRC) {
                    assertTrue(output.lines().all { it.replace(LyricsTimestamp.lrcTimePattern, "").isNotBlank() })
                }
            }
            val ttml = LyricsRenderer.render(input, config(LyricsFormat.TTML))
            val parsedForEditor = requireNotNull(LyricsParser.parseOrNull(ttml))
            assertEquals(1, parsedForEditor.original.size)
            assertEquals("ni hao\ntranslation", LyricsRenderer.renderPlainText(parsedForEditor, config()))
        }
    }

    @Test
    fun `only translation and hiding linked tracks obey the same empty anchor rules`() {
        for (input in inputs()) {
            for (format in listOf(LyricsFormat.TTML, LyricsFormat.PLAIN_LRC)) {
                val onlyTranslation = LyricsRenderer.render(input, config(format).copy(onlyTranslationIfAvailable = true))
                assertTrue(onlyTranslation.contains("translation"))
                assertFalse(onlyTranslation.contains("ni"))
                val hidden = config(format).copy(showTranslation = false, showRomanization = false)
                assertEquals("", LyricsRenderer.render(input, hidden))
            }
            assertEquals("", LyricsRenderer.renderPlainText(structured(), config().copy(
                showTranslation = false, showRomanization = false)))
        }
    }

    @Test
    fun `pure text rendering removes timestamps and uses track switches and roman word separation`() {
        val input = structured().copy(original = listOf(line("original")))

        assertEquals("original\nni hao\ntranslation", LyricsRenderer.renderPlainText(input, config()))
        assertEquals("original", LyricsRenderer.renderPlainText(input, config().copy(
            showTranslation = false, showRomanization = false)))
        assertEquals("translation", LyricsRenderer.renderPlainText(input, config().copy(onlyTranslationIfAvailable = true)))
    }

    @Test
    fun `empty line deletion respects keys for different originals at the same time`() {
        val document = pairedDocument(dropText = "", dropTranslation = "")
        val filtered = RemoveEmptyLinesPostProcessor.process(document)

        assertEquals(listOf("keep"), filtered.tracks.first { it.type == LyricsTrackType.Original }.lines.map { it.linkKey })
        assertEquals(listOf("keep"), filtered.tracks.first { it.type == LyricsTrackType.Translation }.lines.map { it.linkKey })
        assertTrue(TtmlWriter.write(filtered).contains("keep translation"))
    }

    @Test
    fun `tag filtering removes its group without deleting another keyed group at the same time`() {
        val filtered = RemoveTagLinesPostProcessor(listOf("作词：")).process(pairedDocument("作词：drop", "drop translation"))
        val output = TtmlWriter.write(filtered)

        assertTrue(output.contains("keep translation"))
        assertFalse(output.contains("drop translation"))
        assertEquals(listOf("keep"), filtered.tracks.first { it.type == LyricsTrackType.Translation }.lines.map { it.linkKey })
    }

    @Test
    fun `background shared by time survives deleting just one of its original anchors`() {
        val document = LyricsDocument(sourceFormat = LyricsFormat.TTML, tracks = listOf(
            LyricsTrack(LyricsTrackType.Original, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "作词：drop"),
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "keep original"))),
            LyricsTrack(LyricsTrackType.Background, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "shared background")))
        ))
        val filtered = RemoveTagLinesPostProcessor(listOf("作词：")).process(document)

        assertTrue(TtmlWriter.write(filtered).contains("shared background"))
        assertEquals(1, filtered.tracks.first { it.type == LyricsTrackType.Background }.lines.size)
    }

    @Test
    fun `blank timed original with real background survives empty line removal`() {
        val document = LyricsDocument(sourceFormat = LyricsFormat.TTML, tracks = listOf(
            LyricsTrack(LyricsTrackType.Original, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = " ", linkKey = "anchor"))),
            LyricsTrack(LyricsTrackType.Background, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "background", linkKey = "anchor")))
        ))

        assertTrue(TtmlWriter.write(RemoveEmptyLinesPostProcessor.process(document)).contains("background"))
    }

    @Test
    fun `namespace prefixed TTML roots participate in empty detection without matching LRC body text`() {
        val empty = """<tt:tt xmlns:tt="http://www.w3.org/ns/ttml"><tt:body/></tt:tt>"""
        val nonempty = """<tt:tt xmlns:tt="http://www.w3.org/ns/ttml"><tt:body><tt:div><tt:p begin="1s" end="2s">lyrics</tt:p></tt:div></tt:body></tt:tt>"""
        fun raw(text: String) = LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_TTML, rawTtml = text)

        assertEquals(LyricsFormat.TTML, LyricsParser.detectFormat(empty))
        assertEquals("", LyricsRenderer.render(raw(empty), config(LyricsFormat.TTML).copy(removeEmptyLines = false)))
        assertEquals(nonempty, LyricsRenderer.render(raw(nonempty), config(LyricsFormat.TTML).copy(removeEmptyLines = false)))
        assertEquals(LyricsFormat.PLAIN_LRC, LyricsParser.detectFormat("[00:01.00]$empty"))
    }

    private fun inputs(): List<LyricsResult> {
        val structured = structured()
        val raw = LyricsResult(emptyMap(), emptyList(), null, null, payloadType = LyricsPayloadType.RAW_TTML,
            rawTtml = TtmlWriter.write(structured.toLyricsDocument()))
        return listOf(structured, raw)
    }

    private fun structured() = LyricsResult(emptyMap(), listOf(LyricsLine(1000, 2000, emptyList())),
        listOf(line("translation")), listOf(LyricsLine(1000, 2000, listOf(
            LyricsWord(1000, 1500, "ni"), LyricsWord(1500, 2000, "hao")))))

    private fun line(text: String) = LyricsLine(1000, 2000, listOf(LyricsWord(1000, 2000, text)))

    private fun config(format: LyricsFormat = LyricsFormat.PLAIN_LRC) = LyricsRenderConfig(
        format, showRomanization = true, showTranslation = true, removeEmptyLines = true)

    private fun pairedDocument(dropText: String, dropTranslation: String) = LyricsDocument(
        sourceFormat = LyricsFormat.TTML, tracks = listOf(
            LyricsTrack(LyricsTrackType.Original, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "keep original", linkKey = "keep"),
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = dropText, linkKey = "drop"))),
            LyricsTrack(LyricsTrackType.Translation, lines = listOf(
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = "keep translation", linkKey = "keep"),
                LyricsDocumentLine(startMs = 1000, endMs = 2000, text = dropTranslation, linkKey = "drop")))
        ))
}
