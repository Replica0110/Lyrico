package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SourcePluginEntity
import com.lonx.lyrico.viewmodel.PluginDebugViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginDebugDestination
import com.ramcosta.composedestinations.generated.destinations.SearchSourceConfigDestination
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
import top.yukonga.miuix.kmp.basic.TextButton
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
    val viewModel: PluginDebugViewModel = koinViewModel()
    val plugins by viewModel.plugins.collectAsState()
    var currentList by remember(plugins) { mutableStateOf(plugins) }
    val lazyListState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val reorderableLazyColumnState = rememberReorderableLazyListState(lazyListState) { from, to ->
        currentList = currentList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(id = R.string.search_source_priority),
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
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
                contentPadding = PaddingValues(bottom = 12.dp),
                overscrollEffect = null,
            ) {
                if (currentList.isEmpty()) {
                    item("empty") {
                        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.plugin_debug_empty),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                )
                                TextButton(
                                    text = stringResource(R.string.plugin_debug_import_archive),
                                    onClick = { navigator.navigate(PluginDebugDestination()) },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = currentList,
                    key = { _, plugin -> plugin.id }
                ) { index, plugin ->
                    ReorderableItem(
                        state = reorderableLazyColumnState,
                        key = plugin.id
                    ) { isDragging ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragStopped = {
                                        viewModel.setPluginOrder(currentList)
                                    },
                                    interactionSource = interactionSource
                                )
                        ) {
                            ReorderablePluginItem(
                                modifier = Modifier.background(if (isDragging) MiuixTheme.colorScheme.secondary else MiuixTheme.colorScheme.background),
                                index = index,
                                plugin = plugin,
                                onEnabledChanged = { enabled -> viewModel.setEnabled(plugin.id, enabled) },
                                onClick = { navigator.navigate(SearchSourceConfigDestination(plugin.id)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderablePluginItem(
    modifier: Modifier = Modifier,
    index: Int,
    plugin: SourcePluginEntity,
    onEnabledChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    BasicComponent(
        modifier = modifier,
        title = plugin.name,
        summary = "${plugin.id}  v${plugin.versionName}",
        onClick = onClick,
        startAction = {
            Text(
                text = "${index + 1}",
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontWeight = FontWeight.Bold,
                fontSize = MiuixTheme.textStyles.body1.fontSize
            )
        },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = onEnabledChanged
                )
            }
        }
    )
}
