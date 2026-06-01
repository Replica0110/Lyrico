package com.lonx.lyrico.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "plugin_config")
fun PluginConfigScreen(
    pluginId: String,
    navigator: DestinationsNavigator
) {
    val viewModel: SearchSourceConfigViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    val requiredMessage = stringResource(R.string.source_config_required_error)

    LaunchedEffect(pluginId) {
        viewModel.load(pluginId)
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            Toast.makeText(context, R.string.source_config_saved, Toast.LENGTH_SHORT).show()
            viewModel.consumeSaved()
        }
    }

    val title = uiState.title.ifBlank { stringResource(R.string.plugin_config_title) }

    val hasConfigContent by remember {
        derivedStateOf {
            uiState.errorMessage == null &&
                    !uiState.isLoading &&
                    uiState.configFields.any { it.dependency.isSatisfied(uiState.values) }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.save(requiredMessage)
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = stringResource(R.string.source_config_save)
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
                .imePadding()
                .padding(scaffoldTopHorizontalPadding(paddingValues))
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
        ) {
            if (uiState.errorMessage != null) {
                Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@Scaffold
            }

            if (!hasConfigContent) {
                Text(
                    text = stringResource(R.string.source_config_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@Scaffold
            }

            if (!uiState.pluginEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.source_config_plugin_disabled_hint),
                        modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontSize = 14.sp
                    )
                }
            }

            PluginConfigFormItems(
                fields = uiState.configFields,
                values = uiState.values,
                validationErrors = uiState.validationErrors,
                onValueChange = viewModel::updateValue
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PluginConfigFormItems(
    fields: List<PluginConfigField>,
    values: Map<String, String>,
    validationErrors: Map<String, String>,
    onValueChange: (String, String) -> Unit
) {
    if (fields.isEmpty()) return

    val grouped = fields.groupBy { field ->
        field.group.ifBlank { DEFAULT_CONFIG_GROUP }
    }

    grouped.forEach { (group, groupFields) ->
        val hasVisibleField = groupFields.any { field ->
            field.dependency.isSatisfied(values)
        }

        if (!hasVisibleField) {
            return@forEach
        }

        SmallTitle(
            text = if (group == DEFAULT_CONFIG_GROUP) {
                stringResource(R.string.source_config_basic)
            } else {
                group
            }
        )

        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier.animateContentSize()
            ) {
                groupFields.forEach { field ->
                    val visible = field.dependency.isSatisfied(values)

                    AnimatedVisibility(visible = visible) {
                        PluginConfigFormItem(
                            field = field,
                            value = values[field.key].orEmpty(),
                            error = validationErrors[field.key],
                            onValueChange = { value ->
                                onValueChange(field.key, value)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PluginConfigFormItem(
    field: PluginConfigField,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    when (field.type) {
        PluginConfigFieldType.SWITCH -> {
            SwitchPreference(
                title = field.title,
                summary = error ?: field.summary,
                checked = value.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { checked ->
                    onValueChange(checked.toString())
                }
            )
        }

        PluginConfigFieldType.DROPDOWN -> {
            val selectedIndex = field.options
                .indexOfFirst { it.value == value }
                .coerceAtLeast(0)
            val entry = remember(field.options, selectedIndex, onValueChange) {
                DropdownEntry(
                    items = field.options.mapIndexed { index, option ->
                        DropdownItem(
                            text = option.label,
                            summary = option.summary.takeIf { it.isNotBlank() },
                            selected = index == selectedIndex,
                            onClick = {
                                onValueChange(option.value)
                            }
                        )
                    }
                )
            }

            WindowDropdownPreference(
                title = field.title,
                summary = error ?: field.summary,
                entry = entry,
                enabled = field.options.isNotEmpty(),
                collapseOnSelection = true
            )
        }

        PluginConfigFieldType.TEXT,
        PluginConfigFieldType.TEXTAREA,
        PluginConfigFieldType.PASSWORD,
        PluginConfigFieldType.NUMBER -> {
            var passwordVisible by rememberSaveable(field.key) {
                mutableStateOf(false)
            }

            val isPassword = field.type == PluginConfigFieldType.PASSWORD

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    label = field.title,
                    singleLine = field.type != PluginConfigFieldType.TEXTAREA,
                    minLines = if (field.type != PluginConfigFieldType.TEXTAREA) 1 else 2,
                    maxLines = if (field.type != PluginConfigFieldType.TEXTAREA) 1 else 8,
                    visualTransformation = if (isPassword && !passwordVisible) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = if (field.type == PluginConfigFieldType.NUMBER) {
                        KeyboardOptions(keyboardType = KeyboardType.Number)
                    } else {
                        KeyboardOptions.Default
                    },
                    trailingIcon = if (isPassword) {
                        {
                            IconButton(
                                onClick = {
                                    passwordVisible = !passwordVisible
                                }
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        MiuixIcons.Hide
                                    } else {
                                        MiuixIcons.Show
                                    },
                                    contentDescription = if (passwordVisible) {
                                        stringResource(R.string.password_hide)
                                    } else {
                                        stringResource(R.string.password_show)
                                    }
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onValueChange = { input ->
                        onValueChange(
                            if (field.type == PluginConfigFieldType.NUMBER) {
                                input.filter(Char::isDigit)
                            } else {
                                input
                            }
                        )
                    }
                )

                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error
                    )
                }
                field.summary?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                }
            }
        }

        PluginConfigFieldType.MARKDOWN -> {
            val markdown = field.defaultValue.ifBlank { field.summary }
            if (!markdown.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    field.title.takeIf { it.isNotBlank() }?.let { title ->
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    MarkdownText(
                        modifier = Modifier.fillMaxWidth(),
                        markdown = markdown,
                        linkColor = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2.copy(
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}


private const val DEFAULT_CONFIG_GROUP = "__basic__"