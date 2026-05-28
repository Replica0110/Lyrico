package com.lonx.lyrico.utils.lyrics.document

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.document.ExtensionMap
import com.lonx.lyrico.data.model.lyrics.document.LyricsAgent
import com.lonx.lyrico.data.model.lyrics.document.LyricsAgentType
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.model.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.model.lyrics.document.QualifiedName
import com.lonx.lyrico.utils.LyricFormatter
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private const val NS_TTML = "http://www.w3.org/ns/ttml"
private const val NS_TTM = "http://www.w3.org/ns/ttml#metadata"
private const val NS_ITUNES_INTERNAL = "http://music.apple.com/lyric-ttml-internal"
private const val NS_ITUNES_LEGACY = "http://music.apple.com/itunes/ttml"
private const val NS_XML = "http://www.w3.org/XML/1998/namespace"

object TtmlParser : LyricsFormatParser {
    override val format: LyricFormat = LyricFormat.TTML

    override fun parse(raw: String): LyricsDocument {
        val dom = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isIgnoringComments = false
        }.newDocumentBuilder().parse(InputSource(StringReader(raw)))
        val root = dom.documentElement

        val translationsByKey = parseMetadataTranslations(root)
        val originalLines = mutableListOf<LyricsDocumentLine>()
        val inlineTranslationLines = mutableListOf<LyricsDocumentLine>()
        val romanizationLines = mutableListOf<LyricsDocumentLine>()

        root.elementsByLocalName("p").forEach { p ->
            val start = p.attr("begin")?.let(::parseTtmlTimeMs)
            val end = p.attr("end")?.let(::parseTtmlTimeMs)
            if (start == null && end == null) return@forEach

            val role = p.attr("role", NS_TTM)
            val linkKey = p.attr("key", NS_ITUNES_INTERNAL)
                ?: p.attr("key", NS_ITUNES_LEGACY)
                ?: p.attr("key")
            val agentId = p.attr("agent", NS_TTM) ?: p.attr("agent")

            val parsed = parsePText(p, start ?: 0L, end ?: start ?: 0L)
            val line = LyricsDocumentLine(
                startMs = start,
                endMs = end,
                text = parsed.originalText,
                words = parsed.words,
                linkKey = linkKey,
                agentId = agentId,
                extensions = p.attributesAsExtensions()
            )

            when (role) {
                "x-translation" -> inlineTranslationLines.add(line.copy(text = parsed.originalText))
                "x-romanization" -> romanizationLines.add(line.copy(text = parsed.originalText))
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
                }
            }
        }

        val metadataTranslationTracks = translationsByKey.map { (language, lines) ->
            LyricsTrack(
                type = LyricsTrackType.Translation,
                language = language,
                lines = lines
            )
        }

        val tracks = buildList {
            add(LyricsTrack(type = LyricsTrackType.Original, lines = originalLines))
            addAll(metadataTranslationTracks)
            if (inlineTranslationLines.isNotEmpty() && metadataTranslationTracks.isEmpty()) {
                add(LyricsTrack(type = LyricsTrackType.Translation, lines = inlineTranslationLines))
            }
            if (romanizationLines.isNotEmpty()) {
                add(LyricsTrack(type = LyricsTrackType.Romanization, lines = romanizationLines))
            }
        }

        return LyricsDocument(
            metadata = LyricsMetadata(language = root.attr("lang", NS_XML)),
            agents = parseAgents(root),
            tracks = tracks,
            extensions = root.attributesAsExtensions(),
            sourceFormat = LyricFormat.TTML
        )
    }

    private fun parseAgents(root: Element): List<LyricsAgent> {
        return root.elementsByLocalName("agent").mapNotNull { element ->
            val id = element.attr("id", NS_XML) ?: element.attr("id") ?: return@mapNotNull null
            LyricsAgent(
                id = id,
                type = when (element.attr("type")?.lowercase()) {
                    "person" -> LyricsAgentType.Person
                    "group" -> LyricsAgentType.Group
                    "character" -> LyricsAgentType.Character
                    "narrator" -> LyricsAgentType.Narrator
                    else -> LyricsAgentType.Unknown
                },
                name = element.textContent?.takeIf { it.isNotBlank() },
                extensions = element.attributesAsExtensions()
            )
        }
    }

    private fun parseMetadataTranslations(root: Element): Map<String?, List<LyricsDocumentLine>> {
        return root.elementsByLocalName("translation").map { translation ->
            val language = translation.attr("lang", NS_XML)
            val lines = translation.childElementsByLocalName("text").mapNotNull { text ->
                val key = text.attr("for") ?: return@mapNotNull null
                val value = normalizeTtmlText(text.textContent ?: "", trimEdges = true)
                LyricsDocumentLine(
                    text = value,
                    linkKey = key,
                    extensions = text.attributesAsExtensions()
                )
            }
            language to lines
        }.filter { (_, lines) -> lines.isNotEmpty() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, nested) -> nested.flatten() }
    }

    private data class ParsedPText(
        val originalText: String,
        val translationText: String,
        val romanizationText: String,
        val words: List<LyricsDocumentWord>
    )

    private fun parsePText(p: Element, fallbackStart: Long, fallbackEnd: Long): ParsedPText {
        val words = mutableListOf<LyricsDocumentWord>()
        val original = StringBuilder()
        val translation = StringBuilder()
        val romanization = StringBuilder()

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
            if (words.isNotEmpty()) {
                if (normalized.isBlank() && (text.contains('\n') || text.contains('\r'))) return

                val lastIndex = words.lastIndex
                val lastWord = words[lastIndex]
                words[lastIndex] = lastWord.copy(text = lastWord.text + normalized)
            } else {
                original.append(normalized)
            }
        }

        p.childNodesList().forEach { child ->
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> appendOriginalText(child.nodeValue ?: "")
                Node.ELEMENT_NODE -> {
                    val element = child as Element
                    val role = element.attr("role", NS_TTM)
                    val text = StringBuilder().also { appendVisibleText(element, it) }.toString()
                    when (role) {
                        "x-translation" -> translation.append(normalizeTtmlText(text, trimEdges = true))
                        "x-romanization" -> romanization.append(normalizeTtmlText(text, trimEdges = true))
                        else -> {
                            val start = element.attr("begin")?.let(::parseTtmlTimeMs)
                            val end = element.attr("end")?.let(::parseTtmlTimeMs)
                            val normalized = normalizeTtmlText(text, trimEdges = true)
                            if (start != null && normalized.isNotEmpty()) {
                                words.add(
                                    LyricsDocumentWord(
                                        startMs = start,
                                        endMs = end ?: fallbackEnd,
                                        text = normalized,
                                        extensions = element.attributesAsExtensions()
                                    )
                                )
                            }
                            if (start == null) appendOriginalText(normalized)
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
}

object TtmlWriter : LyricsFormatWriter {
    override val format: LyricFormat = LyricFormat.TTML

    override fun write(document: LyricsDocument): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<tt xmlns=\"").append(NS_TTML).append("\"")
        builder.append(" xmlns:ttm=\"").append(NS_TTM).append("\"")
        builder.append(" xmlns:itunes=\"").append(NS_ITUNES_INTERNAL).append("\"")
        document.metadata.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
        builder.append(">\n")

        appendHead(builder, document)
        builder.append("  <body>\n")
        builder.append("    <div>\n")
        document.tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty().forEach { line ->
            appendOriginalLine(builder, line)
        }
        builder.append("    </div>\n")
        builder.append("  </body>\n")
        builder.append("</tt>")
        return builder.toString()
    }

    private fun appendHead(builder: StringBuilder, document: LyricsDocument) {
        val translations = document.tracks.filter { it.type == LyricsTrackType.Translation }
        if (document.agents.isEmpty() && translations.isEmpty()) return

        builder.append("  <head>\n")
        if (document.agents.isNotEmpty()) {
            builder.append("    <metadata>\n")
            document.agents.forEach { agent ->
                builder.append("      <ttm:agent xml:id=\"").append(escapeXml(agent.id)).append("\"")
                val type = agent.type.toTtmlType()
                if (type != null) builder.append(" type=\"").append(type).append("\"")
                if (agent.name.isNullOrBlank()) {
                    builder.append("/>\n")
                } else {
                    builder.append(">").append(escapeXml(agent.name)).append("</ttm:agent>\n")
                }
            }
            builder.append("    </metadata>\n")
        }

        if (translations.isNotEmpty()) {
            builder.append("    <metadata>\n")
            builder.append("      <iTunesMetadata xmlns=\"").append(NS_ITUNES_INTERNAL).append("\">\n")
            builder.append("        <translations>\n")
            translations.forEach { track ->
                builder.append("          <translation")
                track.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
                builder.append(">\n")
                track.lines.forEach { line ->
                    val key = line.linkKey ?: return@forEach
                    builder.append("            <text for=\"").append(escapeXml(key)).append("\">")
                        .append(escapeXml(line.visibleText()))
                        .append("</text>\n")
                }
                builder.append("          </translation>\n")
            }
            builder.append("        </translations>\n")
            builder.append("      </iTunesMetadata>\n")
            builder.append("    </metadata>\n")
        }
        builder.append("  </head>\n")
    }

    private fun appendOriginalLine(builder: StringBuilder, line: LyricsDocumentLine) {
        val start = line.startMs ?: return
        val end = line.endMs ?: line.words.lastOrNull()?.endMs ?: (start + 2000)
        builder.append("      <p begin=\"")
            .append(LyricFormatter.formatTtmlTimestamp(start))
            .append("\" end=\"")
            .append(LyricFormatter.formatTtmlTimestamp(end))
            .append("\"")
        line.linkKey?.let { builder.append(" itunes:key=\"").append(escapeXml(it)).append("\"") }
        line.agentId?.let { builder.append(" ttm:agent=\"").append(escapeXml(it)).append("\"") }
        builder.append(">")

        if (line.words.size > 1) {
            line.words.forEach { word ->
                val wordStart = word.startMs
                val wordEnd = word.endMs
                if (wordStart != null && wordEnd != null) {
                    builder.append("<span begin=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(wordStart))
                        .append("\" end=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(wordEnd))
                        .append("\">")
                        .append(escapeXml(word.text))
                        .append("</span>")
                } else {
                    builder.append(escapeXml(word.text))
                }
            }
        } else {
            builder.append(escapeXml(line.visibleText()))
        }
        builder.append("</p>\n")
    }

    private fun LyricsAgentType.toTtmlType(): String? {
        return when (this) {
            LyricsAgentType.Person -> "person"
            LyricsAgentType.Group -> "group"
            LyricsAgentType.Character -> "character"
            LyricsAgentType.Narrator -> "person"
            LyricsAgentType.Unknown -> null
        }
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

private fun parseTtmlTimeMs(timeStr: String): Long {
    val text = timeStr.trim()
    Regex("""^(\d+(?:\.\d+)?)ms$""").matchEntire(text)?.let {
        return it.groupValues[1].toDouble().toLong()
    }
    Regex("""^(\d+(?:\.\d+)?)s$""").matchEntire(text)?.let {
        return (it.groupValues[1].toDouble() * 1000).toLong()
    }
    Regex("""^(\d+(?:\.\d+)?)$""").matchEntire(text)?.let {
        return (it.groupValues[1].toDouble() * 1000).toLong()
    }
    Regex("""^(\d+):(\d{2}):(\d{2})(?:\.(\d+))?$""").matchEntire(text)?.let { match ->
        val fraction = match.groupValues[4]
        val ms = if (fraction.isBlank()) 0L else fraction.padEnd(3, '0').take(3).toLong()
        return (match.groupValues[1].toLong() * 3600 + match.groupValues[2].toLong() * 60 + match.groupValues[3].toLong()) * 1000 + ms
    }
    Regex("""^(\d+):(\d{2})(?:\.(\d+))?$""").matchEntire(text)?.let { match ->
        val fraction = match.groupValues[3]
        val ms = if (fraction.isBlank()) 0L else fraction.padEnd(3, '0').take(3).toLong()
        return (match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()) * 1000 + ms
    }
    return 0L
}

private fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
