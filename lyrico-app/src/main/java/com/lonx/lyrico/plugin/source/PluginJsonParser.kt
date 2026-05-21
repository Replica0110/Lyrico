package com.lonx.lyrico.plugin.source

import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SongSearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class PluginJsonParser(
    private val json: Json
) {
    fun parseSongResults(
        rawJson: String,
        pluginId: String,
        pluginName: String
    ): List<SongSearchResult> {
        val root = json.parseToJsonElement(rawJson)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("items", "results", "songs", "data") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        return items.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("id", "songId", "trackId") ?: return@mapNotNull null
            val title = obj.string("title", "name", "songName").orEmpty()
            val artist = obj.string("artist", "artists", "singer").orEmpty()
            val album = obj.string("album", "albumName").orEmpty()
            val duration = obj.long("duration", "durationMs", "duration_ms") ?: 0L
            val fields = obj.stringMap("fields", "metadata").orEmpty()
            val extras = obj.stringMap("extras").orEmpty()

            SongSearchResult(
                id = id,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                date = obj.string("date", "releaseDate", "release_date").orEmpty(),
                trackerNumber = obj.string("trackNumber", "trackerNumber", "track_number").orEmpty(),
                picUrl = obj.string("picUrl", "coverUrl", "cover_url", "artworkUrl").orEmpty(),
                extras = extras,
                pluginId = pluginId,
                pluginName = pluginName,
                fields = fields
            )
        }
    }

    fun parseLyrics(rawJson: String): LyricsResult? {
        val root = json.parseToJsonElement(rawJson)
        if (root is JsonNull) return null

        if (root is JsonPrimitive) {
            val lrc = root.contentOrNull.orEmpty()
            return lrc.takeIf { it.isNotBlank() }?.toRawLyricsResult()
        }

        val obj = root as? JsonObject ?: return null
        if (obj.boolean("notFound") == true) return null

        val original = obj.string("original", "lrc", "rawPlainLrc", "raw_plain_lrc").orEmpty()
        val verbatim = obj.string("rawVerbatimLrc", "raw_verbatim_lrc").orEmpty()
        val enhanced = obj.string("rawEnhancedLrc", "raw_enhanced_lrc").orEmpty()
        val ttml = obj.string("rawTtml", "raw_ttml").orEmpty()
        val multiPerson = obj.string("rawMultiPersonEnhancedLrc", "raw_multi_person_enhanced_lrc").orEmpty()
        val tags = obj.stringMap("tags").orEmpty()

        if (
            original.isBlank() &&
            verbatim.isBlank() &&
            enhanced.isBlank() &&
            ttml.isBlank() &&
            multiPerson.isBlank()
        ) {
            return null
        }

        return LyricsResult(
            tags = tags,
            original = emptyList(),
            translated = null,
            romanization = null,
            isWordByWord = false,
            rawPlainLrc = original,
            rawVerbatimLrc = verbatim,
            rawEnhancedLrc = enhanced,
            rawTtml = ttml,
            rawMultiPersonEnhancedLrc = multiPerson
        )
    }

    private fun String.toRawLyricsResult(): LyricsResult {
        return LyricsResult(
            tags = emptyMap(),
            original = emptyList(),
            translated = null,
            romanization = null,
            isWordByWord = false,
            rawPlainLrc = this
        )
    }
}

private fun JsonObject.string(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        val value = this[key] ?: return@firstNotNullOfOrNull null
        when (value) {
            is JsonPrimitive -> value.contentOrNull
            is JsonArray -> value.joinToString("/") { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull.orEmpty()
                    is JsonObject -> item.string("name", "title", "value").orEmpty()
                    else -> ""
                }
            }.takeIf { it.isNotBlank() }
            else -> null
        }
    }
}

private fun JsonObject.long(vararg keys: String): Long? {
    return keys.firstNotNullOfOrNull { key ->
        val value = this[key] ?: return@firstNotNullOfOrNull null
        when (value) {
            is JsonPrimitive -> value.longOrNull ?: value.contentOrNull?.toLongOrNull()
            else -> null
        }
    }
}

private fun JsonObject.boolean(key: String): Boolean? {
    return (this[key] as? JsonPrimitive)?.booleanOrNull
}

private fun JsonObject.array(vararg keys: String): JsonArray? {
    return keys.firstNotNullOfOrNull { key ->
        this[key] as? JsonArray
    }
}

private fun JsonObject.stringMap(vararg keys: String): Map<String, String>? {
    val obj = keys.firstNotNullOfOrNull { key ->
        this[key] as? JsonObject
    } ?: return null

    return obj.mapValuesNotNull { (_, value) ->
        when (value) {
            is JsonPrimitive -> value.contentOrNull
            else -> value.toString()
        }
    }
}

private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(transform: (Map.Entry<K, V>) -> R?): Map<K, R> {
    return mapNotNull { entry ->
        transform(entry)?.let { entry.key to it }
    }.toMap()
}
