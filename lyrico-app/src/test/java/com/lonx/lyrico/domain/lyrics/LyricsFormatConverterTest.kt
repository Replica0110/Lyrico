package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFormatConverterTest {
    @Test
    fun `filtering every line returns Empty without decoding the original again`() {
        assertEquals(LyricsProcessingResult.Empty, LyricsFormatConverter.convert(
            raw = "[00:01.00]作词：某人",
            targetFormat = LyricsFormat.PLAIN_LRC,
            removeTagLineKeywords = listOf("作词：")
        ))
    }

    @Test
    fun `word level targets reject line only timing when the caller requires words`() {
        for (target in listOf(LyricsFormat.ENHANCED_LRC, LyricsFormat.VERBATIM_LRC)) {
            assertEquals(LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.WORD_TIMING_REQUIRED),
                LyricsFormatConverter.convert("[00:01.00]只有整行时间", target, requireWordLevelTiming = true))
        }
    }

    @Test
    fun `word timing from the parsed document allows both word level targets`() {
        for (target in listOf(LyricsFormat.ENHANCED_LRC, LyricsFormat.VERBATIM_LRC)) {
            val result = LyricsFormatConverter.convert(
                "[00:01.00]你[00:01.50]好[00:02.00]", target, requireWordLevelTiming = true)
            assertTrue(result is LyricsProcessingResult.Changed)
            val decoded = requireNotNull(LyricsParser.parseOrNull((result as LyricsProcessingResult.Changed).text))
            assertEquals(listOf(1000L, 1500L), decoded.original.single().words.map { it.start })
        }
    }

    @Test
    fun `callers may still convert line timed lyrics without requiring word timing`() {
        val result = LyricsFormatConverter.convert("[00:01.00]整行", LyricsFormat.ENHANCED_LRC)

        assertTrue(result is LyricsProcessingResult.Changed)
        assertTrue((result as LyricsProcessingResult.Changed).text.contains("整行"))
    }

    @Test
    fun `identical output is Unchanged and blank input is Empty`() {
        assertEquals(LyricsProcessingResult.Unchanged,
            LyricsFormatConverter.convert("[00:01.000]歌词", LyricsFormat.PLAIN_LRC))
        assertEquals(LyricsProcessingResult.Empty,
            LyricsFormatConverter.convert(" \n ", LyricsFormat.TTML))
    }

    @Test
    fun `unrecognized text is Unsupported while malformed recognized XML still throws`() {
        assertEquals(LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.UNRECOGNIZED_FORMAT),
            LyricsFormatConverter.convert("没有时间戳", LyricsFormat.TTML))
        val failure = runCatching {
            LyricsFormatConverter.convert(
                """<tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s" end="2s">未闭合""",
                LyricsFormat.PLAIN_LRC
            )
        }.exceptionOrNull()

        assertNotNull("Malformed recognized input must not become a successful or unsupported result", failure)
    }

    @Test
    fun `single song processing leaves the draft unchanged when conversion filters everything`() {
        val raw = "[00:01.00]作词：某人"
        val keywords = listOf("作词：")
        val options = LyricsProcessingOptions(targetFormat = LyricsFormat.PLAIN_LRC,
            removeTagLines = true, tagLineKeywords = keywords)

        assertEquals(LyricsProcessingResult.Empty, LyricsProcessor.process(raw, options))
        // EditMetadataViewModel only replaces its draft for a Changed processing result.
    }

    @Test
    fun `TTML structural cleanup also keeps the draft when its target output is empty`() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">作词：某人</p></div></body></tt>"""
        val keywords = listOf("作词：")
        val options = LyricsProcessingOptions(targetFormat = LyricsFormat.PLAIN_LRC,
            removeTagLines = true, tagLineKeywords = keywords)

        assertEquals(LyricsProcessingResult.Empty, LyricsProcessor.process(raw, options))
    }

    @Test
    fun `same format TTML filtering all content is Empty and leaves the single song draft unchanged`() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">作词：某人</p></div></body></tt>"""
        val keywords = listOf("作词：")
        assertEquals(LyricsProcessingResult.Empty, LyricsFormatConverter.convert(
            raw, LyricsFormat.TTML, removeTagLineKeywords = keywords))
        assertEquals(LyricsProcessingResult.Empty, LyricsProcessor.process(raw, LyricsProcessingOptions(targetFormat = LyricsFormat.TTML,
            removeTagLines = true, tagLineKeywords = keywords)))
    }
}
