package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.isWordByWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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

            SongSearchResult(
                id = id,
                pluginId = pluginId,
                pluginName = pluginName,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                date = obj.string("date", "releaseDate", "release_date").orEmpty(),
                trackNumber = obj.string("trackNumber", "trackerNumber", "track_number").orEmpty(),
                picUrl = obj.string("picUrl", "coverUrl", "cover_url", "artworkUrl").orEmpty(),
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

        val tags = obj.stringMap("tags").orEmpty()

        val rawPlain = obj.primitiveString(
            "rawPlainLrc",
            "raw_plain_lrc",
            "plainLrc",
            "plain_lrc",
            "lrc",
            "originalLrc",
            "original_lrc"
        ).orEmpty()

        val rawOriginal = obj.primitiveString("original").orEmpty()
        val verbatim = obj.primitiveString("rawVerbatimLrc", "raw_verbatim_lrc").orEmpty()
        val enhanced = obj.primitiveString("rawEnhancedLrc", "raw_enhanced_lrc").orEmpty()
        val ttml = obj.primitiveString("rawTtml", "raw_ttml").orEmpty()
        val multiPerson = obj.primitiveString(
            "rawMultiPersonEnhancedLrc",
            "raw_multi_person_enhanced_lrc"
        ).orEmpty()

        val originalLines = obj.array("original", "lines").parseCompactWordLines()

        val translatedLines = obj.array(
            "translated",
            "translation",
            "translations"
        ).parseCompactTextLines().takeIf { it.isNotEmpty() }

        val romanizationLines = obj.array(
            "romanization",
            "romanized",
            "roma"
        ).parseCompactTextLines().takeIf { it.isNotEmpty() }

        val plain = rawPlain.ifBlank { rawOriginal }

        if (
            plain.isBlank() &&
            verbatim.isBlank() &&
            enhanced.isBlank() &&
            ttml.isBlank() &&
            multiPerson.isBlank() &&
            originalLines.isEmpty() &&
            translatedLines.isNullOrEmpty() &&
            romanizationLines.isNullOrEmpty()
        ) {
            return null
        }

        val isWordByWord =  originalLines.isWordByWord()

        return LyricsResult(
            tags = tags,
            original = originalLines,
            translated = translatedLines,
            romanization = romanizationLines,
            isWordByWord = isWordByWord,
            rawPlainLrc = plain,
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

/**
 * original 紧凑格式：
 *
 * [
 *   [lineStart, lineEnd, [[wordStart, wordEnd, text], ...]]
 * ]
 *
 * 也兼容：
 *
 * [
 *   [lineStart, lineEnd, text]
 * ]
 */
private fun JsonArray?.parseCompactWordLines(): List<LyricsLine> {
    return this?.mapNotNull { element ->
        val line = element as? JsonArray ?: return@mapNotNull null
        val start = line.longAt(0) ?: return@mapNotNull null
        val end = line.longAt(1) ?: start
        val wordsArray = line.arrayAt(2)
        val text = line.stringAt(2)

        val words = when {
            wordsArray != null -> {
                wordsArray.mapNotNull { wordElement ->
                    val word = wordElement as? JsonArray ?: return@mapNotNull null
                    val wordStart = word.longAt(0) ?: start
                    val wordEnd = word.longAt(1) ?: end
                    val wordText = word.stringAt(2).orEmpty()

                    if (wordText.isEmpty()) {
                        return@mapNotNull null
                    }

                    LyricsWord(
                        start = wordStart,
                        end = wordEnd,
                        text = wordText
                    )
                }
            }

            !text.isNullOrEmpty() -> {
                listOf(
                    LyricsWord(
                        start = start,
                        end = end,
                        text = text
                    )
                )
            }

            else -> emptyList()
        }

        if (words.isEmpty()) return@mapNotNull null

        LyricsLine(
            start = start,
            end = end,
            words = words
        )
    }.orEmpty()
}

/**
 * translated / romanization 紧凑格式：
 *
 * [
 *   [lineStart, lineEnd, text]
 * ]
 */
private fun JsonArray?.parseCompactTextLines(): List<LyricsLine> {
    return this?.mapNotNull { element ->
        val line = element as? JsonArray ?: return@mapNotNull null
        val start = line.longAt(0) ?: return@mapNotNull null
        val end = line.longAt(1) ?: start
        val text = line.stringAt(2).orEmpty()

        if (text.isBlank()) return@mapNotNull null

        LyricsLine(
            start = start,
            end = end,
            words = listOf(
                LyricsWord(
                    start = start,
                    end = end,
                    text = text
                )
            )
        )
    }.orEmpty()
}

private fun JsonArray.longAt(index: Int): Long? {
    return (getOrNull(index) as? JsonPrimitive)?.let { primitive ->
        primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
    }
}

private fun JsonArray.stringAt(index: Int): String? {
    return (getOrNull(index) as? JsonPrimitive)?.contentOrNull
}

private fun JsonArray.arrayAt(index: Int): JsonArray? {
    return getOrNull(index) as? JsonArray
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

private fun JsonObject.primitiveString(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? JsonPrimitive)?.contentOrNull
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

private inline fun <K, V, R : Any> Map<K, V>.mapValuesNotNull(
    transform: (Map.Entry<K, V>) -> R?
): Map<K, R> {
    return mapNotNull { entry ->
        transform(entry)?.let { entry.key to it }
    }.toMap()
}
