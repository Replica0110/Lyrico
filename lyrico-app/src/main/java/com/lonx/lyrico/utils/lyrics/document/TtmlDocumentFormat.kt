package com.lonx.lyrico.utils.lyrics.document

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
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
        val transliterationsByKey = parseMetadataTransliterations(root)
        val originalLines = mutableListOf<LyricsDocumentLine>()
        val inlineTranslationLines = mutableListOf<LyricsDocumentLine>()
        val romanizationLines = mutableListOf<LyricsDocumentLine>()
        val backgroundLines = mutableListOf<LyricsDocumentLine>()

        root.elementsByLocalName("p").forEach { p ->
            val start = p.attr("begin")?.let(::parseTtmlTimeMs)
            val end = p.attr("end")?.let(::parseTtmlTimeMs)
            if (start == null && end == null) return@forEach

            val role = p.attr("role", NS_TTM)
            val linkKey = p.attr("key", NS_ITUNES_INTERNAL)
                ?: p.attr("key", NS_ITUNES_LEGACY)
                ?: p.attr("key")
            val agentId = p.attr("agent", NS_TTM) ?: p.attr("agent")
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
                "x-romanization" -> romanizationLines.add(line.copy(text = parsed.originalText))
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

        val metadataTranslationTracks = translationsByKey.map { (language, lines) ->
            LyricsTrack(
                type = LyricsTrackType.Translation,
                language = language,
                lines = lines
            )
        }
        val metadataTransliterationTracks = transliterationsByKey.map { (language, lines) ->
            LyricsTrack(
                type = LyricsTrackType.Romanization,
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
                language = root.attr("lang", NS_XML),
                // 根 <tt itunes:timing="..."> 词级时间标志（如 "Word"），写回时还原（保真往返）
                timing = root.attr("timing", NS_ITUNES_INTERNAL)
                    ?: root.attr("timing", NS_ITUNES_LEGACY)
                    ?: root.attr("timing")
            ),
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

    /**
     * 解析 head sidecar <transliteration>（苹果/站内形态）。
     * 每个 <text for="Ln">：内含定时 <span begin/end> 时按词级（逐字注音）解析；
     * 否则退化为整行文本。和声 <span ttm:role="x-bg"> 不计入音译读音。
     */
    private fun parseMetadataTransliterations(root: Element): Map<String?, List<LyricsDocumentLine>> {
        return root.elementsByLocalName("transliteration").map { transliteration ->
            val language = transliteration.attr("lang", NS_XML)
            val lines = transliteration.childElementsByLocalName("text").mapNotNull { text ->
                val key = text.attr("for") ?: return@mapNotNull null
                parseSidecarTextLine(text, key)
            }
            language to lines
        }.filter { (_, lines) -> lines.isNotEmpty() }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, nested) -> nested.flatten() }
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
            val role = el.attr("role", NS_TTM)
            val start = el.attr("begin")?.let(::parseTtmlTimeMs)
            if (start != null && role != "x-bg" && role != "x-translation" && role != "x-romanization") {
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
                    val role = element.attr("role", NS_TTM)
                    val text = StringBuilder().also { appendVisibleText(element, it) }.toString()
                    when (role) {
                        "x-translation" -> translation.append(normalizeTtmlText(text, trimEdges = true))
                        "x-romanization" -> romanization.append(normalizeTtmlText(text, trimEdges = true))
                        "x-bg" -> background.add(
                            LyricsDocumentLine(
                                startMs = fallbackStart,
                                endMs = fallbackEnd,
                                text = normalizeTtmlText(text, trimEdges = false),
                                words = parseContentWords(element, fallbackEnd),
                                extensions = element.attributesAsExtensions()
                            )
                        )
                        else -> {
                            val start = element.attr("begin")?.let(::parseTtmlTimeMs)
                            val end = element.attr("end")?.let(::parseTtmlTimeMs)
                            val parsedWords = parseContentWords(element, fallbackEnd)
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

    private fun parseContentWords(element: Element, fallbackEnd: Long): List<LyricsDocumentWord> {
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
}

object TtmlWriter : LyricsFormatWriter {
    override val format: LyricFormat = LyricFormat.TTML

    override fun write(document: LyricsDocument, lineOrder: List<LyricLineTrack>): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<tt xmlns=\"").append(NS_TTML).append("\"")
        builder.append(" xmlns:ttm=\"").append(NS_TTM).append("\"")
        builder.append(" xmlns:itunes=\"").append(NS_ITUNES_INTERNAL).append("\"")
        document.metadata.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
        // 根 itunes:timing 词级时间标志（如 "Word"），解析侧收集、写回时还原（保真往返）
        document.metadata.timing?.let { builder.append(" itunes:timing=\"").append(escapeXml(it)).append("\"") }
        builder.append(">\n")

        val originalLines = document.tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
        val translationLines = document.tracks
            .filter { it.type == LyricsTrackType.Translation }
            .flatMap { it.lines }
        val romanizationLines = document.tracks
            .filter { it.type == LyricsTrackType.Romanization }
            .flatMap { it.lines }
        val usedKeys = originalLines.mapNotNull { it.linkKey }.toMutableSet()
        var nextGeneratedKeyIndex = 1
        val originalLineKeys = originalLines.map { line ->
            line.linkKey ?: if (
                translationLines.any { it.startMs != null && it.startMs == line.startMs } ||
                romanizationLines.any { it.startMs != null && it.startMs == line.startMs }
            ) {
                generateLineKey(usedKeys) { nextGeneratedKeyIndex++ }
            } else null
        }

        appendHead(builder, document, originalLines, originalLineKeys)
        builder.append("  <body>\n")
        // 按行扩展中的 itunes:songPart 分组重建 <div>（AMLL 规范 7.1 段落标注）：
        // 连续相同 songPart 的行归入同一 <div itunes:songPart="...">，无 songPart 的行进默认 <div>；
        // 段首行携带 divBegin/divEnd（段落时间窗）时强制开新 div 并写入 begin/end（保真往返），
        // 两个 songPart 相同但源文件中分属不同 <div> 的段落也能正确分开
        var currentSongPart: String? = null
        var divOpen = false
        val backgroundByKey = document.linesByKey(LyricsTrackType.Background)
        val backgroundByStart = document.linesByStart(LyricsTrackType.Background)
        originalLines.forEachIndexed { index, line ->
            val backgroundLines = line.linkKey?.let { backgroundByKey[it] }
                ?: line.startMs?.let { backgroundByStart[it] }
                ?: emptyList()
            val songPart = line.songPartExtensionValue()
            val divBegin = line.divTimingExtensionValue("divBegin")
            val divEnd = line.divTimingExtensionValue("divEnd")
            if (songPart != currentSongPart || divBegin != null) {
                if (divOpen) builder.append("    </div>\n")
                builder.append("    <div")
                songPart?.let {
                    builder.append(" itunes:songPart=\"").append(escapeXml(it)).append("\"")
                }
                // 段落时间窗（段首行扩展携带的 div begin/end 毫秒值 → TTML 时间戳）
                divBegin?.let {
                    builder.append(" begin=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(it)).append("\"")
                }
                divEnd?.let {
                    builder.append(" end=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(it)).append("\"")
                }
                builder.append(">\n")
                currentSongPart = songPart
                divOpen = true
            }
            // 音译统一写入 head <transliterations> sidecar（支持词级），正文不再内联 x-romanization，避免重复
            appendOriginalLine(builder, line, originalLineKeys[index], backgroundLines)
        }
        if (divOpen) builder.append("    </div>\n")
        builder.append("  </body>\n")
        builder.append("</tt>")
        return builder.toString()
    }

    private fun appendHead(
        builder: StringBuilder,
        document: LyricsDocument,
        originalLines: List<LyricsDocumentLine>,
        originalLineKeys: List<String?>
    ) {
        val translations = document.tracks.filter { it.type == LyricsTrackType.Translation }
        val transliterations = document.tracks.filter { it.type == LyricsTrackType.Romanization }
        if (document.agents.isEmpty() && translations.isEmpty() && transliterations.isEmpty()) return

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

        if (translations.isNotEmpty() || transliterations.isNotEmpty()) {
            builder.append("    <metadata>\n")
            builder.append("      <iTunesMetadata xmlns=\"").append(NS_ITUNES_INTERNAL).append("\">\n")
            if (translations.isNotEmpty()) {
                builder.append("        <translations>\n")
                translations.forEach { track ->
                    builder.append("          <translation")
                    track.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
                    builder.append(">\n")
                    track.lines.forEach { line ->
                        val key = line.linkKey ?: originalKeyForLinkedLine(line, originalLines, originalLineKeys) ?: return@forEach
                        builder.append("            <text for=\"").append(escapeXml(key)).append("\">")
                            .append(escapeXml(line.visibleText()))
                            .append("</text>\n")
                    }
                    builder.append("          </translation>\n")
                }
                builder.append("        </translations>\n")
            }
            if (transliterations.isNotEmpty()) {
                builder.append("        <transliterations>\n")
                transliterations.forEach { track ->
                    builder.append("          <transliteration")
                    track.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
                    builder.append(">\n")
                    track.lines.forEach { line ->
                        val key = line.linkKey ?: originalKeyForLinkedLine(line, originalLines, originalLineKeys) ?: return@forEach
                        builder.append("            <text for=\"").append(escapeXml(key)).append("\">")
                        appendSidecarRomanText(builder, line)
                        builder.append("</text>\n")
                    }
                    builder.append("          </transliteration>\n")
                }
                builder.append("        </transliterations>\n")
            }
            builder.append("      </iTunesMetadata>\n")
            builder.append("    </metadata>\n")
        }
        builder.append("  </head>\n")
    }

    /** sidecar 音译文本：词级（带 begin/end 的词）输出 <span>，否则整行纯文本 */
    private fun appendSidecarRomanText(builder: StringBuilder, line: LyricsDocumentLine) {
        val timedWords = line.words.filter { it.startMs != null }
        if (timedWords.isNotEmpty()) {
            line.words.forEach { word ->
                val start = word.startMs
                val end = word.endMs
                if (start != null && end != null) {
                    builder.append("<span xmlns=\"").append(NS_TTML).append("\" begin=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(start))
                        .append("\" end=\"")
                        .append(LyricFormatter.formatTtmlTimestamp(end))
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
    }

    private fun appendOriginalLine(
        builder: StringBuilder,
        line: LyricsDocumentLine,
        lineKey: String?,
        backgroundLines: List<LyricsDocumentLine> = emptyList()
    ) {
        val start = line.startMs ?: return
        val end = line.endMs ?: line.words.lastOrNull()?.endMs ?: (start + 2000)
        builder.append("      <p begin=\"")
            .append(LyricFormatter.formatTtmlTimestamp(start))
            .append("\" end=\"")
            .append(LyricFormatter.formatTtmlTimestamp(end))
            .append("\"")
        lineKey?.let { builder.append(" itunes:key=\"").append(escapeXml(it)).append("\"") }
        line.agentId?.let { builder.append(" ttm:agent=\"").append(escapeXml(it)).append("\"") }
        // 行级扩展属性写回（保真：解析侧收集的行属性原样输出）：
        // - 排除 writer 自管的基础属性（begin/end/id/role/agent/key）与 songPart（由 div 分组承载）；
        // - 命名空间归一化：ttm/itunes 映射到根节点已声明前缀，xml 用内置前缀，
        //   根节点未声明的命名空间前缀写出会破坏 XML 结构 → 跳过
        line.extensions.attributes.forEach { (qName, value) ->
            qName.ttmlOutputName()?.let { outputName ->
                builder.append(" ").append(outputName).append("=\"")
                    .append(escapeXml(value)).append("\"")
            }
        }
        builder.append(">")

        if (line.words.size > 1) {
            line.words.forEach { word ->
                appendWord(builder, word)
            }
        } else {
            builder.append(escapeXml(line.visibleText()))
        }
        backgroundLines.forEach { backgroundLine ->
            if (backgroundLine.visibleText().isNotBlank()) {
                builder.append("<span ttm:role=\"x-bg\">")
                if (backgroundLine.words.isNotEmpty()) {
                    backgroundLine.words.forEach { word -> appendWord(builder, word) }
                } else {
                    builder.append(escapeXml(backgroundLine.visibleText()))
                }
                builder.append("</span>")
            }
        }
        builder.append("</p>\n")
    }

    private fun appendWord(builder: StringBuilder, word: LyricsDocumentWord) {
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

    private fun originalKeyForLinkedLine(
        line: LyricsDocumentLine,
        originalLines: List<LyricsDocumentLine>,
        originalLineKeys: List<String?>
    ): String? {
        line.startMs?.let { start ->
            val index = originalLines.indexOfFirst { it.startMs == start }
            if (index >= 0) return originalLineKeys[index]
        }
        return null
    }

    private fun generateLineKey(usedKeys: MutableSet<String>, nextIndex: () -> Int): String {
        while (true) {
            val key = "L${nextIndex()}"
            if (usedKeys.add(key)) return key
        }
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

private fun LyricsDocument.linesByKey(type: LyricsTrackType): Map<String, List<LyricsDocumentLine>> {
    return tracks
        .firstOrNull { it.type == type }
        ?.lines
        .orEmpty()
        .groupBy { line -> line.linkKey.orEmpty() }
        .filterKeys { it.isNotBlank() }
}

private fun LyricsDocument.linesByStart(type: LyricsTrackType): Map<Long, List<LyricsDocumentLine>> {
    return tracks
        .firstOrNull { it.type == type }
        ?.lines
        .orEmpty()
        .mapNotNull { line -> line.startMs?.let { it to line } }
        .groupBy({ it.first }, { it.second })
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
            return node.attr("songPart", NS_ITUNES_INTERNAL)
                ?: node.attr("songPart", NS_ITUNES_LEGACY)
                ?: node.attr("songPart")
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
                namespaceUri = NS_ITUNES_INTERNAL,
                localName = "songPart",
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

/** 读取行扩展中的 itunes:songPart 值（TtmlWriter 分组重建 div 用）；有 <p> 上遗留的同名属性时同样取出 */
private fun LyricsDocumentLine.songPartExtensionValue(): String? {
    return extensions.attributes.entries
        .firstOrNull { it.key.localName == "songPart" }
        ?.value?.takeIf { it.isNotBlank() }
}

/** 读取行扩展中的段落时间窗值（divBegin/divEnd 毫秒字符串，TtmlWriter 重建 <div> 时消费）；无值返回 null */
private fun LyricsDocumentLine.divTimingExtensionValue(localName: String): Long? {
    return extensions.attributes.entries
        .firstOrNull { it.key.localName == localName }
        ?.value?.toLongOrNull()
}

/**
 * 扩展属性名 → TTML 输出属性名（写回用）：
 * - 排除 writer 自管的基础属性（begin/end/id/role/agent/key）与 songPart（div 分组承载）；
 * - 排除 xmlns 命名空间声明（写出侧自行管理）；
 * - ttm/itunes 命名空间归一化到根节点已声明前缀；xml 用内置前缀；无命名空间裸输出；
 * - 根节点未声明且无法归一化的命名空间 → 返回 null（跳过，避免输出非法 XML）
 */
private fun QualifiedName.ttmlOutputName(): String? {
    if (localName == "xmlns" || prefix == "xmlns" || namespaceUri == "http://www.w3.org/2000/xmlns/") {
        return null
    }
    if (localName in TTML_WRITER_MANAGED_LOCAL_NAMES) return null
    return when (namespaceUri) {
        null -> localName
        NS_TTM -> "ttm:$localName"
        NS_ITUNES_INTERNAL, NS_ITUNES_LEGACY -> "itunes:$localName"
        NS_XML -> "xml:$localName"
        else -> null
    }
}

// TtmlWriter 自管的基础属性名（解析侧全量收集进扩展，写回时由 writer 按标准位置输出，扩展侧跳过避免重复；
// divBegin/divEnd 段落时间窗由 div 分组逻辑消费重建 <div begin/end>，不输出到 <p>）
private val TTML_WRITER_MANAGED_LOCAL_NAMES = setOf(
    "begin", "end", "id", "role", "agent", "key", "songPart", "divBegin", "divEnd"
)

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
