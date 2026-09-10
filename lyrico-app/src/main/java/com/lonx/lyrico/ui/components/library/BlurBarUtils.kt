package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.LocalScaffoldIncludesStartPadding
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val FloatingNavigationBarHeight = 64.dp
internal val FloatingNavigationBarBottomMargin = 16.dp
internal val ContentBreathingRoom = 12.dp
internal val LibraryScrollbarTrackWidth = 20.dp

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

/**
 * 资料库顶栏/底栏是叠在列表上的毛玻璃，列表本身铺满屏幕。
 * 滚动条和字母索引需要单独避开这两块，否则会被挡住。
 */
@Composable
internal fun Modifier.libraryOverlayInsets(
    paddingValues: PaddingValues,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return padding(
        start = if (LocalScaffoldIncludesStartPadding.current) {
            paddingValues.calculateStartPadding(layoutDirection)
        } else {
            0.dp
        },
        top = paddingValues.calculateTopPadding() + extraTop,
        end = paddingValues.calculateEndPadding(layoutDirection),
        bottom = LocalLibraryBottomContentPadding.current + extraBottom,
    )
}

@Composable
internal fun Modifier.libraryScrollbarOverlay(
    paddingValues: PaddingValues,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return padding(
        top = paddingValues.calculateTopPadding() + extraTop,
        end = paddingValues.calculateEndPadding(layoutDirection),
        bottom = LocalLibraryBottomContentPadding.current + extraBottom,
    )
        .fillMaxHeight()
        .width(LibraryScrollbarTrackWidth)
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
