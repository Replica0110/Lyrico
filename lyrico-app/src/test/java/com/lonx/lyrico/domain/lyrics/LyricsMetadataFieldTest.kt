package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.plugin.FieldProcessRule
import com.lonx.lyrico.data.plugin.FieldScriptConversionMode
import com.lonx.lyrico.data.plugin.FollowGlobalBooleanMode
import com.lonx.lyrico.data.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.plugin.PluginFieldPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMetadataFieldTest {
    @Test
    fun `inline lyrics returned among metadata use the same structural field policy`() {
        val processor = PluginFieldPostProcessor(GlobalFieldProcessSettings(
            scriptConversion = ConversionMode.SIMPLIFIED_TO_TRADITIONAL, removeEmptyLines = false))
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="2s">  臺灣  音樂  </p></div></body></tt>"""
        val config = PluginFieldProcessConfig("plugin", mapOf("lyrics" to FieldProcessRule(
            scriptConversion = FieldScriptConversionMode.SIMPLIFIED, trim = FollowGlobalBooleanMode.ENABLED,
            normalizeWhitespace = FollowGlobalBooleanMode.ENABLED)))
        val processed = processor.processFields("plugin", mapOf("lyrics" to raw), config, emptyList(), emptyList())
        val decoded = requireNotNull(LyricsParser.parseOrNull(processed.getValue("lyrics")))

        assertEquals("台湾 音乐", decoded.original.single().words.joinToString("") { it.text })
        assertTrue(processed.getValue("lyrics").contains("<tt"))
        assertFalse(processed.getValue("lyrics").contains("臺灣"))
    }

    @Test
    fun `inline metadata lyrics with no content remain absent rather than clearing an existing field`() {
        val processor = PluginFieldPostProcessor(GlobalFieldProcessSettings(removeEmptyLines = true))
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata><note>not lyrics</note></metadata></head><body/></tt>"""
        val processed = processor.processFields("plugin", mapOf("lyrics" to raw),
            PluginFieldProcessConfig("plugin"), emptyList(), emptyList())

        assertEquals("", processed.getValue("lyrics"))
    }
}
