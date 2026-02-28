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

    // 监听配置状态
    val configState by viewModel.configUiState.collectAsState()
    // 控制 BottomSheet 显示的状态
    val showSheet = remember { mutableStateOf(false) }

    // 监听 configState 变化来自动显示 BottomSheet
    LaunchedEffect(configState) {
        showSheet.value = configState != null
        // 如果外部(如保存成功)置空了状态，确保 Sheet 关闭
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "插件列表",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigator.popBackStack()
                        },
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refresh()
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        configState?.let { state ->
            SuperBottomSheet(
                show = showSheet,
                title = state.pluginName,
                onDismissRequest = {
                    showSheet.value = false
                },
                onDismissFinished = {
                    viewModel.dismissConfig()
                }
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }

                    state.error != null -> {
                        Text(text = "加载配置失败: ${state.error}")
                    }

                    else -> {
                        DynamicConfigForm(
                            schema = state.schema,
                            currentSettings = state.currentSettings,
                            onSave = { settings ->
                                viewModel.saveConfig(settings)
                                showSheet.value = false
                            }
                        )
                    }
                }
            }
        }

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
            items(items = plugins, key = { it.id }) {
                var enabled by remember { mutableStateOf(true) }
                PluginItem(
                    plugin = it,
                    enabled = enabled,
                    onCheckChanged = {
                        enabled = it
                    },
                    onSettings = {
                        viewModel.loadConfig(it.id, it.name)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicConfigForm(
    schema: List<ConfigField>,
    currentSettings: Bundle,
    onSave: (Bundle) -> Unit
) {
    val formState = remember(currentSettings) {
        val map = mutableStateMapOf<String, Any>()
        schema.forEach { field ->
            map[field.key] = when (field.type) {
                FieldType.SWITCH -> currentSettings.getBoolean(
                    field.key,
                    field.defaultValue.toBooleanStrictOrNull() ?: false
                )

                FieldType.NUMBER -> currentSettings.getInt(
                    field.key,
                    field.defaultValue.toIntOrNull() ?: 0
                )

                else -> currentSettings.getString(field.key) ?: field.defaultValue
            }
        }
        map
    }

    val groupedFields = remember(schema) {
        schema.groupBy { field ->
            field.group.ifEmpty { "基础设置" }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .scrollEndHaptic()
            .overScrollVertical(),
        contentPadding = PaddingValues(bottom = 16.dp),
        overscrollEffect = null,
    ) {
        groupedFields.forEach { (groupName, fields) ->
            item(key = groupName) {
                SmallTitle(
                    text = groupName,
                    insideMargin = PaddingValues(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )

            }
            items(items = fields, key = { field -> field.key }) { field ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),

                    ) { FormItem(field = field, formState = formState) }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    val bundle = Bundle()
                    formState.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> bundle.putBoolean(key, value)
                            is Int -> bundle.putInt(key, value)
                            is String -> bundle.putString(key, value)
                        }
                    }
                    onSave(bundle)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp)
            ) {
                Text("保存配置")
            }

            // 底部导航栏避让
            Spacer(
                Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

/**
 * 抽取单个表单项渲染逻辑，保持代码整洁
 */
@Composable
private fun FormItem(
    field: ConfigField,
    formState: SnapshotStateMap<String, Any>
) {
    when (field.type) {
        FieldType.SWITCH -> {
            val isChecked = formState[field.key] as? Boolean ?: false
            SuperSwitch(
                title = field.label,
                summary = field.description, // 如果没有描述，可以传 null
                checked = isChecked,
                onCheckedChange = { formState[field.key] = it }
            )
        }

        FieldType.SELECT -> {
            val selectedValue = formState[field.key] as? String ?: ""
            val options = field.options ?: emptyList()
            // 找到当前选中项的索引，如果找不到默认为 0
            val selectedIndex = options.indexOf(selectedValue).takeIf { it >= 0 } ?: 0

            SuperDropdown(
                title = field.label,
                summary = field.description,
                items = options,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = { index ->
                    if (index in options.indices) {
                        formState[field.key] = options[index]
                    }
                },
                // 如果 SuperDropdown 支持 summary 也可以传 field.description
            )
        }

        FieldType.TEXT, FieldType.PASSWORD -> {

            val textValue = formState[field.key] as? String ?: ""
            TextField(
                value = textValue,
                onValueChange = { formState[field.key] = it },
                label = field.label,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (field.type == FieldType.PASSWORD)
                    PasswordVisualTransformation() else VisualTransformation.None
            )
            if (field.description.isNotEmpty()) {
                Text(
                    text = field.description,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        FieldType.NUMBER -> {
            val numValue = formState[field.key]
            val textValue = numValue?.toString() ?: ""

            TextField(
                value = textValue,
                onValueChange = { input ->
                    if (input.isEmpty()) {
                        formState[field.key] = 0
                    } else if (input.all { it.isDigit() }) {
                        formState[field.key] = input.toInt()
                    }
                },
                label = field.label,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

    }
}