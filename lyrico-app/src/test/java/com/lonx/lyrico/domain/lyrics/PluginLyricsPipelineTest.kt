package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.plugin.FieldProcessRule
import com.lonx.lyrico.data.plugin.FieldScriptConversionMode
import com.lonx.lyrico.data.song.search.SongSearchResult
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.plugin.source.PluginJsonParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginLyricsPipelineTest {
    private val parser = PluginJsonParser(Json)

    @Test
    fun `all declared raw payloads survive parsing processing and target encoding`() {
        val cases = listOf(
            RawCase("rawPlainLrc", "rawPlainLrc", "[00:01.00]原文", LyricsFormat.PLAIN_LRC),
            RawCase("rawVerbatimLrc", "rawVerbatimLrc", "[00:01.00]原[00:01.50]文[00:02.00]", LyricsFormat.VERBATIM_LRC),
            RawCase("rawEnhancedLrc", "rawEnhancedLrc", "[00:01.00]<00:01.00>原<00:01.50>文<00:02.00>", LyricsFormat.ENHANCED_LRC),
            RawCase("rawTtml", "rawTtml", ttml("原文"), LyricsFormat.TTML),
            RawCase("rawMultiPersonEnhancedLrc", "rawMultiPersonEnhancedLrc", "[00:01.00]<00:01.00>原<00:01.50>文<00:02.00>", LyricsFormat.ENHANCED_LRC)
        )

        for (case in cases) {
            val input = JsonObject(mapOf("type" to JsonPrimitive(case.type), case.field to JsonPrimitive(case.raw)))
            val result = requireNotNull(parser.parseLyrics(input.toString()))
            val output = render(result, config(case.target))
            val decoded = requireNotNull(LyricsParser.parseOrNull(output)) { "Cannot decode ${case.type}: $output" }

            assertEquals(case.type, "原文", decoded.original.single().words.joinToString("") { it.text })
            assertEquals(case.type, 1000L, decoded.original.single().start)
            assertTrue(case.type, result.original.isEmpty())
        }
    }

    @Test
    fun `structured compact words retain timing and optional tracks through the full chain`() {
        val result = requireNotNull(parser.parseLyrics(STRUCTURED))
        val encoded = render(result, config(LyricsFormat.TTML))
        val decoded = requireNotNull(LyricsParser.parseOrNull(encoded))

        assertEquals(LyricsPayloadType.STRUCTURED, result.payloadType)
        assertEquals(listOf(1000L, 1500L), decoded.original.single().words.map { it.start })
        assertEquals("Hello", decoded.translated!!.single().words.joinToString("") { it.text })
        val romanization = requireNotNull(decoded.romanization).single()
        assertEquals(listOf("ni", "hao"), romanization.words.map { it.text })
        assertEquals(listOf(1000L, 1500L), romanization.words.map { it.start })
    }

    @Test
    fun `legacy string return is parsed and rendered as plain raw lyrics`() {
        val result = requireNotNull(parser.parseLyrics(JsonPrimitive("[00:01.00]旧歌词").toString()))

        assertEquals(LyricsPayloadType.RAW_PLAIN_LRC, result.payloadType)
        assertTrue(render(result, config()).contains("旧歌词"))
    }

    @Test
    fun `object without type retains the structured compatibility contract`() {
        val result = requireNotNull(parser.parseLyrics(STRUCTURED.replace("\"type\":\"structured\",", "")))

        assertEquals(LyricsPayloadType.STRUCTURED, result.payloadType)
        assertTrue(render(result, config()).contains("你好"))
        assertNull(parser.parseLyrics("""{"rawPlainLrc":"[00:01.00]not structured"}"""))
    }

    @Test
    fun `missing declared raw content is rejected instead of using a different field`() {
        assertNull(parser.parseLyrics("""{"type":"rawTtml","rawPlainLrc":"[00:01.00]different payload"}"""))
        assertNull(parser.parseLyrics("""{"type":"structured","original":[]}"""))
        assertNull(parser.parseLyrics("null"))
        assertNull(parser.parseLyrics("""{"notFound":true}"""))
    }

    @Test
    fun `API4 candidate metadata is enforced while legacy single results use the request song`() {
        val song = SongSearchResult(id = "song", pluginId = "plugin", pluginName = "Plugin", title = "Requested")
        val raw = JsonPrimitive("[00:01.00]歌词").toString()

        assertTrue(parser.parseLyricsCandidates(raw, "plugin", "Plugin", song, enforceApi4Contract = true).isEmpty())
        val legacy = parser.parseLyricsCandidates(raw, "plugin", "Plugin", song).single()
        assertEquals("Requested", legacy.song.title)
        assertTrue(render(legacy.lyrics, config()).contains("歌词"))

        val declared = JsonObject(mapOf(
            "type" to JsonPrimitive("rawPlainLrc"),
            "rawPlainLrc" to JsonPrimitive("[00:01.00]歌词"),
            "tags" to JsonObject(mapOf("ti" to JsonPrimitive("Title"), "ar" to JsonPrimitive("Artist"),
                "al" to JsonPrimitive("Album"), "date" to JsonPrimitive("2026")))
        ))
        assertEquals("Title", parser.parseLyricsCandidates("[$declared]", "plugin", "Plugin", song, true).single().song.title)
    }

    @Test
    fun `matching target raw format takes precedence over richer alternative sources`() {
        val raw = rawAlternatives().copy(rawPlainLrc = "[00:01.00]plain")
        val encoded = render(raw, config(LyricsFormat.PLAIN_LRC))

        assertTrue(encoded.contains("plain"))
        assertFalse(encoded.contains("ttml"))
        assertFalse(encoded.contains("enhanced"))
    }

    @Test
    fun `cross format rendering prefers TTML for translation and enhanced LRC otherwise`() {
        val raw = rawAlternatives()
        val withTranslation = render(raw, config(LyricsFormat.PLAIN_LRC))
        val withoutTranslation = render(raw, config(LyricsFormat.PLAIN_LRC).copy(showTranslation = false))

        assertTrue(withTranslation.contains("ttml"))
        assertFalse(withTranslation.contains("enhanced"))
        assertTrue(withoutTranslation.contains("enhanced"))
        assertFalse(withoutTranslation.contains("verbatim"))
    }

    @Test
    fun `rerendering original results restores tracks and does not mutate cached input`() {
        val original = requireNotNull(parser.parseLyrics(STRUCTURED))
        val all = render(original, config())
        val filtered = render(original, config().copy(showTranslation = false, showRomanization = false))
        val restored = render(original, config())

        assertTrue(all.contains("Hello"))
        assertFalse(filtered.contains("Hello"))
        assertEquals(all, restored)
        assertEquals("Hello", original.translated!!.single().words.single().text)
    }

    @Test
    fun `field conversion override is resolved once without callers clearing the render configuration`() {
        val raw = requireNotNull(parser.parseLyrics("""{"type":"structured","original":[[1000,2000,"臺灣"]]}"""))
        val encoded = LyricsRenderer.render(raw,
            config().copy(conversionMode = ConversionMode.SIMPLIFIED_TO_TRADITIONAL),
            fieldRule = FieldProcessRule(scriptConversion = FieldScriptConversionMode.SIMPLIFIED))

        assertTrue(encoded.contains("台湾"))
        assertEquals("臺灣", raw.original.single().words.single().text)
    }

    @Test
    fun `direct encoder preserves an unchanged matching raw document without parsing opaque content`() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><head><opaque keep="yes"/></head><body><div><p begin="1s" end="2s">text</p></div></body></tt>"""
        val result = LyricsResult(emptyMap(), emptyList(), null, null,
            payloadType = LyricsPayloadType.RAW_TTML, rawTtml = raw)

        // Covers the encoder's direct path; field post-processing may rewrite this document earlier.
        assertEquals(raw, LyricsRenderer.render(result, config(LyricsFormat.TTML)))
    }

    private fun render(result: LyricsResult, config: LyricsRenderConfig): String = LyricsRenderer.render(result, config)

    private fun rawAlternatives() = LyricsResult(
        tags = emptyMap(), original = emptyList(), translated = null, romanization = null,
        payloadType = LyricsPayloadType.RAW_ENHANCED_LRC,
        rawTtml = ttml("ttml"), rawEnhancedLrc = "[00:01.00]<00:01.00>enhanced<00:02.00>",
        rawVerbatimLrc = "[00:01.00]verbatim[00:02.00]"
    )

    private fun config(format: LyricsFormat = LyricsFormat.PLAIN_LRC) = LyricsRenderConfig(
        format = format, showRomanization = true, showTranslation = true,
        removeEmptyLines = false, conversionMode = ConversionMode.NONE
    )

    private fun ttml(text: String) = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">$text</p></div></body></tt>"""

    private data class RawCase(val type: String, val field: String, val raw: String, val target: LyricsFormat)

    private companion object {
        const val STRUCTURED = """{"type":"structured","original":[[1000,2000,[[1000,1500,"你"],[1500,2000,"好"]]]],"translated":[[1000,2000,"Hello"]],"romanization":[[1000,2000,[[1000,1500,"ni"],[1500,2000,"hao"]]]]}"""
    }
}
