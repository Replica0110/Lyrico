package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType

internal fun LyricsDocument.hasRenderableLyrics(format: LyricsFormat): Boolean {
    if (format == LyricsFormat.TTML) return hasTtmlLyrics()
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    if (originals.any { it.startMs != null && it.visibleText().isNotBlank() }) return true

    fun linkedLine(original: LyricsDocumentLine, type: LyricsTrackType): LyricsDocumentLine? {
        val lines = tracks.filter { it.type == type }.flatMap { it.lines }
        return original.linkKey?.let { key -> lines.lastOrNull { it.linkKey == key } }
            ?: original.startMs?.let { start -> lines.lastOrNull { it.startMs == start } }
    }

    return originals.groupBy { it.startMs }.values.any { group ->
        val original = group.first()
        listOf(LyricsTrackType.Translation, LyricsTrackType.Romanization).any { type ->
            val linked = linkedLine(original, type)
            linked != null && (linked.startMs != null || original.startMs != null) && linked.visibleText().isNotBlank()
        }
    }
}

/** Content counts only when the writer can attach it to an original line with a start time. */
internal fun LyricsDocument.hasTtmlLyrics(): Boolean {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    val writable = originals.withIndex().filter { it.value.startMs != null }
    if (writable.any { it.value.visibleText().isNotBlank() }) return true

    return writable.any { (index, _) ->
        linkedLinesForOriginal(index).any { it.visibleText().isNotBlank() }
    }
}

/** Uses the same sidecar, inline-translation and background associations as the TTML writer. */
internal fun LyricsDocument.linkedLinesForOriginal(
    originalIndex: Int,
    type: LyricsTrackType? = null
): List<LyricsDocumentLine> {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    val original = originals.getOrNull(originalIndex) ?: return emptyList()
    if (original.startMs == null) return emptyList()
    val background = tracks.firstOrNull { it.type == LyricsTrackType.Background }?.lines.orEmpty()
    val translations = tracks.filter { it.type == LyricsTrackType.Translation }
    val romanizations = tracks.filter { it.type == LyricsTrackType.Romanization }

    return buildList {
        if (type == null || type == LyricsTrackType.Background) {
            val byKey = original.linkKey?.let { key -> background.filter { it.linkKey == key } }
                ?.takeIf { it.isNotEmpty() }
            addAll(byKey ?: background.filter { it.startMs == original.startMs })
        }
        if (type == null || type == LyricsTrackType.Translation) {
            if (sourceFormat == null) {
                translations.firstOrNull()?.lines.orEmpty().lastOrNull { it.startMs == original.startMs }
                    ?.let(::add)
            } else {
                translations.forEach { track ->
                    addAll(track.lines.filter { it.linkedOriginalIndex(originals) == originalIndex })
                }
            }
        }
        if (type == null || type == LyricsTrackType.Romanization) {
            romanizations.forEach { track ->
                addAll(track.lines.filter { it.linkedOriginalIndex(originals) == originalIndex })
            }
        }
    }
}

internal fun LyricsDocumentLine.linkedOriginalIndex(originals: List<LyricsDocumentLine>): Int? {
    linkKey?.let { key ->
        originals.indexOfFirst { it.linkKey == key }.takeIf { it >= 0 }?.let { return it }
    }
    startMs?.let { start ->
        originals.indexOfFirst { it.startMs == start }.takeIf { it >= 0 }?.let { return it }
    }
    return null
}

internal fun needsRomanizationSeparator(previous: String, current: String): Boolean {
    return previous.lastOrNull()?.isWhitespace() != true && current.firstOrNull()?.isWhitespace() != true
}
