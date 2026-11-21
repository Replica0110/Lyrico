package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsAgentEntry
import com.lonx.lyrico.data.lyrics.LyricsLine
import com.lonx.lyrico.data.lyrics.LyricsMetadataElement
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsRubySyllable
import com.lonx.lyrico.data.lyrics.LyricsWord
import com.lonx.lyrico.data.lyrics.document.ExtensionElement
import com.lonx.lyrico.data.lyrics.document.ExtensionMap
import com.lonx.lyrico.data.lyrics.document.LyricsAgent
import com.lonx.lyrico.data.lyrics.document.LyricsAgentType
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentRubySyllable
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.lyrics.document.QualifiedName

private const val NS_TTM = "http://www.w3.org/ns/ttml#metadata"
private const val NS_ITUNES = "http://music.apple.com/lyric-ttml-internal"
private const val NS_XML = "http://www.w3.org/XML/1998/namespace"

fun LyricsResult.toLyricsDocument(): LyricsDocument {
    val tracks = buildList {
        add(
            LyricsTrack(
                type = LyricsTrackType.Original,
                lines = original.map { it.toDocumentLine() }
            )
        )
        translated?.let { lines ->
            add(
            LyricsTrack(
                type = LyricsTrackType.Translation,
                language = translatedLang.takeIf { it.isNotBlank() },
                lines = lines.map { it.toDocumentLine() }
                )
            )
        }
        romanization?.let { lines ->
            add(
            LyricsTrack(
                type = LyricsTrackType.Romanization,
                language = romanizationLang.takeIf { it.isNotBlank() },
                lines = lines.map { it.toDocumentLine() }
                )
            )
        }
    }
    return LyricsDocument(
        metadata = tags.toLyricsMetadata().copy(
            timing = timing.takeIf { it.isNotBlank() },
            language = language.takeIf { it.isNotBlank() }
        ),
        agents = agents.map { LyricsAgent(id = it.id, name = it.name, rawType = it.type) },
        tracks = tracks,
        bodyExtensions = bodyDur.takeIf { it.isNotBlank() }?.let { duration ->
            ExtensionMap(attributes = mapOf(QualifiedName(localName = "dur") to duration))
        } ?: ExtensionMap(),
        headMetadataElements = metadata.filterNot { it.name == "songwriters" }.map { it.toExtensionElement() },
        itunesMetadataElements = metadata.filter { it.name == "songwriters" }.map { it.toExtensionElement() },
        sourceFormat = null
    )
}

fun LyricsDocument.toLyricsResult(): LyricsResult {
    val originalDocumentLines = tracks
        .firstOrNull { it.type == LyricsTrackType.Original }
        ?.lines
        .orEmpty()
    val originalLines = originalDocumentLines.mapIndexedNotNull { index, line ->
        line.toLyricsLine(keepEmpty = line.visibleText().isBlank() &&
                linkedLinesForOriginal(index).any { !it.isBlankOrPlaceholder() })
    }
    fun linkedTrackLines(type: LyricsTrackType): List<LyricsLine>? {
        return tracks
            .filter { it.type == type }
            .flatMap { it.lines }
            .mapNotNull { line ->
                val fallback = line.linkedOriginalIndex(originalDocumentLines)?.let(originalDocumentLines::get)
                line.toLyricsLine(fallback)
            }
            .ifEmpty { null }
    }

    return LyricsResult(
        tags = metadata.toTags(),
        original = originalLines,
        translated = linkedTrackLines(LyricsTrackType.Translation),
        romanization = linkedTrackLines(LyricsTrackType.Romanization),
        isWordByWord = tracks.any { track -> track.lines.any { line ->
            line.words.any { it.startMs != null || it.ruby.isNotEmpty() }
        } },
        // 演唱者列表带出（document → structured）：避免文档层解析到的 <ttm:agent> 转 structured 时丢失
        agents = agents.map { agent ->
            LyricsAgentEntry(
                id = agent.id,
                type = agent.rawType ?: agent.type.toTtmlAgentType(),
                name = agent.name
            )
        },
        metadata = (headMetadataElements + itunesMetadataElements).map { it.toLyricsMetadataElement() },
        timing = metadata.timing.orEmpty(),
        language = metadata.language.orEmpty(),
        translatedLang = tracks.firstOrNull { it.type == LyricsTrackType.Translation }
            ?.language.orEmpty(),
        romanizationLang = tracks.firstOrNull { it.type == LyricsTrackType.Romanization }
            ?.language.orEmpty(),
        bodyDur = bodyExtensions.attributes.entries
            .firstOrNull { (name, _) -> name.namespaceUri == null && name.localName == "dur" }
            ?.value.orEmpty()
    )
}

private fun com.lonx.lyrico.data.lyrics.LyricsLine.toDocumentLine(): LyricsDocumentLine {
    val extensionMap = ExtensionMap(
        attributes = extensions.map { (name, value) -> name.toQualifiedName() to value }.toMap()
    )
    return LyricsDocumentLine(
        startMs = start,
        endMs = end,
        text = words.joinToString("") { it.text },
        words = words.takeIf { isWordByWord }.orEmpty().map { word ->
            LyricsDocumentWord(
                startMs = word.start,
                endMs = word.end,
                text = word.text,
                ruby = word.ruby.map { syllable ->
                    LyricsDocumentRubySyllable(
                        startMs = syllable.start,
                        endMs = syllable.end,
                        text = syllable.text
                    )
                }
            )
        },
        agentId = extensions["ttm:agent"],
        linkKey = linkKey,
        extensions = extensionMap
    )
}

private fun String.toQualifiedName(): QualifiedName {
    val prefix = substringBefore(':', "").takeIf { it.isNotEmpty() }
    val localName = if (prefix == null) this else substringAfter(':')
    val namespace = when (prefix) {
        "ttm" -> NS_TTM
        "itunes" -> NS_ITUNES
        "xml" -> NS_XML
        else -> null
    }
    return QualifiedName(namespaceUri = namespace, localName = localName, prefix = prefix)
}

private fun LyricsMetadataElement.toExtensionElement(): ExtensionElement {
    val elementName = name.toQualifiedName().let { qualified ->
        if (namespace.isNullOrBlank()) qualified else qualified.copy(namespaceUri = namespace)
    }
    return ExtensionElement(
        name = elementName,
        attributes = attributes.map { (name, value) -> name.toQualifiedName() to value }.toMap(),
        text = text,
        children = children.map { it.toExtensionElement() }
    )
}

private fun ExtensionElement.toLyricsMetadataElement(): LyricsMetadataElement {
    return LyricsMetadataElement(
        name = name.prefix?.takeIf { it.isNotBlank() }?.let { "$it:${name.localName}" } ?: name.localName,
        namespace = name.namespaceUri,
        attributes = attributes.map { (name, value) ->
            (name.prefix?.takeIf { it.isNotBlank() }?.let { "$it:${name.localName}" } ?: name.localName) to value
        }.toMap(),
        text = text,
        children = children.map { it.toLyricsMetadataElement() }
    )
}

private fun LyricsDocumentLine.toLyricsLine(
    fallback: LyricsDocumentLine? = null,
    keepEmpty: Boolean = false
): LyricsLine? {
    val start = startMs ?: fallback?.startMs ?: return null
    val end = endMs
        ?: words.lastOrNull()?.endMs
        ?: fallback?.endMs
        ?: fallback?.words?.lastOrNull()?.endMs
        ?: start
    val lineText = visibleText()
    val convertedWords = mutableListOf<LyricsWord>()
    var pendingUntimedText = ""
    words.forEach { word ->
        val wordStart = word.startMs
        if (wordStart == null) {
            if (convertedWords.isNotEmpty()) {
                val lastIndex = convertedWords.lastIndex
                val lastWord = convertedWords[lastIndex]
                convertedWords[lastIndex] = lastWord.copy(text = lastWord.text + word.text)
            } else {
                pendingUntimedText += word.text
            }
            return@forEach
        }

        val wordEnd = word.endMs ?: fallback?.endMs ?: end
        convertedWords.add(
            LyricsWord(
                start = wordStart,
                end = wordEnd,
                text = pendingUntimedText + word.text,
                ruby = word.ruby.map { syllable ->
                    LyricsRubySyllable(
                        start = syllable.startMs,
                        end = syllable.endMs,
                        text = syllable.text
                    )
                }
            )
        )
        pendingUntimedText = ""
    }

    if (pendingUntimedText.isNotEmpty() && convertedWords.isNotEmpty()) {
        val lastIndex = convertedWords.lastIndex
        val lastWord = convertedWords[lastIndex]
        convertedWords[lastIndex] = lastWord.copy(text = lastWord.text + pendingUntimedText)
    }

    val resultWords = convertedWords.ifEmpty {
            if (lineText.isBlank()) {
                emptyList()
            } else {
                listOf(
                    LyricsWord(
                        start = start,
                        end = end,
                        text = lineText
                    )
                )
            }
        }

    return if (resultWords.isEmpty() && !keepEmpty) {
        null
    } else {
        // 行级扩展属性带出（document → structured）：ttm:agent（演唱者引用）+ itunes:songPart（段落标注），
        // 避免文档层解析到的信息在转 structured 时丢失；无扩展的行保持空 Map（旧插件行为一致）
        val extensions = buildMap {
            (agentId ?: fallback?.agentId)?.let { put("ttm:agent", it) }
            extensions.attributes.entries
                .firstOrNull { it.key.localName == "song-part" || it.key.localName == "songPart" }
                ?.let { put("itunes:song-part", it.value) }
            extensions.attributes.entries
                .firstOrNull { it.key.localName == "divBegin" }
                ?.let { put("divBegin", it.value) }
            extensions.attributes.entries
                .firstOrNull { it.key.localName == "divEnd" }
                ?.let { put("divEnd", it.value) }
        }
        LyricsLine(
            start = start,
            end = end,
            words = resultWords,
            extensions = extensions,
            isWordByWord = words.any { it.startMs != null || it.ruby.isNotEmpty() },
            linkKey = linkKey
        )
    }
}

private fun Map<String, String>.toLyricsMetadata(): LyricsMetadata {
    return LyricsMetadata(
        title = this["ti"],
        artist = this["ar"],
        album = this["al"],
        offsetMs = this["offset"]?.toLongOrNull(),
        extra = filterKeys { it !in setOf("ti", "ar", "al", "offset") }
    )
}

/** document 演唱者类型 → AMLL 规范 ttm:agent type 值（Unknown 输出 null 由写回侧省略属性） */
private fun LyricsAgentType.toTtmlAgentType(): String? {
    return when (this) {
        LyricsAgentType.Person -> "person"
        LyricsAgentType.Group -> "group"
        LyricsAgentType.Character -> "character"
        LyricsAgentType.Organization -> "organization"
        LyricsAgentType.Other -> "other"
        LyricsAgentType.Narrator -> "person"
        LyricsAgentType.Unknown -> null
    }
}

private fun LyricsMetadata.toTags(): Map<String, String> {
    return buildMap {
        title?.let { put("ti", it) }
        artist?.let { put("ar", it) }
        album?.let { put("al", it) }
        offsetMs?.let { put("offset", it.toString()) }
        putAll(extra)
    }
}
