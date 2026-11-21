package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.lyrics.LyricsResult
import com.lonx.lyrico.data.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.lyrics.defaultLyricsLineOrder
import com.lonx.lyrico.data.lyrics.isRaw
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.plugin.FieldProcessRule
import com.lonx.lyrico.data.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.plugin.ResolvedFieldProcessRule
import com.lonx.lyrico.data.plugin.resolveFieldProcessRule
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.LyricsDocumentPipeline
import com.lonx.lyrico.domain.lyrics.document.LyricsTextPostProcessor
import com.lonx.lyrico.domain.lyrics.document.RemoveEmptyLinesPostProcessor
import com.lonx.lyrico.domain.lyrics.document.TtmlParser
import com.lonx.lyrico.domain.lyrics.document.TtmlXml
import com.lonx.lyrico.domain.lyrics.document.hasRenderableLyrics
import com.lonx.lyrico.domain.lyrics.document.toLyricsDocument
import com.lonx.lyrico.domain.lyrics.document.writePlainText
import kotlinx.coroutines.CancellationException

/** Select an original payload, then apply resolved field and display rules exactly once. */
object LyricsRenderer {
    fun render(
        result: LyricsResult,
        config: LyricsRenderConfig,
        offset: Long = 0L,
        fieldRule: FieldProcessRule = FieldProcessRule()
    ): String = render(result, config, offset, resolve(config, fieldRule), plainText = false)

    fun renderText(raw: String, config: LyricsRenderConfig, fieldRule: FieldProcessRule = FieldProcessRule()): String =
        render(rawInput(raw), config, fieldRule = fieldRule)

    internal fun renderFieldText(raw: String, rule: ResolvedFieldProcessRule): String {
        val config = LyricsRenderConfig(LyricsParser.detectFormat(raw) ?: LyricsFormat.PLAIN_LRC, true,
            removeEmptyLines = rule.removeEmptyLines, conversionMode = rule.scriptConversion)
        return render(rawInput(raw), config, 0L, rule, plainText = false)
    }

    /** Editing text may already be converted; keep its existing no-extra-conversion default. */
    fun renderPlainText(
        result: LyricsResult,
        config: LyricsRenderConfig,
        conversionMode: ConversionMode = ConversionMode.NONE,
        fieldRule: FieldProcessRule = FieldProcessRule()
    ): String {
        val effective = config.copy(conversionMode = conversionMode)
        return render(result, effective, 0L, resolve(effective, fieldRule), plainText = true)
    }

    private fun render(
        result: LyricsResult,
        config: LyricsRenderConfig,
        offset: Long,
        rule: ResolvedFieldProcessRule,
        plainText: Boolean
    ): String {
        val source = if (result.payloadType.isRaw() || result.original.isEmpty()) {
            LyricsSourceSelector.select(result, config)
        } else null
        val document: LyricsDocument
        if (source != null) {
            val format = LyricsParser.detectFormat(source.text)
            if (format == null && source.format != LyricsFormat.TTML) {
                if (!source.isTargetPayload && !plainText) return ""
                return processUntimed(source.text, rule)
            }
            val actualFormat = format ?: source.format
            val preserveSource = !plainText && actualFormat == config.format &&
                    !rule.trim && !rule.normalizeWhitespace && rule.scriptConversion == ConversionMode.NONE &&
                    !hasDisplayOperations(config)
            if (preserveSource && actualFormat == LyricsFormat.TTML) {
                val dom = try {
                    TtmlXml.parse(source.text)
                } catch (error: Exception) {
                    if (error is CancellationException || offset != 0L || rule.removeEmptyLines) throw error
                    return source.text
                }
                val parsed = TtmlParser.parse(dom)
                if (!parsed.hasRenderableLyrics(actualFormat)) return ""
                if (emptyFilterLeavesUnchanged(parsed, rule)) {
                    if (offset == 0L) return source.text
                    TtmlXml.shift(dom, offset)
                    return TtmlXml.write(dom)
                }
                document = parsed
            } else {
                document = LyricsDocumentPipeline.parse(source.text, actualFormat) ?: return ""
                if (preserveSource && emptyFilterLeavesUnchanged(document, rule)) {
                    if (!document.hasRenderableLyrics(actualFormat)) return ""
                    return LyricsTextTransform.shiftOffset(source.text, offset)
                }
            }
        } else {
            if (result.original.isEmpty()) return ""
            document = result.toLyricsDocument()
        }

        val processed = LyricsDocumentPipeline.transform(document, config, rule, offset)
        return if (plainText) processed.writePlainText(config.normalizedLineOrder, rule.removeEmptyLines)
        else LyricsDocumentPipeline.write(processed, config, rule.removeEmptyLines).orEmpty()
    }

    private fun processUntimed(raw: String, rule: ResolvedFieldProcessRule): String {
        if (!rule.trim && !rule.normalizeWhitespace && !rule.removeEmptyLines && rule.scriptConversion == ConversionMode.NONE) return raw
        val text = LyricsTextPostProcessor(rule)
        val transformed = raw.lineSequence().joinToString("\n") { text.text(it) }
        return if (rule.removeEmptyLines) LyricsTextCleanup.process(transformed, true, emptyList()) else transformed
    }

    private fun resolve(config: LyricsRenderConfig, fieldRule: FieldProcessRule) = resolveFieldProcessRule(
        GlobalFieldProcessSettings(scriptConversion = config.conversionMode, removeEmptyLines = config.removeEmptyLines), fieldRule)

    private fun rawInput(raw: String) = LyricsResult(emptyMap(), emptyList(), null, null,
        payloadType = LyricsPayloadType.RAW_PLAIN_LRC, rawPlainLrc = raw)

    private fun emptyFilterLeavesUnchanged(document: LyricsDocument, rule: ResolvedFieldProcessRule): Boolean =
        !rule.removeEmptyLines || RemoveEmptyLinesPostProcessor.process(document) == document

    private fun hasDisplayOperations(config: LyricsRenderConfig): Boolean = !config.showTranslation ||
            !config.showRomanization || config.onlyTranslationIfAvailable ||
            (config.format != LyricsFormat.TTML && config.normalizedLineOrder != defaultLyricsLineOrder)
}
