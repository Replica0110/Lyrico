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
 * 水波纹交互反馈组件
 *
 * @param color 波纹颜色，应传入主题感知的颜色（浅色模式用黑色，深色模式用白色）
 */
class RippleIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(
        interactionSource: InteractionSource
    ): DelegatableNode = RippleIndicationInstance(interactionSource, color)

    override fun hashCode(): Int = color.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RippleIndication) return false
        return color == other.color
    }

    private class RippleIndicationInstance(
        private val interactionSource: InteractionSource,
        private val rippleColor: Color
    ) : Modifier.Node(), DrawModifierNode {

        // 状态追踪
        private var isHovered = false
        private var isFocused = false

        // 动画变量
        // 控制水波纹的半径扩散 (0f -> 1f)
        private val rippleRadiusProgress = Animatable(0f)
        // 控制水波纹的透明度
        private val rippleAlpha = Animatable(0f)

        // 记录按下时的位置和目标半径
        private var pressPosition = Offset.Zero
        private var targetRadius = 0f

        override fun onAttach() {
            coroutineScope.launch {
                var hoverCount = 0
                var focusCount = 0

                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            handlePress(interaction.pressPosition)
                        }
                        is PressInteraction.Release -> {
                            handleReleaseOrCancel()
                        }
                        is PressInteraction.Cancel -> {
                            handleReleaseOrCancel()
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

        private fun handlePress(position: Offset) {
            pressPosition = position
            // 启动扩散动画
            coroutineScope.launch {
                // 重置状态
                rippleRadiusProgress.snapTo(0f)
                rippleAlpha.snapTo(0.2f) // 初始按下的透明度

                // 同时播放扩散和保持透明度
                launch {
                    rippleRadiusProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) {
                        invalidateDraw() // 每一帧都重绘
                    }
                }
            }
        }

        private fun handleReleaseOrCancel() {
            coroutineScope.launch {
                // 慢慢淡出
                rippleAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 200, easing = LinearEasing)
                ) {
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()

            // 1. 绘制 Hover 或 Focus 的静态遮罩 (参考原代码逻辑)
            if (isHovered || isFocused) {
                drawRect(color = rippleColor.copy(alpha = 0.1f), size = size)
            }

            // 2. 绘制 Ripple (如果有透明度则绘制)
            if (rippleAlpha.value > 0f) {
                // 计算最大半径（扩散到覆盖整个组件的最远角）
                // 只有在第一次 measure 或者 size 变化时才需要计算，但在 draw 中计算开销也很小
                if (targetRadius == 0f || rippleRadiusProgress.value == 0f) {
                    targetRadius = getRippleTargetRadius(size, pressPosition)
                }

                val currentRadius = targetRadius * rippleRadiusProgress.value

                // 使用 clipRect 确保水波纹不超出组件边界
                clipRect {
                    drawCircle(
                        color = rippleColor.copy(alpha = rippleAlpha.value),
                        radius = currentRadius,
                        center = pressPosition
                    )
                }
            }
        }

        // 计算从触摸点到组件四个角的最远距离，确保波纹能覆盖整个组件
        private fun getRippleTargetRadius(size: Size, pivot: Offset): Float {
            val distanceToTopLeft = getDistance(pivot, Offset(0f, 0f))
            val distanceToTopRight = getDistance(pivot, Offset(size.width, 0f))
            val distanceToBottomLeft = getDistance(pivot, Offset(0f, size.height))
            val distanceToBottomRight = getDistance(pivot, Offset(size.width, size.height))

            return max(
                max(distanceToTopLeft, distanceToTopRight),
                max(distanceToBottomLeft, distanceToBottomRight)
            )
        }

        private fun getDistance(p1: Offset, p2: Offset): Float {
            val dx = p1.x - p2.x
            val dy = p1.y - p2.y
            return sqrt(dx * dx + dy * dy)
        }
    }
}