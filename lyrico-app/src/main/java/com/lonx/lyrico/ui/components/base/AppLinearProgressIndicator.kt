package com.lonx.lyrico.ui.components.base

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme

/**
 * 线性进度：Miuix 用自带指示器；SaltUI 3.0.0-beta02 没有进度组件，
 * 按文档要求使用已配置 Salt 颜色的回退控件。
 */
@Composable
fun AppLinearProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = SaltTheme.colors.highlight,
            trackColor = SaltTheme.colors.stroke,
        )
    } else top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
    )
}

/** 不确定进度（Salt 用回退控件，颜色同样取自 Salt 主题）。 */
@Composable
fun AppLinearProgressIndicator(modifier: Modifier = Modifier) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier,
            color = SaltTheme.colors.highlight,
            trackColor = SaltTheme.colors.stroke,
        )
    } else top.yukonga.miuix.kmp.basic.LinearProgressIndicator(modifier = modifier)
}
