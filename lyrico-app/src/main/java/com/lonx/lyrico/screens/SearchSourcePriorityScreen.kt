package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.lonx.lyrics.model.Source
import com.ramcosta.composedestinations.generated.destinations.SearchSourceConfigDestination
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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

    // 维护启用/禁用状态
    var enabledSources by remember(uiState.enabledSearchSources) {
        mutableStateOf(uiState.enabledSearchSources)
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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.search_source_priority),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.navigateUp() }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.search_source_priority_tip),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(12.dp)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .fillMaxHeight()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                overscrollEffect = null,
            ) {
                itemsIndexed(
                    items = currentList,
                    key = { _, source -> source.labelRes }
                ) { index, source ->
                    ReorderableItem(
                        state = reorderableLazyColumnState,
                        key = source.labelRes
                    ) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isEnabled = enabledSources.contains(source)

                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        viewModel.setSearchSourceOrder(currentList)
                                        viewModel.setEnabledSearchSources(enabledSources)
                                    },
                                    interactionSource = interactionSource
                                )
                        ) {
                            ReorderableSourceItem(
                                modifier = Modifier.background(if (isDragging) MiuixTheme.colorScheme.secondary else MiuixTheme.colorScheme.background),
                                index = index,
                                source = source,
                                isEnabled = isEnabled,
                                onEnabledChanged = { newState ->
                                    // 不允许禁用最后一个启用的源
                                    if (!newState && enabledSources.size == 1) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                        return@ReorderableSourceItem
                                    }
                                    
                                    enabledSources = if (newState) {
                                        enabledSources + source
                                    } else {
                                        enabledSources - source
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.setEnabledSearchSources(enabledSources)
                                },
                                onClick = {
                                    navigator.navigate(SearchSourceConfigDestination(source.name))
                                }
                            )
                        }
                    }
                }

            }

        }
    }
}


@Composable
fun ReorderableSourceItem(
    modifier: Modifier = Modifier,
    index: Int,
    source: Source,
    isEnabled: Boolean = true,
    onEnabledChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit = {}
) {
    BasicComponent(
        modifier = modifier,
        title = stringResource(id = source.labelRes),
        onClick = {
            onClick()
        },
        startAction = {
            Text(
                text = "${index + 1}",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontWeight = FontWeight.Bold,
                fontSize = MiuixTheme.textStyles.body1.fontSize
            )
        },
        endActions = {
            Row(
             verticalAlignment = Alignment.CenterVertically,
            ){
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChanged
                )
            }
        }
    )
}
