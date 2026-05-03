package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.SourceUsage
import com.lonx.lyrico.data.model.SourceUsageConfig
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "search_source_priority")
fun SearchSourcePriorityScreen(
    navigator: DestinationsNavigator,
    usageName: String = SourceUsage.LYRICS.name
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val usage = remember(usageName) {
        runCatching { SourceUsage.valueOf(usageName) }.getOrDefault(SourceUsage.LYRICS)
    }
    val sourceConfig = uiState.sourceUsageConfig(usage)
    val supportedSources = uiState.sourcesForUsage(usage)

    var currentList by remember(sourceConfig.sourceOrder, supportedSources) {
        mutableStateOf(sourceConfig.sourceOrder.filter { it in supportedSources })
    }
    var enabledSources by remember(sourceConfig.enabledSources, supportedSources) {
        mutableStateOf(sourceConfig.enabledSources.filter { it in supportedSources }.toSet())
    }

    fun saveCurrentConfig() {
        viewModel.setSourceUsageConfig(
            usage,
            SourceUsageConfig(
                sourceOrder = currentList,
                enabledSources = enabledSources
            )
        )
    }

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
                title = stringResource(id = usage.titleRes()),
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
        }
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
                overscrollEffect = null
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
                                        saveCurrentConfig()
                                    },
                                    interactionSource = interactionSource
                                )
                        ) {
                            ReorderableSourceItem(
                                modifier = Modifier.background(
                                    if (isDragging) {
                                        MiuixTheme.colorScheme.secondary
                                    } else {
                                        MiuixTheme.colorScheme.background
                                    }
                                ),
                                index = index,
                                source = source,
                                isEnabled = isEnabled,
                                onEnabledChanged = { newState ->
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
                                    saveCurrentConfig()
                                },
                                showDivider = index != currentList.lastIndex
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
    source: com.lonx.lyrics.model.Source,
    isEnabled: Boolean = true,
    onEnabledChanged: (Boolean) -> Unit = {},
    showDivider: Boolean = false
) {
    Column {
        SwitchPreference(
            modifier = modifier,
            title = stringResource(id = source.labelRes),
            checked = isEnabled,
            onCheckedChange = onEnabledChanged,
            startAction = {
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontWeight = FontWeight.Bold,
                        fontSize = MiuixTheme.textStyles.body1.fontSize
                    )
                }
            },
            endActions = {
                Icon(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterVertically),
                    painter = painterResource(R.drawable.ic_draghandle_24dp),
                    contentDescription = stringResource(R.string.cd_drag_to_reorder),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        )
        if (showDivider) {
            HorizontalDivider(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                thickness = 0.5.dp
            )
        }
    }
}

private fun SourceUsage.titleRes(): Int {
    return when (this) {
        SourceUsage.LYRICS -> R.string.lyrics_source_priority
        SourceUsage.COVER -> R.string.cover_source_priority
        SourceUsage.METADATA -> R.string.metadata_source_priority
    }
}
