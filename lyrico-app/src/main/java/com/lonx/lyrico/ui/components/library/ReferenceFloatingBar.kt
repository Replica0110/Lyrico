@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.ui.components.salt.glass.GlassEffect
import com.lonx.lyrico.ui.components.salt.glass.GlassSource
import com.lonx.lyrico.ui.components.salt.glass.glass
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.selection.selectable
import com.lonx.lyrico.ui.components.library.liquid.DampedDragAnimation
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign
private val LocalReferenceBarSelection = staticCompositionLocalOf<(Int) -> Unit> { {} }
private val ReferenceBarPadding = 4.dp
private val ReferenceBarTabMinWidth = 76.dp

@Composable
internal fun BlurNavigationBarV2(
    modifier: Modifier = Modifier,
    backdrop: GlassSource?,
    selectedIndex: Int,
    itemCount: Int,
    effect: FloatingBarEffect,
    onSelectionChanged: (Int) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    if (itemCount <= 0) return
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val colors = MiuixTheme.colorScheme
    val accent = colors.primary
    val pillShape = CircleShape
    val surfaceContainer = colors.surfaceContainer
    val liquid = backdrop != null && effect == FloatingBarEffect.LIQUID_GLASS
    val animationScope = rememberCoroutineScope()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        androidx.compose.runtime.derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
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
                val padding = with(density) { 4.dp.toPx() }
                val globalX = if (isLtr) {
                    padding + indicatorX + offset.x
                } else {
                    totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                }
                globalX in 0f..totalWidthPx
            },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                val targetValue = targetIndex.toFloat()
                if (this.targetValue != targetValue) animateToValue(targetValue)
                currentOnSelectionChanged(targetIndex)
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                animateToValue(currentSelectedIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (itemCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
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
            val targetValue = targetIndex.toFloat()
            if (drag.targetValue != targetValue) drag.animateToValue(targetValue)
            currentOnSelectionChanged(targetIndex)
        }
    }

    val width = ReferenceBarTabMinWidth * itemCount + ReferenceBarPadding * 2
    val bottomPadding = FloatingNavigationBarBottomMargin +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val surface = when {
        backdrop == null -> Modifier.background(surfaceContainer, pillShape)
        liquid -> Modifier.glass(backdrop, pillShape, GlassEffect.Regular())
        else -> Modifier.glass(backdrop, pillShape, GlassEffect.Blur)
    }

    Box(
        modifier = modifier
            .width(width)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalReferenceBarSelection provides selectIndex) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FloatingNavigationBarHeight)
                    .onGloballyPositioned { coordinates ->
                        totalWidthPx = coordinates.size.width.toFloat()
                        val horizontalPadding = with(density) { (ReferenceBarPadding * 2).toPx() }
                        tabWidthPx = ((totalWidthPx - horizontalPadding) / itemCount).coerceAtLeast(0f)
                    }
                    .shadow(4.dp, pillShape, clip = false)
                    .then(surface)
                    .then(if (backdrop == null) Modifier.border(1.dp, colors.outline.copy(alpha = 0.25f), pillShape) else Modifier)
                    .selectableGroup()
                    .padding(ReferenceBarPadding),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .padding(horizontal = ReferenceBarPadding)
                    .graphicsLayer {
                        val offset = drag.value * tabWidthPx
                        translationX = if (isLtr) offset else -offset
                        if (liquid) {
                            translationX += panelOffset
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                        }
                    }
                    .then(if (liquid) drag.modifier else Modifier)
                    .background(accent.copy(alpha = 0.15f), pillShape)
                    .height(FloatingNavigationBarHeight - ReferenceBarPadding * 2)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}

@Composable
internal fun RowScope.BlurNavigationBarItemV2(
    index: Int,
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
) {
    val selection = LocalReferenceBarSelection.current
    val onSurface = MiuixTheme.colorScheme.onSurfaceContainer
    val tint = if (selected) onSurface else onSurface.copy(alpha = 0.5f)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp)
            .fillMaxHeight()
            .weight(1f)
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selection(index)
                },
            ),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            Box(modifier = Modifier.size(22.dp)) { icon() }
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}
