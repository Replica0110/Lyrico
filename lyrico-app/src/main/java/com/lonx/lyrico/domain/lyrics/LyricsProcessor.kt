package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsColumnEdit
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions

/** Applies one operation: an authored column edit, column sorting, or text cleanup/conversion. */
object LyricsProcessor {
    fun process(
        raw: String,
        options: LyricsProcessingOptions,
        requireWordLevelTiming: Boolean = false,
        columnEdit: LyricsColumnEdit? = null
    ): LyricsProcessingResult {
        if (raw.isBlank()) return LyricsProcessingResult.Empty
        if (columnEdit != null) {
            if (LyricsColumnSorter.fingerprint(raw) != columnEdit.sourceHash) {
                return LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.SOURCE_CHANGED)
            }
            return LyricsProcessingResult.fromText(raw, LyricsColumnSorter.apply(raw, columnEdit.mapping))
        }
        if (options.formatLineOrder) {
            return LyricsProcessingResult.fromText(raw,
                LyricsColumnSorter.apply(raw, options.twoColumnMapping, options.threeColumnMapping))
        }

        val keywords = if (options.removeTagLines) options.tagLineKeywords
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct() else emptyList()
        val hasTextOperations = options.removeEmptyLines || keywords.isNotEmpty()
        val currentFormat = LyricsParser.detectFormat(raw)
        val targetFormat = options.targetFormat
        if (targetFormat == null) {
            if (!hasTextOperations) return LyricsProcessingResult.Unchanged
            if (currentFormat != LyricsFormat.TTML) {
                return LyricsProcessingResult.fromText(raw,
                    LyricsTextCleanup.process(raw, options.removeEmptyLines, keywords))
            }
        } else if (targetFormat == currentFormat && !hasTextOperations) {
            return LyricsProcessingResult.Unchanged
        }

        return LyricsFormatConverter.convert(
            raw = raw,
            targetFormat = targetFormat ?: currentFormat ?: return LyricsProcessingResult.Unsupported(
                LyricsProcessingResult.Reason.UNRECOGNIZED_FORMAT),
            removeEmptyLines = options.removeEmptyLines,
            removeTagLineKeywords = keywords,
            requireWordLevelTiming = requireWordLevelTiming
        )
    }
}
