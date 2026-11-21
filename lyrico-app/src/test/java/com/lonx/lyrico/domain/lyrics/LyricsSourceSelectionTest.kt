package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSourceSelectionTest {
    @Test
    fun `matching target payload wins for every output format`() {
        for (format in LyricsFormat.entries) {
            val output = LyricsRenderer.render(result("plain", "verbatim", "enhanced", "ttml", "multi"), config(format))
            val expected = when (format) {
                LyricsFormat.PLAIN_LRC -> "plain"
                LyricsFormat.VERBATIM_LRC -> "verbatim"
                LyricsFormat.ENHANCED_LRC -> "enhanced"
                LyricsFormat.TTML -> "ttml"
            }
            assertTrue("$format: $output", output.contains(expected))
        }
    }

    @Test
    fun `missing target follows the existing primary then secondary source order`() {
        val cases = listOf(
            Case(LyricsFormat.PLAIN_LRC, true, listOf(null, "verbatim", "enhanced", "ttml", "multi"), "ttml"),
            Case(LyricsFormat.PLAIN_LRC, false, listOf(null, "verbatim", "enhanced", "ttml", "multi"), "enhanced"),
            Case(LyricsFormat.PLAIN_LRC, false, listOf(null, "verbatim", null, "ttml", "multi"), "verbatim"),
            Case(LyricsFormat.PLAIN_LRC, false, listOf(null, null, null, "ttml", "multi"), "multi"),
            Case(LyricsFormat.PLAIN_LRC, false, listOf(null, null, null, "ttml", null), "ttml"),
            Case(LyricsFormat.VERBATIM_LRC, true, listOf("plain", null, "enhanced", "ttml", "multi"), "ttml"),
            Case(LyricsFormat.VERBATIM_LRC, false, listOf("plain", null, "enhanced", "ttml", "multi"), "enhanced"),
            Case(LyricsFormat.VERBATIM_LRC, false, listOf("plain", null, null, "ttml", "multi"), "plain"),
            Case(LyricsFormat.ENHANCED_LRC, true, listOf("plain", "verbatim", null, "ttml", "multi"), "ttml"),
            Case(LyricsFormat.ENHANCED_LRC, false, listOf("plain", "verbatim", null, "ttml", "multi"), "verbatim"),
            Case(LyricsFormat.TTML, true, listOf("plain", "verbatim", "enhanced", null, "multi"), "enhanced"),
            Case(LyricsFormat.TTML, true, listOf("plain", "verbatim", null, null, "multi"), "verbatim"),
            Case(LyricsFormat.TTML, true, listOf("plain", null, null, null, "multi"), "plain")
        )
        for (case in cases) {
            val output = LyricsRenderer.render(result(*case.sources.toTypedArray()),
                config(case.format).copy(showTranslation = case.translation))
            assertTrue("$case: $output", output.contains(case.expected))
        }
    }

    @Test
    fun `multi person raw is a final usable fallback for every output format`() {
        for (format in LyricsFormat.entries) {
            val output = LyricsRenderer.render(result(null, null, null, null, "multi"), config(format))
            assertTrue("$format: $output", output.contains("multi"))
        }
    }

    private fun result(vararg sources: String?): LyricsResult {
        fun line(index: Int) = sources[index]?.let { "[00:01.00]$it" }.orEmpty()
        return LyricsResult(emptyMap(), emptyList(), null, null, payloadType = LyricsPayloadType.RAW_PLAIN_LRC,
            rawPlainLrc = line(0), rawVerbatimLrc = line(1), rawEnhancedLrc = line(2),
            rawTtml = sources[3]?.let { """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">$it</p></div></body></tt>""" }.orEmpty(),
            rawMultiPersonEnhancedLrc = line(4))
    }

    private fun config(format: LyricsFormat) = LyricsRenderConfig(format, showRomanization = true,
        showTranslation = true, removeEmptyLines = false)

    private data class Case(val format: LyricsFormat, val translation: Boolean, val sources: List<String?>, val expected: String)
}
