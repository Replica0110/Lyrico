package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType

interface LyricsPostProcessor {
    fun process(document: LyricsDocument): LyricsDocument
}

object RemoveTranslationPostProcessor : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        return document.copy(tracks = document.tracks.filterNot { it.type == LyricsTrackType.Translation })
    }
}

object RemoveRomanizationPostProcessor : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        return document.copy(tracks = document.tracks.filterNot { it.type == LyricsTrackType.Romanization })
    }
}

object RemoveEmptyLinesPostProcessor : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        val filtered = document.filterLinkedLines { type, line, index ->
            !line.isBlankOrPlaceholder() ||
                    (type == LyricsTrackType.Original &&
                            document.linkedLinesForOriginal(index).any { !it.isBlankOrPlaceholder() })
        }
        return filtered.copy(tracks = filtered.tracks.map { track ->
            if (track.type != LyricsTrackType.Original) track else track.copy(lines = track.lines.map { line ->
                // Retain the timed anchor and extensions, without rendering its empty placeholder.
                if (line.isBlankOrPlaceholder()) line.copy(text = "", words = emptyList()) else line
            })
        })
    }
}

class RemoveTagLinesPostProcessor(
    private val keywords: List<String>
) : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        val filtered = document.filterLinkedLines { _, line, _ ->
            keywords.none { keyword -> line.visibleText().contains(keyword, ignoreCase = true) }
        }
        return filtered.copy(metadata = document.metadata.removeMatchingTags())
    }

    private fun LyricsMetadata.removeMatchingTags(): LyricsMetadata {
        fun shouldRemove(key: String, value: String?): Boolean {
            val tagText = "[$key:${value.orEmpty()}]"
            return keywords.any { keyword -> tagText.contains(keyword, ignoreCase = true) }
        }

        return copy(
            title = title.takeUnless { shouldRemove("ti", it) },
            artist = artist.takeUnless { shouldRemove("ar", it) },
            album = album.takeUnless { shouldRemove("al", it) },
            offsetMs = offsetMs.takeUnless { shouldRemove("offset", it?.toString()) },
            extra = extra.filterNot { (key, value) -> shouldRemove(key, value) }
        )
    }
}

object OnlyTranslationPostProcessor : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        val original = document.tracks.firstOrNull { it.type == LyricsTrackType.Original } ?: return document
        val translation = document.tracks.firstOrNull { it.type == LyricsTrackType.Translation } ?: return document
        val translationsByKey = translation.lines.mapNotNull { line ->
            line.linkKey?.let { it to line }
        }.toMap()
        val translationsByStart = translation.lines.mapNotNull { line ->
            line.startMs?.let { it to line }
        }.toMap()

        val merged = original.lines.map { line ->
            val translated = line.linkKey?.let { translationsByKey[it] }
                ?: line.startMs?.let { translationsByStart[it] }
            if (translated != null && translated.text.isNotBlank()) {
                line.copy(text = translated.text, words = emptyList())
            } else {
                line
            }
        }

        return document.copy(
            tracks = listOf(original.copy(lines = merged)) +
                    document.tracks.filterNot {
                        it.type == LyricsTrackType.Original ||
                                it.type == LyricsTrackType.Translation ||
                                it.type == LyricsTrackType.Romanization ||
                                it.type == LyricsTrackType.Background
                    }
        )
    }
}

private fun LyricsDocument.filterLinkedLines(
    keep: (LyricsTrackType, LyricsDocumentLine, Int) -> Boolean
): LyricsDocument {
    val originals = tracks.firstOrNull { it.type == LyricsTrackType.Original }?.lines.orEmpty()
    val removedOriginals = originals.indices.filterNot { keep(LyricsTrackType.Original, originals[it], it) }.toSet()
    return copy(tracks = tracks.map { track ->
        track.copy(lines = track.lines.filterIndexed { index, line ->
            if (track.type == LyricsTrackType.Original) index !in removedOriginals
            else keep(track.type, line, index) && (
                !track.type.isLinkedToOriginal() || removedOriginals.isEmpty() || run {
                    val anchors = originals.indices.filter { originalIndex ->
                        linkedLinesForOriginal(originalIndex, track.type).any { it === line }
                    }
                    anchors.isEmpty() || anchors.any { it !in removedOriginals }
                }
            )
        })
    })
}

private fun LyricsTrackType.isLinkedToOriginal(): Boolean {
    return this == LyricsTrackType.Translation ||
            this == LyricsTrackType.Romanization ||
            this == LyricsTrackType.Background
}

class OffsetPostProcessor(
    private val offsetMs: Long
) : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        return document.copy(
            tracks = document.tracks.map { track ->
                track.copy(
                    lines = track.lines.map { line ->
                        line.copy(
                            startMs = line.startMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) },
                            endMs = line.endMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) },
                            extensions = line.extensions.copy(attributes = line.extensions.attributes.mapValues { (name, value) ->
                                if (name.namespaceUri == null && name.localName in setOf("divBegin", "divEnd")) {
                                    value.toLongOrNull()?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs).toString() } ?: value
                                } else value
                            }),
                            words = line.words.map { word ->
                                word.copy(
                                    startMs = word.startMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) },
                                    endMs = word.endMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) },
                                    ruby = word.ruby.map { syllable ->
                                        syllable.copy(
                                            startMs = syllable.startMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) },
                                            endMs = syllable.endMs?.let { com.lonx.lyrico.domain.lyrics.LyricsTimestamp.shift(it, offsetMs) }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}

internal fun LyricsDocumentLine.isBlankOrPlaceholder(): Boolean {
    val visible = visibleText().trim()
    return visible.isEmpty() || visible.matches(Regex("^[\\s/\\\\|｜·・.。…_-]*$"))
}
