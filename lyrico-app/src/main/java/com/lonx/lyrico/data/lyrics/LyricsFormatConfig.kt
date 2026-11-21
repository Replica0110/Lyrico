package com.lonx.lyrico.data.lyrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricsFormatConfig(
    val targetFormat: LyricsFormat? = null,
    val concurrency: Int,
    val formatLineOrder: Boolean = true,
    val removeTagLines: Boolean = false,
    val tagLineKeywords: List<String> = LyricsProcessingOptions.defaultTagLineKeywords,
    val removeEmptyLines: Boolean = false,
    val twoColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(2),
    val threeColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(3),
    @SerialName("columnEdits")
    val legacyColumnEdits: Map<String, LyricsColumnEdit> = emptyMap(),
    val operation: LyricsOperation = LyricsOperation.CONVERT
) {
    fun processingOptions() = LyricsProcessingOptions(
        targetFormat = targetFormat,
        formatLineOrder = formatLineOrder,
        removeTagLines = removeTagLines,
        tagLineKeywords = tagLineKeywords,
        removeEmptyLines = removeEmptyLines,
        twoColumnMapping = twoColumnMapping,
        threeColumnMapping = threeColumnMapping
    )
}
