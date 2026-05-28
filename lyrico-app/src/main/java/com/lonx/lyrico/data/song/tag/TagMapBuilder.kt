package com.lonx.lyrico.data.song.tag

import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.AudioTagKeys
import java.util.Locale

class TagMapBuilder {
    fun build(
        uri: String,
        current: AudioTagData,
        mutation: AudioTagMutation
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()

        mutation.fields.forEach { (key, fieldMutation) ->
            if (key == AudioTagFieldKey.Rating) {
                applyRating(result, uri, fieldMutation)
                return@forEach
            }

            val rule = AudioTagWriteRules.ruleOf(key) ?: return@forEach
            when (fieldMutation) {
                FieldMutation.Unchanged -> Unit
                FieldMutation.Clear -> {
                    result[rule.standardKey] = ""
                    rule.aliasesToClear.forEach { alias -> result[alias] = "" }
                }
                is FieldMutation.Set -> {
                    result[rule.standardKey] = fieldMutation.value
                    rule.aliasesToClear.forEach { alias -> result[alias] = "" }
                }
            }
        }

        mutation.customFields.forEach { customMutation ->
            when (customMutation) {
                CustomTagFieldMutation.Unchanged -> Unit
                is CustomTagFieldMutation.ReplaceAll -> {
                    current.rawProperties
                        .orEmpty()
                        .keys
                        .filterNot { AudioTagKeys.isReserved(it) }
                        .forEach { key -> result.putIfAbsent(key, "") }
                    customMutation.fields.forEach { field ->
                        putCustomField(result, field.key, field.value)
                    }
                }
                is CustomTagFieldMutation.Set -> putCustomField(
                    result,
                    customMutation.key,
                    customMutation.value
                )
                is CustomTagFieldMutation.Clear -> putCustomField(result, customMutation.key, "")
            }
        }

        return result
    }

    private fun applyRating(
        result: MutableMap<String, String>,
        uri: String,
        fieldMutation: FieldMutation
    ) {
        when (fieldMutation) {
            FieldMutation.Unchanged -> Unit
            FieldMutation.Clear -> {
                result["POPM"] = ""
                result["RATING"] = ""
                result["RATE"] = ""
            }
            is FieldMutation.Set -> {
                val star = fieldMutation.value.toIntOrNull() ?: 0
                val ext = uri.substringAfterLast(".").uppercase(Locale.ROOT)
                when {
                    star in 1..5 && ext == "MP3" -> {
                        val popmVal = when (star) {
                            1 -> 1
                            2 -> 64
                            3 -> 128
                            4 -> 196
                            5 -> 255
                            else -> 0
                        }
                        result["POPM"] = "no@email|$popmVal|0"
                        result["RATING"] = ""
                        result["RATE"] = ""
                    }
                    star in 1..5 && (ext == "FLAC" || ext == "OGG") -> {
                        result["RATING"] = (star * 20).toString()
                        result["POPM"] = ""
                        result["RATE"] = ""
                    }
                    star in 1..5 -> {
                        result["RATE"] = (star * 20).toString()
                        result["RATING"] = ""
                        result["POPM"] = ""
                    }
                    else -> {
                        result["POPM"] = ""
                        result["RATING"] = ""
                        result["RATE"] = ""
                    }
                }
            }
        }
    }

    private fun putCustomField(
        result: MutableMap<String, String>,
        rawKey: String,
        rawValue: String
    ) {
        val key = rawKey.trim().uppercase(Locale.ROOT)
        if (key.isEmpty() || AudioTagKeys.isReserved(key)) return
        result[key] = rawValue.trim()
    }
}
