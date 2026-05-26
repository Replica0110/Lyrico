package com.lonx.lyrico.ui.components.selection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private enum class ScrollDirection {
    UP,
    DOWN,
    NONE
}

@Composable
fun Modifier.dragSelection(
    listState: LazyListState,
    itemCount: Int,
    isSelectionMode: Boolean,
    onDragSelectionStart: (index: Int) -> Unit,
    onDragSelectionChange: (startIndex: Int, endIndex: Int) -> Unit,
    onDragSelectionEnd: () -> Unit,
    itemIndexMapper: (lazyListIndex: Int) -> Int? = { it },
    itemInfoMapper: ((itemInfo: LazyListItemInfo) -> Int?)? = null,
): Modifier {
    var initialDragY by remember { mutableStateOf<Float?>(null) }
    var currentDragY by remember { mutableStateOf<Float?>(null) }

    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }

    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableFloatStateOf(0f) }

    var peakDragY by remember { mutableStateOf<Float?>(null) }
    var scrollDirection by remember { mutableStateOf(ScrollDirection.NONE) }

    fun resetDragState() {
        initialDragIndex = null
        currentDragIndex = null
        initialDragY = null
        currentDragY = null

        autoScrollSpeed = 0f
        currentSpeed = 0f
        peakDragY = null
        scrollDirection = ScrollDirection.NONE
    }

    fun mappedItemIndex(itemInfo: LazyListItemInfo): Int? {
        return itemInfoMapper?.invoke(itemInfo) ?: itemIndexMapper(itemInfo.index)
    }

    fun itemInfoAt(y: Float): LazyListItemInfo? {
        val layoutInfo = listState.layoutInfo
        val itemY = y + layoutInfo.viewportStartOffset
        return layoutInfo.visibleItemsInfo.find {
            itemY >= it.offset && itemY <= it.offset + it.size
        }
    }

    LaunchedEffect(listState, isSelectionMode, itemCount) {
        while (isActive) {
            currentSpeed += (autoScrollSpeed - currentSpeed) * 0.2f

            if (autoScrollSpeed == 0f) {
                currentSpeed *= 0.85f
            }

            if (abs(currentSpeed) < 0.5f) {
                currentSpeed = 0f
            }

            if (currentSpeed != 0f && isSelectionMode && itemCount > 0) {
                listState.scrollBy(currentSpeed)

                currentDragY?.let { y ->
                    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                    val clampedY = y.coerceIn(0f, viewportHeight - 1f)

                    val itemInfo = itemInfoAt(clampedY)

                    val startIndex = initialDragIndex
                    if (itemInfo != null && startIndex != null) {
                        val newIndex = mappedItemIndex(itemInfo)
                            ?.coerceIn(0, itemCount - 1)
                            ?: return@let
                        if (newIndex != currentDragIndex) {
                            currentDragIndex = newIndex
                            onDragSelectionChange(startIndex, newIndex)
                        }
                    }
                }
            }

            delay(16)
        }
    }

    if (!isSelectionMode || itemCount <= 0) {
        return this
    }

    return this.pointerInput(itemCount, isSelectionMode) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                initialDragY = offset.y
                currentDragY = offset.y

                peakDragY = null
                scrollDirection = ScrollDirection.NONE

                val itemInfo = itemInfoAt(offset.y)

                itemInfo?.let {
                    val index = mappedItemIndex(it)
                        ?.coerceIn(0, itemCount - 1)
                        ?: return@let
                    initialDragIndex = index
                    currentDragIndex = index
                    onDragSelectionStart(index)
                }
            },
            onDrag = { change, _ ->
                val y = change.position.y
                currentDragY = y

                val retreatThreshold = 20f
                val startThreshold = 180f
                val speedFactor = 0.1f
                val maxSpeed = 60f

                initialDragY?.let { startY ->
                    val dragDistance = y - startY

                    when (scrollDirection) {
                        ScrollDirection.NONE -> {
                            if (dragDistance > startThreshold) {
                                scrollDirection = ScrollDirection.DOWN
                                peakDragY = y
                            } else if (dragDistance < -startThreshold) {
                                scrollDirection = ScrollDirection.UP
                                peakDragY = y
                            }
                        }

                        ScrollDirection.DOWN -> {
                            peakDragY = maxOf(peakDragY ?: y, y)

                            if (y < (peakDragY!! - retreatThreshold)) {
                                scrollDirection = ScrollDirection.NONE
                                peakDragY = null
                            } else if (dragDistance < 0) {
                                scrollDirection = ScrollDirection.NONE
                                peakDragY = null
                            }
                        }

                        ScrollDirection.UP -> {
                            peakDragY = minOf(peakDragY ?: y, y)

                            if (y > (peakDragY!! + retreatThreshold)) {
                                scrollDirection = ScrollDirection.NONE
                                peakDragY = null
                            } else if (dragDistance > 0) {
                                scrollDirection = ScrollDirection.NONE
                                peakDragY = null
                            }
                        }
                    }

                    autoScrollSpeed = when (scrollDirection) {
                        ScrollDirection.DOWN -> {
                            ((dragDistance - startThreshold) * speedFactor)
                                .coerceAtMost(maxSpeed)
                        }

                        ScrollDirection.UP -> {
                            ((dragDistance + startThreshold) * speedFactor)
                                .coerceAtLeast(-maxSpeed)
                        }

                        ScrollDirection.NONE -> 0f
                    }
                }

                val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                val clampedY = y.coerceIn(0f, viewportHeight - 1f)

                val itemInfo = itemInfoAt(clampedY)

                val startIndex = initialDragIndex
                if (itemInfo != null && startIndex != null) {
                    val newIndex = mappedItemIndex(itemInfo)
                        ?.coerceIn(0, itemCount - 1)
                        ?: return@detectDragGesturesAfterLongPress
                    if (newIndex != currentDragIndex) {
                        currentDragIndex = newIndex
                        onDragSelectionChange(startIndex, newIndex)
                    }
                }
            },
            onDragEnd = {
                resetDragState()
                onDragSelectionEnd()
            },
            onDragCancel = {
                resetDragState()
                onDragSelectionEnd()
            }
        )
    }
}
