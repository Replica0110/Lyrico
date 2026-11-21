package com.lonx.lyrico.data.lyrics

import com.lonx.lyrico.R

@kotlinx.serialization.Serializable
enum class LyricsOperation(val titleRes: Int) {
    CONVERT(R.string.lyrics_convert),
    SORT(R.string.lyrics_format_line_order),
    REMOVE_EMPTY(R.string.remove_empty_lines),
    REMOVE_TAGS(R.string.lyrics_remove_tag_lines);

    fun processingOptions(format: LyricsFormat? = null) = LyricsProcessingOptions(
        targetFormat = if (this == CONVERT) format else null,
        formatLineOrder = this == SORT,
        removeEmptyLines = this == REMOVE_EMPTY,
        removeTagLines = this == REMOVE_TAGS
    )
}
