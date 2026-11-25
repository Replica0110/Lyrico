package com.lonx.lyrico.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.data.settings.UiEngine
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun systemAccentColor(context: Context): Color {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val dynamicScheme = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        dynamicScheme.primary
    } else {
        // API<31 无动态取色，Salt 下回退到 Salt 语义色。
        if (LocalUiEngine.current == UiEngine.SALT) {
            SaltTheme.colors.highlight
        } else {
            MiuixTheme.colorScheme.primary
        }
    }
}
