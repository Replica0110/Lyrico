package com.lonx.lyrico.plugin.source

import android.util.Log
import com.lonx.lyrico.data.model.lyrics.LyricsAgentEntry
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsCandidateResult
import com.lonx.lyrico.data.model.lyrics.LyricsMetadataElement
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

        // 音译支持词级（逐字注音，与 original 同构）：第三元素为词数组时逐词解析，
        // 为整行字符串时退化为整行，兼容旧插件。
        val romanizationLines = obj.array(
            "romanization",
            "romanized",
            "roma"
        ).parseCompactWordLines().takeIf { it.isNotEmpty() }

        // structured 协议扩展：演唱者列表（写回 TTML head <ttm:agent>）；旧插件不传为空
        val agents = obj.array("agents").parseAgentEntries()

        // structured 协议扩展：head 元数据元素树。
        // 三分支规则：官方 key + 官方结构 → 按规范保留；非官方 key → 原样透传；
        // 官方 key + 错误结构 → 丢弃并输出 warn 日志（见 parseMetadataElements 内部）。
        val metadata = obj.array("metadata").parseMetadataElements()

        // structured 协议扩展：根 <tt> 属性与轨语言码（写回 TTML 用）；旧插件不传为空。
        // timing = 词级时间标志（根 itunes:timing）；language = 原文语言码（根 xml:lang）；
        // translatedLang / romanizationLang = 翻译/音译轨语言码（BCP47，如 zh-Hans / zh-Latn-jyutping）
        val timing = obj.string("timing").orEmpty()
        val language = obj.string("language").orEmpty()
        val translatedLang = obj.string("translatedLang", "translated_lang").orEmpty()
        val romanizationLang = obj.string("romanizationLang", "romanization_lang").orEmpty()

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
            agents = agents,
            metadata = metadata,
            timing = timing,
            language = language,
            translatedLang = translatedLang,
            romanizationLang = romanizationLang
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
 * original / romanization 紧凑格式（词级；romanization 词级用于逐字注音）：
 *
 * [
 *   [lineStart, lineEnd, [[wordStart, wordEnd, text], ...]]
 * ]
 *
 * 也兼容整行：
 *
 * [
 *   [lineStart, lineEnd, text]
 * ]
 *
 * 第 4 元素（可选）为行级扩展属性对象，key 为带命名空间前缀的 TTML 属性名：
 *
 * [
 *   [lineStart, lineEnd, words, {"ttm:agent": "v1", "itunes:song-part": "Verse"}]
 * ]
 *
 * 属性值必须是字符串（JsonPrimitive）；非字符串值、非对象形态的第 4 元素整组忽略（不影响行本身）。
 */
private fun JsonArray?.parseCompactWordLines(): List<LyricsLine> {
    return this?.mapNotNull { element ->
        val line = element as? JsonArray ?: return@mapNotNull null
        val start = line.longAt(0) ?: return@mapNotNull null
        val end = line.longAt(1) ?: start
        val wordsArray = line.arrayAt(2)
        val text = line.stringAt(2)
        // 第 4 元素：行级扩展属性（ttm:agent / itunes:song-part 等），旧插件不传为空。
        // 前缀白名单（ttm: / itunes: / 无前缀）：其他前缀的属性无根节点命名空间声明，
        // 写出会导致 XML 非法，故解析时即过滤（静默，行本身不受影响）
        val extensions = line.objectAt(3)
            ?.parseStringMap()
            ?.filterKeys { key -> key.substringBefore(':', "").let { it == "" || it == "ttm" || it == "itunes" } }
            .orEmpty()

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
            words = words,
            extensions = extensions
        )
    }.orEmpty()
}

/**
 * translated 紧凑格式（仅整行文本；翻译无词级语义）：
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

private fun JsonArray.objectAt(index: Int): JsonObject? {
    return getOrNull(index) as? JsonObject
}

/**
 * agents 紧凑格式（对象数组；数量少，用可读性好的对象形态而不挤占紧凑空间）：
 *
 * [
 *   { "id": "v1", "type": "person", "name": "艺人A" },
 *   { "id": "v1000", "type": "group" }
 * ]
 *
 * id 必填（缺失整条丢弃）；type/name 可选。type 原样字符串透传，不做枚举映射避免丢信息。
 */
private fun JsonArray?.parseAgentEntries(): List<LyricsAgentEntry> {
    return this?.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val id = obj.primitiveString("id") ?: return@mapNotNull null
        LyricsAgentEntry(
            id = id,
            type = obj.primitiveString("type"),
            name = obj.primitiveString("name")
        )
    }.orEmpty()
}

/**
 * metadata 元素树解析 + 三分支规则（核心约定，勿改动语义）：
 *
 * 1. 官方 key + 官方结构 → 按规范保留，写回时输出到 TTML 对应位置；
 * 2. 非官方 key（官方规范中不存在的元素名）→ 原样透传保留元素树；
 * 3. 官方 key + 错误结构 → 丢弃整棵子树并输出 warn 日志（不猜插件意图、不做纠错兜底）。
 *
 * 官方 key 大小写敏感：AMLL 规范元素名全小写（songwriters / songwriter / ...），
 * camelCase 形态（如 songWriters）视为非官方 key 走透传分支，不做归一化。
 */
private const val METADATA_TAG = "PluginJsonParser"

// AMLL TTML 规范定义的 head 元素名（小写，大小写敏感）
private const val META_NAME_SONGWRITERS = "songwriters"
private const val META_NAME_SONGWRITER = "songwriter"
// 官方 key，但已有专门字段承载（translated/romanization/agents），metadata 里出现必然重复 → 丢弃
private val META_NAMES_DUPLICATED = setOf("translations", "transliterations", "ttm:agent")

private fun JsonArray?.parseMetadataElements(): List<LyricsMetadataElement> {
    return this?.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val node = obj.parseMetadataElement() ?: return@mapNotNull null
        // 三分支规则按顶层元素名分派（子元素递归解析时不重复校验，树结构由插件负责）
        when (node.name) {
            META_NAME_SONGWRITERS -> {
                // 官方 songwriters 结构：children 全部为带非空 text 的 songwriter 元素
                val valid = node.children.isNotEmpty() &&
                    node.children.all { it.name == META_NAME_SONGWRITER && !it.text.isNullOrBlank() }
                if (valid) {
                    node
                } else {
                    Log.w(
                        METADATA_TAG,
                        "metadata 元素 \"songwriters\" 结构不符合 AMLL 规范（应为 songwriters 包裹带文本的 songwriter children），已丢弃"
                    )
                    null
                }
            }

            in META_NAMES_DUPLICATED -> {
                Log.w(
                    METADATA_TAG,
                    "metadata 元素 \"${node.name}\" 已由 structured 协议专门字段承载（translated/romanization/agents），请勿在 metadata 中重复提供，已丢弃"
                )
                null
            }

            else -> node // 非官方 key：原样透传
        }
    }.orEmpty()
}

/** 单个 metadata 节点：{ name, namespace, attributes, text, children }，name 必填（缺失整节点丢弃） */
private fun JsonObject.parseMetadataElement(): LyricsMetadataElement? {
    val name = primitiveString("name")?.takeIf { it.isNotBlank() } ?: return null
    val namespace = primitiveString("namespace")
    // 带前缀的元素名（非 ttm/itunes/xml 内置前缀）必须提供 namespace URI，否则写出 XML 非法 → 丢弃该节点
    val prefix = name.substringBefore(':', "")
    if (prefix.isNotEmpty() && prefix != "ttm" && prefix != "itunes" && prefix != "xml" &&
        namespace.isNullOrBlank()
    ) {
        Log.w(METADATA_TAG, "metadata 元素 \"$name\" 带前缀但未提供 namespace URI，无法写回合法 XML，已丢弃")
        return null
    }
    val attributes = this["attributes"] as? JsonObject
    val children = this["children"] as? JsonArray
    return LyricsMetadataElement(
        name = name,
        namespace = namespace,
        attributes = attributes.parseStringMap(),
        text = primitiveString("text"),
        // children 纯解析不校验：非官方元素的树必须整体原样透传，宿主不深入子层校验/丢弃
        children = children.parseMetadataTree()
    )
}

/** 纯解析（无三分支校验）：用于 children 递归，保证透传元素树完整 */
private fun JsonArray?.parseMetadataTree(): List<LyricsMetadataElement> {
    return this?.mapNotNull { element ->
        (element as? JsonObject)?.parseMetadataElement()
    }.orEmpty()
}

/** JsonObject → Map<String, String>：仅保留字符串值（JsonPrimitive），非字符串值跳过 */
private fun JsonObject?.parseStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return mapValuesNotNull { (_, value) ->
        (value as? JsonPrimitive)?.contentOrNull
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
