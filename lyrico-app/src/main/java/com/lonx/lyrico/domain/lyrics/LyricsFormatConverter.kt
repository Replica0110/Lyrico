package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.document.LyricsDocumentPipeline

/**
 * 单份歌词的格式转换。
 *
 * 优先走文档管线，保留时间轴与结构；管线撑不住源格式时回退到
 * 「解码 → 按目标格式重新编码」。编辑页与批量任务共用这一条路径，
 * 避免各自拼装回退逻辑导致同一次转换在不同入口结果不同。
 */
object LyricsFormatConverter {

    /**
     * 把 [raw] 转成 [targetFormat]。
     *
     * @param requireWordLevelTiming 目标格式按词标时时，源歌词必须自带词级时间，
     *   否则返回 null（批量任务据此跳过，而不是写出坏文件）。
     * @return 转换结果；无法识别源格式或转换不出内容时为 null。
     */
    fun convert(
        raw: String,
        targetFormat: LyricFormat,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        requireWordLevelTiming: Boolean = false,
    ): String? {
        if (raw.isBlank()) return null
        val sourceFormat = LyricDecoder.detectFormat(raw) ?: return null

        LyricsDocumentPipeline.process(
            raw = raw,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            removeEmptyLines = removeEmptyLines,
            removeTagLineKeywords = removeTagLineKeywords,
        )?.let { return it }

        return reencode(
            raw = raw,
            targetFormat = targetFormat,
            removeEmptyLines = removeEmptyLines,
            requireWordLevelTiming = requireWordLevelTiming,
        )
    }

    /** 解码后按目标格式重新编码，用于管线无法表达源结构的情况。 */
    private fun reencode(
        raw: String,
        targetFormat: LyricFormat,
        removeEmptyLines: Boolean,
        requireWordLevelTiming: Boolean,
    ): String? {
        val lyricsResult = LyricDecoder.decode(raw) ?: return null
        if (lyricsResult.original.isEmpty()) return null
        if (requireWordLevelTiming && targetFormat.requiresWordLevelTiming() && !lyricsResult.isWordByWord) {
            return null
        }

        val config = LyricRenderConfig(
            format = targetFormat,
            conversionMode = ConversionMode.NONE,
            showTranslation = lyricsResult.translated != null,
            showRomanization = lyricsResult.romanization != null,
            removeEmptyLines = removeEmptyLines,
            onlyTranslationIfAvailable = false
        )

        return LyricEncoder.encode(lyricsResult, config).takeIf { it.isNotBlank() }
    }

    /** 逐词时间轴是这两类格式的硬性前提。 */
    private fun LyricFormat.requiresWordLevelTiming(): Boolean =
        this == LyricFormat.VERBATIM_LRC || this == LyricFormat.ENHANCED_LRC
}
