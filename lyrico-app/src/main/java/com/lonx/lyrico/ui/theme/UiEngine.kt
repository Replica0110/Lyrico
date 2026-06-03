package com.lonx.lyrico.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.lonx.lyrico.R

enum class UiEngine(
    @field:StringRes val labelRes: Int
) {
    SaltUI(R.string.ui_theme_saltui),
    Miuix(R.string.ui_theme_miuix);
}

val LocalUiEngine = staticCompositionLocalOf { UiEngine.SaltUI }
