@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.lyrics

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.lyrics.LyricLineTrack
import com.lonx.lyrico.data.lyrics.normalizedLyricLineOrder
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricLineOrderBottomSheetContent(
    lineOrder: List<LyricLineTrack>,
    visibleTracks: List<LyricLineTrack>,
    onLineOrderChange: (List<LyricLineTrack>) -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    var currentVisibleOrder by remember(lineOrder, visibleTracks) {
        mutableStateOf(lineOrder.normalizedLyricLineOrder().filter { it in visibleTracks })
    }
    // Pointer-input callbacks can outlive recomposition while dragging the same row again.
    val saveOrder by rememberUpdatedState {
        onLineOrderChange(mergeVisibleOrder(lineOrder, currentVisibleOrder))
    }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        currentVisibleOrder = currentVisibleOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        if (salt) {
            com.moriafly.salt.ui.Text(
                text = stringResource(R.string.lyric_line_order_hint),
                color = SaltTheme.colors.subText,
                style = SaltTheme.textStyles.sub,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.lyric_line_order_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            contentPadding = PaddingValues(horizontal = if (salt) 16.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            overscrollEffect = null,
        ) {
            items(
                items = currentVisibleOrder,
                key = { track -> track.name },
            ) { track ->
                ReorderableItem(
                    state = reorderableState,
                    key = track.name
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val dragModifier = Modifier.longPressDraggableHandle(
                        onDragStarted = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = { saveOrder() },
                        interactionSource = interactionSource,
                    )
                    if (salt) {
                        com.moriafly.salt.ui.Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = dragModifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.moriafly.salt.ui.Text(
                                    text = stringResource(track.labelRes),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                com.moriafly.salt.ui.Icon(
                                    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.GripVertical),
                                    contentDescription = stringResource(R.string.lyric_line_order_drag_handle),
                                    tint = SaltTheme.colors.subText,
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                        ) {
                            BasicComponent(
                                modifier = dragModifier,
                                title = stringResource(track.labelRes),
                                endActions = {
                                    Icon(
                                        imageVector = LucideIcons.GripVertical,
                                        contentDescription = stringResource(R.string.lyric_line_order_drag_handle),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun mergeVisibleOrder(
    fullOrder: List<LyricLineTrack>,
    visibleOrder: List<LyricLineTrack>
): List<LyricLineTrack> {
    val pendingVisible = ArrayDeque(visibleOrder)
    val visibleSet = visibleOrder.toSet()
    val merged = fullOrder.normalizedLyricLineOrder().map { track ->
        if (track in visibleSet) pendingVisible.removeFirst() else track
    }
    return (merged + pendingVisible).normalizedLyricLineOrder()
}
