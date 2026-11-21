package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.domain.lyrics.document.LyricsDocumentPipeline
import com.lonx.lyrico.domain.lyrics.document.hasRenderableLyrics
import com.lonx.lyrico.domain.lyrics.document.toLyricsResult

/**
 * 单份歌词的格式转换。
 *
 * 编辑页与批量任务共用文档解析、逐词时间检查及处理结果语义。
 */
object LyricsFormatConverter {

    /**
     * 把 [raw] 转成 [targetFormat]。
     *
     * @param requireWordLevelTiming 目标格式按词标时时，源歌词必须自带词级时间，
     *   否则返回 [LyricsProcessingResult.Unsupported]，由批量任务跳过。
     * @return 区分改动、未变化、空输出与不支持；解析异常继续向调用者传播。
     */
    fun convert(
        raw: String,
        targetFormat: LyricsFormat,
        removeEmptyLines: Boolean = false,
        removeTagLineKeywords: List<String> = emptyList(),
        requireWordLevelTiming: Boolean = false,
    ): LyricsProcessingResult {
        if (raw.isBlank()) return LyricsProcessingResult.Empty
        val sourceFormat = LyricsParser.detectFormat(raw)
            ?: return LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.UNRECOGNIZED_FORMAT)
        val document = LyricsDocumentPipeline.parse(raw, sourceFormat)
            ?: return LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.UNRECOGNIZED_FORMAT)
        if (!document.hasRenderableLyrics(targetFormat)) return LyricsProcessingResult.Empty
        if (requireWordLevelTiming && targetFormat.requiresWordLevelTiming() && !document.toLyricsResult().isWordByWord) {
            return LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.WORD_TIMING_REQUIRED)
        }

        val output = LyricsDocumentPipeline.processParsed(
            raw = raw,
            document = document,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            removeEmptyLines = removeEmptyLines,
            removeTagLineKeywords = removeTagLineKeywords,
        )
        return LyricsProcessingResult.fromText(raw, output)
    }

    /** 逐词时间轴是这两类格式的硬性前提。 */
    private fun LyricsFormat.requiresWordLevelTiming(): Boolean =
        this == LyricsFormat.VERBATIM_LRC || this == LyricsFormat.ENHANCED_LRC
}
