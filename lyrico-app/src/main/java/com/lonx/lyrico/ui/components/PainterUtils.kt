package com.lonx.lyrico.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

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
