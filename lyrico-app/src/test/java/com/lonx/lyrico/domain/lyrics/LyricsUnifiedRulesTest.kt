package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLine
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsRubySyllable
import com.lonx.lyrico.data.lyrics.LyricsWord
import com.lonx.lyrico.data.plugin.FieldProcessRule
import com.lonx.lyrico.data.plugin.FieldScriptConversionMode
import com.lonx.lyrico.data.plugin.FollowGlobalBooleanMode
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.TtmlWriter
import com.lonx.lyrico.domain.lyrics.document.toLyricsDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import com.lonx.lyrico.plugin.source.PluginJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsUnifiedRulesTest {
    @Test
    fun `raw and structured apply identical conversion whitespace trim Ruby and romanization rules once`() {
        val structured = source()
        val raw = LyricsResult(emptyMap(), emptyList(), null, null, payloadType = LyricsPayloadType.RAW_TTML,
            rawTtml = TtmlWriter.write(structured.toLyricsDocument()))
        val rule = FieldProcessRule(scriptConversion = FieldScriptConversionMode.SIMPLIFIED,
            trim = FollowGlobalBooleanMode.ENABLED, normalizeWhitespace = FollowGlobalBooleanMode.ENABLED)
        val results = listOf(structured, raw).map { input ->
            val output = LyricsRenderer.render(input, config().copy(conversionMode = ConversionMode.SIMPLIFIED_TO_TRADITIONAL),
                fieldRule = rule)
            requireNotNull(LyricsParser.parseOrNull(output))
        }

        for (result in results) {
            assertEquals("台湾 音乐", result.original.single().words.joinToString("") { it.text })
            assertEquals("台湾 翻译", result.translated!!.single().words.joinToString("") { it.text })
            assertEquals("台 湾", result.romanization!!.single().words.joinToString("") { it.text })
            assertEquals(listOf("台", "湾"), result.original.single().words.first().ruby.map { it.text })
            assertEquals(listOf(1000L, 1500L), result.original.single().words.map { it.start })
        }
        assertEquals(results[0].original, results[1].original)
        assertEquals(" \t臺灣  ", structured.original.single().words.first().text)
    }

    @Test
    fun `field remove empty override is not undone by a second render pass using global defaults`() {
        val input = LyricsResult(emptyMap(), listOf(LyricsLine(1000, 2000,
            listOf(LyricsWord(1000, 2000, "/")))), null, null)
        assertEquals("", LyricsRenderer.render(input, config().copy(removeEmptyLines = true)))
        val kept = LyricsRenderer.render(input, config().copy(removeEmptyLines = true),
            fieldRule = FieldProcessRule(removeEmptyLines = FollowGlobalBooleanMode.DISABLED))
        assertTrue(kept.contains("/"))
    }

    @Test
    fun `continuous config changes rerender the original source without accumulating transformations`() {
        val source = source()
        val initial = LyricsRenderer.render(source, config())
        val simplified = LyricsRenderer.render(source, config().copy(conversionMode = ConversionMode.TRADITIONAL_TO_SIMPLIFIED))
        assertTrue(simplified.contains("台湾"))
        val hidden = LyricsRenderer.render(source, config().copy(showTranslation = false, showRomanization = false))
        assertFalse(hidden.contains("翻譯"))
        assertEquals(initial, LyricsRenderer.render(source, config()))
    }

    @Test
    fun `legacy untimed strings honor the same text rules and preserve exact bytes when no rules apply`() {
        val raw = " 臺灣\r\n\r\n 音樂 "
        val parsed = requireNotNull(PluginJsonParser(Json).parseLyrics(JsonPrimitive(raw).toString()))
        val config = config().copy(format = LyricsFormat.PLAIN_LRC)
        assertEquals(raw, LyricsRenderer.render(parsed, config))
        val output = LyricsRenderer.render(parsed, config.copy(removeEmptyLines = true,
            conversionMode = ConversionMode.TRADITIONAL_TO_SIMPLIFIED), fieldRule = FieldProcessRule(
            trim = FollowGlobalBooleanMode.ENABLED, normalizeWhitespace = FollowGlobalBooleanMode.ENABLED))
        assertEquals("台湾\n音乐", output)
    }

    private fun config() = LyricsRenderConfig(LyricsFormat.TTML, showRomanization = true,
        showTranslation = true, removeEmptyLines = false)

    private fun source() = LyricsResult(emptyMap(), listOf(LyricsLine(1000, 2000, listOf(
        LyricsWord(1000, 1500, " \t臺灣  ", ruby = listOf(
            LyricsRubySyllable(1000, 1250, " 臺"), LyricsRubySyllable(1250, 1500, " 灣 "))),
        LyricsWord(1500, 2000, " \t 音樂 \t ")))),
        listOf(LyricsLine(1000, 2000, listOf(LyricsWord(1000, 2000, " 臺灣  翻譯 ")))),
        listOf(LyricsLine(1000, 2000, listOf(LyricsWord(1000, 1500, " 臺"), LyricsWord(1500, 2000, " 灣 ")))))
}
