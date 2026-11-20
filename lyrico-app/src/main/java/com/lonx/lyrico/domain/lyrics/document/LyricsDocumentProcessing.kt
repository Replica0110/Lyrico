package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType

interface LyricsPostProcessor {
    fun process(document: LyricsDocument): LyricsDocument
}

class TextTransformPostProcessor(
    private val transformer: (String) -> String
) : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        return document.copy(
            metadata = document.metadata.copy(
                title = document.metadata.title?.let(transformer),
                artist = document.metadata.artist?.let(transformer),
                album = document.metadata.album?.let(transformer),
                extra = document.metadata.extra.mapValues { (_, value) -> transformer(value) }
            ),
            tracks = document.tracks.map { track ->
                track.copy(
                    lines = track.lines.map { line ->
                        line.copy(
                            text = transformer(line.text),
                            words = line.words.map { word ->
                                word.copy(
                                    text = transformer(word.text),
                                    ruby = word.ruby.map { syllable ->
                                        syllable.copy(text = transformer(syllable.text))
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
        val removedKeys = mutableSetOf<String>()
        val removedStarts = mutableSetOf<Long>()
        val tracks = document.tracks.map { track ->
            val keptLines = track.lines.filter { line ->
                val empty = line.isBlankOrPlaceholder()
                if (empty && track.type == LyricsTrackType.Original) {
                    line.linkKey?.let(removedKeys::add)
                    line.startMs?.let(removedStarts::add)
                }
                !empty
            }
            track.copy(lines = keptLines)
        }.map { track ->
            if (track.type.isLinkedToOriginal()) {
                track.copy(lines = track.lines.filterNot {
                    it.linkKey in removedKeys || it.startMs?.let(removedStarts::contains) == true
                })
            } else {
                track
            }
        }
        return document.copy(tracks = tracks)
    }
}

class RemoveTagLinesPostProcessor(
    private val keywords: List<String>
) : LyricsPostProcessor {
    override fun process(document: LyricsDocument): LyricsDocument {
        val removedKeys = mutableSetOf<String>()
        val removedStarts = mutableSetOf<Long>()
        val tracks = document.tracks.map { track ->
            val keptLines = track.lines.filter { line ->
                val shouldRemove = keywords.any { keyword -> line.visibleText().contains(keyword, ignoreCase = true) }
                if (shouldRemove && track.type == LyricsTrackType.Original) {
                    line.linkKey?.let(removedKeys::add)
                    line.startMs?.let(removedStarts::add)
                }
                !shouldRemove
            }
            track.copy(lines = keptLines)
        }.map { track ->
            if (track.type.isLinkedToOriginal()) {
                track.copy(lines = track.lines.filterNot {
                    it.linkKey in removedKeys || it.startMs?.let(removedStarts::contains) == true
                })
            } else {
                track
            }
        }

        return document.copy(
            metadata = document.metadata.removeMatchingTags(),
            tracks = tracks
        )
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
                            startMs = line.startMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                            endMs = line.endMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                            words = line.words.map { word ->
                                word.copy(
                                    startMs = word.startMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                                    endMs = word.endMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                                    ruby = word.ruby.map { syllable ->
                                        syllable.copy(
                                            startMs = syllable.startMs?.let { (it + offsetMs).coerceAtLeast(0L) },
                                            endMs = syllable.endMs?.let { (it + offsetMs).coerceAtLeast(0L) }
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

private fun LyricsDocumentLine.isBlankOrPlaceholder(): Boolean {
    val visible = visibleText().trim()
    return visible.isEmpty() || visible.matches(Regex("^[\\s/\\\\|｜·・.。…_-]*$"))
}
