package com.lonx.lyrico.utils.lyrics.document

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.isWordByWord
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentLine
import com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentWord
import com.lonx.lyrico.data.model.lyrics.document.LyricsMetadata
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrack
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrackType
import com.lonx.lyrico.data.model.plugin.ResolvedFieldProcessRule

object LyricsDocumentPipeline {
    private val parsers: Map<LyricFormat, LyricsFormatParser> = listOf(
        PlainLrcParser,
        VerbatimLrcParser,
        EnhancedLrcParser,
        TtmlParser
    ).associateBy { it.format }

    private val writers: Map<LyricFormat, LyricsFormatWriter> = listOf(
        PlainLrcWriter,
        VerbatimLrcWriter,
        EnhancedLrcWriter,
        TtmlWriter
    ).associateBy { it.format }

    fun parse(raw: String, sourceFormat: LyricFormat): LyricsDocument? {
        return parsers[sourceFormat]?.parse(raw)
    }

    fun processRawResult(
        result: LyricsResult,
        config: LyricRenderConfig,
        offset: Long = 0L
    ): String? {
        val source = selectSource(result, config) ?: return null
        val raw = result.rawFor(source).takeIf { it.isNotBlank() } ?: return null
        return process(
            raw = raw,
            sourceFormat = source,
            targetFormat = config.format,
            conversionMode = config.conversionMode,
            showTranslation = config.showTranslation,
            showRomanization = config.showRomanization,
            onlyTranslationIfAvailable = config.onlyTranslationIfAvailable,
            removeEmptyLines = config.removeEmptyLines,
            offset = offset
        )
    }

    fun processRawResultForFieldRule(
        result: LyricsResult,
        rule: ResolvedFieldProcessRule
    ): LyricsResult {
        fun processRaw(raw: String, sourceFormat: LyricFormat): String {
            if (raw.isBlank()) return raw
            return process(
                raw = raw,
                sourceFormat = sourceFormat,
                targetFormat = sourceFormat,
                conversionMode = rule.scriptConversion,
                normalizeWhitespace = rule.normalizeWhitespace,
                removeEmptyLines = rule.removeEmptyLines,
            ) ?: raw
        }

        return result.copy(
            rawPlainLrc = processRaw(result.rawPlainLrc, LyricFormat.PLAIN_LRC),
            rawVerbatimLrc = processRaw(result.rawVerbatimLrc, LyricFormat.VERBATIM_LRC),
            rawEnhancedLrc = processRaw(result.rawEnhancedLrc, LyricFormat.ENHANCED_LRC),
            rawTtml = processRaw(result.rawTtml, LyricFormat.TTML),
            rawMultiPersonEnhancedLrc = processRaw(result.rawMultiPersonEnhancedLrc, LyricFormat.ENHANCED_LRC)
        )
    }

    fun process(
        raw: String,
        sourceFormat: LyricFormat,
        targetFormat: LyricFormat,
        conversionMode: ConversionMode = ConversionMode.NONE,
        showTranslation: Boolean = true,
        showRomanization: Boolean = true,
        onlyTranslationIfAvailable: Boolean = false,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long = 0L
    ): String? {
        val parser = parsers[sourceFormat] ?: return null
        val writer = writers[targetFormat] ?: return null
        var document = parser.parse(raw)

        val processors = buildList {
            if (conversionMode != ConversionMode.NONE) {
                add(TextTransformPostProcessor { text -> convertText(text, conversionMode) })
            }
            if (normalizeWhitespace) {
                add(TextTransformPostProcessor(::normalizeVisibleWhitespace))
            }
            if (!showTranslation) add(RemoveTranslationPostProcessor)
            if (!showRomanization) add(RemoveRomanizationPostProcessor)
            val normalizedTagLineKeywords = removeTagLineKeywords
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (normalizedTagLineKeywords.isNotEmpty()) {
                add(RemoveTagLinesPostProcessor(normalizedTagLineKeywords))
            }
            if (removeEmptyLines) add(RemoveEmptyLinesPostProcessor)
            if (onlyTranslationIfAvailable) add(OnlyTranslationPostProcessor)
            if (offset != 0L) add(OffsetPostProcessor(offset))
        }

        processors.forEach { processor ->
            document = processor.process(document)
        }
        return writer.write(document).takeIf { it.isNotBlank() }
    }

    fun LyricsResult.toLyricsDocument(): LyricsDocument {
        val tracks = buildList {
            add(
                LyricsTrack(
                    type = LyricsTrackType.Original,
                    lines = original.map { it.toDocumentLine() }
                )
            )
            translated?.let { lines ->
                add(
                    LyricsTrack(
                        type = LyricsTrackType.Translation,
                        lines = lines.map { it.toDocumentLine() }
                    )
                )
            }
            romanization?.let { lines ->
                add(
                    LyricsTrack(
                        type = LyricsTrackType.Romanization,
                        lines = lines.map { it.toDocumentLine() }
                    )
                )
            }
        }
        return LyricsDocument(
            metadata = tags.toLyricsMetadata(),
            tracks = tracks,
            sourceFormat = null
        )
    }

    fun LyricsDocument.toLyricsResult(): LyricsResult {
        val originalDocumentLines = tracks
            .firstOrNull { it.type == LyricsTrackType.Original }
            ?.lines
            .orEmpty()
        val originalLines = originalDocumentLines.mapNotNull { it.toLyricsLine() }
        val originalByKey = originalDocumentLines
            .mapNotNull { line -> line.linkKey?.let { it to line } }
            .toMap()
        val originalByStart = originalDocumentLines
            .mapNotNull { line -> line.startMs?.let { it to line } }
            .toMap()

        fun linkedTrackLines(type: LyricsTrackType): List<LyricsLine>? {
            return tracks
                .filter { it.type == type }
                .flatMap { it.lines }
                .mapNotNull { line ->
                    val fallback = line.linkKey?.let { originalByKey[it] }
                        ?: line.startMs?.let { originalByStart[it] }
                    line.toLyricsLine(fallback)
                }
                .ifEmpty { null }
        }

        return LyricsResult(
            tags = metadata.toTags(),
            original = originalLines,
            translated = linkedTrackLines(LyricsTrackType.Translation),
            romanization = linkedTrackLines(LyricsTrackType.Romanization),
            isWordByWord = originalLines.isWordByWord()
        )
    }

    private fun selectSource(result: LyricsResult, config: LyricRenderConfig): LyricFormat? {
        if (config.format == LyricFormat.TTML && result.rawTtml.isNotBlank()) return LyricFormat.TTML
        if (config.format == LyricFormat.ENHANCED_LRC && result.rawEnhancedLrc.isNotBlank()) return LyricFormat.ENHANCED_LRC
        if (config.format == LyricFormat.VERBATIM_LRC && result.rawVerbatimLrc.isNotBlank()) return LyricFormat.VERBATIM_LRC
        if (config.format == LyricFormat.PLAIN_LRC && result.rawPlainLrc.isNotBlank()) return LyricFormat.PLAIN_LRC

        if (result.rawTtml.isNotBlank() && (config.showTranslation || config.onlyTranslationIfAvailable)) {
            return LyricFormat.TTML
        }
        if (result.rawEnhancedLrc.isNotBlank()) return LyricFormat.ENHANCED_LRC
        if (result.rawVerbatimLrc.isNotBlank()) return LyricFormat.VERBATIM_LRC
        if (result.rawPlainLrc.isNotBlank()) return LyricFormat.PLAIN_LRC
        if (result.payloadType == LyricsPayloadType.STRUCTURED && result.original.isNotEmpty()) return null
        return null
    }

    private fun LyricsResult.rawFor(format: LyricFormat): String {
        return when (format) {
            LyricFormat.PLAIN_LRC -> rawPlainLrc
            LyricFormat.VERBATIM_LRC -> rawVerbatimLrc.ifBlank { rawEnhancedLrc }
            LyricFormat.ENHANCED_LRC -> rawEnhancedLrc
            LyricFormat.TTML -> rawTtml
        }
    }

    private fun com.lonx.lyrico.data.model.lyrics.LyricsLine.toDocumentLine(): LyricsDocumentLine {
        return LyricsDocumentLine(
            startMs = start,
            endMs = end,
            text = words.joinToString("") { it.text },
            words = words.map { word ->
                LyricsDocumentWord(
                    startMs = word.start,
                    endMs = word.end,
                    text = word.text
                )
            }
        )
    }

    private fun LyricsDocumentLine.toLyricsLine(fallback: LyricsDocumentLine? = null): LyricsLine? {
        val start = startMs ?: fallback?.startMs ?: return null
        val end = endMs
            ?: words.lastOrNull()?.endMs
            ?: fallback?.endMs
            ?: fallback?.words?.lastOrNull()?.endMs
            ?: start
        val lineText = visibleText()
        val resultWords = words
            .mapNotNull { word ->
                val wordStart = word.startMs ?: startMs ?: fallback?.startMs
                val wordEnd = word.endMs ?: fallback?.endMs ?: end
                if (wordStart == null) {
                    null
                } else {
                    LyricsWord(
                        start = wordStart,
                        end = wordEnd,
                        text = word.text
                    )
                }
            }
            .ifEmpty {
                if (lineText.isBlank()) {
                    emptyList()
                } else {
                    listOf(
                        LyricsWord(
                            start = start,
                            end = end,
                            text = lineText
                        )
                    )
                }
            }

        return if (resultWords.isEmpty()) {
            null
        } else {
            LyricsLine(
                start = start,
                end = end,
                words = resultWords
            )
        }
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

    private fun LyricsMetadata.toTags(): Map<String, String> {
        return buildMap {
            title?.let { put("ti", it) }
            artist?.let { put("ar", it) }
            album?.let { put("al", it) }
            offsetMs?.let { put("offset", it.toString()) }
            putAll(extra)
        }
    }

    private fun convertText(text: String, conversionMode: ConversionMode): String {
        return when (conversionMode) {
            ConversionMode.TRADITIONAL_TO_SIMPLIFIED -> ZhConverterUtil.toSimple(text)
            ConversionMode.SIMPLIFIED_TO_TRADITIONAL -> ZhConverterUtil.toTraditional(text)
            else -> text
        }
    }

    private fun normalizeVisibleWhitespace(text: String): String {
        return text.replace(Regex("""[ \t\u00A0]+"""), " ")
    }
}

interface LyricsFormatParser {
    val format: LyricFormat
    fun parse(raw: String): LyricsDocument
}

interface LyricsFormatWriter {
    val format: LyricFormat
    fun write(document: LyricsDocument): String
}

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
                                word.copy(text = transformer(word.text))
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
                                    endMs = word.endMs?.let { (it + offsetMs).coerceAtLeast(0L) }
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
