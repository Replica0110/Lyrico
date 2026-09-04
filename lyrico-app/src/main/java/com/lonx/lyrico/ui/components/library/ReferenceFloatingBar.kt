package com.lonx.lyrico.ui.components.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
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
import androidx.compose.ui.util.lerp
import com.lonx.lyrico.ui.components.library.liquid.DampedDragAnimation
import com.lonx.lyrico.ui.components.library.liquid.InnerShadow
import com.lonx.lyrico.ui.components.library.liquid.InteractiveHighlight
import com.lonx.lyrico.ui.components.library.liquid.innerShadow
import com.lonx.lyrico.ui.components.library.liquid.lens
import com.lonx.lyrico.ui.components.library.liquid.rememberCombinedBackdrop
import com.lonx.lyrico.ui.components.library.liquid.vibrancy
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private val LocalReferenceBarSelection = staticCompositionLocalOf<(Int) -> Unit> { {} }
private val LocalReferenceBarColor = staticCompositionLocalOf<Color?> { null }
private val LocalReferenceBarScale = staticCompositionLocalOf { { 1f } }
private val ReferenceBarPadding = 4.dp
private val ReferenceBarTabMinWidth = 76.dp

@Composable
internal fun BlurNavigationBarV2(
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop?,
    selectedIndex: Int,
    itemCount: Int,
    isBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    onSelectionChanged: (Int) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    if (itemCount <= 0) return
    // Keep every rendering path on the same compact geometry. Intrinsic sizing
    // of weighted children can otherwise consume the full screen in the blur path.
    val barWidth = ReferenceBarTabMinWidth * itemCount + ReferenceBarPadding * 2
    when {
        isLiquidGlassEnabled && backdrop != null -> ReferenceLiquidBar(
            modifier,
            barWidth,
            backdrop,
            selectedIndex,
            itemCount,
            onSelectionChanged,
            content,
        )

        isBlurEnabled -> ReferenceHachimiBar(
            modifier,
            barWidth,
            backdrop,
            selectedIndex,
            itemCount,
            onSelectionChanged,
            content,
        )

        else -> ReferenceLiquidBar(
            modifier,
            barWidth,
            null,
            selectedIndex,
            itemCount,
            onSelectionChanged,
            content,
        )
    }
}

@Composable
private fun ReferenceHachimiBar(
    modifier: Modifier,
    barWidth: androidx.compose.ui.unit.Dp,
    backdrop: LayerBackdrop?,
    selectedIndex: Int,
    itemCount: Int,
    onSelectionChanged: (Int) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val accent = colors.primary
    val surface = colors.surface.copy(alpha = 0.95f)
    val borderColor = colors.outline.copy(alpha = if (isDark) 0.35f else 0.25f)
    val bottomPadding = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    val indicatorOffset = remember { Animatable(selectedIndex.coerceIn(0, itemCount - 1).toFloat()) }

    LaunchedEffect(selectedIndex) {
        indicatorOffset.animateTo(
            selectedIndex.coerceIn(0, itemCount - 1).toFloat(),
            spring(1f, 500f),
        )
    }

    Box(
        modifier = modifier
            .width(barWidth)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalReferenceBarSelection provides onSelectionChanged,
            LocalReferenceBarColor provides null,
            LocalReferenceBarScale provides { 1f },
        ) {
            Row(
                modifier = Modifier
                    .background(surface, CircleShape)
                    .then(
                        if (backdrop != null) {
                            Modifier.textureBlur(
                                backdrop = backdrop,
                                shape = CircleShape,
                                blurRadius = with(density) { 25.dp.toPx() },
                                colors = BlurColors(
                                    brightness = 0f,
                                    contrast = 1f,
                                    saturation = 1f,
                                ),
                                highlight = if (isDark) {
                                    Highlight.GlassStrokeMiddleDark
                                } else {
                                    Highlight.GlassStrokeMiddleLight
                                },
                            )
                        } else {
                            Modifier.border(1.dp, borderColor, CircleShape)
                        },
                    )
                    .clip(CircleShape)
                    .selectableGroup()
                    .height(64.dp)
                    .padding(ReferenceBarPadding)
                    .onGloballyPositioned { coordinates ->
                        // coordinates.size.width 已经是 padding 之后的内容宽度，无需再减
                        tabWidthPx = coordinates.size.width.toFloat() / itemCount
                    },
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        if (tabWidthPx > 0f) {
            val tabWidth = with(density) { tabWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(ReferenceBarPadding),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = indicatorOffset.value * tabWidthPx }
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f), CircleShape)
                        .height(56.dp)
                        .width(tabWidth),
                )
            }
        }
    }
}

@Composable
private fun ReferenceLiquidBar(
    modifier: Modifier,
    barWidth: androidx.compose.ui.unit.Dp,
    backdrop: LayerBackdrop?,
    selectedIndex: Int,
    itemCount: Int,
    onSelectionChanged: (Int) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val colors = MiuixTheme.colorScheme
    val isDark = colors.surface.luminance() < 0.5f
    val accent = colors.primary
    val pillShape = CircleShape
    val surfaceContainer = colors.surfaceContainer
    val containerColor = if (backdrop != null) surfaceContainer.copy(alpha = 0.4f) else surfaceContainer
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
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, itemCount - 1)
                val targetValue = targetIndex.toFloat()
                if (this.targetValue != targetValue) animateToValue(targetValue)
                currentOnSelectionChanged(targetIndex)
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

    val interactiveHighlight = remember(animationScope, isLtr) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (drag.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - (drag.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f,
                )
            },
        )
    }
    val baseHighlight = rememberRotatedHighlight(LiquidReferenceHighlight, -45f)
    val pillHighlight = rememberRotatedHighlight(LiquidReferenceHighlight, 90f)
    val tabsBackdrop = if (backdrop != null) rememberLayerBackdrop() else null
    val combinedBackdrop = if (backdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        null
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = 16.dp + bottomInset

    Box(
        modifier = modifier
            .width(barWidth)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    totalWidthPx = coordinates.size.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / itemCount)
                        .coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .dropShadow(
                    shape = pillShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = if (isDark) 0.2f else 0.1f,
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .selectableGroup()
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            highlight = { baseHighlight.copy(alpha = 0.75f) },
                            layerBlock = {
                                val width = size.width.coerceAtLeast(1f)
                                val scale = lerp(
                                    1f,
                                    1f + 16.dp.toPx() / width,
                                    drag.pressProgress,
                                )
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                    } else {
                        Modifier.background(containerColor, pillShape)
                    },
                )
                .then(if (backdrop != null) interactiveHighlight.modifier else Modifier)
                .height(64.dp)
                .padding(ReferenceBarPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalReferenceBarSelection provides selectIndex,
                LocalReferenceBarColor provides colors.onSurface,
                LocalReferenceBarScale provides { 1f },
            ) {
                content()
            }
        }

        if (backdrop != null && tabsBackdrop != null) {
            CompositionLocalProvider(
                LocalReferenceBarSelection provides selectIndex,
                LocalReferenceBarColor provides accent,
                LocalReferenceBarScale provides { lerp(1f, 1.2f, drag.pressProgress) },
                LocalContentColor provides accent,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx(), 4.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .height(56.dp)
                        .padding(horizontal = ReferenceBarPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidth = with(density) { tabWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .padding(horizontal = ReferenceBarPadding)
                    .graphicsLayer {
                        val progressOffset = drag.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }
                    .then(if (backdrop != null) interactiveHighlight.gestureModifier else Modifier)
                    .then(if (backdrop != null) drag.modifier else Modifier)
                    .then(
                        if (backdrop != null && combinedBackdrop != null) {
                            Modifier
                                .drawBackdrop(
                                    backdrop = combinedBackdrop,
                                    shape = { pillShape },
                                    effects = {
                                        val progress = drag.pressProgress
                                        lens(
                                            10.dp.toPx() * progress,
                                            14.dp.toPx() * progress,
                                            depthEffect = true,
                                            chromaticAberration = 0.5f,
                                        )
                                    },
                                    highlight = { pillHighlight.copy(alpha = drag.pressProgress) },
                                    layerBlock = {
                                        scaleX = drag.scaleX
                                        scaleY = drag.scaleY
                                        val velocity = drag.velocity / 10f
                                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                    },
                                    onDrawSurface = {
                                        val progress = drag.pressProgress
                                        drawRect(
                                            if (!isDark) Color.Black.copy(alpha = 0.1f)
                                            else Color.White.copy(alpha = 0.1f),
                                            alpha = 1f - progress,
                                        )
                                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                    },
                                )
                                .innerShadow(shape = pillShape) {
                                    InnerShadow(
                                        radius = 8.dp * drag.pressProgress,
                                        color = Color.Black.copy(alpha = 0.15f),
                                        alpha = drag.pressProgress,
                                    )
                                }
                        } else {
                            Modifier
                                .clip(pillShape)
                                .background(accent.copy(alpha = 0.15f), pillShape)
                        },
                    )
                    .height(56.dp)
                    .width(tabWidth),
            )
        }
}
}

@Composable
private fun rememberRotatedHighlight(base: Highlight, degrees: Float): Highlight {
    val style = base.style as? BloomStroke ?: return base
    val tilt by rememberDeviceTilt()
    val primary = remember(tilt, style.primaryLight, degrees) {
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val magnitudeSquared = gx * gx + gy * gy
        val magnitude = if (magnitudeSquared > 0.01f) sqrt(magnitudeSquared) else 1f
        val x = if (magnitudeSquared > 0.01f) gx / magnitude else 0f
        val y = if (magnitudeSquared > 0.01f) gy / magnitude else -1f
        val radians = degrees * Math.PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        style.primaryLight.copy(
            position = LightPosition(
                x = 0.5f + c * x - s * y,
                y = 0.7f + s * x + c * y,
                z = style.primaryLight.position.z,
            ),
        )
    }
    return remember(base, primary) { base.copy(style = style.copy(primaryLight = primary)) }
}

@Composable
internal fun RowScope.BlurNavigationBarItemV2(
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    val overrideColor = LocalReferenceBarColor.current
    val selection = LocalReferenceBarSelection.current
    val scale = LocalReferenceBarScale.current
    val onSurface = MiuixTheme.colorScheme.onSurfaceContainer
    val baseColor = overrideColor ?: onSurface
    val tint = if (overrideColor != null || selected) baseColor else baseColor.copy(alpha = 0.5f)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            }
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    selection(index)
                    onClick()
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
                fontWeight = if (overrideColor == null && selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

private val LiquidReferenceHighlight = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f,
        ),
        dualPeak = true,
    ),
)
