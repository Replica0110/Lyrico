package com.lonx.lyrico.ui.components.library

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

/** Shared song/artist selection gestures, reveal labels, and selected-row styling. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SelectableLibraryItem(
    itemKey: Any,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    swipeSelectionLabel: String? = null,
    swipeSelectionSecondaryLabel: String? = null,
    onClick: () -> Unit,
    onToggleSelection: (() -> Unit)? = null,
    onSwipeSelection: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val maxSwipeOffsetPx = swipeThresholdPx * 1.35f
    var swipeOffsetX by remember(itemKey) { mutableFloatStateOf(0f) }
    var isSwipeDragging by remember(itemKey) { mutableStateOf(false) }
    val animatedSwipeOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = spring(),
        label = "SongListItemSwipeOffset"
    )
    val displayedSwipeOffsetX = if (isSwipeDragging) swipeOffsetX else animatedSwipeOffsetX
    val revealWidth = with(density) { abs(displayedSwipeOffsetX).toDp() }

    val salt = com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT
    val selectionColor = if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight else MiuixTheme.colorScheme.primary
    val surfaceColor = if (salt) com.moriafly.salt.ui.SaltTheme.colors.background else MiuixTheme.colorScheme.surface
    val backgroundColor =
        if (isSelected) {
            selectionColor.copy(alpha = 0.1f)
        } else {
            surfaceColor
        }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        if (onSwipeSelection != null && swipeSelectionLabel != null && revealWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(
                            if (displayedSwipeOffsetX >= 0f) {
                                Alignment.CenterStart
                            } else {
                                Alignment.CenterEnd
                            }
                        )
                        .width(revealWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                        .background(selectionColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (salt) {
                            com.moriafly.salt.ui.Text(
                                text = swipeSelectionLabel,
                                color = selectionColor,
                                style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )

                            swipeSelectionSecondaryLabel?.let { secondaryLabel ->
                                com.moriafly.salt.ui.Text(
                                    text = secondaryLabel,
                                    color = selectionColor,
                                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        } else {
                            Text(
                                text = swipeSelectionLabel,
                                color = selectionColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )

                            swipeSelectionSecondaryLabel?.let { secondaryLabel ->
                                Text(
                                    text = secondaryLabel,
                                    color = selectionColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = displayedSwipeOffsetX
                }
                .background(backgroundColor)
            .then(
                if (onSwipeSelection != null) {
                    Modifier.pointerInput(onSwipeSelection, swipeThresholdPx, maxSwipeOffsetPx) {
                        var totalDragX = 0f

                        detectHorizontalDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                isSwipeDragging = true
                                swipeOffsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDragX += dragAmount
                                swipeOffsetX = totalDragX.coerceIn(
                                    -maxSwipeOffsetPx,
                                    maxSwipeOffsetPx
                                )
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDragX) >= swipeThresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onSwipeSelection()
                                }
                                totalDragX = 0f
                                swipeOffsetX = 0f
                                isSwipeDragging = false
                            },
                            onDragCancel = {
                                totalDragX = 0f
                                swipeOffsetX = 0f
                                isSwipeDragging = false
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelection?.invoke()
                    } else {
                        onClick()
                    }
                },
                onLongClick = if (isSelectionMode) {
                    null
                } else {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleSelection?.invoke()
                    }
                }
            )
            .padding(vertical = 8.dp)
        ) {
            content()
        }
    }
}
