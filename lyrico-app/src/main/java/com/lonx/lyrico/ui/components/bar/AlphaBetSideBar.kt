package com.lonx.lyrico.ui.components.bar

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.Icon as SaltIcon
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface AlphabetSideBarItem {
    data object ScrollTop : AlphabetSideBarItem
    data class Section(val value: String) : AlphabetSideBarItem
}

@Composable
fun AlphabetSideBar(
    sections: List<String>,
    sectionIndexMap: Map<String, Int>,
    order: SortOrder,
    scrollController: AlphabetSideBarScrollController,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var currentItem by remember { mutableStateOf<AlphabetSideBarItem?>(null) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }
    var indicatorItem by remember {
        mutableStateOf<AlphabetSideBarItem?>(null)
    }

    var indicatorVisible by remember {
        mutableStateOf(false)
    }
    val items = remember(sections) {
        listOf<AlphabetSideBarItem>(AlphabetSideBarItem.ScrollTop) +
                sections.map { AlphabetSideBarItem.Section(it) }
    }

    val resolvedSectionIndexMap = remember(sectionIndexMap, sections, order) {
        sections.associateWith { section ->
            findScrollIndex(
                section = section,
                sectionIndexMap = sectionIndexMap,
                order = order
            )
        }
    }

    LaunchedEffect(sections, sectionIndexMap, order, scrollController) {
        scrollJob?.cancel()
        scrollJob = null
        currentItem = null
        indicatorItem = null
        indicatorVisible = false
        lastSelectedIndex = -1
    }
    LaunchedEffect(indicatorVisible) {
        if (!indicatorVisible) {
            delay(180)
            if (!indicatorVisible) {
                indicatorItem = null
            }
        }
    }

    fun scrollToIndexFromSideBar(index: Int) {
        if (index < 0) return

        if (scrollController.firstVisibleItemIndex == index) {
            return
        }

        scrollJob?.cancel()
        scrollJob = scope.launch {
            scrollController.scrollToItem(index)
        }
    }

    fun handleItemSelected(item: AlphabetSideBarItem) {
        when (item) {
            AlphabetSideBarItem.ScrollTop -> {
                scrollToIndexFromSideBar(0)
            }

            is AlphabetSideBarItem.Section -> {
                val targetIndex = resolvedSectionIndexMap[item.value] ?: 0
                scrollToIndexFromSideBar(targetIndex)
            }
        }
    }

    fun updateSelection(index: Int) {
        if (index == -1 || index == lastSelectedIndex) return
        if (index !in items.indices) return

        lastSelectedIndex = index

        val item = items[index]

        currentItem = item
        indicatorItem = item
        indicatorVisible = true

        handleItemSelected(item)

        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun clearSelection() {
        currentItem = null
        indicatorVisible = false
        lastSelectedIndex = -1
    }

    BoxWithConstraints(
        modifier = modifier
    ) {
        if (items.isEmpty()) {
            return@BoxWithConstraints
        }

        val preferredCellSize = 20.dp
        val itemCount = items.size

        val cellSize = (maxHeight / itemCount.toFloat())
            .coerceAtMost(preferredCellSize)
            .coerceAtLeast(10.dp)

        val barHeight = cellSize * itemCount

        val cellHeightPx = with(density) { cellSize.toPx() }

        fun getItemIndex(offsetY: Float): Int {
            if (cellHeightPx <= 0f) return -1

            return (offsetY / cellHeightPx)
                .toInt()
                .coerceIn(0, items.lastIndex)
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(maxHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            AlphabetSideBarIndicator(
                item = indicatorItem,
                visible = indicatorVisible
            )

            Box(
                modifier = Modifier.width(12.dp)
            )

            Column(
                modifier = Modifier
                    .width(cellSize)
                    .height(barHeight)
                    .pointerInput(items, cellSize, barHeight, resolvedSectionIndexMap) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                updateSelection(getItemIndex(offset.y))
                            },
                            onDragEnd = {
                                clearSelection()
                            },
                            onDragCancel = {
                                clearSelection()
                            }
                        ) { change, _ ->
                            change.consume()
                            updateSelection(getItemIndex(change.position.y))
                        }
                    }
                    .pointerInput(items, cellSize, barHeight, resolvedSectionIndexMap) {
                        detectTapGestures(
                            onPress = { offset ->
                                updateSelection(getItemIndex(offset.y))
                                tryAwaitRelease()
                                clearSelection()
                            }
                        )
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEach { item ->
                    AlphabetSideBarCell(
                        item = item,
                        selected = currentItem == item,
                        size = cellSize
                    )
                }
            }
        }
    }
}
@Composable
private fun AlphabetSideBarIndicator(
    item: AlphabetSideBarItem?,
    visible: Boolean
) {
    // 指示气泡按引擎取语义色：Salt 高亮底 + onHighlight 前景，Miuix 保持原色。
    val isSalt = LocalUiEngine.current == UiEngine.SALT
    AnimatedVisibility(
        visible = visible && item != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        if (item != null) {
            Box(
                modifier = Modifier
                    .width(50.dp)
                    .height(50.dp)
                    .background(
                        color = if (isSalt) SaltTheme.colors.highlight else MiuixTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (item) {
                    AlphabetSideBarItem.ScrollTop -> {
                        if (isSalt) {
                            SaltIcon(
                                painter = painterResource(R.drawable.ic_arrow_up_24dp),
                                modifier = Modifier.size(30.dp),
                                contentDescription = null,
                                tint = SaltTheme.colors.onHighlight
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_up_24dp),
                                modifier = Modifier.size(30.dp),
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    is AlphabetSideBarItem.Section -> {
                        if (isSalt) {
                            SaltText(
                                text = item.value,
                                style = SaltTheme.textStyles.main,
                                color = SaltTheme.colors.onHighlight,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = item.value,
                                style = MiuixTheme.textStyles.title1,
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
interface AlphabetSideBarScrollController {
    val firstVisibleItemIndex: Int
    suspend fun scrollToItem(index: Int)
}

private class LazyListAlphabetScrollController(
    private val state: LazyListState
) : AlphabetSideBarScrollController {
    override val firstVisibleItemIndex: Int
        get() = state.firstVisibleItemIndex

    override suspend fun scrollToItem(index: Int) {
        state.scrollToItem(index)
    }
}

private class LazyGridAlphabetScrollController(
    private val state: LazyGridState
) : AlphabetSideBarScrollController {
    override val firstVisibleItemIndex: Int
        get() = state.firstVisibleItemIndex

    override suspend fun scrollToItem(index: Int) {
        state.scrollToItem(index)
    }
}

@Composable
fun rememberAlphabetSideBarScrollController(
    state: LazyListState
): AlphabetSideBarScrollController {
    return remember(state) {
        LazyListAlphabetScrollController(state)
    }
}

@Composable
fun rememberAlphabetSideBarScrollController(
    state: LazyGridState
): AlphabetSideBarScrollController {
    return remember(state) {
        LazyGridAlphabetScrollController(state)
    }
}
@Composable
private fun AlphabetSideBarCell(
    item: AlphabetSideBarItem,
    selected: Boolean,
    size: Dp
) {
    // 字号随格位高度推导（最小格仅 10dp），是布局约束而非照搬某个引擎的主题字号。
    val fontSize = when {
        size < 15.dp -> 6.sp
        size < 20.dp -> 9.sp
        size < 24.dp -> 10.sp
        else -> 12.sp
    }
    val iconSize = when {
        size < 15.dp -> size * 0.78f
        size < 20.dp -> size * 0.70f
        size < 24.dp -> size * 0.66f
        else -> size * 0.62f
    }
    // 单元格按引擎取语义色：Salt 用 highlight/subText，Miuix 保持原色。
    val isSalt = LocalUiEngine.current == UiEngine.SALT
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when (item) {
            AlphabetSideBarItem.ScrollTop -> {
                if (isSalt) {
                    SaltIcon(
                        painter = painterResource(R.drawable.ic_arrow_up_24dp),
                        contentDescription = null,
                        tint = if (selected) {
                            SaltTheme.colors.highlight
                        } else {
                            SaltTheme.colors.subText
                        },
                        modifier = Modifier.size(iconSize)
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up_24dp),
                        contentDescription = null,
                        tint = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        },
                        modifier = Modifier.size(iconSize)
                    )
                }
            }

            is AlphabetSideBarItem.Section -> {
                if (isSalt) {
                    SaltText(
                        text = item.value,
                        style = SaltTheme.textStyles.sub.copy(fontSize = fontSize),
                        color = if (selected) {
                            SaltTheme.colors.highlight
                        } else {
                            SaltTheme.colors.subText
                        },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = item.value,
                        style = MiuixTheme.textStyles.body2.copy(fontSize = fontSize),
                        color = if (selected) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainer
                        },
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}


fun findScrollIndex(
    section: String,
    sectionIndexMap: Map<String, Int>,
    order: SortOrder
): Int {
    if (sectionIndexMap.isEmpty()) return 0

    sectionIndexMap[section]?.let { return it }

    val keys = sectionIndexMap.keys.sorted()
    if (keys.isEmpty()) return 0

    return if (order == SortOrder.ASC) {
        keys.firstOrNull { it >= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.last()]
            ?: 0
    } else {
        keys.lastOrNull { it <= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.first()]
            ?: 0
    }
}