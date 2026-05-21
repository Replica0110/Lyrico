package com.lonx.lyrico.plugin.source

import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsWord
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.isWordByWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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

        val tags = obj.stringMap("tags").orEmpty()

        val rawPlainLrc = obj.primitiveString(
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

        val originalLines = obj.array(
            "original",
            "originalLines",
            "original_lines",
            "lines"
        ).parseLyricsLines()

        val translatedLines = obj.array(
            "translated",
            "translation",
            "translations",
            "translatedLines",
            "translated_lines"
        ).parseLyricsLines().takeIf { it.isNotEmpty() }

        val romanizationLines = obj.array(
            "romanization",
            "romanized",
            "romanizedLines",
            "romanized_lines",
            "roma",
            "romaLines",
            "roma_lines"
        ).parseLyricsLines().takeIf { it.isNotEmpty() }

        val plain = rawPlainLrc.ifBlank { rawOriginal }

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

        val explicitWordByWord = obj.boolean("isWordByWord")
            ?: obj.boolean("is_word_by_word")
            ?: obj.boolean("wordByWord")
            ?: obj.boolean("word_by_word")

        val inferredWordByWord = if (originalLines.isNotEmpty()) {
            originalLines.isWordByWord()
        } else {
            enhanced.isNotBlank() || multiPerson.isNotBlank()
        }

        return LyricsResult(
            tags = tags,
            original = originalLines,
            translated = translatedLines,
            romanization = romanizationLines,
            isWordByWord = explicitWordByWord ?: inferredWordByWord,
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

private data class RawLyricsLine(
    val start: Long,
    val end: Long?,
    val words: List<RawLyricsWord>
)

private data class RawLyricsWord(
    val start: Long?,
    val end: Long?,
    val text: String
)

private fun JsonArray?.parseLyricsLines(): List<LyricsLine> {
    val rawLines = this
        ?.mapNotNull { element -> element.toRawLyricsLine() }
        .orEmpty()

    if (rawLines.isEmpty()) return emptyList()

    return rawLines.mapIndexed { index, line ->
        val nextLineStart = rawLines.getOrNull(index + 1)?.start
        val lineEnd = listOfNotNull(
            line.end,
            nextLineStart,
            line.words.lastOrNull()?.end
        ).firstOrNull { it >= line.start } ?: line.start

        LyricsLine(
            start = line.start,
            end = lineEnd,
            words = line.words.normalizeWords(
                lineStart = line.start,
                lineEnd = lineEnd
            )
        )
    }
}

private fun JsonElement.toRawLyricsLine(): RawLyricsLine? {
    val obj = this as? JsonObject ?: return null

    val rawWords = obj.array("words", "word", "tokens", "chars")
        ?.mapNotNull { element -> element.toRawLyricsWord(obj) }
        .orEmpty()

    val text = obj.primitiveString("text", "content", "lyric", "line").orEmpty()

    val start = obj.long("start", "startMs", "start_ms", "time", "timeMs", "time_ms")
        ?: rawWords.firstNotNullOfOrNull { it.start }
        ?: return null

    val end = obj.long("end", "endMs", "end_ms")
        ?: obj.long("duration", "durationMs", "duration_ms")?.let { start + it }
        ?: rawWords.lastOrNull()?.end

    val words = if (rawWords.isNotEmpty()) {
        rawWords
    } else if (text.isNotEmpty()) {
        listOf(
            RawLyricsWord(
                start = start,
                end = end,
                text = text
            )
        )
    } else {
        emptyList()
    }

    return RawLyricsLine(
        start = start,
        end = end,
        words = words
    )
}

private fun JsonElement.toRawLyricsWord(lineObj: JsonObject): RawLyricsWord? {
    return when (this) {
        is JsonPrimitive -> {
            val text = contentOrNull ?: return null
            RawLyricsWord(
                start = null,
                end = null,
                text = text
            )
        }

        is JsonObject -> {
            val lineStart = lineObj.long(
                "start",
                "startMs",
                "start_ms",
                "time",
                "timeMs",
                "time_ms"
            ) ?: 0L

            val start = long("start", "startMs", "start_ms", "time", "timeMs", "time_ms")
                ?: long("offset", "offsetMs", "offset_ms", "startOffset", "start_offset")
                    ?.let { lineStart + it }

            val end = long("end", "endMs", "end_ms")
                ?: long("duration", "durationMs", "duration_ms")?.let { duration ->
                    start?.plus(duration)
                }

            val text = primitiveString("text", "content", "word", "value", "lyric")
                ?: return null

            RawLyricsWord(
                start = start,
                end = end,
                text = text
            )
        }

        else -> null
    }
}

private fun List<RawLyricsWord>.normalizeWords(
    lineStart: Long,
    lineEnd: Long
): List<LyricsWord> {
    if (isEmpty()) return emptyList()

    return mapIndexed { index, word ->
        val previousWordEnd = getOrNull(index - 1)?.end
        val wordStart = word.start
            ?: previousWordEnd
            ?: lineStart

        val nextWordStart = getOrNull(index + 1)?.start
        val wordEnd = listOfNotNull(
            word.end,
            nextWordStart,
            lineEnd
        ).firstOrNull { it >= wordStart } ?: wordStart

        LyricsWord(
            start = wordStart,
            end = wordEnd,
            text = word.text
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