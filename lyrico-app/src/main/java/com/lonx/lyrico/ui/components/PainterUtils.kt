package com.lonx.lyrico.ui.components

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Creates a Painter that applies a tint color to the source painter.
 * Used for theming icons that don't support direct tinting.
 */
@Composable
fun rememberTintedPainter(
    painter: Painter,
    tint: Color
): Painter = remember(tint, painter) {
    TintedPainter(painter, tint)
}

private class TintedPainter(
    private val painter: Painter,
    private val tint: Color
) : Painter() {
    override val intrinsicSize = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        with(painter) {
            draw(size, colorFilter = ColorFilter.tint(tint))
        }
    }
}
class RoundedRectanglePainter(
    private val cornerRadius: Dp = 6.dp
) : Painter() {
    override val intrinsicSize = Size.Unspecified

    override fun DrawScope.onDraw() {
        drawRoundRect(
            color = Color.White,
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        )
    }
}
@Composable
fun getSystemWallpaperColor(context: Context): Color {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isDark = isSystemInDarkTheme()
        val dynamicScheme = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        dynamicScheme.primary
    } else {
        MiuixTheme.colorScheme.primary
    }
}