package com.lonx.lyrico.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.model.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
internal fun LyricoMiuixTheme(
    colorMode: ThemeMode,
    keyColor: Color?,
    monetEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val controller =
        remember(colorMode, keyColor, monetEnabled) {
            if (monetEnabled) {
                when (colorMode) {
                    ThemeMode.LIGHT ->
                        ThemeController(ColorSchemeMode.MonetLight, keyColor = keyColor)
                    ThemeMode.DARK ->
                        ThemeController(ColorSchemeMode.MonetDark, keyColor = keyColor)
                    ThemeMode.AUTO ->
                        ThemeController(ColorSchemeMode.MonetSystem, keyColor = keyColor)
                }
            } else {
                when (colorMode) {
                    ThemeMode.LIGHT -> ThemeController(ColorSchemeMode.Light)
                    ThemeMode.DARK -> ThemeController(ColorSchemeMode.Dark)
                    ThemeMode.AUTO -> ThemeController(ColorSchemeMode.System)
                }
            }
        }
    MiuixTheme(controller = controller, content = content)
}
