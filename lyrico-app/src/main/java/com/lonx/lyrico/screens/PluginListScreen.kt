package com.lonx.lyrico.screens

import android.os.Bundle
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.plugin.ConfigField
import com.lonx.lyrico.plugin.FieldType
import com.lonx.lyrico.viewmodel.PluginListViewModel
import com.lonx.lyrico.viewmodel.PluginUiModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginConfigDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "plugin_list")
fun PluginListScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: PluginListViewModel = koinViewModel()
    val plugins by viewModel.pluginList.collectAsState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()


    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "插件列表",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() },
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(imageVector = MiuixIcons.Back, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            overscrollEffect = null,
        ) {
            items(items = plugins, key = { it.id }) { plugin ->
                var enabled by remember { mutableStateOf(true) }
                PluginItem(
                    plugin = plugin,
                    enabled = enabled,
                    onCheckChanged = { enabled = it },
                    onSettings = {
                        navigator.navigate(
                            PluginConfigDestination(
                                pluginId = plugin.id,
                                pluginName = plugin.name
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun PluginItem(
    plugin: PluginUiModel,
    enabled: Boolean,
    onSettings: () -> Unit,
    onCheckChanged: (Boolean) -> Unit,
) {
    // ... (保持原样，没有任何逻辑需要修改) ...
    // 原代码...
    // Miuix 风格常量
    val secondaryContainer = colorScheme.secondaryContainer.copy(alpha = 0.8f)
    val badgeBg = colorScheme.tertiaryContainer.copy(alpha = 0.6f)
    val badgeTint = colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
    val actionIconTint = colorScheme.onSurface.copy(alpha = 0.9f)
    var expanded by remember { mutableStateOf(false) }
    val hasDescription = plugin.description.isNotBlank()

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        onClick = { if (hasDescription) expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "id: ${plugin.id}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "作者: ${plugin.author}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = "版本: ${plugin.version}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onCheckChanged
                )
            }

            if (hasDescription) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .animateContentSize(
                            animationSpec = tween(
                                durationMillis = 250,
                                easing = FastOutSlowInEasing
                            )
                        )
                ) {
                    Text(
                        text = plugin.description,
                        fontSize = 14.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = if (expanded) Int.MAX_VALUE else 3
                    )
                }
            }

            if (plugin.isSearchSource || plugin.isLyricSource) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    thickness = 0.5.dp,
                    color = colorScheme.outline.copy(alpha = 0.3f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (plugin.isSearchSource) {
                            CapabilityBadge("搜索源", badgeBg, badgeTint)
                        }
                        if (plugin.isLyricSource) {
                            CapabilityBadge("歌词源", badgeBg, badgeTint)
                        }
                    }

                    IconButton(
                        onClick = onSettings,
                        backgroundColor = secondaryContainer,
                        minHeight = 36.dp,
                        minWidth = 36.dp
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Settings,
                            modifier = Modifier.size(18.dp),
                            tint = actionIconTint,
                            contentDescription = null
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun CapabilityBadge(text: String, bg: Color, tint: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight(700),
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}


