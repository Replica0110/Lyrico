package com.lonx.lyrico.ui.components.Indication


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 渐变交互反馈组件
 *
 * @param color 渐变颜色，应传入主题感知的颜色（浅色模式用黑色，深色模式用白色）
 */
class GradientIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(
        interactionSource: InteractionSource
    ): DelegatableNode = GradientIndicationInstance(interactionSource, color)

    override fun hashCode(): Int = color.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GradientIndication) return false
        return color == other.color
    }

    private class GradientIndicationInstance(
        private val interactionSource: InteractionSource,
        private val gradientColor: Color
    ) : Modifier.Node(), DrawModifierNode {

        private var isHovered = false
        private var isFocused = false

        // 动画进度：0f (无) -> 1f (完全覆盖)
        private val animationProgress = Animatable(0f)

        // 记录按压位置
        private var pressPosition = Offset.Zero
        // 缓存目标半径
        private var targetRadius = 0f

        override fun onAttach() {
            coroutineScope.launch {
                var hoverCount = 0
                var focusCount = 0

                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            pressPosition = interaction.pressPosition
                            // 按下时：快速扩散
                            launch {
                                animationProgress.snapTo(0f)
                                animationProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                ) {
                                    invalidateDraw()
                                }
                            }
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                            // 松开时：快速淡出 (通过将进度重置或反向动画，这里选择淡出效果通常通过透明度，
                            // 但为了保持渐变连贯性，我们让它快速走完或者渐隐)
                            launch {
                                // 这里做一个简单的逻辑：如果还在扩散中，让它继续走完但伴随透明度消失
                                // 或者直接反向收缩/淡出。
                                // 最自然的阴影效果通常是：扩散占满 -> 整体透明度变0
                                animationProgress.animateTo(
                                    targetValue = 0f, // 收缩回 0，或者可以加一个 alpha 变量来控制整体透明度
                                    animationSpec = tween(durationMillis = 250, easing = LinearEasing)
                                ) {
                                    invalidateDraw()
                                }
                            }
                        }
                        is HoverInteraction.Enter -> {
                            hoverCount++
                            isHovered = true
                            invalidateDraw()
                        }
                        is HoverInteraction.Exit -> {
                            hoverCount--
                            isHovered = hoverCount > 0
                            invalidateDraw()
                        }
                        is FocusInteraction.Focus -> {
                            focusCount++
                            isFocused = true
                            invalidateDraw()
                        }
                        is FocusInteraction.Unfocus -> {
                            focusCount--
                            isFocused = focusCount > 0
                            invalidateDraw()
                        }
                    }
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()

            // 1. Hover/Focus 状态保持简单的平铺遮罩 (符合一般设计规范)
            if (isHovered || isFocused) {
                drawRect(color = gradientColor.copy(alpha = 0.05f))
            }

            // 2. Press 状态绘制渐变阴影
            if (animationProgress.value > 0f) {
                if (targetRadius == 0f) {
                    targetRadius = getMaxDistanceToCorner(size, pressPosition)
                }

                // 核心逻辑：创建径向渐变
                // 颜色从中心的半透明黑色 -> 边缘的全透明
                // 这样看起来就像一个柔和的影子在扩散
                val currentRadius = targetRadius * animationProgress.value

                // 避免半径为0导致的Crash
                if (currentRadius > 0f) {
                    val gradientBrush = Brush.radialGradient(
                        colors = listOf(
                            gradientColor.copy(alpha = 0.15f), // 中心颜色（最深）
                            gradientColor.copy(alpha = 0.05f), // 中间过渡
                            Color.Transparent               // 边缘颜色
                        ),
                        center = pressPosition,
                        radius = currentRadius
                    )

                    // 使用 clipRect 保证不画出边界
                    clipRect {
                        drawRect(
                            brush = gradientBrush,
                            size = size
                        )
                    }
                }
            }
        }

        // 计算从触摸点到最远角的距离，保证渐变能覆盖全图
        private fun getMaxDistanceToCorner(size: Size, pivot: Offset): Float {
            val topLeft = getDistance(pivot, Offset(0f, 0f))
            val topRight = getDistance(pivot, Offset(size.width, 0f))
            val bottomLeft = getDistance(pivot, Offset(0f, size.height))
            val bottomRight = getDistance(pivot, Offset(size.width, size.height))

            return max(
                max(topLeft, topRight),
                max(bottomLeft, bottomRight)
            )
        }

        private fun getDistance(p1: Offset, p2: Offset): Float {
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}