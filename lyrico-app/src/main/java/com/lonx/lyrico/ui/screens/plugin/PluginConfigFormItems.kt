@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.plugin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.model.plugin.PluginConfigField
import com.lonx.lyrico.data.model.plugin.PluginConfigFieldType
import com.lonx.lyrico.data.model.plugin.isSatisfied
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.editor.rememberEditorTextFieldState
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.input.salt.SaltTextInput
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.screen.ScreenCard
import dev.jeziellago.compose.markdowntext.MarkdownText
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PluginConfigFormItems(
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
            val inputState = rememberEditorTextFieldState(value, onValueChange)
            val lineLimits = if (field.type == PluginConfigFieldType.TEXTAREA) {
                TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 8)
            } else TextFieldLineLimits.SingleLine
            val numberInputTransformation = if (field.type == PluginConfigFieldType.NUMBER) {
                InputTransformation {
                    val digits = asCharSequence().filter(Char::isDigit)
                    if (digits.length != length) replace(0, length, digits)
                }
            } else null

            if (salt) {
                SaltTextInput(
                    state = inputState,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    backgroundColor = com.moriafly.salt.ui.SaltTheme.colors.background,
                    label = field.title,
                    summary = field.summary,
                    error = error,
                    keyboardOptions = keyboardOptions,
                    lineLimits = lineLimits,
                    secure = isPassword,
                    passwordVisible = passwordVisible,
                    inputTransformation = numberInputTransformation,
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
                        state = inputState,
                        label = field.title,
                        outputTransformation = if (isPassword && !passwordVisible) {
                            OutputTransformation {
                                // Keep each character boundary available to cursor/selection mapping.
                                for (index in 0 until length) replace(index, index + 1, "•")
                            }
                        } else null,
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
                        lineLimits = lineLimits,
                        inputTransformation = numberInputTransformation,
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
