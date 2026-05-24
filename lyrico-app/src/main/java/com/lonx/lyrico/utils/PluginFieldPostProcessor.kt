package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginFieldValueType
import com.lonx.lyrico.data.model.plugin.PluginMetadataField
import com.lonx.lyrico.data.model.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.model.plugin.resolveFieldProcessRule
import com.lonx.lyrico.data.model.plugin.valueType

class PluginFieldPostProcessor(
    private val globalSettings: GlobalFieldProcessSettings
) {
    fun processFields(
        pluginId: String,
        fields: Map<String, String>,
        config: PluginFieldProcessConfig,
        fieldDefinitions: List<PluginMetadataField>,
        writeRules: List<MetadataFieldWriteRule>
    ): Map<String, String> {
        return fields.mapValues { (key, value) ->
            val writeRule = writeRules.firstOrNull {
                it.pluginId == pluginId && it.normalizedKey == key
            }
            val field = fieldDefinitions.firstOrNull { it.key == key }
            val valueType = writeRule?.target?.valueType() ?: inferValueType(key, field)
            val rule = resolveFieldProcessRule(globalSettings, resolveFieldProcessRule(config, key))

            processTextField(value, valueType, rule)
        }
    }

    fun processTextField(
        value: String,
        valueType: PluginFieldValueType,
        rule: ResolvedFieldProcessRule
    ): String {
        var text = value

        if (rule.trim && canTrim(valueType)) {
            text = text.trim()
        }
        if (rule.normalizeWhitespace && canNormalizeWhitespace(valueType)) {
            text = normalizeWhitespace(text, valueType)
        }
        if (rule.removeEmptyLines && canRemoveEmptyLines(valueType)) {
            text = removeEmptyLines(text)
        }
        if (canConvertScript(valueType)) {
            text = LyricEncoder.convertLyricsText(text, rule.scriptConversion)
        }

        return text
    }

    fun processLyrics(
        lyrics: LyricsResult,
        config: PluginFieldProcessConfig,
        fieldKey: String = "lyrics"
    ): LyricsResult {
        val rule = resolveFieldProcessRule(globalSettings, resolveFieldProcessRule(config, fieldKey))

        return lyrics.copy(
            original = processLyricLines(lyrics.original, rule),
            translated = lyrics.translated?.let { processLyricLines(it, rule) },
            romanization = lyrics.romanization?.let { processLyricLines(it, rule, convertScript = false) },
            tags = lyrics.tags.mapValues { (_, value) ->
                LyricEncoder.convertLyricsText(value, rule.scriptConversion)
            },
            rawPlainLrc = processLyricsText(lyrics.rawPlainLrc, rule),
            rawVerbatimLrc = processLyricsText(lyrics.rawVerbatimLrc, rule),
            rawEnhancedLrc = processLyricsText(lyrics.rawEnhancedLrc, rule),
            rawTtml = processLyricsText(lyrics.rawTtml, rule),
            rawMultiPersonEnhancedLrc = processLyricsText(lyrics.rawMultiPersonEnhancedLrc, rule)
        )
    }

    private fun inferValueType(key: String, field: PluginMetadataField?): PluginFieldValueType {
        return when (key) {
            "artist", "album_artist", "composer", "lyricist" -> PluginFieldValueType.PERSON_LIST
            "lyrics", "lyric" -> PluginFieldValueType.LYRICS
            "cover_url", "pic_url", "image_url" -> PluginFieldValueType.IMAGE_URL
            "track_number", "disc_number", "duration", "rating" -> PluginFieldValueType.NUMBER
            "date", "year" -> PluginFieldValueType.DATE
            else -> when (field?.type?.name) {
                "LYRICS" -> PluginFieldValueType.LYRICS
                "COVER" -> PluginFieldValueType.IMAGE_URL
                "NUMBER" -> PluginFieldValueType.NUMBER
                "DATE" -> PluginFieldValueType.DATE
                else -> PluginFieldValueType.TEXT
            }
        }
    }

    private fun processLyricLines(
        lines: List<LyricsLine>,
        rule: ResolvedFieldProcessRule,
        convertScript: Boolean = true
    ): List<LyricsLine> {
        val processed = lines.map { line ->
            line.copy(
                words = line.words.map { word ->
                    val text = processTextField(
                        value = word.text,
                        valueType = PluginFieldValueType.LYRICS,
                        rule = if (convertScript) rule else rule.copy(scriptConversion = ConversionMode.NONE)
                    )
                    word.copy(text = text)
                }
            )
        }

        return if (rule.removeEmptyLines) {
            processed.filterNot { it.words.joinToString("") { word -> word.text }.isBlank() }
        } else {
            processed
        }
    }

    private fun processLyricsText(
        value: String,
        rule: ResolvedFieldProcessRule
    ): String {
        var text = value
        if (rule.scriptConversion != ConversionMode.NONE) {
            text = LyricEncoder.convertLyricsText(text, rule.scriptConversion)
        }
        if (rule.normalizeWhitespace) {
            text = normalizeWhitespace(text, PluginFieldValueType.LYRICS)
        }
        if (rule.removeEmptyLines) {
            text = removeEmptyLines(text)
        }
        return text
    }

    private fun canTrim(valueType: PluginFieldValueType): Boolean {
        return valueType in setOf(
            PluginFieldValueType.TEXT,
            PluginFieldValueType.MULTILINE_TEXT,
            PluginFieldValueType.PERSON_LIST,
            PluginFieldValueType.LYRICS
        )
    }

    private fun canNormalizeWhitespace(valueType: PluginFieldValueType): Boolean {
        return valueType in setOf(
            PluginFieldValueType.TEXT,
            PluginFieldValueType.MULTILINE_TEXT,
            PluginFieldValueType.PERSON_LIST,
            PluginFieldValueType.LYRICS
        )
    }

    private fun canRemoveEmptyLines(valueType: PluginFieldValueType): Boolean {
        return valueType in setOf(
            PluginFieldValueType.MULTILINE_TEXT,
            PluginFieldValueType.LYRICS
        )
    }

    private fun canConvertScript(valueType: PluginFieldValueType): Boolean {
        return valueType in setOf(
            PluginFieldValueType.TEXT,
            PluginFieldValueType.MULTILINE_TEXT,
            PluginFieldValueType.PERSON_LIST,
            PluginFieldValueType.LYRICS
        )
    }

    private fun normalizeWhitespace(value: String, valueType: PluginFieldValueType): String {
        return if (valueType == PluginFieldValueType.LYRICS || valueType == PluginFieldValueType.MULTILINE_TEXT) {
            value.lineSequence()
                .joinToString("\n") { line -> line.replace(Regex("""[ \t\u00A0]+"""), " ") }
        } else {
            value.replace(Regex("""\s+"""), " ")
        }
    }

    private fun removeEmptyLines(value: String): String {
        return value.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
