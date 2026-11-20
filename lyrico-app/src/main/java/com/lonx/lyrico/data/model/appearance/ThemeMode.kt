package com.lonx.lyrico.data.model.appearance

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class ThemeMode(
    @field:StringRes val labelRes: Int
) {
    AUTO(R.string.theme_mode_auto),
    LIGHT(R.string.theme_mode_light),
    DARK(R.string.theme_mode_dark)

}
