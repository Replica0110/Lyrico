package com.lonx.lyrico.ui.components.lyrics

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.normalizedLyricLineOrder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricLineOrderBottomSheetContent(
    lineOrder: List<LyricLineTrack>,
    visibleTracks: List<LyricLineTrack>,
    onLineOrderChange: (List<LyricLineTrack>) -> Unit
) {
    var currentVisibleOrder by remember(lineOrder, visibleTracks) {
        mutableStateOf(lineOrder.normalizedLyricLineOrder().filter { it in visibleTracks })
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
            .padding(bottom = 32.dp)
    ) {
        SmallTitle(
            text = stringResource(R.string.lyric_line_order),
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        )
        Text(
            text = stringResource(R.string.lyric_line_order_hint),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer
            )
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                overscrollEffect = null
            ) {
                itemsIndexed(
                    items = currentVisibleOrder,
                    key = { _, track -> track.name }
                ) { _, track ->
                    ReorderableItem(
                        state = reorderableState,
                        key = track.name
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        BasicComponent(
                            modifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = {
                                    onLineOrderChange(
                                        mergeVisibleOrder(
                                            fullOrder = lineOrder,
                                            visibleOrder = currentVisibleOrder
                                        )
                                    )
                                },
                                interactionSource = interactionSource
                            ),
                            endActions = {
                                Icon(
                                    imageVector = MiuixIcons.Basic.ArrowUpDown,
                                    contentDescription = stringResource(R.string.lyric_line_order_drag_handle)
                                )
                            }
                        ) {
                            Text(
                                text = stringResource(track.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
