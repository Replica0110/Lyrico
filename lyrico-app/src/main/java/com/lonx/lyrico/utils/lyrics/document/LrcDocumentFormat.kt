package com.lonx.lyrico.utils.lyrics.document

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.model.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.model.lyrics.normalizedLyricLineOrder
import com.lonx.lyrico.utils.LyricFormatter

object PlainLrcParser : LyricsFormatParser {
    override val format: LyricFormat = LyricFormat.PLAIN_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object VerbatimLrcParser : LyricsFormatParser {
    override val format: LyricFormat = LyricFormat.VERBATIM_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object EnhancedLrcParser : LyricsFormatParser {
    override val format: LyricFormat = LyricFormat.ENHANCED_LRC

    override fun parse(raw: String): LyricsDocument = parseLrc(raw, format)
}

object PlainLrcWriter : LyricsFormatWriter {
    override val format: LyricFormat = LyricFormat.PLAIN_LRC

    override fun write(document: LyricsDocument, lineOrder: List<LyricLineTrack>): String {
        val builder = StringBuilder()
        appendTags(builder, document.metadata)
        val original = document.tracks.firstOrNull { it.type == LyricsTrackType.Original } ?: return builder.toString().trim()

        original.lines.groupBy { it.startMs }.values.forEach { lines ->
            appendOrderedLineGroup(
                builder = builder,
                document = document,
                originalLines = lines,
                lineOrder = lineOrder,
                appendOriginal = { appendLine(builder, it) }
            )
        }
        return builder.toString().trim()
    }
}

object EnhancedLrcWriter : LyricsFormatWriter {
    override val format: LyricFormat = LyricFormat.ENHANCED_LRC

    override fun write(document: LyricsDocument, lineOrder: List<LyricLineTrack>): String {
        val builder = StringBuilder()
        appendTags(builder, document.metadata)
        val original = document.tracks.firstOrNull { it.type == LyricsTrackType.Original } ?: return builder.toString().trim()

        original.lines.groupBy { it.startMs }.values.forEach { lines ->
            appendOrderedLineGroup(
                builder = builder,
                document = document,
                originalLines = lines,
                lineOrder = lineOrder,
                appendOriginal = { appendEnhancedOriginalLine(builder, it) }
            )
        }
        return builder.toString().trim()
    }
}

object VerbatimLrcWriter : LyricsFormatWriter {
    override val format: LyricFormat = LyricFormat.VERBATIM_LRC

    override fun write(document: LyricsDocument, lineOrder: List<LyricLineTrack>): String {
        val builder = StringBuilder()
        appendTags(builder, document.metadata)
        val original = document.tracks.firstOrNull { it.type == LyricsTrackType.Original } ?: return builder.toString().trim()
        val isWordLevel = original.lines.any { it.words.size > 1 }

        original.lines.groupBy { it.startMs }.values.forEach { lines ->
            appendOrderedLineGroup(
                builder = builder,
                document = document,
                originalLines = lines,
                lineOrder = lineOrder,
                appendOriginal = { appendVerbatimOriginalLine(builder, it, isWordLevel) }
            )
        }
        return builder.toString().trim()
    }
}

private fun parseLrc(raw: String, sourceFormat: LyricFormat): LyricsDocument {
    val metadata = mutableMapOf<String, String>()
    val lines = mutableListOf<LyricsDocumentLine>()

    raw.lines().forEach { line ->
        if (line.isBlank()) return@forEach
        val tag = Regex("""^\[([A-Za-z][A-Za-z0-9_-]*):(.*)]$""").matchEntire(line)
        if (tag != null) {
            metadata[tag.groupValues[1]] = tag.groupValues[2].trim()
            return@forEach
        }

        val matches = LyricFormatter.LRC_TIME_PATTERN.findAll(line).toList()
        if (matches.isEmpty()) return@forEach
        val start = parseLrcTimeMs(matches.first())
        val words = mutableListOf<LyricsDocumentWord>()

        matches.forEachIndexed { index, match ->
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: line.length
            val text = line.substring(match.range.last + 1, nextStart)
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
                    words = words
                )
            )
        }
    }

    val tracks = separateLrcTracks(lines, sourceFormat)
    return LyricsDocument(
        metadata = metadata.toLyricsMetadata(),
        tracks = tracks,
        sourceFormat = sourceFormat
    )
}

private fun separateLrcTracks(
    lines: List<LyricsDocumentLine>,
    sourceFormat: LyricFormat
): List<LyricsTrack> {
    val grouped = lines.groupBy { it.startMs }.toSortedMap(compareBy(nullsLast()) { it })
    if (grouped.values.none { it.size > 1 }) {
        return listOf(LyricsTrack(type = LyricsTrackType.Original, lines = lines))
    }

    val original = mutableListOf<LyricsDocumentLine>()
    val translation = mutableListOf<LyricsDocumentLine>()
    val romanization = mutableListOf<LyricsDocumentLine>()
    grouped.values.forEach { group ->
        val wordLevelOriginals = if (sourceFormat != LyricFormat.PLAIN_LRC) {
            group.filter { it.words.size > 1 }
        } else {
            emptyList()
        }
        val originalLines = wordLevelOriginals.ifEmpty { listOf(group[0]) }
        val subLines = group.filterNot { it in originalLines }

        original.addAll(originalLines)
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

private fun appendTags(builder: StringBuilder, metadata: LyricsMetadata) {
    metadata.title?.let { builder.append("[ti:").append(it).append("]\n") }
    metadata.artist?.let { builder.append("[ar:").append(it).append("]\n") }
    metadata.album?.let { builder.append("[al:").append(it).append("]\n") }
    metadata.offsetMs?.let { builder.append("[offset:").append(it).append("]\n") }
    metadata.extra.forEach { (key, value) -> builder.append("[").append(key).append(":").append(value).append("]\n") }
}

private fun appendOrderedLineGroup(
    builder: StringBuilder,
    document: LyricsDocument,
    originalLines: List<LyricsDocumentLine>,
    lineOrder: List<LyricLineTrack>,
    appendOriginal: (LyricsDocumentLine) -> Unit
) {
    val originalLine = originalLines.firstOrNull() ?: return
    val romanization = document.findLinkedLine(originalLine, LyricsTrackType.Romanization)
    val translation = document.findLinkedLine(originalLine, LyricsTrackType.Translation)

    lineOrder.normalizedLyricLineOrder().forEach { track ->
        when (track) {
            LyricLineTrack.ORIGINAL -> originalLines.forEach(appendOriginal)
            LyricLineTrack.ROMANIZATION -> {
                if (romanization != null) appendLine(builder, romanization, originalLine.startMs)
            }
            LyricLineTrack.TRANSLATION -> {
                if (translation != null) appendLine(builder, translation, originalLine.startMs)
            }
        }
    }
}

private fun appendEnhancedOriginalLine(builder: StringBuilder, line: LyricsDocumentLine) {
    val start = line.startMs ?: return
    if (line.words.size <= 1) {
        appendLine(builder, line)
        return
    }

    builder.append("[")
        .append(LyricFormatter.formatTimestamp(start))
        .append("]")
    line.words.forEach { word ->
        val wordStart = word.startMs
        if (wordStart != null) {
            builder.append("<")
                .append(LyricFormatter.formatTimestamp(wordStart))
                .append(">")
        }
        builder.append(word.text)
    }
    val lineEnd = line.words.asReversed().firstOrNull { it.endMs != null }?.endMs ?: line.endMs
    if (lineEnd != null) {
        builder.append("<")
            .append(LyricFormatter.formatTimestamp(lineEnd))
            .append(">")
    }
    builder.append("\n")
}

private fun appendVerbatimOriginalLine(
    builder: StringBuilder,
    line: LyricsDocumentLine,
    isWordLevel: Boolean
) {
    if (!isWordLevel || line.words.isEmpty()) {
        appendLine(builder, line)
        return
    }

    line.words.forEach { word ->
        val wordStart = word.startMs
        if (wordStart != null) {
            builder.append("[")
                .append(LyricFormatter.formatTimestamp(wordStart))
                .append("]")
        }
        builder.append(word.text)
    }
    val lineEnd = line.words.asReversed().firstOrNull { it.endMs != null }?.endMs ?: line.endMs
    if (lineEnd != null) {
        builder.append("[")
            .append(LyricFormatter.formatTimestamp(lineEnd))
            .append("]")
    }
    builder.append("\n")
}

private fun appendLine(builder: StringBuilder, line: LyricsDocumentLine) {
    val start = line.startMs ?: return
    builder.append("[")
        .append(LyricFormatter.formatTimestamp(start))
        .append("]")
        .append(line.visibleText())
        .append("\n")
}

private fun LyricsDocument.findLinkedLine(
    originalLine: LyricsDocumentLine,
    type: LyricsTrackType
): LyricsDocumentLine? {
    return originalLine.linkKey?.let { linesByKey(type)[it] }
        ?: originalLine.startMs?.let { linesByStart(type)[it] }
}

private fun LyricsDocument.linesByKey(type: LyricsTrackType): Map<String, LyricsDocumentLine> {
    return tracks
        .filter { it.type == type }
        .flatMap { it.lines }
        .mapNotNull { line -> line.linkKey?.let { it to line } }
        .toMap()
}

private fun LyricsDocument.linesByStart(type: LyricsTrackType): Map<Long, LyricsDocumentLine> {
    return tracks
        .filter { it.type == type }
        .flatMap { it.lines }
        .mapNotNull { line -> line.startMs?.let { it to line } }
        .toMap()
}

internal fun LyricsDocumentLine.visibleText(): String {
    return if (words.isNotEmpty()) words.joinToString("") { it.text } else text
}

private fun appendLine(builder: StringBuilder, line: LyricsDocumentLine, fallbackStartMs: Long?) {
    val start = line.startMs ?: fallbackStartMs ?: return
    builder.append("[")
        .append(LyricFormatter.formatTimestamp(start))
        .append("]")
        .append(line.visibleText())
        .append("\n")
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
