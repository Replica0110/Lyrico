package com.lonx.lyrico.domain.plugin

import com.lonx.lyrico.data.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.plugin.PluginFieldValueType
import com.lonx.lyrico.data.plugin.PluginMetadataField
import com.lonx.lyrico.data.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.plugin.resolveFieldProcessRule
import com.lonx.lyrico.data.plugin.valueType
import com.lonx.lyrico.domain.lyrics.LyricsTextTransform
import com.lonx.lyrico.domain.lyrics.LyricsRenderer

class PluginFieldPostProcessor(
    private val globalSettings: GlobalFieldProcessSettings
) {
    fun processFields(
        pluginId: String,
        fields: Map<String, String>,
        config: PluginFieldProcessConfig,
        fieldDefinitions: List<PluginMetadataField>,
        writeRules: List<PluginMetadataFieldWriteRule>
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
        if (valueType == PluginFieldValueType.LYRICS) return LyricsRenderer.renderFieldText(value, rule)
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
            text = LyricsTextTransform.convertScript(text, rule.scriptConversion)
        }

        return text
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
