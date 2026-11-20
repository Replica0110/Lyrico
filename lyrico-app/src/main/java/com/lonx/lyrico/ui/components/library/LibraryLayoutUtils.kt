package com.lonx.lyrico.ui.components.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.ui.components.scaffold.LocalScaffoldIncludesStartPadding

internal val FloatingNavigationBarHeight = 64.dp
internal val FloatingNavigationBarBottomMargin = 16.dp
internal val ContentBreathingRoom = 12.dp
internal val LibraryScrollbarTrackWidth = 22.dp

internal val LocalLibraryBottomContentPadding = staticCompositionLocalOf { ContentBreathingRoom }

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
    return fillMaxHeight()
        .padding(
            top = paddingValues.calculateTopPadding() + extraTop,
            end = paddingValues.calculateEndPadding(layoutDirection),
            bottom = LocalLibraryBottomContentPadding.current + extraBottom,
        )
        .width(LibraryScrollbarTrackWidth)
}
