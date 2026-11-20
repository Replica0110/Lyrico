package com.lonx.lyrico.data.lyrics

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.Serializable

@Serializable
enum class LyricFormat(
    @field:StringRes val labelRes: Int
) {
    PLAIN_LRC(R.string.lyric_format_plain),
    VERBATIM_LRC(R.string.lyric_format_verbatim),
    ENHANCED_LRC(R.string.lyric_format_enhanced),
    TTML(R.string.lyric_format_ttml),
}