// Adapted from SukiSU Ultra and Kyant0/AndroidLiquidGlass (Apache 2.0).
package com.lonx.lyrico.ui.components.library.liquid

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Immutable
internal data class InnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
)

internal fun Modifier.innerShadow(
    shape: Shape,
    shadow: () -> InnerShadow?,
): Modifier = this then InnerShadowElement(shape, shadow)

private class InnerShadowElement(
    val shape: Shape,
    val shadow: () -> InnerShadow?,
) : ModifierNodeElement<InnerShadowNode>() {
    override fun create(): InnerShadowNode = InnerShadowNode(shape, shadow)

    override fun update(node: InnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean =
        this === other || other is InnerShadowElement && shape == other.shape && shadow == other.shadow

    override fun hashCode(): Int = 31 * shape.hashCode() + shadow.hashCode()
}

private class InnerShadowNode(
    var shape: Shape,
    var shadow: () -> InnerShadow?,
) : Modifier.Node(), DrawModifierNode {
    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var previousRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()

        val currentShadow = shadow() ?: return
        val layer = shadowLayer ?: return
        val radius = currentShadow.radius.toPx()
        val offsetX = currentShadow.offset.x.toPx()
        val offsetY = currentShadow.offset.y.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)

        clipPath.reset()
        when (outline) {
            is Outline.Rectangle -> clipPath.addRect(outline.rect)
            is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect)
            is Outline.Generic -> clipPath.addPath(outline.path)
        }

        paint.color = currentShadow.color
        layer.alpha = currentShadow.alpha
        layer.blendMode = currentShadow.blendMode
        if (previousRadius != radius) {
            layer.renderEffect = if (radius > 0f) {
                BlurEffect(radius, radius, TileMode.Decal)
            } else {
                null
            }
            previousRadius = radius
        }

        layer.record {
            drawContext.canvas.run {
                save()
                clipPath(clipPath)
                drawOutline(outline, paint)
                translate(offsetX, offsetY)
                drawOutline(outline, ShadowMaskPaint)
                translate(-offsetX, -offsetY)
                restore()
            }
        }

        drawContext.canvas.run {
            save()
            clipPath(clipPath)
            drawLayer(layer)
            restore()
        }
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        shadowLayer?.let(requireGraphicsContext()::releaseGraphicsLayer)
        shadowLayer = null
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
