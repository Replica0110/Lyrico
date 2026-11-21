@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.bar.miuix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.lonx.lyrico.data.settings.FloatingBarEffect
import com.lonx.lyrico.ui.components.bar.miuix.animation.DampedDragAnimation
import com.lonx.lyrico.ui.components.effect.glass.GlassEffect
import com.lonx.lyrico.ui.components.effect.glass.GlassSource
import com.lonx.lyrico.ui.components.effect.glass.GlassStyles
import com.lonx.lyrico.ui.components.effect.glass.glassSurface
import com.lonx.lyrico.ui.components.library.FloatingNavigationBarBottomMargin
import com.lonx.lyrico.ui.components.library.FloatingNavigationBarHeight
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BarPadding = 4.dp
internal val TabMinWidth = 76.dp

/** 从栏传给子项的选择回调，避免 Row 内容自己记录索引。 */
internal val LocalMiuixBarSelection = staticCompositionLocalOf<(Int) -> Unit> { {} }

/** 指示块当前位置（以项为单位），子项据此把颜色过渡到主题色。 */
internal val LocalMiuixBarPosition = staticCompositionLocalOf { { 0f } }

/**
 * 悬浮栏与滑动指示块。指示块只跟随拖动增量，不会在按下时瞬移。
 * 栏体与指示块都从同一个 [GlassSource] 取样，调用方需在内容层用 `glassSource` 标记背景。
 */
@Composable
internal fun MiuixFloatingNavigationBar(
    backdrop: GlassSource?,
    selectedIndex: Int,
    itemCount: Int,
    effect: FloatingBarEffect,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (itemCount <= 0) return

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val shape = CircleShape
    val liquid = backdrop != null && effect == FloatingBarEffect.LIQUID_GLASS
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { BarPadding.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)

    // The animation needs the bar geometry to decide whether a drag still belongs to the lens.
    class DragHolder {
        var instance: DampedDragAnimation? = null
    }

    val holder = remember { DragHolder() }
    val drag = remember(animationScope, itemCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex.coerceIn(0, itemCount - 1).toFloat(),
            valueRange = 0f..(itemCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val animation = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = animation.value * tabWidthPx
                val padding = with(density) { BarPadding.toPx() }
                val globalX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalX in 0f..totalWidthPx
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                if (this.targetValue != targetIndex.toFloat()) animateToValue(targetIndex.toFloat())
                currentOnSelectionChanged(targetIndex)
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                animateToValue(currentSelectedIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (itemCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        val targetValue = selectedIndex.coerceIn(0, itemCount - 1).toFloat()
        if (drag.targetValue != targetValue) drag.animateToValue(targetValue)
    }

    val selectIndex: (Int) -> Unit = remember(drag, itemCount) {
        { index ->
            val targetIndex = index.coerceIn(0, itemCount - 1)
            if (drag.targetValue != targetIndex.toFloat()) drag.animateToValue(targetIndex.toFloat())
            currentOnSelectionChanged(targetIndex)
        }
    }

    // 中性玻璃，不跟随主题色：选中态只靠文字与图标的对比度区分。
    val indicatorStyle = remember(colors.surfaceContainer, isDark) {
        GlassStyles.transparent(isDark).then {
            shape(CircleShape)
            backgroundColor(colors.surfaceContainer.copy(alpha = 0.45f))
            optics {
                copy(
                    depth = OpticalSizeValue.Fixed(1f),
                    blurRadius = OpticalSizeValue.Fixed(1.5.dp),
                    refractionStrength = 0.2f,
                    refractionDisplacement = 14.dp,
                )
            }
            specularIntensity(0.55f)
            chromaticAberrationStrength(0.1f)
        }
    }
    // 与 Modifier.glass 的区别：这里只要材质，变换由外层统一施加，阴影和指示块才能跟着一起走。
    val surface = when {
        backdrop == null -> Modifier.background(colors.surfaceContainer, shape)
        liquid -> Modifier.glassSurface(
            backdrop,
            shape,
            GlassEffect.Regular(
                colors.surfaceContainer.copy(alpha = 0.4f),
                colors.surfaceContainer.copy(alpha = 0.4f),
            ),
        )
        else -> Modifier.glassSurface(backdrop, shape, GlassEffect.Blur)
    }

    val width = TabMinWidth * itemCount + BarPadding * 2
    val bottomPadding = FloatingNavigationBarBottomMargin +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .width(width)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingNavigationBarHeight)
                // 弹性位移作用在整块上：阴影、材质与指示块共用这一层变换。
                .graphicsLayer { translationX = panelOffset }
                .shadow(4.dp, shape, clip = false)
                .then(surface)
                .then(
                    if (backdrop == null) {
                        Modifier.border(1.dp, colors.outline.copy(alpha = 0.25f), shape)
                    } else {
                        Modifier
                    },
                )
                .clip(shape),
            contentAlignment = Alignment.CenterStart,
        ) {
            // 指示块画在内容之下：液态玻璃取样的是栏后画面，画在上层会盖住选中项的文字与图标。
            if (tabWidthPx > 0f) {
                val offset = if (isLtr) drag.value * tabWidthPx else -drag.value * tabWidthPx
                val indicator = if (liquid) {
                    Modifier.hazeGlass(input = backdrop.hazeInput, style = indicatorStyle)
                } else {
                    Modifier.background(colors.primary.copy(alpha = 0.15f), shape)
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = BarPadding)
                        .graphicsLayer {
                            translationX = offset
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                        }
                        // 手势挂在指示块上：只有从指示块起手的拖动才改变选中项。
                        .then(if (liquid) drag.modifier else Modifier)
                        .then(indicator)
                        .height(FloatingNavigationBarHeight - BarPadding * 2)
                        .width(with(density) { tabWidthPx.toDp() }),
                )
            }

            CompositionLocalProvider(
                LocalMiuixBarSelection provides selectIndex,
                LocalMiuixBarPosition provides { drag.value },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            totalWidthPx = coordinates.size.width.toFloat()
                            val horizontalPadding = with(density) { (BarPadding * 2).toPx() }
                            tabWidthPx = ((totalWidthPx - horizontalPadding) / itemCount)
                                .coerceAtLeast(0f)
                        }
                        .selectableGroup()
                        .padding(BarPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}
