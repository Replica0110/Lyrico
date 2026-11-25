package com.lonx.lyrico.data.lyrics

import com.lonx.lyrico.data.settings.decodeLyricLineOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSerializationTest {
    @Test
    fun `existing render config enum values and field names still decode after Kotlin type renaming`() {
        val raw = """{"format":"ENHANCED_LRC","showRomanization":true,"showTranslation":false,"lineOrder":["TRANSLATION","ORIGINAL","ROMANIZATION"],"removeEmptyLines":true,"conversionMode":"NONE"}"""
        val config = Json.decodeFromString<LyricsRenderConfig>(raw)

        assertEquals(LyricsFormat.ENHANCED_LRC, config.format)
        assertEquals(listOf(LyricsLineTrack.TRANSLATION, LyricsLineTrack.ORIGINAL, LyricsLineTrack.ROMANIZATION), config.lineOrder)
        assertEquals(config, Json.decodeFromString<LyricsRenderConfig>(Json.encodeToString(config)))
    }

    @Test
    fun `old stored line order names and all format enum strings remain valid`() {
        assertEquals(listOf(LyricsLineTrack.TRANSLATION, LyricsLineTrack.ORIGINAL, LyricsLineTrack.ROMANIZATION),
            decodeLyricLineOrder("TRANSLATION,ORIGINAL,ROMANIZATION"))
        for (name in listOf("PLAIN_LRC", "VERBATIM_LRC", "ENHANCED_LRC", "TTML")) {
            assertEquals(name, LyricsFormat.valueOf(name).name)
            assertEquals(LyricsFormat.valueOf(name), Json.decodeFromString<LyricsFormat>("\"$name\""))
        }
    }

    @Test
    fun `existing conversion tasks retain targetFormat and columnEdits wire keys`() {
        val raw = """{"targetFormat":"TTML","concurrency":2,"columnEdits":{"song":{"mapping":{"sourceCount":2,"order":[1,0]},"sourceHash":"hash"}}}"""
        val config = Json.decodeFromString<LyricsFormatConfig>(raw)
        val encoded = Json.parseToJsonElement(Json.encodeToString(config)).jsonObject

        assertEquals(LyricsFormat.TTML, config.targetFormat)
        assertTrue(encoded.containsKey("targetFormat"))
        assertTrue(encoded.containsKey("columnEdits"))
        assertEquals("hash", config.legacyColumnEdits.getValue("song").sourceHash)
    }
}
