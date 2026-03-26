package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R


enum class ConversionMode(
    @field:StringRes val labelRes: Int
) {
    NONE(R.string.chinese_conversion_mode_none),
    TRADITIONAL_TO_SIMPLIFIED(R.string.chinese_conversion_mode_traditional_to_simplified),
    SIMPLIFIED_TO_TRADITIONAL(R.string.chinese_conversion_mode_simplified_to_traditional),
}