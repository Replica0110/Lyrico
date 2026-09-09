package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricEncoderTest {
    @Test
    fun structuredWordLevelRomanizationIsWrittenToTtmlHeadSidecar() {
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(
                        LyricsWord(start = 1000L, end = 1500L, text = "眼"),
                        LyricsWord(start = 1500L, end = 2000L, text = "前")
                    )
                )
            ),
            translated = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "面前"))
                )
            ),
            romanization = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(
                        LyricsWord(start = 1000L, end = 1500L, text = "ngaan"),
                        LyricsWord(start = 1500L, end = 2000L, text = "cin")
                    )
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(
                format = LyricFormat.TTML,
                showRomanization = true,
                showTranslation = true
            )
        )

        // 音译写入 head <transliterations> sidecar（词级时间保留），正文 <p> 挂 itunes:key
        assertTrue(output.contains("""itunes:key="L1""""))
        assertTrue(output.contains("<transliterations>"))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.000" end="00:00:01.500">ngaan</span>"""))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.500" end="00:00:02.000">cin</span>"""))
        // 词级音译不再被拼成整行内联 x-romanization
        assertFalse(output.contains("""ttm:role="x-romanization""""))
        // 翻译为整行内联，保持不变
        assertTrue(output.contains("""ttm:role="x-translation">面前</span>"""))
    }

    @Test
    fun structuredWholeLineRomanizationKeepsSingleSidecarSpan() {
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼"))
                )
            ),
            translated = null,
            romanization = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "ngaan"))
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(
                format = LyricFormat.TTML,
                showRomanization = true,
                showTranslation = false
            )
        )

        assertTrue(output.contains("<transliterations>"))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.000" end="00:00:02.000">ngaan</span>"""))
        assertFalse(output.contains("""ttm:role="x-romanization""""))
    }
}
