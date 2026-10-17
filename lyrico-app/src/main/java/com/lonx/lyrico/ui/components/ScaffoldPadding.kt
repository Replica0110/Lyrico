package com.lonx.lyrico.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalScaffoldIncludesStartPadding = compositionLocalOf { true }
val LocalScaffoldIncludesBottomPadding = compositionLocalOf { true }

/** Safe area remaining after the surrounding rail or bottom navigation has taken its share. */
@Composable
fun scaffoldPageWindowInsets(): WindowInsets {
    val topAndSides = scaffoldTopAppBarWindowInsets()
    return if (LocalScaffoldIncludesBottomPadding.current) {
        topAndSides.union(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
    } else topAndSides
}

@Composable
fun scaffoldTopAppBarWindowInsets(): WindowInsets {
    val sides = if (LocalScaffoldIncludesStartPadding.current) {
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
    } else {
        WindowInsetsSides.Top + WindowInsetsSides.End
    }
    return WindowInsets.safeDrawing.only(sides)
}

@Composable
fun Modifier.scaffoldTopAppBarInsetsPadding(): Modifier =
    windowInsetsPadding(scaffoldTopAppBarWindowInsets())

@Composable
fun scaffoldContentPadding(
    paddingValues: PaddingValues,
    topExtra: Dp = 0.dp,
    bottomExtra: Dp = 0.dp,
    horizontalExtra: Dp = 0.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = paddingValues.calculateStartPadding(layoutDirection) + horizontalExtra,
        top = paddingValues.calculateTopPadding() + topExtra,
        end = paddingValues.calculateEndPadding(layoutDirection) + horizontalExtra,
        bottom = paddingValues.calculateBottomPadding() + bottomExtra
    )
}

@Composable
fun scaffoldTopHorizontalPadding(
    paddingValues: PaddingValues,
    topExtra: Dp = 0.dp,
    horizontalExtra: Dp = 0.dp,
    includeStartPadding: Boolean = LocalScaffoldIncludesStartPadding.current
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = if (includeStartPadding) {
            paddingValues.calculateStartPadding(layoutDirection) + horizontalExtra
        } else {
            horizontalExtra
        },
        top = paddingValues.calculateTopPadding() + topExtra,
        end = paddingValues.calculateEndPadding(layoutDirection) + horizontalExtra,
        bottom = 0.dp
    )
}

@Composable
fun scaffoldHorizontalBottomPadding(
    paddingValues: PaddingValues,
    bottomExtra: Dp = 0.dp,
    horizontalExtra: Dp = 0.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = paddingValues.calculateStartPadding(layoutDirection) + horizontalExtra,
        top = 0.dp,
        end = paddingValues.calculateEndPadding(layoutDirection) + horizontalExtra,
        bottom = paddingValues.calculateBottomPadding() + bottomExtra
    )
}

fun scaffoldBottomPadding(
    paddingValues: PaddingValues,
    bottomExtra: Dp = 0.dp
): Dp = paddingValues.calculateBottomPadding() + bottomExtra
