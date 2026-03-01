package com.lonx.lyrico.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.plugin.ConfigDependency
import com.lonx.lyrico.plugin.ConfigField
import com.lonx.lyrico.plugin.FieldType
import com.lonx.lyrico.viewmodel.PluginListViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(route = "plugin_config")
@Composable
fun PluginConfigScreen(
    pluginId: String,
    pluginName: String,
    navigator: DestinationsNavigator
) {
    val viewModel: PluginListViewModel = koinViewModel()

    val configState by viewModel.configUiState.collectAsState()
    val formState by viewModel.formState.collectAsState()

    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(pluginId) {
        viewModel.loadConfig(pluginId, pluginName)
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = pluginName,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.popBackStack() },
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Icon(MiuixIcons.Back, null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveConfig()
                            navigator.popBackStack()
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(MiuixIcons.Ok, null)
                    }
                }
            )
        }
    ) { padding ->

        Box(Modifier.fillMaxSize()) {

            val state = configState ?: return@Box

            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center)
                    )
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("加载配置失败: ${state.error}")
                        Button(
                            onClick = {
                                viewModel.loadConfig(pluginId, pluginName)
                            }
                        ) { Text("重试") }
                    }
                }

                else -> {
                    DynamicConfigForm(
                        schema = state.schema,
                        formState = formState,
                        paddingValues = padding,
                        scrollBehavior = scrollBehavior,
                        onValueChange = viewModel::updateField
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicConfigForm(
    schema: List<ConfigField>,
    formState: Map<String, Any>,
    paddingValues: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onValueChange: (String, Any) -> Unit
) {
    val grouped = remember(schema) {
        schema.groupBy { it.group.ifEmpty { "基础设置" } }
    }

    LazyColumn(
        modifier = Modifier
            .imePadding()
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(
            top = paddingValues.calculateTopPadding(),
            bottom = paddingValues.calculateBottomPadding() + 12.dp,
        ),
        overscrollEffect = null,
    ) {

        grouped.forEach { (groupName, fields) ->
            item(groupName) {

                Column(Modifier.animateContentSize()) {

                    SmallTitle(text = groupName)

                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Column {

                            fields.forEach { field ->

                                val visible by remember(field.dependency, formState) {
                                    derivedStateOf {
                                        checkDependency(field.dependency, formState)
                                    }
                                }

                                AnimatedVisibility(visible) {
                                    FormItem(
                                        field = field,
                                        value = formState[field.key],
                                        onChange = { onValueChange(field.key, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 递归检查依赖条件
 */
private fun checkDependency(
    dependency: ConfigDependency?,
    formState: Map<String, Any>
): Boolean {
    if (dependency == null) return true

    return when (dependency) {

        is ConfigDependency.Match -> {
            val currentValue = formState[dependency.key] ?: return false

            when (currentValue) {
                is Boolean -> currentValue.toString() == dependency.value.lowercase()
                is Number -> currentValue.toString() == dependency.value
                else -> currentValue.toString() == dependency.value
            }
        }

        is ConfigDependency.And ->
            dependency.conditions.all { checkDependency(it, formState) }

        is ConfigDependency.Or ->
            dependency.conditions.any { checkDependency(it, formState) }

        is ConfigDependency.Not ->
            !checkDependency(dependency.condition, formState)
    }
}

@Composable
private fun FormItem(
    field: ConfigField,
    value: Any?,
    onChange: (Any) -> Unit
) {
    when (field.type) {

        FieldType.SWITCH -> {
            val checked = value as? Boolean ?: false

            SuperSwitch(
                title = field.label,
                summary = field.description.ifEmpty { null },
                checked = checked,
                onCheckedChange = onChange
            )
        }

        FieldType.SELECT -> {
            val options = field.options ?: emptyList()
            val selected = value as? String ?: ""
            val index = options.indexOf(selected).takeIf { it >= 0 } ?: 0

            SuperDropdown(
                title = field.label,
                summary = field.description.ifEmpty { null },
                items = options,
                selectedIndex = index,
                onSelectedIndexChange = {
                    if (it in options.indices) {
                        onChange(options[it])
                    }
                }
            )
        }

        FieldType.TEXT, FieldType.PASSWORD -> {
            val text = value as? String ?: ""

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = onChange,
                    label = field.label,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation =
                        if (field.type == FieldType.PASSWORD)
                            PasswordVisualTransformation()
                        else VisualTransformation.None
                )

                if (field.description.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = field.description,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }

        FieldType.NUMBER -> {
            val text =  value as? String ?: ""

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                TextField(
                    value = text,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.all { it.isDigit() }) {
                            onChange(input)
                        }
                    },
                    label = field.label,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}