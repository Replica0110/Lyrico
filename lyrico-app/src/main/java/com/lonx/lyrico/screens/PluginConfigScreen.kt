@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

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
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.graphics.Color
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled
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
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.lonx.lyrico.ui.components.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.utils.isSatisfied
import com.lonx.lyrico.viewmodel.SearchSourceConfigViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTip
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemValue
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.ScreenCard
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
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
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val salt = LocalUiEngine.current == UiEngine.SALT
    val barBackdrop = rememberBarBlurBackdrop(enabled = !salt && rememberBarBlurEnabled())

    val requiredMessage = stringResource(R.string.source_config_required_error)

    LaunchedEffect(pluginId, configuration) {
        com.lonx.lyrico.plugin.i18n.PluginLocales.update(configuration)
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

    LibraryDetailPage(
        title = title,
        onBack = { navigator.navigateUp() },
        actions = {
            SaltIconButton(LucideIcons.Check, stringResource(R.string.source_config_save),
                onClick = { viewModel.save(requiredMessage) })
        },
        miuixTopBar = {
            BlurredTopBar(barBackdrop) {
                SmallTopAppBar(
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    title = title,
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateUp() }) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
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
                                imageVector = LucideIcons.Check,
                                contentDescription = stringResource(R.string.source_config_save)
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        }
    ) { paddingValues ->
        // Salt 的 verticalScroll 默认没有过度滚动，短内容时拖不动；挂上 Cupertino 效果对齐列表页。
        val overscrollEffect = rememberOverscrollEffect()
        Column(
            modifier = (if (salt) Modifier else Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection))
                .then(if (salt && overscrollEffect != null) Modifier.overscroll(overscrollEffect) else Modifier)
                .fillMaxSize()
                .blurSource(barBackdrop)
                .then(if (salt) Modifier else Modifier.imePadding())
                .verticalScroll(rememberScrollState())
                .padding(com.lonx.lyrico.ui.components.scaffoldContentPadding(paddingValues, bottomExtra = 12.dp))
        ) {
            if (uiState.errorMessage != null) {
                if (salt) com.moriafly.salt.ui.Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    style = SaltTheme.textStyles.main,
                    color = SaltTheme.colors.subText
                )
                else Text(
                    text = stringResource(R.string.source_config_invalid_source),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                return@LibraryDetailPage
            }

            val typeText = stringResource(
                R.string.plugin_type_with_value,
                uiState.sourceTypes
                    .map { stringResource(it.labelRes()) }
                    .joinToString(" / ")
            )
            val apiText = stringResource(
                R.string.plugin_api_versions_with_value,
                uiState.apiVersion,
                uiState.minHostApiVersion
            )

            if (salt) {
                ScreenCard {
                    ItemValue(text = typeText, sub = apiText)
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (uiState.sourceTypes.isNotEmpty()) {
                            Text(
                                text = typeText,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            text = apiText,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (!hasConfigContent) {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Plugins,
                    title = stringResource(R.string.source_config_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
                return@LibraryDetailPage
            }

            if (!uiState.pluginEnabled) {
                val hint = stringResource(R.string.source_config_plugin_disabled_hint)
                if (salt) ItemOuterTip(text = hint)
                else Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = hint,
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

    val salt = LocalUiEngine.current == UiEngine.SALT
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

        val groupTitle = if (group == DEFAULT_CONFIG_GROUP) {
            stringResource(R.string.source_config_basic)
        } else {
            groupFields.first().groupTitle ?: group
        }

        if (salt) {
            ItemOuterTitle(text = groupTitle)
            // ScreenCard 自带外边距，不再叠加，否则分组标题离上一组内容的距离会翻倍。
            ScreenCard {
                Column(modifier = Modifier.animateContentSize()) {
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
        } else {
            SmallTitle(text = groupTitle)

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
}

@Composable
private fun PluginConfigFormItem(
    field: PluginConfigField,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    when (field.type) {
        PluginConfigFieldType.SWITCH -> {
            if (salt) {
                ItemSwitcher(
                    state = value.toBooleanStrictOrNull() ?: false,
                    onChange = { checked -> onValueChange(checked.toString()) },
                    text = field.title,
                    sub = error ?: field.summary
                )
            } else SwitchPreference(
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
            if (salt) {
                ItemDropdown(
                    text = field.title,
                    sub = error ?: field.summary,
                    value = field.options.getOrNull(selectedIndex)?.label.orEmpty(),
                    enabled = field.options.isNotEmpty()
                ) {
                    field.options.forEachIndexed { index, option ->
                        PopupMenuItem(
                            text = option.label,
                            sub = option.summary.takeIf { it.isNotBlank() },
                            selected = index == selectedIndex,
                            onClick = {
                                state.dismiss()
                                onValueChange(option.value)
                            }
                        )
                    }
                }
            } else {
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
        }

        PluginConfigFieldType.TEXT,
        PluginConfigFieldType.TEXTAREA,
        PluginConfigFieldType.PASSWORD,
        PluginConfigFieldType.NUMBER -> {
            var passwordVisible by rememberSaveable(field.key) {
                mutableStateOf(false)
            }

            val isPassword = field.type == PluginConfigFieldType.PASSWORD
            val keyboardOptions = if (field.type == PluginConfigFieldType.NUMBER) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
            } else {
                KeyboardOptions.Default
            }
            val sanitize: (String) -> String = { input ->
                if (field.type == PluginConfigFieldType.NUMBER) input.filter(Char::isDigit) else input
            }

            if (salt) {
                SaltTextInput(
                    text = value,
                    onChange = onValueChange,
                    label = field.title,
                    summary = field.summary,
                    error = error,
                    keyboardOptions = keyboardOptions,
                    lineLimits = if (field.type == PluginConfigFieldType.TEXTAREA) {
                        TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 8)
                    } else TextFieldLineLimits.SingleLine,
                    secure = isPassword,
                    passwordVisible = passwordVisible,
                    inputTransformation = if (field.type == PluginConfigFieldType.NUMBER) {
                        InputTransformation {
                            val digits = asCharSequence().filter(Char::isDigit)
                            if (digits.length != length) replace(0, length, digits)
                        }
                    } else null,
                    actionContent = if (isPassword) {
                        {
                            SaltIconButton(
                                icon = if (passwordVisible) LucideIcons.EyeOff else LucideIcons.Eye,
                                label = stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show),
                                onClick = { passwordVisible = !passwordVisible },
                                size = 32.dp, iconSize = 18.dp,
                            )
                        }
                    } else null,
                )
            } else {
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
                        keyboardOptions = keyboardOptions,
                        trailingIcon = if (isPassword) {
                            {
                                IconButton(
                                    onClick = {
                                        passwordVisible = !passwordVisible
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            LucideIcons.EyeOff
                                        } else {
                                            LucideIcons.Eye
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
                            onValueChange(sanitize(input))
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
        }

        PluginConfigFieldType.MARKDOWN -> {
            val markdown = field.defaultValue.ifBlank { field.summary }
            if (!markdown.isNullOrEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (salt) Modifier.innerPadding()
                            else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    field.title.takeIf { it.isNotBlank() }?.let { markdownTitle ->
                        if (salt) com.moriafly.salt.ui.Text(
                            text = markdownTitle,
                            style = SaltTheme.textStyles.main,
                            fontWeight = FontWeight.SemiBold
                        )
                        else Text(
                            text = markdownTitle,
                            style = MiuixTheme.textStyles.body2,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (salt) MarkdownText(
                        modifier = Modifier.fillMaxWidth(),
                        markdown = markdown,
                        linkColor = SaltTheme.colors.highlight,
                        style = SaltTheme.textStyles.main.copy(
                            color = SaltTheme.colors.text
                        )
                    )
                    else MarkdownText(
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

private fun PluginSourceType.labelRes(): Int = when (this) {
    PluginSourceType.METADATA -> R.string.plugin_type_metadata
    PluginSourceType.LYRICS -> R.string.plugin_type_lyrics
    PluginSourceType.COVER -> R.string.plugin_type_cover
}
