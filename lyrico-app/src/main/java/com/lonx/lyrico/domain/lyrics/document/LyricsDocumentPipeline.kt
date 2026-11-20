package com.lonx.lyrico.domain.lyrics.document

import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.lonx.lyrico.data.log.AppLog
import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogType
import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.lyrics.LyricLineTrack
import com.lonx.lyrico.data.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.text.ConversionMode

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
            lineOrder = config.normalizedLineOrder,
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
        lineOrder: List<LyricLineTrack> = com.lonx.lyrico.data.lyrics.DefaultLyricLineOrder,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long = 0L
    ): String? {
        val writer = writers[targetFormat] ?: return null
        val document = parse(raw, sourceFormat) ?: return null
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

    fun processStructuredResult(result: LyricsResult, config: LyricRenderConfig, offset: Long = 0L): String? {
        return processDocument(
            document = result.toLyricsDocument(),
            writer = TtmlWriter,
            conversionMode = config.conversionMode,
            showTranslation = config.showTranslation,
            showRomanization = config.showRomanization,
            onlyTranslationIfAvailable = config.onlyTranslationIfAvailable,
            lineOrder = config.normalizedLineOrder,
            removeEmptyLines = config.removeEmptyLines,
            offset = offset
        )
    }

    private fun processDocument(
        document: LyricsDocument,
        writer: LyricsFormatWriter,
        conversionMode: ConversionMode,
        showTranslation: Boolean,
        showRomanization: Boolean,
        onlyTranslationIfAvailable: Boolean,
        lineOrder: List<LyricLineTrack>,
        normalizeWhitespace: Boolean = false,
        removeEmptyLines: Boolean,
        removeTagLineKeywords: List<String> = emptyList(),
        offset: Long
    ): String? {
        var processed = document
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
            processed = processor.process(processed)
        }
        return writer.write(processed, lineOrder).takeIf { it.isNotBlank() }
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
