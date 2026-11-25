package com.lonx.lyrico.data.lyrics

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.lonx.lyrico.domain.lyrics.LyricsProcessor
import com.lonx.lyrico.domain.lyrics.LyricsProcessingResult

class LyricsFormatConfigTest {
    @Test
    fun `persisted columnEdits retains per song mappings and source fingerprints`() {
        val raw = """{"concurrency":2,"columnEdits":{"content://song/1":{"mapping":{"sourceCount":2,"order":[1,0]},"sourceHash":"source-fingerprint"}}}"""
        val config = Json.decodeFromString<LyricsFormatConfig>(raw)
        val edit = requireNotNull(config.legacyColumnEdits["content://song/1"])

        assertEquals(listOf(1, 0), edit.mapping.order)
        assertEquals("source-fingerprint", edit.sourceHash)
        val encoded = Json.encodeToString(config)
        assertTrue(encoded.contains("\"columnEdits\""))
        assertFalse(encoded.contains("legacyColumnEdits"))
        assertEquals(config, Json.decodeFromString<LyricsFormatConfig>(encoded))
    }

    @Test
    fun `tasks without historical per song edits use empty legacy mappings`() {
        val config = Json.decodeFromString<LyricsFormatConfig>("""{"concurrency":1}""")

        assertTrue(config.legacyColumnEdits.isEmpty())
        assertEquals(LyricsColumnMapping.identity(2), config.twoColumnMapping)
    }

    @Test
    fun `restored task flags and saved keywords determine processing without consulting current settings`() {
        val raw = """{"concurrency":1,"formatLineOrder":false,"removeTagLines":true,"tagLineKeywords":[" saved keyword "],"operation":"CONVERT"}"""
        val restored = Json.decodeFromString<LyricsFormatConfig>(raw)

        assertEquals(LyricsProcessingResult.Changed("歌词\n"), LyricsProcessor.process(
            "saved keyword\n歌词\n", restored.processingOptions()))
    }
}
