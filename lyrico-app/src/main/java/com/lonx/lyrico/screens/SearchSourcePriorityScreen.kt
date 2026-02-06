package com.lonx.lyrico.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrics.model.Source
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.JustifiedRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.innerPadding
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "search_source_priority")
fun SearchSourcePriorityScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // 维护本地列表状态用于实时排序
    var currentList by remember(uiState.searchSourceOrder) {
        mutableStateOf(uiState.searchSourceOrder)
    }

    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // 初始化库提供的 Reorderable 状态
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 当项发生位移时实时更新本地列表
        currentList = currentList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        // 移动时的轻微震动反馈
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }


    BasicScreenBox(
        title = "搜索源优先级",
        onBack = { navigator.popBackStack() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ItemTip(text = "长按拖动以调整搜索源的优先级顺序，优先级影响批量匹配时使用的搜索源顺序，以及单曲搜索时的默认源")
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = currentList,
                    key = { _, source -> source.sourceName }
                ) { index, source ->
                    ReorderableItem(
                        reorderableLazyColumnState,
                        source.sourceName
                    ) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }

                        val elevation by animateDpAsState(if (isDragging) 2.dp else 0.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation)
                                .background(SaltTheme.colors.subBackground)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        viewModel.setSearchSourceOrder(currentList)
                                    },
                                    interactionSource = interactionSource
                                )
                        ) {

                            ReorderableSourceItem(
                                index = index,
                                source = source,
                                showDivider = index != currentList.lastIndex
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(UnstableSaltUiApi::class)
@Composable
private fun ReorderableSourceItem(
    modifier: Modifier = Modifier,
    index: Int,
    source: Source,
    showDivider: Boolean = false
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaltTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(SaltTheme.dimens.item)
                .innerPadding(vertical = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(SaltTheme.dimens.itemIcon),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = SaltTheme.colors.subText,
                    fontWeight = FontWeight.Bold,
                    style = SaltTheme.textStyles.main
                )
            }

            Spacer(modifier = Modifier.width(SaltTheme.dimens.subPadding))

            JustifiedRow(
                startContent = {
                    Column {
                        Text(
                            text = source.sourceName,
                            color = SaltTheme.colors.text,
                            style = SaltTheme.textStyles.main
                        )
                    }
                },
                endContent = {
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .innerPadding(horizontal = false),
                verticalAlignment = Alignment.CenterVertically
            )


                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.ic_draghandle_24dp),
                    contentDescription = "拖动排序",
                    tint = SaltTheme.colors.subText.copy(alpha = 0.5f)
                )

        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = SaltTheme.colors.subText.copy(alpha = 0.05f),
                thickness = 0.5.dp
            )
        }
    }
}