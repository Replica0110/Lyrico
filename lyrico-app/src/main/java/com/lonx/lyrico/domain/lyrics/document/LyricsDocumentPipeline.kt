package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.log.AppLog
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsLineTrack
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.text.ConversionMode

object LyricsDocumentPipeline {
    private val parsers: Map<LyricsFormat, LyricsFormatParser> = listOf(
        PlainLrcParser,
        VerbatimLrcParser,
        EnhancedLrcParser,
        TtmlParser
    ).associateBy { it.format }

    private val writers: Map<LyricsFormat, LyricsFormatWriter> = listOf(
        PlainLrcWriter,
        VerbatimLrcWriter,
        EnhancedLrcWriter,
        TtmlWriter
    ).associateBy { it.format }

    fun parse(raw: String, sourceFormat: LyricsFormat): LyricsDocument? {
        return try {
            parsers[sourceFormat]?.parse(raw)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            AppLog.record(AppLogType.METADATA, "LyricsParser", "Lyrics parsing failed",
                "format=$sourceFormat\ncharacters=${raw.length}\nlines=${raw.count { it == '\n' } + 1}\n" +
                    "hasXmlDeclaration=${raw.contains("<?xml")}\nhasTtmlEnd=${raw.contains("</tt>")}\nhasLrcTime=${Regex("\\[\\d{2}:\\d{2}").containsMatchIn(raw)}",
                level = AppLogLevel.WARNING, error = error,
                repeatedFailureKey = "lyrics:$sourceFormat:${raw.hashCode()}:${error.javaClass.name}")
            throw error
        }
    }

    fun process(
        raw: String,
        sourceFormat: LyricsFormat,
        targetFormat: LyricsFormat,
        conversionMode: ConversionMode = ConversionMode.NONE,
        showTranslation: Boolean = true,
        showRomanization: Boolean = true,
        onlyTranslationIfAvailable: Boolean = false,
        lineOrder: List<LyricsLineTrack> = com.lonx.lyrico.data.lyrics.defaultLyricsLineOrder,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long = 0L
    ): String? {
        val document = parse(raw, sourceFormat) ?: return null
        return processParsed(
            raw = raw,
            document = document,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            conversionMode = conversionMode,
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onlyTranslationIfAvailable = onlyTranslationIfAvailable,
            lineOrder = lineOrder,
            normalizeWhitespace = normalizeWhitespace,
            removeEmptyLines = removeEmptyLines,
            removeTagLineKeywords = removeTagLineKeywords,
            offset = offset
        )
    }

    internal fun processParsed(
        raw: String,
        document: LyricsDocument,
        sourceFormat: LyricsFormat,
        targetFormat: LyricsFormat,
        conversionMode: ConversionMode = ConversionMode.NONE,
        showTranslation: Boolean = true,
        showRomanization: Boolean = true,
        onlyTranslationIfAvailable: Boolean = false,
        lineOrder: List<LyricsLineTrack> = com.lonx.lyrico.data.lyrics.defaultLyricsLineOrder,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long = 0L
    ): String? {
        val writer = writers[targetFormat] ?: return null
        return processDocument(
            document = document,
            writer = writer,
            conversionMode = conversionMode,
            showTranslation = showTranslation,
            showRomanization = showRomanization,
            onlyTranslationIfAvailable = onlyTranslationIfAvailable,
            lineOrder = lineOrder,
            normalizeWhitespace = normalizeWhitespace,
            removeEmptyLines = removeEmptyLines,
            removeTagLineKeywords = removeTagLineKeywords,
            offset = offset
        ).also { output ->
            AppLog.record(AppLogType.METADATA, "LyricsProcessor", "Lyrics processing finished",
                "sourceFormat=$sourceFormat\ntargetFormat=$targetFormat\ninputChars=${raw.length}\noutputChars=${output?.length ?: 0}\n" +
                    "conversion=$conversionMode\ntranslation=$showTranslation\nromanization=$showRomanization\n" +
                    "removeEmptyLines=$removeEmptyLines\nremoveTagRules=${removeTagLineKeywords.size}\noffset=$offset")
        }
    }

    private fun processDocument(
        document: LyricsDocument,
        writer: LyricsFormatWriter,
        conversionMode: ConversionMode,
        showTranslation: Boolean,
        showRomanization: Boolean,
        onlyTranslationIfAvailable: Boolean,
        lineOrder: List<LyricsLineTrack>,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long
    ): String? {
        val config = LyricsRenderConfig(writer.format, showRomanization, showTranslation,
            onlyTranslationIfAvailable, lineOrder, removeEmptyLines, conversionMode)
        val rule = ResolvedFieldProcessRule(conversionMode, trim = false,
            normalizeWhitespace = normalizeWhitespace, removeEmptyLines = removeEmptyLines)
        val processed = transform(document, config, rule, offset, removeTagLineKeywords)
        return write(processed, config, rule.removeEmptyLines)
    }

    internal fun transform(
        document: LyricsDocument,
        config: LyricsRenderConfig,
        rule: ResolvedFieldProcessRule,
        offset: Long = 0L,
        removeTagLineKeywords: List<String> = emptyList()
    ): LyricsDocument {
        val sourceOffset = document.metadata.offsetMs ?: 0L
        // LRC's positive source offset advances lyrics; the user's adjustment below delays them.
        val timestampOffset = if (sourceOffset == Long.MIN_VALUE) Long.MAX_VALUE else -sourceOffset
        val normalized = if (timestampOffset != 0L) OffsetPostProcessor(timestampOffset).process(document) else document
        var processed = LyricsTextPostProcessor(rule).process(
            normalized.copy(metadata = normalized.metadata.copy(offsetMs = null))
        )
        if (!config.showTranslation) processed = RemoveTranslationPostProcessor.process(processed)
        if (!config.showRomanization) processed = RemoveRomanizationPostProcessor.process(processed)
        val keywords = removeTagLineKeywords.map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isNotEmpty()) processed = RemoveTagLinesPostProcessor(keywords).process(processed)
        if (rule.removeEmptyLines) processed = RemoveEmptyLinesPostProcessor.process(processed)
        if (config.onlyTranslationIfAvailable) processed = OnlyTranslationPostProcessor.process(processed)
        if (offset != 0L) processed = OffsetPostProcessor(offset).process(processed)
        return processed
    }

    internal fun write(document: LyricsDocument, config: LyricsRenderConfig, removeEmptyLines: Boolean): String? {
        val writer = writers[config.format] ?: return null
        if (!document.hasRenderableLyrics(config.format)) return null
        return writer.write(document, config.normalizedLineOrder, removeEmptyLines).takeIf { it.isNotBlank() }
    }
}
