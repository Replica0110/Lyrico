package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.ui.components.Indication.AlphaIndication
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltDynamicColors
import com.moriafly.salt.ui.SaltTheme

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
    content: @Composable () -> Unit
) {
    SaltTheme(
        configs = SaltConfigs.default(
            isDarkTheme = isSystemInDarkTheme(),
            indication = AlphaIndication(indicationColor())
        ),
        dynamicColors = SaltDynamicColors.default(),
        content = content
    )
}