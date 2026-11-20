package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.settings.ThemeMode
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.blur.LocalBarBlurEnabled
import com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 为共享控件提供两套调色板，控件按 [LocalUiEngine] 选择主题实现。 */
@Composable
fun LyricoTheme(
    uiEngine: UiEngine = UiEngine.MIUIX,
    colorMode: ThemeMode = ThemeMode.AUTO,
    keyColor: Color? = null,
    monetEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val indication = ripple()
    val barBlurEnabled = rememberBarBlurEnabled()
    LyricoMiuixTheme(colorMode, keyColor, monetEnabled) {
        CompositionLocalProvider(LocalBarBlurEnabled provides barBlurEnabled) {
            LyricoSaltTheme(colorMode, MiuixTheme.colorScheme.primary, monetEnabled, indication) {
                CompositionLocalProvider(
                    LocalUiEngine provides uiEngine,
                    LocalIndication provides indication,
                    content = content,
                )
            }
        }
    }
}
