package com.lonx.lyrico.plugin.source

import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsCandidateResult
import com.lonx.lyrico.data.model.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.isWordByWord
import com.lonx.lyrico.data.model.lyrics.sanitizePluginInternal
import com.lonx.lyrico.data.model.lyrics.sanitizeStandardFields
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
        return parseSongResultItems(
            rawJson = rawJson,
            pluginId = pluginId,
            pluginName = pluginName,
            requireId = true
        )
    }

    fun parseCoverResults(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        enforceApi4Contract: Boolean = false
    ): List<SongSearchResult> {
        return parseSongResultItems(
            rawJson = rawJson,
            pluginId = pluginId,
            pluginName = pluginName,
            requireId = false,
            enforceCoverJudgmentMetadata = enforceApi4Contract
        )
    }

    private fun parseSongResultItems(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        requireId: Boolean,
        enforceCoverJudgmentMetadata: Boolean = false
    ): List<SongSearchResult> {
        val root = json.parseToJsonElement(rawJson)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("items", "results", "songs", "data") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }

        return items.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val coverUrl = obj.string("picUrl", "coverUrl", "cover_url", "artworkUrl").orEmpty()
            val id = obj.string("id", "songId", "trackId")
                ?: if (requireId) return@mapIndexedNotNull null else coverUrl.ifBlank {
                    "$pluginId:cover:$index"
                }
            val title = obj.string("title", "name", "songName").orEmpty()
            val artist = obj.string("artist", "artists", "singer").orEmpty()
            val album = obj.string("album", "albumName").orEmpty()
            val date = obj.string("year", "date", "releaseDate", "release_date").orEmpty()
            if (enforceCoverJudgmentMetadata && listOf(
                    title,
                    artist,
                    album,
                    date,
                    coverUrl
                ).any { it.isBlank() }
            ) {
                return@mapIndexedNotNull null
            }
            val duration = obj.long("duration", "durationMs", "duration_ms") ?: 0L
            val fields = obj.stringMap("fields", "metadata").orEmpty().sanitizeStandardFields()
            val internal = obj.stringMap("internal").orEmpty().sanitizePluginInternal()

            SongSearchResult(
                id = id,
                pluginId = pluginId,
                pluginName = pluginName,
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                date = date,
                trackNumber = obj.string("trackNumber", "trackerNumber", "track_number").orEmpty(),
                picUrl = coverUrl,
                fields = fields,
                internal = internal
            )
        }
    }

    fun parseLyricsCandidates(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        fallbackSong: SongSearchResult,
        enforceApi4Contract: Boolean = false
    ): List<LyricsCandidateResult> {
        val root = json.parseToJsonElement(rawJson)
        if (root is JsonNull) return emptyList()

        val candidateElements = when (root) {
            is JsonArray -> root.toList()
            is JsonObject -> root.array("items", "results", "candidates")?.toList()
                ?: listOf(root)
            else -> listOf(root)
        }

        return candidateElements.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject
            val tags = obj?.stringMap("tags").orEmpty()
            if (enforceApi4Contract) {
                val judgmentFields = listOf(
                    tags["ti"],
                    tags["ar"],
                    tags["al"],
                    tags["date"]
                )
                if (judgmentFields.any { it.isNullOrBlank() }) {
                    return@mapIndexedNotNull null
                }
            }
            val lyrics = parseLyrics(element.toString()) ?: return@mapIndexedNotNull null
            val candidateId = if (candidateElements.size == 1) {
                fallbackSong.id
            } else {
                "${fallbackSong.id}:lyrics:$index"
            }

            LyricsCandidateResult(
                song = SongSearchResult(
                    id = candidateId,
                    pluginId = pluginId,
                    pluginName = pluginName,
                    title = tags["ti"] ?: fallbackSong.title,
                    artist = tags["ar"] ?: fallbackSong.artist,
                    album = tags["al"] ?: fallbackSong.album,
                    duration = fallbackSong.duration,
                    date = tags["date"] ?: fallbackSong.date,
                    trackNumber = fallbackSong.trackNumber,
                    picUrl = fallbackSong.picUrl,
                    fields = fallbackSong.fields,
                    internal = fallbackSong.internal
                ),
                lyrics = lyrics
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
        val payloadType = obj.primitiveString("type")
            ?.toLyricsPayloadType()
            ?: LyricsPayloadType.STRUCTURED

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

        if (payloadType != LyricsPayloadType.STRUCTURED) {
            return obj.toRawLyricsResult(
                type = payloadType,
                tags = tags,
                rawPlain = rawPlain,
                rawOriginal = rawOriginal,
                rawVerbatim = verbatim,
                rawEnhanced = enhanced,
                rawTtml = ttml,
                rawMultiPerson = multiPerson
            )
        }

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

        if (originalLines.isEmpty()) {
            return null
        }

        val isWordByWord =  originalLines.isWordByWord()

        return LyricsResult(
            tags = tags,
            original = originalLines,
            translated = translatedLines,
            romanization = romanizationLines,
            payloadType = LyricsPayloadType.STRUCTURED,
            isWordByWord = isWordByWord,
        )
    }

    private fun String.toRawLyricsResult(): LyricsResult {
        return LyricsResult(
            tags = emptyMap(),
            original = emptyList(),
            translated = null,
            romanization = null,
            payloadType = LyricsPayloadType.RAW_PLAIN_LRC,
            isWordByWord = false,
            rawPlainLrc = this
        )
    }
}

private fun JsonObject.toRawLyricsResult(
    type: LyricsPayloadType,
    tags: Map<String, String>,
    rawPlain: String,
    rawOriginal: String,
    rawVerbatim: String,
    rawEnhanced: String,
    rawTtml: String,
    rawMultiPerson: String
): LyricsResult? {
    val plain = rawPlain.ifBlank { rawOriginal }
    val hasDeclaredRaw = when (type) {
        LyricsPayloadType.RAW_PLAIN_LRC -> plain.isNotBlank()
        LyricsPayloadType.RAW_VERBATIM_LRC -> rawVerbatim.isNotBlank()
        LyricsPayloadType.RAW_ENHANCED_LRC -> rawEnhanced.isNotBlank()
        LyricsPayloadType.RAW_TTML -> rawTtml.isNotBlank()
        LyricsPayloadType.RAW_MULTI_PERSON_ENHANCED_LRC -> rawMultiPerson.isNotBlank()
        LyricsPayloadType.STRUCTURED -> false
    }

    if (!hasDeclaredRaw) return null

    return LyricsResult(
        tags = tags,
        original = emptyList(),
        translated = null,
        romanization = null,
        payloadType = type,
        isWordByWord = false,
        rawPlainLrc = plain,
        rawVerbatimLrc = rawVerbatim,
        rawEnhancedLrc = rawEnhanced,
        rawTtml = rawTtml,
        rawMultiPersonEnhancedLrc = rawMultiPerson
    )
}

private fun String.toLyricsPayloadType(): LyricsPayloadType? {
    return when (trim()) {
        "structured", "STRUCTURED" -> LyricsPayloadType.STRUCTURED
        "rawPlainLrc", "raw_plain_lrc", "RAW_PLAIN_LRC", "plainLrc", "plain_lrc", "lrc" ->
            LyricsPayloadType.RAW_PLAIN_LRC
        "rawVerbatimLrc", "raw_verbatim_lrc", "RAW_VERBATIM_LRC" ->
            LyricsPayloadType.RAW_VERBATIM_LRC
        "rawEnhancedLrc", "raw_enhanced_lrc", "RAW_ENHANCED_LRC" ->
            LyricsPayloadType.RAW_ENHANCED_LRC
        "rawTtml", "raw_ttml", "RAW_TTML", "ttml" ->
            LyricsPayloadType.RAW_TTML
        "rawMultiPersonEnhancedLrc", "raw_multi_person_enhanced_lrc", "RAW_MULTI_PERSON_ENHANCED_LRC" ->
            LyricsPayloadType.RAW_MULTI_PERSON_ENHANCED_LRC
        else -> null
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
