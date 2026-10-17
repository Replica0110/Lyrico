package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.UiEngine
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Both palettes remain available to shared content during the UI migration. */
@Composable
fun LyricoTheme(
    uiEngine: UiEngine = UiEngine.MIUIX,
    colorMode: ThemeMode = ThemeMode.AUTO,
    keyColor: Color? = null,
    monetEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val indication = ripple()
    LyricoMiuixTheme(colorMode, keyColor, monetEnabled) {
        LyricoSaltTheme(colorMode, MiuixTheme.colorScheme.primary, monetEnabled, indication) {
            CompositionLocalProvider(
                LocalUiEngine provides uiEngine,
                LocalIndication provides indication,
                content = content,
            )
        }
    }
}
