package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLine
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsWord
import com.lonx.lyrico.plugin.source.PluginJsonParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTrackTimingTest {
    @Test
    fun `enhanced LRC retains simultaneous duet voices with their own linked tracks`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                <translations><translation><text for="a">甲译</text><text for="b">乙译</text></translation></translations>
                <transliterations><transliteration><text for="a">jia</text><text for="b">yi</text></transliteration></transliterations>
              </iTunesMetadata></metadata></head>
              <body><div>
                <p begin="1s" end="2s" itunes:key="a" ttm:agent="v1"><span begin="1s" end="2s">甲</span></p>
                <p begin="1s" end="2s" itunes:key="b" ttm:agent="v2"><span begin="1s" end="2s">乙</span></p>
              </div></body>
            </tt>
        """.trimIndent()
        val directParsed = requireNotNull(LyricsParser.parseOrNull(raw))
        val directText = LyricsRenderer.renderPlainText(directParsed, config(LyricsFormat.PLAIN_LRC))
        assertTrue(directText.contains("甲译"))
        assertTrue(directText.contains("乙译"))
        assertTrue(directText.contains("jia"))
        assertTrue(directText.contains("yi"))
        val directOutput = LyricsRenderer.render(directParsed, config(LyricsFormat.ENHANCED_LRC))
        assertTrue(directOutput.contains("v1:甲译"))
        assertTrue(directOutput.contains("v2:乙译"))
        val shiftedSidecar = raw.replace("<text for=\"b\">乙译</text>",
            "<text for=\"b\" begin=\"1.2s\" end=\"2.5s\">乙译</text>")
        val shiftedResult = requireNotNull(LyricsParser.parseOrNull(shiftedSidecar))
        val shiftedRestored = requireNotNull(LyricsParser.parseOrNull(
            LyricsRenderer.render(shiftedResult, config(LyricsFormat.TTML))))
        val secondTranslation = shiftedRestored.translated!!.single { it.words.single().text == "乙译" }
        assertEquals(1200L, secondTranslation.start)
        assertEquals(2500L, secondTranslation.end)
        val changed = LyricsFormatConverter.convert(raw, LyricsFormat.ENHANCED_LRC) as LyricsProcessingResult.Changed
        val output = changed.text
        assertTrue(output.contains("[00:01.000]v1:<00:01.000>甲<00:02.000>"))
        assertTrue(output.contains("[00:01.000]v2:<00:01.000>乙<00:02.000>"))
        assertTrue(output.contains("v2:yi"))
        assertTrue(output.contains("v2:乙译"))
        val decoded = requireNotNull(LyricsParser.parseOrNull(output))
        assertEquals(listOf("v1", "v2"), decoded.original.map { it.extensions["ttm:agent"] })
        assertEquals(listOf("甲", "乙"), decoded.original.map { it.words.single().text })
        assertEquals(listOf("甲译", "乙译"), decoded.translated!!.map { it.words.single().text })
        val restored = LyricsRenderer.render(decoded, config(LyricsFormat.TTML))
        val reread = requireNotNull(LyricsParser.parseOrNull(restored))
        assertEquals(listOf("v1", "v2"), reread.original.map { it.extensions["ttm:agent"] })
        assertEquals(listOf("甲译", "乙译"), reread.translated!!.map { it.words.single().text })
        val only = LyricsRenderer.render(decoded, config(LyricsFormat.ENHANCED_LRC).copy(onlyTranslationIfAvailable = true))
        assertTrue(only.contains("v1:甲译"))
        assertTrue(only.contains("v2:乙译"))
        val missingFirstTranslation = raw.replace("<text for=\"a\">甲译</text>", "")
            .replace("<text for=\"b\">乙译</text>", "<text for=\"b\" begin=\"1s\" end=\"2s\">乙译</text>")
        val missingOutput = (LyricsFormatConverter.convert(missingFirstTranslation,
            LyricsFormat.ENHANCED_LRC) as LyricsProcessingResult.Changed).text
        assertFalse(missingOutput.contains("v1:乙译"))
        assertEquals(1, missingOutput.lines().count { it.contains("乙译") })
        val emptyAnchors = missingFirstTranslation
            .replace("<span begin=\"1s\" end=\"2s\">甲</span>", "")
            .replace("<span begin=\"1s\" end=\"2s\">乙</span>", "")
            .replace("<text for=\"a\">jia</text>", "")
            .replace("<text for=\"b\">yi</text>", "")
        val anchorOutput = LyricsFormatConverter.convert(emptyAnchors, LyricsFormat.ENHANCED_LRC)
            as LyricsProcessingResult.Changed
        assertTrue(anchorOutput.text.contains("v2:乙译"))
        assertEquals("literal v1: text", requireNotNull(LyricsParser.parseOrNull(
            "[00:01.000]literal v1: text")).original.single().words.single().text)
    }

    @Test
    fun `existing LRC offset is baked into timestamps when metadata tags are omitted`() {
        val result = LyricsFormatConverter.convert("[offset:250]\n[ti:title]\n[00:01.000]歌词",
            LyricsFormat.PLAIN_LRC) as LyricsProcessingResult.Changed
        assertEquals("[00:00.750]歌词", result.text)
        val second = LyricsFormatConverter.convert(result.text, LyricsFormat.TTML) as LyricsProcessingResult.Changed
        assertEquals(750L, requireNotNull(LyricsParser.parseOrNull(second.text)).original.single().start)
        val delayed = LyricsFormatConverter.convert("[offset:-250]\n[00:01.000]歌词",
            LyricsFormat.PLAIN_LRC) as LyricsProcessingResult.Changed
        assertEquals("[00:01.250]歌词", delayed.text)
    }

    @Test
    fun `timed space words survive independently on all three plugin tracks`() {
        val line = """[1000,2000,[[1000,1400,"Hello"],[1400,1500," "],[1500,2000,"world"]]]"""
        val parsed = requireNotNull(PluginJsonParser(Json).parseLyrics(
            """{"type":"structured","original":[$line],"translated":[$line],"romanization":[$line]}"""))
        for (format in wordFormats + LyricsFormat.TTML) {
            val decoded = requireNotNull(LyricsParser.parseOrNull(LyricsRenderer.render(parsed, config(format))))
            for (track in listOf(decoded.original, decoded.translated!!, decoded.romanization!!)) {
                assertEquals(listOf("Hello", " ", "world"), track.single().words.map { it.text })
                assertEquals(listOf(1000L, 1400L, 1500L), track.single().words.map { it.start })
            }
        }
    }

    @Test
    fun `LRC rendering never appends plugin metadata as lyric tag lines`() {
        val withTags = input().copy(tags = mapOf("ti" to "缘分一道桥", "ar" to "artist",
            "al" to "album", "hash" to "hash-value", "key" to "key-value", "offset" to "250"))
        for (format in wordFormats + LyricsFormat.PLAIN_LRC) {
            val output = LyricsRenderer.render(withTags, config(format))
            assertFalse(output.contains("缘分一道桥"))
            assertFalse(Regex("\\[[A-Za-z]+:").containsMatchIn(output))
            assertTrue(output.contains("君"))
            assertTrue(output.contains("kimi"))
        }
    }

    @Test
    fun `each plugin track independently retains line single word and multiple word timing`() {
        val line = "[1000,2400,\"歌词\"]"
        val single = "[1000,2400,[[1100,2300,\"歌词\"]]]"
        val multiple = "[1000,2400,[[1100,1500,\"歌\"],[1700,2300,\"词\"]]]"
        val cases = listOf(line, single, multiple)
        for (original in cases) for (translation in cases) for (romanization in cases) {
            val plugin = """{"type":"structured","original":[$original],"translated":[$translation],"romanization":[$romanization]}"""
            val parsed = requireNotNull(PluginJsonParser(Json).parseLyrics(plugin))
            val inputs = listOf(parsed.original.single(), parsed.translated!!.single(), parsed.romanization!!.single())
            val expected = listOf(original, translation, romanization).map { it != line }
            assertEquals(expected, inputs.map { it.isWordByWord })
            for (format in wordFormats + LyricsFormat.TTML) {
                val output = LyricsRenderer.render(parsed, config(format))
                val decoded = requireNotNull(LyricsParser.parseOrNull(output))
                val tracks = listOf(decoded.original.single(), decoded.translated!!.single(), decoded.romanization!!.single())
                assertEquals("$format: $plugin\n$output", expected, tracks.map { it.isWordByWord })
                tracks.zip(inputs).forEach { (actual, input) ->
                    assertEquals(input.words.map { it.text.trim() }, actual.words.map { it.text.trim() })
                    if (input.isWordByWord) {
                        assertEquals(input.words.map { it.start }, actual.words.map { it.start })
                        assertEquals(input.words.map { it.end }, actual.words.map { it.end })
                    }
                }
                if (format == LyricsFormat.TTML) {
                    assertEquals(inputs.map { it.start }, tracks.map { it.start })
                    assertEquals(inputs.map { it.end }, tracks.map { it.end })
                    val rewritten = LyricsRenderer.render(decoded, config(format))
                    val reread = requireNotNull(LyricsParser.parseOrNull(rewritten))
                    assertEquals(expected, listOf(reread.original.single(), reread.translated!!.single(),
                        reread.romanization!!.single()).map { it.isWordByWord })
                }
            }
        }
    }

    @Test
    fun `word timed translation survives translation only output and word timing eligibility`() {
        val parsed = requireNotNull(PluginJsonParser(Json).parseLyrics("""
            {"type":"structured","original":[[1000,2400,"原文"]],
             "translated":[[1000,2400,[[1100,1500,"trans"],[1700,2300,"lation"]]]]}
        """.trimIndent()))
        assertTrue(parsed.isWordByWord)
        for (format in wordFormats + LyricsFormat.TTML) {
            val output = LyricsRenderer.render(parsed, config(format).copy(onlyTranslationIfAvailable = true))
            val decoded = requireNotNull(LyricsParser.parseOrNull(output))
            assertEquals(listOf(1100L, 1700L), decoded.original.single().words.map { it.start })
            assertEquals(listOf(1500L, 2300L), decoded.original.single().words.map { it.end })
        }
        val raw = LyricsRenderer.render(parsed, config(LyricsFormat.TTML))
        assertTrue(LyricsFormatConverter.convert(raw, LyricsFormat.ENHANCED_LRC,
            requireWordLevelTiming = true) is LyricsProcessingResult.Changed)
    }

    @Test
    fun `plugin word timing survives LRC rendering reading and conversion back to TTML`() {
        val input = requireNotNull(PluginJsonParser(Json).parseLyrics("""
            {"type":"structured",
             "original":[[1000,2400,[[1000,1400,"君"],[1600,2400,"へ"]]]],
             "romanization":[[1000,2400,[[1100,1500,"kimi"],[1700,2300,"e"]]]],
             "translated":[[1000,2400,"致你"]]}
        """.trimIndent()))

        for (format in wordFormats) {
            val output = LyricsRenderer.render(input, config(format))
            val decoded = requireNotNull(LyricsParser.parseOrNull(output))
            assertTiming(decoded)
            val translation = output.lines().last()
            assertEquals("[00:01.000]致你", translation)
            val converted = LyricsFormatConverter.convert(output, LyricsFormat.TTML)
                as LyricsProcessingResult.Changed
            assertTiming(requireNotNull(LyricsParser.parseOrNull(converted.text)))
        }
    }

    @Test
    fun `two LRC tracks retain timing without guessing roles from word timestamps`() {
        for (format in wordFormats) {
            val output = LyricsRenderer.render(input().copy(translated = null), config(format))
            val decoded = requireNotNull(LyricsParser.parseOrNull(output))
            assertEquals(1, decoded.original.size)
            assertEquals(listOf("kimi", "e"), decoded.translated!!.single().words.map { it.text.trim() })
            assertEquals(listOf(1100L, 1700L), decoded.translated.single().words.map { it.start })
            assertNull(decoded.romanization)
        }
    }

    @Test
    fun `line timed original does not get replaced by word timed romanization`() {
        val raw = "[00:01.000]君へ\n[00:01.000]<00:01.100>kimi <00:01.700>e<00:02.300>"
        val decoded = requireNotNull(LyricsParser.parseOrNull(raw))
        assertEquals("君へ", decoded.original.single().words.single().text)
        assertEquals(listOf(1100L, 1700L), decoded.translated!!.single().words.map { it.start })
    }

    @Test
    fun `single timed word retains its end in both LRC formats`() {
        for (format in wordFormats) {
            val output = LyricsRenderer.render(input().copy(romanization = listOf(
                LyricsLine(1000, 2400, listOf(LyricsWord(1100, 2300, "kimi")))
            )), config(format))
            val decoded = requireNotNull(LyricsParser.parseOrNull(output))
            assertEquals(2300L, decoded.romanization!!.single().words.single().end)
        }
    }

    @Test
    fun `old line timed translations and romanizations remain readable`() {
        val three = requireNotNull(LyricsParser.parseOrNull(
            "[00:01.00]君へ\n[00:01.00]kimi e\n[00:01.00]致你"))
        assertEquals("kimi e", three.romanization!!.single().words.single().text)
        assertEquals("致你", three.translated!!.single().words.single().text)
        val two = requireNotNull(LyricsParser.parseOrNull("[00:01.00]君へ\n[00:01.00]致你"))
        assertNull(two.romanization)
        assertEquals("致你", two.translated!!.single().words.single().text)
    }

    @Test
    fun `plain output joins roman words while word formats obey track switches and offsets`() {
        val plain = LyricsRenderer.render(input(), config(LyricsFormat.PLAIN_LRC))
        assertTrue(plain.contains("[00:01.000]kimi e"))
        for (format in wordFormats) {
            val output = LyricsRenderer.render(input(), config(format), offset = 250)
            val decoded = requireNotNull(LyricsParser.parseOrNull(output))
            assertEquals(listOf(1350L, 1950L), decoded.romanization!!.single().words.map { it.start })
            assertEquals(listOf(1250L, 1850L), decoded.original.single().words.map { it.start })
            val hidden = LyricsRenderer.render(input(), config(format).copy(showRomanization = false))
            assertFalse(hidden.contains("kimi"))
            val noTranslation = LyricsRenderer.render(input(), config(format).copy(showTranslation = false))
            assertFalse(noTranslation.contains("致你"))
        }
    }

    private fun assertTiming(result: LyricsResult) {
        assertEquals(listOf(1000L, 1600L), result.original.single().words.map { it.start })
        assertEquals(listOf(1400L, 2400L), result.original.single().words.map { it.end })
        val romanization = result.romanization!!.single()
        assertEquals(listOf("kimi", "e"), romanization.words.map { it.text.trim() })
        assertEquals(listOf(1100L, 1700L), romanization.words.map { it.start })
        assertEquals(listOf(1500L, 2300L), romanization.words.map { it.end })
        assertEquals("致你", result.translated!!.single().words.single().text)
    }

    private fun input() = LyricsResult(emptyMap(),
        listOf(LyricsLine(1000, 2400, listOf(LyricsWord(1000, 1400, "君"), LyricsWord(1600, 2400, "へ")))),
        listOf(LyricsLine(1000, 2400, listOf(LyricsWord(1000, 2400, "致你")))),
        listOf(LyricsLine(1000, 2400, listOf(LyricsWord(1100, 1500, "kimi"), LyricsWord(1700, 2300, "e")))))

    private fun config(format: LyricsFormat) = LyricsRenderConfig(format,
        showRomanization = true, showTranslation = true, removeEmptyLines = false)

    private val wordFormats = listOf(LyricsFormat.VERBATIM_LRC, LyricsFormat.ENHANCED_LRC)
}
