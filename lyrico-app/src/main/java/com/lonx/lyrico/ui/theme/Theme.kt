package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.components.indication.AlphaIndication
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 主题感知的交互反馈颜色
 *
 * 根据当前主题模式返回合适的颜色：
 * - 浅色模式：使用黑色（用于在浅色背景上显示阴影）
 * - 深色模式：使用白色（用于在深色背景上显示高光）
 */
@Composable
private fun indicationColor(): Color {
    return if (SaltTheme.configs.isDarkTheme) {
        Color.White.copy(alpha = 0.8f)
    } else {
        Color.Black.copy(alpha = 0.8f)
    }
}
@Composable
fun LyricoTheme(
    colorMode: ThemeMode = ThemeMode.AUTO,
    keyColor: Color? = null,
    monetEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val controller = remember(colorMode, keyColor, monetEnabled) {
        if (monetEnabled) {
            when (colorMode) {
                ThemeMode.LIGHT -> ThemeController(ColorSchemeMode.MonetLight, keyColor = keyColor)
                ThemeMode.DARK -> ThemeController(ColorSchemeMode.MonetDark, keyColor = keyColor)
                ThemeMode.AUTO -> ThemeController(ColorSchemeMode.MonetSystem, keyColor = keyColor)
            }
        } else {
            when (colorMode) {
                ThemeMode.LIGHT -> ThemeController(ColorSchemeMode.Light)
                ThemeMode.DARK -> ThemeController(ColorSchemeMode.Dark)
                ThemeMode.AUTO -> ThemeController(ColorSchemeMode.System)
            }
        }
    }

    MiuixTheme(
        controller = controller,
        content = content,
    )
}