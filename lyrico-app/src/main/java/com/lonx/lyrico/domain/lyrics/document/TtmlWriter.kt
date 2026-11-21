package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLineTrack
import com.lonx.lyrico.data.lyrics.document.ExtensionElement
import com.lonx.lyrico.data.lyrics.document.ExtensionMap
import com.lonx.lyrico.data.lyrics.document.LyricsAgentType
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.lyrics.document.QualifiedName
import com.lonx.lyrico.domain.lyrics.LyricsTimestamp

private val SAFE_XML_NAME = Regex("[A-Za-z_][A-Za-z0-9_.:-]*")

private const val NS_XMLNS = "http://www.w3.org/2000/xmlns/"

private const val NS_TTML = "http://www.w3.org/ns/ttml"

object TtmlWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.TTML

    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String {
        if (!document.hasTtmlLyrics()) return ""
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<tt xmlns=\"").append(NS_TTML).append("\"")
        builder.append(" xmlns:ttm=\"").append(TtmlXmlNames.NS_TTM).append("\"")
        builder.append(" xmlns:itunes=\"").append(TtmlXmlNames.NS_ITUNES_INTERNAL).append("\"")
        collectDocumentNamespaces(document).forEach { (prefix, uri) ->
            if (prefix != "ttm" && prefix != "itunes" && prefix != "xml") {
                builder.append(" xmlns:").append(prefix).append("=\"").append(escapeXml(uri)).append("\"")
            }
        }
        document.metadata.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
        // 根 itunes:timing 词级时间标志（如 "Word"），解析侧收集、写回时还原（保真往返）
        document.metadata.timing?.let { builder.append(" itunes:timing=\"").append(escapeXml(it)).append("\"") }
        appendUnmanagedAttributes(builder, document.extensions, setOf("lang", "timing"))
        builder.append(">\n")

        val originalLines = document.tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
        // AMLL 要求每个 <p> 都有连续递增的 itunes:key；所有 sidecar 引用同步使用这组规范化 key。
        var nextLineKey = 1
        val originalLineKeys = originalLines.map { line ->
            if (line.startMs == null) null else "L${nextLineKey++}"
        }

        appendHead(builder, document, originalLines, originalLineKeys)
        builder.append("  <body")
        appendUnmanagedAttributes(builder, document.bodyExtensions.withValidBodyDuration(), emptySet())
        builder.append(">\n")
        // 按行扩展中的 itunes:songPart 分组重建 <div>（AMLL 规范 7.1 段落标注）：
        // 连续相同 songPart 的行归入同一 <div itunes:songPart="...">，无 songPart 的行进默认 <div>；
        // 段首行携带 divBegin/divEnd（段落时间窗）时强制开新 div 并写入 begin/end（保真往返），
        // 两个 songPart 相同但源文件中分属不同 <div> 的段落也能正确分开
        var currentSongPart: String? = null
        var divOpen = false
        val inlineTranslationTrack = document.tracks.firstOrNull { it.type == LyricsTrackType.Translation }
            .takeIf { document.sourceFormat == null }
        originalLines.forEachIndexed { index, line ->
            val backgroundLines = document.linkedLinesForOriginal(index, LyricsTrackType.Background)
            val songPart = line.songPartExtensionValue()
            val divBegin = line.divTimingExtensionValue("divBegin")
            val divEnd = line.divTimingExtensionValue("divEnd")
            if (!divOpen || songPart != currentSongPart || divBegin != null) {
                if (divOpen) builder.append("    </div>\n")
                builder.append("    <div")
                songPart?.let {
                    builder.append(" itunes:song-part=\"").append(escapeXml(it)).append("\"")
                }
                // 段落时间窗（段首行扩展携带的 div begin/end 毫秒值 → TTML 时间戳）
                divBegin?.let {
                    builder.append(" begin=\"")
                        .append(LyricsTimestamp.formatTtml(it)).append("\"")
                }
                divEnd?.let {
                    builder.append(" end=\"")
                        .append(LyricsTimestamp.formatTtml(it)).append("\"")
                }
                builder.append(">\n")
                currentSongPart = songPart
                divOpen = true
            }
            // 音译统一写入 head <transliterations> sidecar（支持词级），正文不再内联 x-romanization，避免重复
            appendOriginalLine(
                builder,
                line,
                originalLineKeys[index],
                backgroundLines,
                inlineTranslationTrack?.let {
                    document.linkedLinesForOriginal(index, LyricsTrackType.Translation).singleOrNull()
                },
                inlineTranslationTrack?.language
            )
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
        val translations = document.tracks
            .filter { it.type == LyricsTrackType.Translation }
            .takeUnless { document.sourceFormat == null }
            .orEmpty()
        val transliterations = document.tracks.filter { it.type == LyricsTrackType.Romanization }
        if (document.agents.isEmpty() && translations.isEmpty() && transliterations.isEmpty() &&
            document.headMetadataElements.isEmpty() && document.itunesMetadataElements.isEmpty()
        ) return

        builder.append("  <head>\n")
        if (document.agents.isNotEmpty()) {
            builder.append("    <metadata>\n")
            document.agents.forEach { agent ->
                builder.append("      <ttm:agent xml:id=\"").append(escapeXml(agent.id)).append("\"")
                val type = agent.rawType ?: agent.type.toTtmlType()
                if (type != null) builder.append(" type=\"").append(escapeXml(type)).append("\"")
                if (agent.name.isNullOrBlank()) {
                    builder.append("/>\n")
                } else {
                    builder.append(">\n")
                    builder.append("        <ttm:name type=\"full\">")
                        .append(escapeXml(agent.name))
                        .append("</ttm:name>\n")
                    builder.append("      </ttm:agent>\n")
                }
            }
            builder.append("    </metadata>\n")
        }

        if (document.headMetadataElements.isNotEmpty()) {
            builder.append("    <metadata>\n")
            document.headMetadataElements.forEach { appendExtensionElement(builder, it, "      ") }
            builder.append("    </metadata>\n")
        }

        if (translations.isNotEmpty() || transliterations.isNotEmpty() || document.itunesMetadataElements.isNotEmpty()) {
            builder.append("    <metadata>\n")
            builder.append("      <iTunesMetadata xmlns=\"").append(TtmlXmlNames.NS_ITUNES_INTERNAL).append("\">\n")
            if (translations.isNotEmpty()) {
                builder.append("        <translations>\n")
                translations.forEach { track ->
                    builder.append("          <translation")
                    val translationType = track.extensions.attributes.entries
                        .firstOrNull { it.key.localName == "type" }
                        ?.value
                        ?.takeIf { it.isNotBlank() }
                        ?: "subtitle"
                    builder.append(" type=\"").append(escapeXml(translationType)).append("\"")
                    track.language?.let { builder.append(" xml:lang=\"").append(escapeXml(it)).append("\"") }
                    builder.append(">\n")
                    track.lines.forEach { line ->
                        val key = originalKeyForLinkedLine(line, originalLines, originalLineKeys) ?: return@forEach
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
                        val key = originalKeyForLinkedLine(line, originalLines, originalLineKeys) ?: return@forEach
                        builder.append("            <text for=\"").append(escapeXml(key)).append("\">")
                        appendSidecarRomanText(builder, line)
                        builder.append("</text>\n")
                    }
                    builder.append("          </transliteration>\n")
                }
                builder.append("        </transliterations>\n")
            }
            document.itunesMetadataElements.forEach {
                appendExtensionElement(builder, it, "        ", TtmlXmlNames.NS_ITUNES_INTERNAL)
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
            line.words.forEachIndexed { index, word ->
                if (index > 0 && needsRomanizationSeparator(line.words[index - 1].text, word.text)) {
                    builder.append(" ")
                }
                val start = word.startMs
                val end = word.endMs
                if (start != null && end != null) {
                    builder.append("<span xmlns=\"").append(NS_TTML).append("\" begin=\"")
                        .append(LyricsTimestamp.formatTtml(start))
                        .append("\" end=\"")
                        .append(LyricsTimestamp.formatTtml(end))
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
        backgroundLines: List<LyricsDocumentLine> = emptyList(),
        inlineTranslation: LyricsDocumentLine? = null,
        inlineTranslationLanguage: String? = null
    ) {
        val start = line.startMs ?: return
        val end = line.endMs ?: line.words.lastOrNull()?.endMs ?: (start + 2000)
        builder.append("      <p begin=\"")
            .append(LyricsTimestamp.formatTtml(start))
            .append("\" end=\"")
            .append(LyricsTimestamp.formatTtml(end))
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

        if (line.words.size > 1 || line.words.any { it.ruby.isNotEmpty() }) {
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
        inlineTranslation?.visibleText()?.takeIf { it.isNotBlank() }?.let { translation ->
            builder.append("<span ttm:role=\"x-translation\"")
            inlineTranslationLanguage?.takeIf { it.isNotBlank() }?.let { language ->
                builder.append(" xml:lang=\"").append(escapeXml(language)).append("\"")
            }
            builder.append(">").append(escapeXml(translation)).append("</span>")
        }
        builder.append("</p>\n")
    }

    private fun appendWord(builder: StringBuilder, word: LyricsDocumentWord) {
        if (word.ruby.isNotEmpty()) {
            builder.append("<span tts:ruby=\"container\"")
            appendUnmanagedAttributes(builder, word.extensions, setOf("ruby", "begin", "end"))
            builder.append(">")
                .append("<span tts:ruby=\"base\">").append(escapeXml(word.text)).append("</span>")
                .append("<span tts:ruby=\"textContainer\">")
            resolveRubyTimings(word).forEach { syllable ->
                builder.append("<span tts:ruby=\"text\"")
                builder.append(" begin=\"").append(LyricsTimestamp.formatTtml(syllable.startMs)).append("\"")
                builder.append(" end=\"").append(LyricsTimestamp.formatTtml(syllable.endMs)).append("\"")
                builder.append(">").append(escapeXml(syllable.text)).append("</span>")
            }
            builder.append("</span></span>")
            return
        }
        val wordStart = word.startMs
        val wordEnd = word.endMs
        if (wordStart != null && wordEnd != null) {
            builder.append("<span begin=\"")
                .append(LyricsTimestamp.formatTtml(wordStart))
                .append("\" end=\"")
                .append(LyricsTimestamp.formatTtml(wordEnd))
                .append("\"")
            appendUnmanagedAttributes(builder, word.extensions, setOf("begin", "end"))
            builder.append(">")
                .append(escapeXml(word.text))
                .append("</span>")
        } else {
            builder.append(escapeXml(word.text))
        }
    }

    private data class ResolvedRubySyllable(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    /**
     * AMLL only accepts Ruby annotations that contain both begin and end. Keep the document model
     * lenient, but materialize missing boundaries here. Adjacent known boundaries are propagated
     * first; any remaining fully unknown chain is divided evenly across its available word range.
     * Explicit source timestamps are never rewritten by this normalization.
     */
    private fun resolveRubyTimings(word: LyricsDocumentWord): List<ResolvedRubySyllable> {
        if (word.ruby.isEmpty()) return emptyList()

        val starts = word.ruby.map { it.startMs }.toMutableList()
        val ends = word.ruby.map { it.endMs }.toMutableList()
        val fallbackStart = word.startMs
            ?: starts.firstNotNullOfOrNull { it }
            ?: ends.firstNotNullOfOrNull { it }
            ?: 0L
        val fallbackEnd = word.endMs
            ?: ends.asReversed().firstNotNullOfOrNull { it }
            ?: starts.asReversed().firstNotNullOfOrNull { it }
            ?: fallbackStart

        if (starts.first() == null) starts[0] = fallbackStart
        if (ends.last() == null) ends[ends.lastIndex] = fallbackEnd

        repeat(word.ruby.size) {
            for (index in 0 until word.ruby.lastIndex) {
                if (ends[index] != null && starts[index + 1] == null) {
                    starts[index + 1] = ends[index]
                }
                if (ends[index] == null && starts[index + 1] != null) {
                    ends[index] = starts[index + 1]
                }
            }
        }

        var index = 0
        while (index < word.ruby.size) {
            val rangeStart = starts[index]
            if (rangeStart == null || ends[index] != null) {
                index++
                continue
            }

            var rangeEndIndex = index
            while (rangeEndIndex < word.ruby.lastIndex && ends[rangeEndIndex] == null) {
                rangeEndIndex++
            }
            val rangeEnd = (ends[rangeEndIndex] ?: fallbackEnd).coerceAtLeast(rangeStart)
            val syllableCount = rangeEndIndex - index + 1
            for (syllableIndex in index..rangeEndIndex) {
                val position = syllableIndex - index
                if (starts[syllableIndex] == null) {
                    starts[syllableIndex] = interpolateTime(rangeStart, rangeEnd, position, syllableCount)
                }
                if (ends[syllableIndex] == null) {
                    ends[syllableIndex] = interpolateTime(rangeStart, rangeEnd, position + 1, syllableCount)
                }
            }
            index = rangeEndIndex + 1
        }

        var previousEnd = fallbackStart
        return word.ruby.mapIndexed { syllableIndex, syllable ->
            val start = starts[syllableIndex] ?: previousEnd
            val end = ends[syllableIndex] ?: fallbackEnd.coerceAtLeast(start)
            previousEnd = end
            ResolvedRubySyllable(startMs = start, endMs = end, text = syllable.text)
        }
    }

    private fun interpolateTime(start: Long, end: Long, position: Int, segmentCount: Int): Long {
        if (start == end) return start
        return start + ((end - start).toDouble() * position / segmentCount).toLong()
    }

    private fun originalKeyForLinkedLine(
        line: LyricsDocumentLine,
        originalLines: List<LyricsDocumentLine>,
        originalLineKeys: List<String?>
    ): String? {
        return line.linkedOriginalIndex(originalLines)?.let(originalLineKeys::get)
    }

    private fun LyricsAgentType.toTtmlType(): String? {
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
}

/** 读取行扩展中的 itunes:songPart 值（TtmlWriter 分组重建 div 用）；有 <p> 上遗留的同名属性时同样取出 */
private fun LyricsDocumentLine.songPartExtensionValue(): String? {
    return extensions.attributes.entries
        .firstOrNull { it.key.localName == TtmlXmlNames.SONG_PART || it.key.localName == TtmlXmlNames.SONG_PART_LEGACY }
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
        TtmlXmlNames.NS_TTM -> "ttm:$localName"
        TtmlXmlNames.NS_ITUNES_INTERNAL, TtmlXmlNames.NS_ITUNES_LEGACY -> "itunes:$localName"
        TtmlXmlNames.NS_XML -> "xml:$localName"
        else -> null
    }?.takeIf(SAFE_XML_NAME::matches)
}

// TtmlWriter 自管的基础属性名（解析侧全量收集进扩展，写回时由 writer 按标准位置输出，扩展侧跳过避免重复；
// divBegin/divEnd 段落时间窗由 div 分组逻辑消费重建 <div begin/end>，不输出到 <p>）
private val TTML_WRITER_MANAGED_LOCAL_NAMES = setOf(
    "begin", "end", "id", "role", "agent", "key", TtmlXmlNames.SONG_PART, TtmlXmlNames.SONG_PART_LEGACY, "divBegin", "divEnd"
)

private fun appendExtensionElement(
    builder: StringBuilder,
    element: ExtensionElement,
    indent: String,
    defaultNamespace: String? = null
) {
    val name = if (element.name.namespaceUri == defaultNamespace) element.name.localName else element.name.outputName()
    if (!SAFE_XML_NAME.matches(name)) return
    builder.append(indent).append("<").append(name)
    element.attributes.forEach { (attribute, value) ->
        val attributeName = attribute.outputName()
        if (attribute.namespaceUri != NS_XMLNS && SAFE_XML_NAME.matches(attributeName)) {
            builder.append(" ").append(attributeName).append("=\"")
                .append(escapeXml(value)).append("\"")
        }
    }
    if (element.text == null && element.children.isEmpty()) {
        builder.append("/>\n")
        return
    }
    builder.append(">")
    element.text?.let { builder.append(escapeXml(it)) }
    if (element.children.isNotEmpty()) {
        builder.append("\n")
        element.children.forEach { appendExtensionElement(builder, it, "$indent  ", defaultNamespace) }
        builder.append(indent)
    }
    builder.append("</").append(name).append(">\n")
}

private fun appendUnmanagedAttributes(
    builder: StringBuilder,
    extensions: ExtensionMap,
    excludedLocalNames: Set<String>
) {
    extensions.attributes.forEach { (name, value) ->
        val outputName = name.outputName()
        if (name.namespaceUri != NS_XMLNS && name.localName !in excludedLocalNames &&
            SAFE_XML_NAME.matches(outputName)
        ) {
            builder.append(" ").append(outputName).append("=\"")
                .append(escapeXml(value)).append("\"")
        }
    }
}

private fun ExtensionMap.withValidBodyDuration(): ExtensionMap {
    return copy(
        attributes = attributes.filterNot { (name, value) ->
            name.namespaceUri == null && name.localName == "dur" && !TtmlTime.isValid(value)
        }
    )
}

private fun QualifiedName.outputName(): String {
    return when (namespaceUri) {
        TtmlXmlNames.NS_TTM -> "ttm:$localName"
        TtmlXmlNames.NS_ITUNES_INTERNAL, TtmlXmlNames.NS_ITUNES_LEGACY -> "itunes:$localName"
        TtmlXmlNames.NS_XML -> "xml:$localName"
        TtmlXmlNames.NS_TTS -> "tts:$localName"
        else -> prefix?.takeIf { it.isNotBlank() }?.let { "$it:$localName" } ?: localName
    }
}

private fun collectDocumentNamespaces(document: LyricsDocument): Map<String, String> {
    val namespaces = linkedMapOf<String, String>()
    fun collectName(name: QualifiedName) {
        val prefix = name.prefix
        val uri = name.namespaceUri
        if (!prefix.isNullOrBlank() && SAFE_XML_NAME.matches(prefix) && !uri.isNullOrBlank() && uri != NS_XMLNS) {
            namespaces.putIfAbsent(prefix, uri)
        }
    }
    fun collectMap(map: ExtensionMap) {
        map.attributes.keys.forEach(::collectName)
    }
    fun collectElement(element: ExtensionElement) {
        collectName(element.name)
        element.attributes.keys.forEach(::collectName)
        element.children.forEach(::collectElement)
    }
    collectMap(document.extensions)
    collectMap(document.bodyExtensions)
    document.headMetadataElements.forEach(::collectElement)
    document.itunesMetadataElements.forEach(::collectElement)
    if (document.tracks.any { track -> track.lines.any { line -> line.words.any { it.ruby.isNotEmpty() } } }) {
        namespaces["tts"] = TtmlXmlNames.NS_TTS
    }
    return namespaces
}



private fun escapeXml(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
