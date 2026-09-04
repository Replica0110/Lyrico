package com.lonx.lyrico.ui.components.library.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Draws the additive press sheen used by SukiSU's floating bar. */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (Size, Offset) -> Offset = { _, offset -> offset },
) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private var pointerPosition = Offset.Zero
    private val pressSpec = spring(0.5f, 300f, 0.001f)

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(alpha = 0.04f * progress), blendMode = BlendMode.Plus)
            val p = position(size, pointerPosition)
            val center = Offset(p.x.coerceIn(0f, size.width), p.y.coerceIn(0f, size.height))
            val radius = (size.minDimension * 1.2f).coerceAtLeast(1f)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.White.copy(alpha = 0.04f * progress),
                        0.5f to Color.White.copy(alpha = 0.04f * progress),
                        1f to Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                pointerPosition = down.position
                animationScope.launch { pressProgressAnimation.animateTo(1f, pressSpec) }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ -> pointerPosition = change.position }
    }

    private fun release() {
        animationScope.launch { pressProgressAnimation.animateTo(0f, pressSpec) }
    }
}
