package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLineTrack
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.lyrics.normalizedLyricsLineOrder
import com.lonx.lyrico.domain.lyrics.LyricsTimestamp

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
        writeLrc(document, lineOrder, removeEmptyLines, ::appendLine)
}

object EnhancedLrcWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.ENHANCED_LRC
    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String =
        writeLrc(document, lineOrder, removeEmptyLines, ::appendEnhancedOriginalLine)
}

object VerbatimLrcWriter : LyricsFormatWriter {
    override val format: LyricsFormat = LyricsFormat.VERBATIM_LRC
    override fun write(document: LyricsDocument, lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String {
        val wordLevel = document.tracks.firstOrNull { it.type == LyricsTrackType.Original }
            ?.lines.orEmpty().any { it.words.size > 1 }
        return writeLrc(document, lineOrder, removeEmptyLines) { builder, line ->
            appendVerbatimOriginalLine(builder, line, wordLevel)
        }
    }
}

private fun writeLrc(
    document: LyricsDocument,
    lineOrder: List<LyricsLineTrack>,
    removeEmptyLines: Boolean,
    appendOriginal: (StringBuilder, LyricsDocumentLine) -> Unit
): String {
    val builder = StringBuilder()
    appendTags(builder, document.metadata)
    document.forEachOrderedLine(lineOrder, removeEmptyLines) { track, line, fallback ->
        if (track == LyricsLineTrack.ORIGINAL) appendOriginal(builder, line) else appendLine(builder, line, fallback)
    }
    return builder.toString().trim()
}

internal fun LyricsDocument.writePlainText(lineOrder: List<LyricsLineTrack>, removeEmptyLines: Boolean): String {
    val builder = StringBuilder()
    forEachOrderedLine(lineOrder, removeEmptyLines) { track, line, _ ->
        val text = if (track == LyricsLineTrack.ROMANIZATION && line.words.isNotEmpty()) {
            buildString {
                line.words.forEachIndexed { index, word ->
                    if (index > 0 && needsRomanizationSeparator(line.words[index - 1].text, word.text)) append(" ")
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

        val matches = LyricsTimestamp.lrcTimePattern.findAll(line).toList()
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
    sourceFormat: LyricsFormat
): List<LyricsTrack> {
    val grouped = lines.groupBy { it.startMs }.toSortedMap(compareBy(nullsLast()) { it })
    if (grouped.values.none { it.size > 1 }) {
        return listOf(LyricsTrack(type = LyricsTrackType.Original, lines = lines))
    }

    val original = mutableListOf<LyricsDocumentLine>()
    val translation = mutableListOf<LyricsDocumentLine>()
    val romanization = mutableListOf<LyricsDocumentLine>()
    grouped.values.forEach { group ->
        val wordLevelOriginals = if (sourceFormat != LyricsFormat.PLAIN_LRC) {
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

private fun LyricsDocument.forEachOrderedLine(
    lineOrder: List<LyricsLineTrack>,
    removeEmptyLines: Boolean,
    render: (LyricsLineTrack, LyricsDocumentLine, Long?) -> Unit
) {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    originals.groupBy { it.startMs }.values.forEach { group ->
        val original = group.first()
        val romanization = findLinkedLine(original, LyricsTrackType.Romanization)
        val translation = findLinkedLine(original, LyricsTrackType.Translation)
        lineOrder.normalizedLyricsLineOrder().forEach { track ->
            when (track) {
                LyricsLineTrack.ORIGINAL -> group.filter { !removeEmptyLines || !it.isBlankOrPlaceholder() }
                    .forEach { render(track, it, it.startMs) }
                LyricsLineTrack.ROMANIZATION -> romanization?.let { render(track, it, original.startMs) }
                LyricsLineTrack.TRANSLATION -> translation?.let { render(track, it, original.startMs) }
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
        .append(LyricsTimestamp.formatLrc(start))
        .append("]")
    line.words.forEach { word ->
        val wordStart = word.startMs
        if (wordStart != null) {
            builder.append("<")
                .append(LyricsTimestamp.formatLrc(wordStart))
                .append(">")
        }
        builder.append(word.text)
    }
    val lineEnd = line.words.asReversed().firstOrNull { it.endMs != null }?.endMs ?: line.endMs
    if (lineEnd != null) {
        builder.append("<")
            .append(LyricsTimestamp.formatLrc(lineEnd))
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
                .append(LyricsTimestamp.formatLrc(wordStart))
                .append("]")
        }
        builder.append(word.text)
    }
    val lineEnd = line.words.asReversed().firstOrNull { it.endMs != null }?.endMs ?: line.endMs
    if (lineEnd != null) {
        builder.append("[")
            .append(LyricsTimestamp.formatLrc(lineEnd))
            .append("]")
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
        .append(LyricsTimestamp.formatLrc(start))
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
