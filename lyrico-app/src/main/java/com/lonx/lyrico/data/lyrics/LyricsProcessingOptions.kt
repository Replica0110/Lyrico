package com.lonx.lyrico.data.lyrics

import kotlinx.serialization.Serializable

@Serializable
data class LyricsProcessingOptions(
    val targetFormat: LyricFormat? = null,
    val formatLineOrder: Boolean = false,
    val removeTagLines: Boolean = false,
    val tagLineKeywords: List<String> = emptyList(),
    val removeEmptyLines: Boolean = false,
    val twoColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(2),
    val threeColumnMapping: LyricsColumnMapping = LyricsColumnMapping.identity(3)
) {
    fun hasTextOperations(): Boolean {
        return formatLineOrder || twoColumnMapping.isChanged || threeColumnMapping.isChanged ||
                removeTagLines && tagLineKeywords.any { it.isNotBlank() } ||
                removeEmptyLines
    }

    companion object {
        val DefaultTagLineKeywords = listOf(
            "[by:",
            "[kana:",
            "[trans:",
            "[roma:",
            "作词：",
            "作词:",
            "作曲：",
            "作曲:",
            "编曲：",
            "编曲:",
            "制作人：",
            "制作人:",
            "监制：",
            "监制:",
            "混音：",
            "混音:",
            "录音：",
            "录音:",
            "母带：",
            "母带:",
            "和声：",
            "和声:",
            "配唱制作人：",
            "配唱制作人:",
            "OP：",
            "OP:",
            "SP：",
            "SP:",
            "出品：",
            "出品:",
            "发行：",
            "发行:"
        )
    }
}
