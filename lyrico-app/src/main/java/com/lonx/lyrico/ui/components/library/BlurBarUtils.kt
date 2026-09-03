package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val FloatingNavigationBarHeight = 64.dp
internal val FloatingNavigationBarBottomMargin = 16.dp
internal val ContentBreathingRoom = 12.dp

internal val LocalLibraryBottomContentPadding = staticCompositionLocalOf { ContentBreathingRoom }
internal val LocalLibraryBarBlurEnabled = staticCompositionLocalOf { false }

private const val LibraryBarBlurRadius = 32f
private const val LibraryBarSurfaceAlpha = 0.68f

internal fun floatingContentBottomPadding(systemBottom: Dp, hasFloatingBar: Boolean): Dp =
    systemBottom + ContentBreathingRoom + if (hasFloatingBar) {
        FloatingNavigationBarHeight + FloatingNavigationBarBottomMargin
    } else {
        0.dp
    }

@Composable
internal fun rememberBlurBackdrop(enableBlur: Boolean = true): LayerBackdrop? {
    val surfaceColor = MiuixTheme.colorScheme.surface
    // rememberLayerBackdrop 必须无条件调用，否则开关切换时 Compose 槽位数量突变会崩溃。
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    return if (enableBlur && isRenderEffectSupported()) backdrop else null
}

@Composable
internal fun LibraryBlurredBar(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box(
        modifier = (if (backdrop != null) {
            Modifier.textureBlur(
                backdrop = backdrop,
                shape = RectangleShape,
                blurRadius = LibraryBarBlurRadius,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(surfaceColor.copy(alpha = LibraryBarSurfaceAlpha)),
                    ),
                ),
            )
        } else {
            Modifier.background(surfaceColor)
        }).then(modifier),
    ) {
        content()
    }
}
