package com.lonx.lyrico.data.model.lyrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricsFormatConfig(
    val targetFormat: LyricFormat? = null,
    val concurrency: Int,
    val formatLineOrder: Boolean = true,
    val removeTagLines: Boolean = false,
    val tagLineKeywords: List<String> = LyricsProcessingOptions.DefaultTagLineKeywords,
    val removeEmptyLines: Boolean = false,
    val twoColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(2),
    val threeColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(3),
    @SerialName("columnEdits")
    val legacyColumnEdits: Map<String, LyricsColumnEdit> = emptyMap(),
    val operation: LyricsOperation = LyricsOperation.CONVERT
)
