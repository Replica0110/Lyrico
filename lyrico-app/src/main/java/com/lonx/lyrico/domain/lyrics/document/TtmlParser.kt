package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
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
import org.w3c.dom.Element
import org.w3c.dom.Node

private const val ROLE_ROMAN_LEGACY = "x-romanization"

private const val ROLE_ROMAN = "x-roman"

object TtmlParser : LyricsFormatParser {
    override val format: LyricsFormat = LyricsFormat.TTML

    override fun parse(raw: String): LyricsDocument {
        return parse(TtmlXml.parse(raw))
    }

    internal fun parse(dom: org.w3c.dom.Document): LyricsDocument {
        val root = dom.documentElement
        val body = root.elementsByLocalName("body").firstOrNull()
        val (headMetadataElements, itunesMetadataElements) = parseHeadExtensionElements(root)

        val metadataTranslationTracks = parseMetadataTranslations(root)
        val metadataTransliterationTracks = parseMetadataTransliterations(root)
        val originalLines = mutableListOf<LyricsDocumentLine>()
        val inlineTranslationLines = mutableListOf<LyricsDocumentLine>()
        val romanizationLines = mutableListOf<LyricsDocumentLine>()
        val backgroundLines = mutableListOf<LyricsDocumentLine>()

        root.elementsByLocalName("p").forEach { p ->
            val start = p.attr("begin")?.let(::parseTtmlTimeMs)
            val end = p.attr("end")?.let(::parseTtmlTimeMs)
            if (start == null && end == null) return@forEach

            val role = p.attr("role", TtmlXmlNames.NS_TTM)
            val linkKey = p.attr("key", TtmlXmlNames.NS_ITUNES_INTERNAL)
                ?: p.attr("key", TtmlXmlNames.NS_ITUNES_LEGACY)
                ?: p.attr("key")
            val agentId = p.attr("agent", TtmlXmlNames.NS_TTM) ?: p.attr("agent")
            // 祖先 <div> 的 itunes:songPart（段落标注）存入行扩展，写回时按值分组重建 div（保真往返）
            val songPart = p.ancestorDivSongPart()
            // 段落时间窗：p 为所在 <div> 的首个 <p>（段首行）时携带 div 的 begin/end 存入行扩展，
            // 写回时用于重建 <div begin/end>（时间窗保真往返）；非段首行不携带
            val divTiming = p.firstPInAncestorDiv()

            val parsed = parsePText(p, start ?: 0L, end ?: start ?: 0L)
            val line = LyricsDocumentLine(
                startMs = start,
                endMs = end,
                text = parsed.originalText,
                words = parsed.words,
                linkKey = linkKey,
                agentId = agentId,
                extensions = p.attributesAsExtensions()
                    .withSongPart(songPart)
                    .withDivTiming(divTiming)
            )

            when (role) {
                "x-translation" -> inlineTranslationLines.add(line.copy(text = parsed.originalText))
                ROLE_ROMAN, ROLE_ROMAN_LEGACY -> romanizationLines.add(line.copy(text = parsed.originalText))
                "x-bg" -> backgroundLines.add(line.copy(text = parsed.originalText))
                else -> {
                    originalLines.add(line)
                    if (parsed.translationText.isNotBlank()) {
                        inlineTranslationLines.add(
                            LyricsDocumentLine(
                                startMs = start,
                                endMs = end,
                                text = parsed.translationText,
                                linkKey = linkKey
                            )
                        )
                    }
                    if (parsed.romanizationText.isNotBlank()) {
                        romanizationLines.add(
                            LyricsDocumentLine(
                                startMs = start,
                                endMs = end,
                                text = parsed.romanizationText,
                                linkKey = linkKey
                            )
                        )
                    }
                    backgroundLines.addAll(parsed.backgroundLines.map { background ->
                        background.copy(
                            startMs = background.startMs ?: start,
                            endMs = background.endMs ?: end,
                            linkKey = background.linkKey ?: linkKey
                        )
                    })
                }
            }
        }

        val tracks = buildList {
            add(LyricsTrack(type = LyricsTrackType.Original, lines = originalLines))
            addAll(metadataTranslationTracks)
            if (inlineTranslationLines.isNotEmpty() && metadataTranslationTracks.isEmpty()) {
                add(LyricsTrack(type = LyricsTrackType.Translation, lines = inlineTranslationLines))
            }
            // head sidecar <transliteration> 携带词级 <span>（逐字注音），优先于正文内联整行音译
            addAll(metadataTransliterationTracks)
            if (romanizationLines.isNotEmpty() && metadataTransliterationTracks.isEmpty()) {
                add(LyricsTrack(type = LyricsTrackType.Romanization, lines = romanizationLines))
            }
            if (backgroundLines.isNotEmpty()) {
                add(LyricsTrack(type = LyricsTrackType.Background, lines = backgroundLines))
            }
        }

        return LyricsDocument(
            metadata = LyricsMetadata(
                language = root.attr("lang", TtmlXmlNames.NS_XML),
                // 根 <tt itunes:timing="..."> 词级时间标志（如 "Word"），写回时还原（保真往返）
                timing = root.attr("timing", TtmlXmlNames.NS_ITUNES_INTERNAL)
                    ?: root.attr("timing", TtmlXmlNames.NS_ITUNES_LEGACY)
                    ?: root.attr("timing")
            ),
            agents = parseAgents(root),
            tracks = tracks,
            extensions = root.attributesAsExtensions(),
            bodyExtensions = body?.attributesAsExtensions() ?: ExtensionMap(),
            headMetadataElements = headMetadataElements,
            itunesMetadataElements = itunesMetadataElements,
            sourceFormat = LyricsFormat.TTML
        )
    }

    private fun parseHeadExtensionElements(root: Element): Pair<List<ExtensionElement>, List<ExtensionElement>> {
        val regular = mutableListOf<ExtensionElement>()
        val itunes = mutableListOf<ExtensionElement>()
        val head = root.childElementsByLocalName("head").firstOrNull() ?: return regular to itunes
        head.childElementsByLocalName("metadata").forEach { metadata ->
            metadata.childNodesList().filterIsInstance<Element>().forEach { element ->
                if (element.localName == "iTunesMetadata") {
                    element.childNodesList().filterIsInstance<Element>()
                        .filterNot { it.localName == "translations" || it.localName == "transliterations" }
                        .mapTo(itunes) { it.toExtensionElement() }
                } else if (element.localName != "agent") {
                    regular.add(element.toExtensionElement())
                }
            }
        }
        return regular to itunes
    }

    private fun parseAgents(root: Element): List<LyricsAgent> {
        return root.elementsByLocalName("agent").mapNotNull { element ->
            val id = element.attr("id", TtmlXmlNames.NS_XML) ?: element.attr("id") ?: return@mapNotNull null
            val rawType = element.attr("type")
            LyricsAgent(
                id = id,
                type = when (rawType?.lowercase()) {
                    "person" -> LyricsAgentType.Person
                    "group" -> LyricsAgentType.Group
                    "character" -> LyricsAgentType.Character
                    "organization" -> LyricsAgentType.Organization
                    "other" -> LyricsAgentType.Other
                    "narrator" -> LyricsAgentType.Narrator
                    else -> LyricsAgentType.Unknown
                },
                name = element.childElementsByLocalName("name")
                    .firstOrNull()
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: element.directTextContent().trim().takeIf { it.isNotBlank() },
                rawType = rawType,
                extensions = element.attributesAsExtensions()
            )
        }
    }

    private fun parseMetadataTranslations(root: Element): List<LyricsTrack> {
        return root.elementsByLocalName("translation").mapNotNull { translation ->
            val lines = translation.childElementsByLocalName("text").mapNotNull { text ->
                val key = text.attr("for") ?: return@mapNotNull null
                val value = normalizeTtmlText(text.textContent ?: "", trimEdges = true)
                LyricsDocumentLine(
                    text = value,
                    linkKey = key,
                    extensions = text.attributesAsExtensions()
                )
            }
            if (lines.isEmpty()) return@mapNotNull null
            LyricsTrack(
                type = LyricsTrackType.Translation,
                language = translation.attr("lang", TtmlXmlNames.NS_XML),
                lines = lines,
                extensions = translation.attributesAsExtensions()
            )
        }
    }

    /**
     * 解析 head sidecar <transliteration>（苹果/站内形态）。
     * 每个 <text for="Ln">：内含定时 <span begin/end> 时按词级（逐字注音）解析；
     * 否则退化为整行文本。和声 <span ttm:role="x-bg"> 不计入音译读音。
     */
    private fun parseMetadataTransliterations(root: Element): List<LyricsTrack> {
        return root.elementsByLocalName("transliteration").mapNotNull { transliteration ->
            val lines = transliteration.childElementsByLocalName("text").mapNotNull { text ->
                val key = text.attr("for") ?: return@mapNotNull null
                parseSidecarTextLine(text, key)
            }
            if (lines.isEmpty()) return@mapNotNull null
            LyricsTrack(
                type = LyricsTrackType.Romanization,
                language = transliteration.attr("lang", TtmlXmlNames.NS_XML),
                lines = lines,
                extensions = transliteration.attributesAsExtensions()
            )
        }
    }

    private fun parseSidecarTextLine(text: Element, key: String): LyricsDocumentLine? {
        val words = parseSidecarTimedWords(text)
        if (words.isNotEmpty()) {
            return LyricsDocumentLine(
                text = words.joinToString("") { it.text },
                words = words,
                linkKey = key,
                extensions = text.attributesAsExtensions()
            )
        }
        val whole = normalizeTtmlText(text.textContent ?: "", trimEdges = true)
        if (whole.isBlank()) return null
        return LyricsDocumentLine(
            text = whole,
            linkKey = key,
            extensions = text.attributesAsExtensions()
        )
    }

    private fun parseSidecarTimedWords(element: Element): List<LyricsDocumentWord> {
        val words = mutableListOf<LyricsDocumentWord>()
        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val el = node as Element
            val role = el.attr("role", TtmlXmlNames.NS_TTM)
            val start = el.attr("begin")?.let(::parseTtmlTimeMs)
            if (start != null && role != "x-bg" && role != "x-translation" && !role.isRomanizationRole()) {
                val value = normalizeTtmlText(el.textContent ?: "", trimEdges = false)
                if (value.isNotBlank()) {
                    words.add(
                        LyricsDocumentWord(
                            startMs = start,
                            endMs = el.attr("end")?.let(::parseTtmlTimeMs),
                            text = value,
                            extensions = el.attributesAsExtensions()
                        )
                    )
                }
            } else {
                el.childNodesList().forEach(::visit)
            }
        }
        element.childNodesList().forEach(::visit)
        return words
    }

    private data class ParsedPText(
        val originalText: String,
        val translationText: String,
        val romanizationText: String,
        val backgroundLines: List<LyricsDocumentLine>,
        val words: List<LyricsDocumentWord>
    )

    private fun parsePText(p: Element, fallbackStart: Long, fallbackEnd: Long): ParsedPText {
        val words = mutableListOf<LyricsDocumentWord>()
        val original = StringBuilder()
        val translation = StringBuilder()
        val romanization = StringBuilder()
        val background = mutableListOf<LyricsDocumentLine>()

        fun appendVisibleText(node: Node, target: StringBuilder) {
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> target.append(normalizeTtmlText(node.nodeValue ?: ""))
                Node.ELEMENT_NODE -> {
                    node.childNodesList().forEach { appendVisibleText(it, target) }
                }
            }
        }

        fun appendOriginalText(text: String) {
            val normalized = normalizeTtmlText(text)
            val isFormattingWhitespace = normalized.isBlank() && (text.contains('\n') || text.contains('\r'))
            if (normalized.isEmpty() || isFormattingWhitespace) return

            if (words.isNotEmpty()) {
                words.add(LyricsDocumentWord(text = normalized))
            } else {
                original.append(normalized)
            }
        }

        p.childNodesList().forEach { child ->
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> appendOriginalText(child.nodeValue ?: "")
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    val role = element.attr("role", TtmlXmlNames.NS_TTM)
                    val text = StringBuilder().also { appendVisibleText(element, it) }.toString()
                    val rubyWord = element.parseRubyWord(fallbackStart, fallbackEnd)
                    when {
                        rubyWord != null -> words.add(rubyWord)
                        role == "x-translation" -> translation.append(normalizeTtmlText(text, trimEdges = true))
                        role.isRomanizationRole() -> romanization.append(normalizeTtmlText(text, trimEdges = true))
                        role == "x-bg" -> background.add(
                            LyricsDocumentLine(
                                startMs = fallbackStart,
                                endMs = fallbackEnd,
                                text = normalizeTtmlText(text, trimEdges = false),
                                words = parseContentWords(element, fallbackStart, fallbackEnd),
                                extensions = element.attributesAsExtensions()
                            )
                        )
                        else -> {
                            val start = element.attr("begin")?.let(::parseTtmlTimeMs)
                            val end = element.attr("end")?.let(::parseTtmlTimeMs)
                            val parsedWords = parseContentWords(element, start ?: fallbackStart, fallbackEnd)
                            if (start != null) {
                                val normalized = normalizeTtmlText(text, trimEdges = false)
                                val isFormattingWhitespace =
                                    normalized.isBlank() && (text.contains('\n') || text.contains('\r'))
                                if (normalized.isNotEmpty() && !isFormattingWhitespace) {
                                    words.add(
                                        LyricsDocumentWord(
                                            startMs = start,
                                            endMs = end ?: fallbackEnd,
                                            text = normalized,
                                            extensions = element.attributesAsExtensions()
                                        )
                                    )
                                }
                            } else if (parsedWords.any { it.startMs != null }) {
                                words.addAll(parsedWords)
                            } else {
                                appendOriginalText(text)
                            }
                        }
                    }
                }
            }
        }

        val finalOriginal = if (words.isNotEmpty()) {
            words.joinToString("") { it.text }
        } else {
            normalizeTtmlText(original.toString(), trimEdges = true)
        }

        return ParsedPText(
            originalText = finalOriginal,
            translationText = normalizeTtmlText(translation.toString(), trimEdges = true),
            romanizationText = normalizeTtmlText(romanization.toString(), trimEdges = true),
            backgroundLines = background,
            words = words.ifEmpty {
                if (finalOriginal.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        LyricsDocumentWord(
                            startMs = fallbackStart,
                            endMs = fallbackEnd,
                            text = finalOriginal
                        )
                    )
                }
            }
        )
    }

    private fun parseContentWords(
        element: Element,
        fallbackStart: Long,
        fallbackEnd: Long
    ): List<LyricsDocumentWord> {
        val words = mutableListOf<LyricsDocumentWord>()

        fun visit(node: Node) {
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val rawText = node.nodeValue ?: ""
                    val text = normalizeTtmlText(rawText, trimEdges = false)
                    val isFormattingWhitespace = text.isBlank() && (rawText.contains('\n') || rawText.contains('\r'))
                    if (text.isNotEmpty() && !isFormattingWhitespace) {
                        words.add(LyricsDocumentWord(text = text))
                    }
                }

                Node.ELEMENT_NODE -> {
                    val child = node as Element
                    val rubyWord = child.parseRubyWord(fallbackStart, fallbackEnd)
                    if (rubyWord != null) {
                        words.add(rubyWord)
                        return
                    }
                    val start = child.attr("begin")?.let(::parseTtmlTimeMs)
                    val end = child.attr("end")?.let(::parseTtmlTimeMs)
                    if (start != null) {
                        val rawText = child.textContent ?: ""
                        val text = normalizeTtmlText(rawText, trimEdges = false)
                        val isFormattingWhitespace = text.isBlank() && (rawText.contains('\n') || rawText.contains('\r'))
                        if (text.isNotEmpty() && !isFormattingWhitespace) {
                            words.add(
                                LyricsDocumentWord(
                                    startMs = start,
                                    endMs = end ?: fallbackEnd,
                                    text = text,
                                    extensions = child.attributesAsExtensions()
                                )
                            )
                        }
                    } else {
                        child.childNodesList().forEach(::visit)
                    }
                }
            }
        }

        element.childNodesList().forEach(::visit)
        return words
    }

    private fun Element.parseRubyWord(
        fallbackStart: Long,
        fallbackEnd: Long
    ): LyricsDocumentWord? {
        if (attr("ruby", TtmlXmlNames.NS_TTS) != "container") return null

        val directSpans = childNodesList().filterIsInstance<Element>()
            .filter { it.localName == "span" }
        val descendantSpans = elementsByLocalName("span")
        val base = directSpans.firstOrNull { it.attr("ruby", TtmlXmlNames.NS_TTS) == "base" }
            ?: descendantSpans.firstOrNull { it.attr("ruby", TtmlXmlNames.NS_TTS) == "base" }
            ?: return null
        val baseText = normalizeTtmlText(base.textContent.orEmpty(), trimEdges = false)
        if (baseText.isEmpty()) return null

        val textContainer = directSpans.firstOrNull { it.attr("ruby", TtmlXmlNames.NS_TTS) == "textContainer" }
        val directAnnotationElements = textContainer?.let {
            textContainer.childNodesList().filterIsInstance<Element>()
                .filter { it.localName == "span" && it.attr("ruby", TtmlXmlNames.NS_TTS) == "text" }
        }
        val annotationElements = directAnnotationElements?.takeIf { it.isNotEmpty() }
            ?: descendantSpans.filter { it.attr("ruby", TtmlXmlNames.NS_TTS) == "text" }
        val ruby = annotationElements.mapNotNull { annotation ->
            val text = normalizeTtmlText(annotation.textContent.orEmpty(), trimEdges = false)
            text.takeIf { it.isNotEmpty() }?.let {
                LyricsDocumentRubySyllable(
                    startMs = annotation.attr("begin")?.let(::parseTtmlTimeMs),
                    endMs = annotation.attr("end")?.let(::parseTtmlTimeMs),
                    text = it
                )
            }
        }
        if (ruby.isEmpty()) return null

        val start = ruby.mapNotNull { it.startMs }.minOrNull()
            ?: attr("begin")?.let(::parseTtmlTimeMs)
            ?: base.attr("begin")?.let(::parseTtmlTimeMs)
            ?: fallbackStart
        val end = ruby.mapNotNull { it.endMs }.maxOrNull()
            ?: attr("end")?.let(::parseTtmlTimeMs)
            ?: base.attr("end")?.let(::parseTtmlTimeMs)
            ?: fallbackEnd
        return LyricsDocumentWord(
            startMs = start,
            endMs = end,
            text = baseText,
            ruby = ruby,
            extensions = attributesAsExtensions()
        )
    }
}



private fun Element.attr(localName: String, namespace: String? = null): String? {
    if (namespace != null) {
        getAttributeNS(namespace, localName).takeIf { it.isNotBlank() }?.let { return it }
    }
    getAttribute(localName).takeIf { it.isNotBlank() }?.let { return it }
    for (i in 0 until attributes.length) {
        val attr = attributes.item(i)
        if (attr.localName == localName || attr.nodeName.endsWith(":$localName")) {
            return attr.nodeValue
        }
    }
    return null
}

private fun Element.attributesAsExtensions(): ExtensionMap {
    val attributes = buildMap {
        for (i in 0 until this@attributesAsExtensions.attributes.length) {
            val attr = this@attributesAsExtensions.attributes.item(i)
            val name = attr.localName ?: attr.nodeName.substringAfter(':')
            put(
                QualifiedName(
                    namespaceUri = attr.namespaceURI,
                    localName = name,
                    prefix = attr.prefix
                ),
                attr.nodeValue
            )
        }
    }
    return ExtensionMap(attributes = attributes)
}

/** 向上找最近的 <div> 祖先并读取其 itunes:songPart 属性（嵌套 div 取最近一层；AMLL 规范 7.1 段落标注） */
private fun Element.ancestorDivSongPart(): String? {
    var node: Node? = parentNode
    while (node != null) {
        if (node is Element && node.localName == "div") {
            return node.attr(TtmlXmlNames.SONG_PART, TtmlXmlNames.NS_ITUNES_INTERNAL)
                ?: node.attr(TtmlXmlNames.SONG_PART_LEGACY, TtmlXmlNames.NS_ITUNES_INTERNAL)
                ?: node.attr(TtmlXmlNames.SONG_PART, TtmlXmlNames.NS_ITUNES_LEGACY)
                ?: node.attr(TtmlXmlNames.SONG_PART_LEGACY, TtmlXmlNames.NS_ITUNES_LEGACY)
                ?: node.attr(TtmlXmlNames.SONG_PART)
                ?: node.attr(TtmlXmlNames.SONG_PART_LEGACY)
        }
        node = node.parentNode
    }
    return null
}

/**
 * 判断 p 是否为最近 <div> 祖先内的首个 <p>（段首行）：
 * 是则返回该 div 的 begin/end 时间窗（毫秒），否则返回 null（非段首行不携带段落时间窗）。
 * 嵌套 div 时取最近一层（内层优先，外层时间窗忽略——AMLL 规范为平铺 div，嵌套极罕见）。
 */
private fun Element.firstPInAncestorDiv(): Pair<Long?, Long?>? {
    var node: Node? = parentNode
    while (node != null) {
        if (node is Element && node.localName == "div") {
            val firstP = node.elementsByLocalName("p").firstOrNull() ?: return null
            if (firstP != this) return null
            val begin = node.attr("begin")?.let(::parseTtmlTimeMs)
            val end = node.attr("end")?.let(::parseTtmlTimeMs)
            return begin to end
        }
        node = node.parentNode
    }
    return null
}

/** 行扩展追加/覆盖 itunes:songPart 属性（值为 null 时原样返回，不修改） */
private fun ExtensionMap.withSongPart(value: String?): ExtensionMap {
    if (value == null) return this
    return copy(
        attributes = attributes + (
            QualifiedName(
                namespaceUri = TtmlXmlNames.NS_ITUNES_INTERNAL,
                localName = TtmlXmlNames.SONG_PART,
                prefix = "itunes"
            ) to value
            )
    )
}

/**
 * 行扩展追加段落时间窗（段首行携带的 div begin/end，无命名空间裸 key "divBegin"/"divEnd"，
 * 与 structured 协议 Line 扩展 key 一致，写回时由 TtmlWriter 消费重建 <div begin/end>）。
 * divTiming 为 null（非段首行）或 begin/end 均无值时原样返回，不修改。
 */
private fun ExtensionMap.withDivTiming(divTiming: Pair<Long?, Long?>?): ExtensionMap {
    if (divTiming == null) return this
    var merged = attributes
    divTiming.first?.let { begin ->
        merged = merged + (QualifiedName(localName = "divBegin") to begin.toString())
    }
    divTiming.second?.let { end ->
        merged = merged + (QualifiedName(localName = "divEnd") to end.toString())
    }
    if (merged === attributes) return this
    return copy(attributes = merged)
}

private fun Element.toExtensionElement(): ExtensionElement {
    return ExtensionElement(
        name = QualifiedName(namespaceURI, localName ?: nodeName.substringAfter(':'), prefix),
        attributes = attributesAsExtensions().attributes,
        text = directTextContent().takeIf { it.isNotEmpty() },
        children = childNodesList().filterIsInstance<Element>().map { it.toExtensionElement() }
    )
}

private fun String?.isRomanizationRole(): Boolean {
    return this == ROLE_ROMAN || this == ROLE_ROMAN_LEGACY
}

private fun Element.directTextContent(): String {
    return childNodesList()
        .filter { it.nodeType == Node.TEXT_NODE || it.nodeType == Node.CDATA_SECTION_NODE }
        .joinToString("") { it.nodeValue.orEmpty() }
}

private fun Element.elementsByLocalName(localName: String): List<Element> {
    val result = mutableListOf<Element>()
    fun visit(node: Node) {
        if (node is Element && node.localName == localName) result.add(node)
        node.childNodesList().forEach(::visit)
    }
    visit(this)
    return result
}

private fun Element.childElementsByLocalName(localName: String): List<Element> {
    return childNodesList().filterIsInstance<Element>().filter { it.localName == localName }
}

private fun Node.childNodesList(): List<Node> {
    return (0 until childNodes.length).map { childNodes.item(it) }
}

private fun normalizeTtmlText(text: String, trimEdges: Boolean = false): String {
    if (!text.contains('\n') && !text.contains('\r')) {
        return if (trimEdges) text.trim() else text
    }
    val collapsed = text.replace(Regex("\\s+"), " ")
    return if (trimEdges) collapsed.trim() else collapsed
}

private fun parseTtmlTimeMs(timeStr: String): Long = TtmlTime.parseMsOrNull(timeStr) ?: 0L
