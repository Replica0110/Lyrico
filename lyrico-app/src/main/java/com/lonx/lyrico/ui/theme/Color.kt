package com.lonx.lyrico.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import com.moriafly.salt.ui.SaltTheme

/**
 * Theme-aware colors that adapt to light/dark mode.
 * Use these instead of hardcoded colors for proper dark mode support.
 */
object LyricoColors {
    /**
     * Placeholder background color for album covers.
     * In light mode: light gray, in dark mode: darker gray.
     */
    val coverPlaceholder: Color
        @Composable
        get() = SaltTheme.colors.subBackground.copy(alpha = 0.5f)

    /**
     * Secondary text color for metadata like bitrate.
     */
    val secondaryText: Color
        @Composable
        get() = SaltTheme.colors.subText

    /**
     * Modified state background color (for edited items).
     * In light mode: amber tint, in dark mode: darker amber tint.
     */
    val modifiedBackground: Color
        @Composable
        get() = if (SaltTheme.configs.isDarkTheme) {
            Color(0xFF433519) // Dark amber
        } else {
            Color(0xFFFFFBEB) // Light amber
        }

    /**
     * Modified state border color.
     */
    val modifiedBorder: Color
        @Composable
        get() = if (SaltTheme.configs.isDarkTheme) {
            Color(0xFF9D5D00) // Dark amber
        } else {
            Color(0xFFFCD34D) // Light amber
        }

    /**
     * Modified state badge background.
     */
    val modifiedBadgeBackground: Color
        @Composable
        get() = if (SaltTheme.configs.isDarkTheme) {
            Color(0xFF433519) // Dark amber
        } else {
            Color(0xFFFEF3C7) // Light amber
        }

    /**
     * Modified state text color.
     */
    val modifiedText: Color
        @Composable
        get() = if (SaltTheme.configs.isDarkTheme) {
            Color(0xFFFCE100) // Dark amber
        } else {
            Color(0xFFD97706) // Light amber
        }

    /**
     * Input field border color (unfocused).
     */
    val inputBorder: Color
        @Composable
        get() = SaltTheme.colors.stroke

    /**
     * Input field focused border color.
     */
    val inputFocusedBorder: Color
        @Composable
        get() = SaltTheme.colors.highlight

    /**
     * Icon color for album cover placeholder.
     * Adapts to light/dark mode using subText color.
     */
    val coverPlaceholderIcon: Color
        @Composable
        get() = SaltTheme.colors.subText
}

