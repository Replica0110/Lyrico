package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.screen.BasicScreenStyle
import com.moriafly.salt.ui.SaltColors
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun LyricoSaltTheme(
    colorMode: ThemeMode,
    accent: Color,
    monetEnabled: Boolean,
    indication: Indication,
    content: @Composable () -> Unit,
) {
    val dark =
        when (colorMode) {
            ThemeMode.AUTO -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    SaltTheme(
        configs = SaltConfigs.default(isDarkTheme = dark, indication = indication),
        dynamicColors =
            SaltDynamicColors(
                light =
                    if (monetEnabled) SaltColors.defaultLight(highlight = accent)
                    else SaltColors.defaultLight(),
                dark =
                    if (monetEnabled) SaltColors.defaultDark(highlight = accent)
                    else SaltColors.defaultDark(),
            ),
        basicScreenStyle = BasicScreenStyle.default(
            titleBarBackdropType = if (rememberBarBlurEnabled()) {
                BasicScreenStyle.TitleBarBackdropType.default()
            } else {
                BasicScreenStyle.TitleBarBackdropType.None
            },
        ),
        content = content,
    )
}
