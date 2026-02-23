package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class LyricFormat(
    @field:StringRes val labelRes: Int
) {
    PLAIN_LRC(R.string.lyric_format_plain),
    VERBATIM_LRC(R.string.lyric_format_verbatim),
    ENHANCED_LRC(R.string.lyric_format_enhanced)
}