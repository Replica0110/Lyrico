package com.lonx.lyrico.ui.components.fab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine

enum class ExpandableFabMenuPosition {
    BottomEnd,
    BottomStart,
    TopEnd,
    TopStart,
    CenterStart,
    CenterEnd
}

@Composable
fun BoxScope.ExpandableFabMenu(
    visible: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    itemCount: Int,
    modifier: Modifier = Modifier,
    style: ExpandableFabMenuStyle = ExpandableFabMenuStyle.default(),
    position: ExpandableFabMenuPosition = ExpandableFabMenuPosition.BottomEnd,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    val effectiveExpanded = visible && expanded && enabled

    AnimatedVisibility(
        visible = effectiveExpanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(style.scrimColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onExpandedChange(false)
                }
        )
    }

    val baseModifier = modifier
        .align(position.alignment)
        .then(
            position.windowInsets?.let { Modifier.windowInsetsPadding(it) } ?: Modifier
        )
        .padding(position.padding)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = baseModifier
    ) {
        MorphExpandableFabMenu(
            expanded = effectiveExpanded,
            enabled = enabled,
            position = position,
            style = style,
            onExpandedChange = onExpandedChange,
            menuContent = menuContent,
            itemCount = itemCount
        )
    }
}

@Composable
private fun MorphExpandableFabMenu(
    expanded: Boolean,
    enabled: Boolean,
    position: ExpandableFabMenuPosition,
    itemCount: Int,
    style: ExpandableFabMenuStyle,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    val expandedHeight = style.expandedHeightFor(itemCount)

    val transition = updateTransition(
        targetState = expanded,
        label = "expandableFabMenuTransition"
    )

    val width by transition.animateDp(
        transitionSpec = { spring(stiffness = 600f) },
        label = "fabMenuWidth"
    ) { isExpanded ->
        if (isExpanded) style.expandedWidth else style.mainFabSize
    }

    val height by transition.animateDp(
        transitionSpec = { spring(stiffness = 600f) },
        label = "fabMenuHeight"
    ) { isExpanded ->
        if (isExpanded) expandedHeight else style.mainFabSize
    }

    val containerColor by transition.animateColor(
        transitionSpec = { spring(stiffness = 600f) },
        label = "fabMenuContainerColor"
    ) { isExpanded ->
        if (isExpanded) style.expandedContainerColor else style.mainContainerColor
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(style.cornerRadius))
            .background(containerColor)
            .clickable(
                enabled = enabled && !expanded,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onExpandedChange(true)
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = expanded,
            animationSpec = tween(durationMillis = 120),
            label = "expandableFabMenuContent"
        ) { isExpanded ->
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .width(style.expandedWidth)
                        .height(expandedHeight)
                        .padding(style.contentPadding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalAlignment = position.horizontalContentAlignment,
                    content = menuContent
                )
            } else {
                Box(
                    modifier = Modifier.size(style.mainFabSize),
                    contentAlignment = Alignment.Center
                ) {
                    CollapsedFabIcon(
                        style = style
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedFabIcon(
    style: ExpandableFabMenuStyle
) {
    if (LocalLibraryUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Icon(
            painter = rememberVectorPainter(style.mainIcon),
            contentDescription = "Actions",
            tint = style.mainContentColor,
            modifier = Modifier.size(style.mainIconSize),
        )
        return
    }
    Icon(
        imageVector = style.mainIcon,
        contentDescription = "Actions",
        tint = style.mainContentColor,
        modifier = Modifier
            .size(style.mainIconSize)
    )
}

private val ExpandableFabMenuPosition.alignment: Alignment
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd -> Alignment.BottomEnd
        ExpandableFabMenuPosition.BottomStart -> Alignment.BottomStart
        ExpandableFabMenuPosition.TopEnd -> Alignment.TopEnd
        ExpandableFabMenuPosition.TopStart -> Alignment.TopStart
        ExpandableFabMenuPosition.CenterStart -> Alignment.CenterStart
        ExpandableFabMenuPosition.CenterEnd -> Alignment.CenterEnd
    }

private val ExpandableFabMenuPosition.padding: PaddingValues
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd,
        ExpandableFabMenuPosition.CenterEnd -> PaddingValues(
            end = 16.dp,
            bottom = if (this == ExpandableFabMenuPosition.BottomEnd) 24.dp else 0.dp
        )

        ExpandableFabMenuPosition.BottomStart,
        ExpandableFabMenuPosition.CenterStart -> PaddingValues(
            start = 16.dp,
            bottom = if (this == ExpandableFabMenuPosition.BottomStart) 24.dp else 0.dp
        )

        ExpandableFabMenuPosition.TopEnd -> PaddingValues(
            top = 24.dp,
            end = 16.dp
        )

        ExpandableFabMenuPosition.TopStart -> PaddingValues(
            top = 24.dp,
            start = 16.dp
        )
    }

private val ExpandableFabMenuPosition.windowInsets: WindowInsets?
    @Composable
    get() = when (this) {
        ExpandableFabMenuPosition.TopStart,
        ExpandableFabMenuPosition.TopEnd -> WindowInsets.statusBars

        ExpandableFabMenuPosition.BottomStart,
        ExpandableFabMenuPosition.BottomEnd -> WindowInsets.navigationBars

        ExpandableFabMenuPosition.CenterStart,
        ExpandableFabMenuPosition.CenterEnd -> null
    }
private val ExpandableFabMenuPosition.sizeAnimationAlignment: Alignment
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd -> Alignment.BottomEnd
        ExpandableFabMenuPosition.BottomStart -> Alignment.BottomStart
        ExpandableFabMenuPosition.TopEnd -> Alignment.TopEnd
        ExpandableFabMenuPosition.TopStart -> Alignment.TopStart
        ExpandableFabMenuPosition.CenterStart -> Alignment.CenterStart
        ExpandableFabMenuPosition.CenterEnd -> Alignment.CenterEnd
    }

private val ExpandableFabMenuPosition.contentAlignment: Alignment
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd -> Alignment.BottomEnd
        ExpandableFabMenuPosition.BottomStart -> Alignment.BottomStart
        ExpandableFabMenuPosition.TopEnd -> Alignment.TopEnd
        ExpandableFabMenuPosition.TopStart -> Alignment.TopStart
        ExpandableFabMenuPosition.CenterStart -> Alignment.CenterStart
        ExpandableFabMenuPosition.CenterEnd -> Alignment.CenterEnd
    }

private val ExpandableFabMenuPosition.horizontalContentAlignment: Alignment.Horizontal
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd,
        ExpandableFabMenuPosition.TopEnd,
        ExpandableFabMenuPosition.CenterEnd -> Alignment.End

        ExpandableFabMenuPosition.BottomStart,
        ExpandableFabMenuPosition.TopStart,
        ExpandableFabMenuPosition.CenterStart -> Alignment.Start
    }
@Composable
fun ExpandableFabMenuStyle.expandedHeightFor(itemCount: Int): Dp {

    val verticalPadding = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()

    return (itemHeight * itemCount.coerceAtLeast(1) + verticalPadding)
        .coerceIn(minExpandedHeight, maxExpandedHeight)
}
private val ExpandableFabMenuPosition.transformOrigin: TransformOrigin
    get() = when (this) {
        ExpandableFabMenuPosition.BottomEnd -> TransformOrigin(1f, 1f)
        ExpandableFabMenuPosition.BottomStart -> TransformOrigin(0f, 1f)
        ExpandableFabMenuPosition.TopEnd -> TransformOrigin(1f, 0f)
        ExpandableFabMenuPosition.TopStart -> TransformOrigin(0f, 0f)
        ExpandableFabMenuPosition.CenterStart -> TransformOrigin(0f, 0.5f)
        ExpandableFabMenuPosition.CenterEnd -> TransformOrigin(1f, 0.5f)
    }
