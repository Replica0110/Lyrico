@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.model.lyrics.*
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.outerPadding
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.popup.rememberPopupState
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun LyricsActionButton(text: String, enabled: Boolean = true, primary: Boolean = false, onCard: Boolean = false,
                       onClick: () -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        Button(
            onClick = onClick,
            text = text,
            enabled = enabled,
            appearance = if (primary) ButtonAppearance.Filled else ButtonAppearance.Plain,
        )
        return
    }
    PillButton(text = text, enabled = enabled, selected = primary, onClick = onClick,
        modifier = Modifier.minimumInteractiveComponentSize().semantics { role = Role.Button },
        style = PillButtonDefaults.style(PillButtonSize.Large),
        colors = PillButtonDefaults.colors(containerColor = if (onCard)
            MiuixTheme.colorScheme.surface else MiuixTheme.colorScheme.secondaryContainer))
}

@Composable
fun LyricsHint(text: String, modifier: Modifier = Modifier) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        com.moriafly.salt.ui.Text(
            text = text,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.subText,
            modifier = modifier.outerPadding(),
        )
        return
    }
    Text(text, modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

/** Anchored menu shared by the metadata actions and selection toolbar. */
@Composable
fun LyricsOrganizationMenu(onSelect: (LyricsOperation) -> Unit, anchor: @Composable (() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (LocalUiEngine.current == UiEngine.SALT) {
        val popup = rememberPopupState()
        Box {
            anchor { popup.expend() }
            PopupMenu(expanded = popup.expend, onDismissRequest = popup::dismiss) {
                listOf(LyricsOperation.SORT, LyricsOperation.REMOVE_EMPTY, LyricsOperation.REMOVE_TAGS).forEach { action ->
                    PopupMenuItem(
                        text = stringResource(action.titleRes),
                        onClick = {
                            popup.dismiss()
                            onSelect(action)
                        }
                    )
                }
            }
        }
        return
    }
    Box {
        anchor { expanded = true }
        WindowListPopup(show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false }, alignment = PopupPositionProvider.Align.End) {
            ListPopupColumn {
                val actions = listOf(LyricsOperation.SORT, LyricsOperation.REMOVE_EMPTY, LyricsOperation.REMOVE_TAGS)
                actions.forEachIndexed { index, action ->
                    DropdownImpl(item = DropdownItem(
                        text = stringResource(action.titleRes),
                        icon = { modifier ->
                            PreferenceIcon(
                                when (action) {
                                    LyricsOperation.SORT -> LucideIcons.Sort
                                    LyricsOperation.REMOVE_EMPTY -> LucideIcons.RemoveLines
                                    else -> LucideIcons.FilterLyrics
                                },
                                PreferenceIconTone.Appearance,
                                modifier = modifier,
                            )
                        },
                    ), isSelected = false,
                        optionSize = actions.size, index = index,
                        onSelectedIndexChange = { expanded = false; onSelect(action) })
                }
            }
        }
    }
}

@Composable
fun LyricsChoice(label: String, items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (LocalUiEngine.current == UiEngine.SALT) {
        val popup = rememberPopupState()
        Box(modifier) {
            Button(
                onClick = { popup.expend() },
                text = label,
                appearance = ButtonAppearance.Subtle,
            )
            PopupMenu(expanded = popup.expend, onDismissRequest = popup::dismiss) {
                items.forEachIndexed { index, item ->
                    PopupMenuItem(
                        text = item,
                        selected = index == selected,
                        onClick = {
                            popup.dismiss()
                            onSelect(index)
                        }
                    )
                }
            }
        }
        return
    }
    Box(modifier) {
        PillButton(text = label, onClick = { expanded = true },
            modifier = Modifier.widthIn(max = 240.dp).minimumInteractiveComponentSize().semantics { role = Role.Button },
            style = PillButtonDefaults.style(PillButtonSize.Large),
            trailing = { Icon(LucideIcons.ArrowDownUp, null, Modifier.size(16.dp)) })
        WindowListPopup(show = expanded,
            popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
            onDismissRequest = { expanded = false }, alignment = PopupPositionProvider.Align.End) {
            ListPopupColumn {
                items.forEachIndexed { index, item ->
                    DropdownImpl(text = item, isSelected = index == selected, optionSize = items.size,
                        index = index, onSelectedIndexChange = { expanded = false; onSelect(index) })
                }
            }
        }
    }
}

@Composable
fun LyricsColumnOrderSettings(
    twoColumnMapping: LyricsColumnMapping, threeColumnMapping: LyricsColumnMapping,
    onTwoColumnMapping: (LyricsColumnMapping) -> Unit, onThreeColumnMapping: (LyricsColumnMapping) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LyricsColumnCountSettings(2, twoColumnMapping, onTwoColumnMapping)
        LyricsColumnCountSettings(3, threeColumnMapping, onThreeColumnMapping)
    }
}

@Composable
private fun LyricsColumnCountSettings(
    count: Int, mapping: LyricsColumnMapping, onMapping: (LyricsColumnMapping) -> Unit
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        RoundedColumn {
            com.moriafly.salt.ui.ItemValue(
                text = stringResource(if (count == 2) R.string.lyrics_two_columns else R.string.lyrics_three_columns),
                sub = stringResource(R.string.lyrics_column_hint),
            )
            repeat(count) { source ->
                val destination = mapping.order.indexOf(source)
                val kept = destination >= 0
                val outputCount = mapping.order.size + if (kept) 0 else 1
                val positions = (1..outputCount).map { stringResource(R.string.lyrics_output_line, it) }
                com.lonx.lyrico.ui.components.salt.SaltDropdownRow(
                    title = stringResource(R.string.lyrics_input_line, source + 1),
                    labels = positions + stringResource(R.string.lyrics_column_remove),
                    selectedIndex = if (kept) destination else positions.size,
                ) { selected -> onMapping(mapping.place(source, selected.takeIf { it < positions.size })) }
            }
        }
        return
    }
    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
        Text(
            text = stringResource(if (count == 2) R.string.lyrics_two_columns else R.string.lyrics_three_columns),
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            style = MiuixTheme.textStyles.subtitle,
        )
        LyricsHint(stringResource(R.string.lyrics_column_hint))
        repeat(count) { source ->
            val destination = mapping.order.indexOf(source)
            val kept = destination >= 0
            val outputCount = mapping.order.size + if (kept) 0 else 1
            val positions = (1..outputCount).map { stringResource(R.string.lyrics_output_line, it) }
            val choices = positions + stringResource(R.string.lyrics_column_remove)
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.lyrics_input_line, source + 1),
                    modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body2)
                LyricsChoice(
                    label = if (kept) positions[destination] else stringResource(R.string.lyrics_column_remove),
                    items = choices, selected = if (kept) destination else positions.size,
                ) { selected -> onMapping(mapping.place(source, selected.takeIf { it < positions.size })) }
            }
        }
    }
}

@Composable
fun LyricsConcurrency(value: Int, onChange: (Int) -> Unit, header: (@Composable () -> Unit)? = null) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        RoundedColumn(Modifier.padding(top = 12.dp)) {
            header?.invoke()
            ItemSlider(
                value = (value - 1).toFloat(),
                onValueChange = { onChange(it.roundToInt() + 1) },
                text = stringResource(R.string.batch_replay_gain_concurrency),
                sub = value.toString(),
                valueRange = 0f..4f,
                steps = 3,
            )
        }
        return
    }
    Card(modifier = Modifier.padding(top = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
        header?.invoke()
        top.yukonga.miuix.kmp.preference.WindowDropdownPreference(
            startAction = { PreferenceIcon(LucideIcons.Parallel, PreferenceIconTone.Tools) },
            title = stringResource(R.string.batch_replay_gain_concurrency), items = (1..5).map { it.toString() },
            selectedIndex = value - 1, onSelectedIndexChange = { onChange(it + 1) })
    }
}

@Composable
fun LyricsOperationContent(operation: LyricsOperation, targetFormat: LyricFormat, onFormat: (LyricFormat) -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        when (operation) {
            LyricsOperation.CONVERT -> RoundedColumn {
                LyricFormat.entries.forEach { format ->
                ItemCheck(
                    state = format == targetFormat,
                    onChange = { checked -> if (checked) onFormat(format) },
                    text = stringResource(format.labelRes),
                )
            }
            }
            LyricsOperation.REMOVE_EMPTY -> LyricsHint(stringResource(R.string.lyrics_remove_empty_lines_manual_hint))
            LyricsOperation.REMOVE_TAGS -> LyricsHint(stringResource(R.string.lyrics_remove_tag_lines_settings_hint))
            LyricsOperation.SORT -> Unit
        }
        return
    }
    when (operation) {
        LyricsOperation.CONVERT -> Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
            LyricFormat.entries.forEach { format ->
                RadioButtonPreference(title = stringResource(format.labelRes), selected = format == targetFormat,
                    onClick = { onFormat(format) })
            }
        }
        LyricsOperation.REMOVE_EMPTY -> LyricsHint(stringResource(R.string.lyrics_remove_empty_lines_manual_hint))
        LyricsOperation.REMOVE_TAGS -> LyricsHint(stringResource(R.string.lyrics_remove_tag_lines_settings_hint))
        LyricsOperation.SORT -> Unit
    }
}

@Composable
fun LyricsProcessingSheet(show: Boolean, raw: String, operation: LyricsOperation, onDismiss: () -> Unit,
                          onConfirm: (LyricsProcessingOptions) -> Unit) {
    var target by remember { mutableStateOf(LyricFormat.PLAIN_LRC) }
    var twoColumnMapping by remember { mutableStateOf(LyricsColumnMapping.identity(2)) }
    var threeColumnMapping by remember { mutableStateOf(LyricsColumnMapping.identity(3)) }
    // Keep the content mounted while the sheet animates out. Reset only when opening.
    LaunchedEffect(show, raw, operation) {
        if (show) {
            target = LyricFormat.PLAIN_LRC
            twoColumnMapping = LyricsColumnMapping.identity(2)
            threeColumnMapping = LyricsColumnMapping.identity(3)
        }
    }
    val canConfirm = raw.isNotBlank()
    ActionBottomSheet(show = show, title = stringResource(operation.titleRes), onDismissRequest = onDismiss,
        // 内容自带 verticalScroll（Salt 下同样），外壳不再提供第二个滚动出口。
        saltScrollContent = false,
        startAction = { LyricsActionButton(stringResource(R.string.cancel), onClick = onDismiss) },
        endAction = {
            LyricsActionButton(stringResource(R.string.confirm), canConfirm, primary = true) {
                onConfirm(operation.options(target).copy(twoColumnMapping = twoColumnMapping, threeColumnMapping = threeColumnMapping))
            }
        }, content = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .then(if (LocalUiEngine.current == UiEngine.SALT) Modifier else Modifier.padding(bottom = 24.dp))
            ) {
                if (operation == LyricsOperation.SORT) LyricsColumnOrderSettings(
                    twoColumnMapping, threeColumnMapping, { twoColumnMapping = it }, { threeColumnMapping = it })
                else LyricsOperationContent(operation, target) { target = it }
            }
        })
}
