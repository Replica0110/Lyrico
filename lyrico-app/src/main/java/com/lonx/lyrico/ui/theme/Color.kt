package com.lonx.lyrico.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.ThemeColor
import com.lonx.lyrico.data.settings.UiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetDark
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetSystem
import top.yukonga.miuix.kmp.theme.MiuixTheme

val isDarkTheme: Boolean
    @Composable
    get() = when (MiuixTheme.colorSchemeMode) {
        Dark, MonetDark -> true
        Light, MonetLight -> false
        ColorSchemeMode.System, MonetSystem -> isSystemInDarkTheme()
        null -> false
    }
object LyricoColors {
    val emptyIllustrationWarm: Color
        @Composable
        get() = if (isDarkTheme) Color(0xFFFFBD7A) else Color(0xFFEBA15B)

    val emptyIllustrationFresh: Color
        @Composable
        get() = if (isDarkTheme) Color(0xFF80D4C3) else Color(0xFF55B7A5)

    /**
     * Placeholder background color for album covers.
     * In light mode: light gray, in dark mode: darker gray.
     */
    val coverPlaceholder: Color
        @Composable
        get() = if (LocalUiEngine.current == UiEngine.SALT) {
            SaltTheme.colors.subBackground
        } else {
            MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        }

    /**
     * Modified state background color (for edited items).
     * In light mode: amber tint, in dark mode: darker amber tint.
     */
    val modifiedBackground: Color
        @Composable
        get() = if (isDarkTheme) {
            Color(0xFF433519) // Dark amber
        } else {
            Color(0xFFFFFBEB) // Light amber
        }

    /**
     * Modified state border color.
     */
    val modifiedBorder: Color
        @Composable
        get() = if (isDarkTheme) {
            Color(0xFF9D5D00) // Dark amber
        } else {
            Color(0xFFFCD34D) // Light amber
        }

    /**
     * Modified state badge background.
     */
    val modifiedBadgeBackground: Color
        @Composable
        get() = if (isDarkTheme) {
            Color(0xFF433519) // Dark amber
        } else {
            Color(0xFFFEF3C7) // Light amber
        }

    /**
     * Modified state text color.
     */
    val modifiedText: Color
        @Composable
        get() = if (isDarkTheme) {
            Color(0xFFFCE100) // Dark amber
        } else {
            Color(0xFFD97706) // Light amber
        }

    /**
     * Icon color for album cover placeholder.
     * Adapts to light/dark mode using subText color.
     */
    val coverPlaceholderIcon: Color
        @Composable
        get() = if (LocalUiEngine.current == UiEngine.SALT) {
            SaltTheme.colors.subText.copy(alpha = 0.5f)
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f)
        }
}

val ThemeColor.color: Color?
    get() = argb?.let { Color(it) }

val ThemeColor.nameResId: Int
    @StringRes get() = when (this) {
        ThemeColor.DEFAULT -> R.string.color_default
        ThemeColor.BLUE -> R.string.color_blue
        ThemeColor.GREEN -> R.string.color_green
        ThemeColor.YELLOW -> R.string.color_yellow
        ThemeColor.ORANGE -> R.string.color_orange
        ThemeColor.PURPLE -> R.string.color_purple
        ThemeColor.PINK -> R.string.color_pink
        ThemeColor.TEAL -> R.string.color_teal
    }
