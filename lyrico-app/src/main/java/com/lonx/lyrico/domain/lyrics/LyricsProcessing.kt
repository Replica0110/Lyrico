package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions

/**
 * 单份歌词按 [LyricsProcessingOptions] 的实际处理决策。
 *
 * 三条分支互斥：整理行内列序、纯文本清理（空行/标签行）、格式转换。
 * 用于单曲编辑；格式转换规则与批量任务共用 [LyricsFormatConverter]。
 */
object LyricsProcessing {

    /**
     * @param tagLineKeywords 需要剔除的标签行前缀；[LyricsProcessingOptions.removeTagLines] 为 false 时忽略。
     * @return 处理后的歌词；无需改动或无法处理时为 null。
     */
    fun apply(
        raw: String,
        options: LyricsProcessingOptions,
        tagLineKeywords: List<String> = emptyList(),
    ): String? {
        if (raw.isBlank()) return null
        val keywords = if (options.removeTagLines) tagLineKeywords else emptyList()

        if (options.formatLineOrder) {
            return LyricsColumnSorter
                .apply(raw, options.twoColumnMapping, options.threeColumnMapping)
                .takeIf { it != raw }
        }

        val currentFormat = LyricDecoder.detectFormat(raw)
        val targetFormat = options.targetFormat

        if (targetFormat == null) {
            if (!options.removeEmptyLines && !options.removeTagLines) return null

            // TTML 不是行式格式，只能交给文档管线按结构清理。
            val processed = if (currentFormat == LyricFormat.TTML) {
                LyricsFormatConverter.convert(
                    raw = raw,
                    targetFormat = currentFormat,
                    removeEmptyLines = options.removeEmptyLines,
                    removeTagLineKeywords = keywords,
                )
            } else {
                LyricsTextCleanup.process(
                    raw = raw,
                    removeEmptyLines = options.removeEmptyLines,
                    tagLineKeywords = keywords,
                )
            }
            return processed?.takeIf { it != raw }
        }

        if (targetFormat == currentFormat && !options.hasTextOperations()) return null

        return LyricsFormatConverter.convert(
            raw = raw,
            targetFormat = targetFormat,
            removeEmptyLines = options.removeEmptyLines,
            removeTagLineKeywords = keywords,
        )?.takeIf { it != raw }
    }
}
