package com.lonx.lyrico.ui.components.fab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme


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
    icon: ImageVector,
    modifier: Modifier = Modifier,
    position: ExpandableFabMenuPosition = ExpandableFabMenuPosition.BottomEnd,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible && expanded,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
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

    when {
        position.isHorizontal -> {
            HorizontalExpandableFabMenu(
                visible = visible,
                expanded = expanded,
                enabled = enabled,
                position = position,
                modifier = baseModifier,
                icon = icon,
                onExpandedChange = onExpandedChange,
                menuContent = menuContent
            )
        }

        else -> {
            VerticalExpandableFabMenu(
                visible = visible,
                expanded = expanded,
                enabled = enabled,
                position = position,
                modifier = baseModifier,
                icon = icon,
                onExpandedChange = onExpandedChange,
                menuContent = menuContent
            )
        }
    }
}
@Composable
private fun VerticalExpandableFabMenu(
    visible: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    position: ExpandableFabMenuPosition,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        val horizontalAlignment = when (position) {
            ExpandableFabMenuPosition.BottomStart,
            ExpandableFabMenuPosition.TopStart -> Alignment.Start

            else -> Alignment.End
        }

        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (position.expandTowardBottom) {
                MainFab(
                    expanded = expanded,
                    enabled = enabled,
                    icon = icon,
                    onExpandedChange = onExpandedChange
                )
            }

            AnimatedVisibility(
                visible = expanded && enabled,
                enter = slideInVertically {
                    if (position.expandTowardBottom) -it / 2 else it / 2
                } + fadeIn(),
                exit = slideOutVertically {
                    if (position.expandTowardBottom) -it / 2 else it / 2
                } + fadeOut()
            ) {
                Column(
                    horizontalAlignment = horizontalAlignment,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(
                        top = if (position.expandTowardBottom) 8.dp else 0.dp,
                        bottom = if (position.expandTowardBottom) 0.dp else 8.dp
                    ),
                    content = menuContent
                )
            }

            if (!position.expandTowardBottom) {
                MainFab(
                    expanded = expanded,
                    enabled = enabled,
                    icon = icon,
                    onExpandedChange = onExpandedChange
                )
            }
        }
    }
}

@Composable
private fun HorizontalExpandableFabMenu(
    visible: Boolean,
    expanded: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    position: ExpandableFabMenuPosition,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (position == ExpandableFabMenuPosition.CenterEnd) {
                AnimatedHorizontalMenu(
                    expanded = expanded,
                    enabled = enabled,
                    expandTowardEnd = false,
                    menuContent = menuContent
                )
            }

            MainFab(
                expanded = expanded,
                enabled = enabled,
                icon = icon,
                onExpandedChange = onExpandedChange
            )

            if (position == ExpandableFabMenuPosition.CenterStart) {
                AnimatedHorizontalMenu(
                    expanded = expanded,
                    enabled = enabled,
                    expandTowardEnd = true,
                    menuContent = menuContent
                )
            }
        }
    }
}

@Composable
private fun AnimatedHorizontalMenu(
    expanded: Boolean,
    enabled: Boolean,
    expandTowardEnd: Boolean,
    menuContent: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = expanded && enabled,
        enter = slideInHorizontally {
            if (expandTowardEnd) -it / 2 else it / 2
        } + fadeIn(),
        exit = slideOutHorizontally {
            if (expandTowardEnd) -it / 2 else it / 2
        } + fadeOut()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = if (expandTowardEnd) Alignment.Start else Alignment.End,
                content = menuContent
            )
        }
    }
}

@Composable
private fun MainFab(
    expanded: Boolean,
    icon: ImageVector,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    FloatingActionButton(
        onClick = {
            if (enabled) {
                onExpandedChange(!expanded)
            }
        }
    ) {
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 45f else 0f,
            label = "expandableFabRotation"
        )

        Icon(
            imageVector = icon,
            contentDescription = "Actions",
            tint = MiuixTheme.colorScheme.onPrimary,
            modifier = Modifier.rotate(rotation)
        )
    }
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

private val ExpandableFabMenuPosition.isHorizontal: Boolean
    get() = this == ExpandableFabMenuPosition.CenterStart ||
            this == ExpandableFabMenuPosition.CenterEnd

private val ExpandableFabMenuPosition.expandTowardBottom: Boolean
    get() = this == ExpandableFabMenuPosition.TopStart ||
            this == ExpandableFabMenuPosition.TopEnd

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