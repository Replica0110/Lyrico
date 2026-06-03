package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.model.ThemeMode
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController


@Composable
fun LyricoTheme(
    colorMode: ThemeMode = ThemeMode.AUTO,
    uiEngine: UiEngine = UiEngine.SaltUI,
    keyColor: Color? = null,
    monetEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val rippleIndication = ripple()
    val isDarkTheme = when (colorMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
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

    CompositionLocalProvider(LocalUiEngine provides uiEngine) {
        if (uiEngine == UiEngine.Miuix) {
            MiuixTheme(
                controller = controller,
                content = {
                    CompositionLocalProvider(
                        LocalIndication provides rippleIndication
                    ) {
                        content()
                    }
                }
            )
        } else {
            SaltTheme(
                configs = SaltConfigs(
                    isDarkTheme = isDarkTheme,
                    indication = rippleIndication
                )
            ) {
                val saltColors = SaltTheme.colors
                val materialColorScheme = remember(
                    isDarkTheme,
                    saltColors.highlight,
                    saltColors.onHighlight,
                    saltColors.background,
                    saltColors.subBackground,
                    saltColors.text,
                    saltColors.subText
                ) {
                    if (isDarkTheme) {
                        darkColorScheme(
                            primary = saltColors.highlight,
                            onPrimary = saltColors.onHighlight,
                            background = saltColors.background,
                            onBackground = saltColors.text,
                            surface = saltColors.background,
                            onSurface = saltColors.text,
                            surfaceVariant = saltColors.subBackground,
                            onSurfaceVariant = saltColors.subText
                        )
                    } else {
                        lightColorScheme(
                            primary = saltColors.highlight,
                            onPrimary = saltColors.onHighlight,
                            background = saltColors.background,
                            onBackground = saltColors.text,
                            surface = saltColors.background,
                            onSurface = saltColors.text,
                            surfaceVariant = saltColors.subBackground,
                            onSurfaceVariant = saltColors.subText
                        )
                    }
                }
                MaterialTheme(
                    colorScheme = materialColorScheme
                ) {
                    CompositionLocalProvider(
                        LocalIndication provides rippleIndication
                    ) {
                        content()
                    }
                }
            }
        }
    }
}
