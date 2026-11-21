package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLineTrack
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsAgent
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.lyrics.normalizedLyricsLineOrder
import com.lonx.lyrico.domain.lyrics.LyricsTimestamp

private val VOCAL_AGENT = Regex("v\\d+")
private val VOCAL_PREFIX = Regex("^(\\[\\d{2,}:\\d{2}\\.\\d{2,3}])\\s*(v\\d+):")

object PlainLrcParser : LyricsFormatParser {
    override val format: LyricsFormat = LyricsFormat.PLAIN_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object VerbatimLrcParser : LyricsFormatParser {
    override val format: LyricsFormat = LyricsFormat.VERBATIM_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object EnhancedLrcParser : LyricsFormatParser {
    override val format: LyricsFormat = LyricsFormat.ENHANCED_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object PlainLrcWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.PLAIN_LRC
    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String =
        writeLrc(document, lineOrder, removeEmptyLines, LyricsFormat.PLAIN_LRC)
}

object EnhancedLrcWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.ENHANCED_LRC
    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String =
        writeLrc(document, lineOrder, removeEmptyLines, LyricsFormat.ENHANCED_LRC)
}

object VerbatimLrcWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.VERBATIM_LRC
    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String {
        return writeLrc(document, lineOrder, removeEmptyLines, LyricsFormat.VERBATIM_LRC)
    }
}

private fun writeLrc(
    document: LyricsDocument,
    lineOrder: List<LyricsLineTrack>,
    removeEmptyLines: Boolean,
    format: LyricsFormat
): String {
    val builder = StringBuilder()
    val agentIds = (document.agents.map { it.id } + document.tracks.flatMap { track ->
        track.lines.mapNotNull { it.agentId }
    }).distinct()
    val usedAgents = agentIds.filter { VOCAL_AGENT.matches(it) }.toMutableSet()
    var nextAgent = 1
    val vocalAgents = agentIds.associateWith { id ->
        if (VOCAL_AGENT.matches(id)) id else {
            while ("v$nextAgent" in usedAgents) nextAgent++
            "v${nextAgent++}".also { usedAgents.add(it) }
        }
    }
    document.forEachOrderedLine(lineOrder, removeEmptyLines) { track, line, fallback ->
        val timedLine = if (line.startMs == null) line.copy(startMs = fallback) else line
        val outputLine = if (track == LyricsLineTrack.ROMANIZATION) timedLine.copy(
            words = timedLine.words.mapIndexed { index, word ->
                val next = timedLine.words.getOrNull(index + 1)
                if (next != null && word.startMs != null && next.startMs != null &&
                    needsRomanizationSeparator(word.text, next.text)) {
                    word.copy(text = word.text + " ")
                } else word
            }
        ) else timedLine
        when {
            format == LyricsFormat.PLAIN_LRC -> appendLine(builder, outputLine)
            else -> appendTimedLine(builder, outputLine, format,
                if (format == LyricsFormat.ENHANCED_LRC) outputLine.agentId?.let(vocalAgents::get) else null)
        }
    }
    return builder.toString().trim()
}

internal fun LyricsDocument.writePlainText(lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String {
    val builder = StringBuilder()
    forEachOrderedLine(lineOrder, removeEmptyLines) { track, line, _ ->
        val text = if (track == LyricsLineTrack.ROMANIZATION && line.words.isNotEmpty()) {
            buildString {
                line.words.forEachIndexed { index, word ->
                    if (index > 0 && word.startMs != null && line.words[index - 1].startMs != null &&
                        needsRomanizationSeparator(line.words[index - 1].text, word.text)) append(" ")
                    append(word.text)
                }
            }
        } else line.visibleText()
        if (text.isNotBlank()) builder.append(text).append("\n")
    }
    return builder.toString().trim()
}

private fun parseLrc(raw: String, sourceFormat: LyricsFormat): LyricsDocument {
    val metadata = mutableMapOf<String, String>()
    val lines = mutableListOf<LyricsDocumentLine>()

    raw.lines().forEach { line ->
        if (line.isBlank()) return@forEach
        val tag = Regex("""^\[([A-Za-z][A-Za-z0-9_-]*):(.*)]$""").matchEntire(line)
        if (tag != null) {
            metadata[tag.groupValues[1]] = tag.groupValues[2].trim()
            return@forEach
        }

        val vocalPrefix = VOCAL_PREFIX.find(line)
        val agentId = vocalPrefix?.groupValues?.get(2)
        val content = if (vocalPrefix != null) {
            vocalPrefix.groupValues[1] + line.substring(vocalPrefix.range.last + 1)
        } else line
        val matches = LyricsTimestamp.lrcTimePattern.findAll(content).toList()
        if (matches.isEmpty()) return@forEach
        val start = parseLrcTimeMs(matches.first())
        val words = mutableListOf<LyricsDocumentWord>()

        matches.forEachIndexed { index, match ->
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: content.length
            val text = content.substring(match.range.last + 1, nextStart)
            val isLeadingEnhancedLineTimestamp =
                index == 0 &&
                        match.value.startsWith("[") &&
                        matches.getOrNull(index + 1)?.value?.startsWith("<") == true &&
                        text.isBlank()

            if (isLeadingEnhancedLineTimestamp) return@forEachIndexed
            if (text.isEmpty()) return@forEachIndexed

            val wordStart = parseLrcTimeMs(match)
            val wordEnd = matches.getOrNull(index + 1)?.let(::parseLrcTimeMs) ?: (wordStart + 500)
            words.add(
                LyricsDocumentWord(
                    startMs = wordStart,
                    endMs = wordEnd,
                    text = text
                )
            )
        }

        if (words.isNotEmpty()) {
            lines.add(
                LyricsDocumentLine(
                    startMs = start,
                    endMs = words.last().endMs,
                    text = words.joinToString("") { it.text },
                    words = if (matches.size > 1) words else emptyList(),
                    agentId = agentId
                )
            )
        }
    }

    val tracks = separateLrcTracks(lines)
    return LyricsDocument(
        metadata = metadata.toLyricsMetadata(),
        agents = lines.mapNotNull { it.agentId }.distinct().map { LyricsAgent(id = it) },
        tracks = tracks,
        sourceFormat = sourceFormat
    )
}

private fun separateLrcTracks(
    lines: List<LyricsDocumentLine>
): List<LyricsTrack> {
    val grouped = lines.sortedBy { it.startMs }.groupBy { it.startMs to it.agentId }
    if (grouped.values.none { it.size > 1 }) {
        return listOf(LyricsTrack(type = LyricsTrackType.Original, lines = lines))
    }

    val original = mutableListOf<LyricsDocumentLine>()
    val translation = mutableListOf<LyricsDocumentLine>()
    val romanization = mutableListOf<LyricsDocumentLine>()
    grouped.values.forEach { group ->
        // LRC has no track roles. Keep the existing positional convention:
        // two lines mean original / translation, three mean original / romanization / translation.
        // Timing granularity cannot identify a track's role.
        original.add(group[0])
        val subLines = group.drop(1)
        if (subLines.size >= 2) {
            romanization.add(subLines[0])
            translation.addAll(subLines.drop(1))
        } else if (subLines.size == 1) {
            translation.add(subLines[0])
        }
    }

    return buildList {
        add(LyricsTrack(type = LyricsTrackType.Original, lines = original))
        if (translation.isNotEmpty()) add(LyricsTrack(type = LyricsTrackType.Translation, lines = translation))
        if (romanization.isNotEmpty()) add(LyricsTrack(type = LyricsTrackType.Romanization, lines = romanization))
    }
}

private fun LyricsDocument.forEachOrderedLine(
    lineOrder: List<LyricsLineTrack>,
    removeEmptyLines: Boolean,
    render: (LyricsLineTrack, LyricsDocumentLine, Long?) -> Unit
) {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    originals.groupBy { Triple(it.startMs, it.agentId, it.linkKey) }.values.forEach { group ->
        val original = group.first()
        val romanization = findLinkedLine(original, LyricsTrackType.Romanization)
        val translation = findLinkedLine(original, LyricsTrackType.Translation)
        lineOrder.normalizedLyricsLineOrder().forEach { track ->
            when (track) {
                LyricsLineTrack.ORIGINAL -> group.filter { !removeEmptyLines || !it.isBlankOrPlaceholder() }
                    .forEach { render(track, it, it.startMs) }
                LyricsLineTrack.ROMANIZATION -> romanization?.let {
                    render(track, it.copy(agentId = it.agentId ?: original.agentId), original.startMs)
                }
                LyricsLineTrack.TRANSLATION -> translation?.let {
                    render(track, it.copy(agentId = it.agentId ?: original.agentId), original.startMs)
                }
            }
        }
    }
}

private fun appendTimedLine(
    builder: StringBuilder,
    line: LyricsDocumentLine,
    format: LyricsFormat,
    vocalAgent: String? = null
) {
    val start = line.startMs ?: return
    if (line.words.isEmpty()) {
        if (vocalAgent != null) {
            builder.append("[").append(LyricsTimestamp.formatLrc(start)).append("]")
                .append(vocalAgent).append(":").append(line.visibleText()).append("\n")
        } else appendLine(builder, line)
        return
    }

    val enhanced = format == LyricsFormat.ENHANCED_LRC
    val open = if (enhanced) "<" else "["
    val close = if (enhanced) ">" else "]"
    if (enhanced || line.words.first().startMs != start) {
        builder.append("[").append(LyricsTimestamp.formatLrc(start)).append("]")
    }
    vocalAgent?.let { builder.append(it).append(":") }
    line.words.forEachIndexed { index, word ->
        val wordStart = word.startMs
        if (wordStart != null) {
            builder.append(open)
                .append(LyricsTimestamp.formatLrc(wordStart))
                .append(close)
        }
        builder.append(word.text)
        val next = line.words.getOrNull(index + 1)
        if (next != null && word.endMs != null && word.endMs != next.startMs) {
            builder.append(open).append(LyricsTimestamp.formatLrc(word.endMs)).append(close)
        }
    }
    val lineEnd = line.words.asReversed().firstOrNull { it.endMs != null }?.endMs ?: line.endMs
    if (lineEnd != null) {
        builder.append(open)
            .append(LyricsTimestamp.formatLrc(lineEnd))
            .append(close)
    }
    builder.append("\n")
}

private fun appendLine(builder: StringBuilder, line: LyricsDocumentLine) {
    val start = line.startMs ?: return
    builder.append("[")
        .append(LyricsTimestamp.formatLrc(start))
        .append("]")
        .append(line.visibleText())
        .append("\n")
}

private fun LyricsDocument.findLinkedLine(
    originalLine: LyricsDocumentLine,
    type: LyricsTrackType
): LyricsDocumentLine? {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    return linkedLinesForOriginal(originals.indexOf(originalLine), type).lastOrNull()
}

internal fun LyricsDocumentLine.visibleText(): String {
    return if (words.isNotEmpty()) words.joinToString("") { it.text } else text
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

private fun parseLrcTimeMs(match: MatchResult): Long {
    val min = match.groupValues[2].toLong()
    val sec = match.groupValues[3].toLong()
    val ms = match.groupValues[4].padEnd(3, '0').take(3).toLong()
    return (min * 60 + sec) * 1000 + ms
}
