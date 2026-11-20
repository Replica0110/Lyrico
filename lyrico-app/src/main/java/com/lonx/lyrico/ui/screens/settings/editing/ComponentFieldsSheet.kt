@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.settings.editing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldBlock
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldListItem
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.ItemOrderState
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.sheet.miuix.MiuixBottomSheet
import com.lonx.lyrico.ui.components.sheet.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import com.moriafly.salt.ui.innerPadding
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 组件成员弹层：调整组件内部字段的显隐和顺序，并可从成员行跳到该字段的歌曲列表。
 *
 * 只处理组件成员；主列表里的组件整行开关由调用方负责。
 */
@Composable
internal fun ComponentFieldsBottomSheet(
    show: Boolean,
    component: EditFieldBlock?,
    items: List<EditFieldListItem>,
    onEnabledChange: (String, Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOpenSongs: (EditFieldDefinition) -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
) {
    val title = if (component == null) "" else component.displayTitle()
    val content: @Composable () -> Unit = {
        ComponentFieldsSheetContent(
            component = component,
            items = items,
            onEnabledChange = onEnabledChange,
            onOrderChange = onOrderChange,
            onOpenSongs = onOpenSongs,
        )
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            title = title,
        ) { content() }
        return
    }
    MiuixBottomSheet(
        show = show,
        title = title,
        enableNestedScroll = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) { content() }
}

@Composable
private fun ComponentFieldsSheetContent(
    component: EditFieldBlock?,
    items: List<EditFieldListItem>,
    onEnabledChange: (String, Boolean) -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onOpenSongs: (EditFieldDefinition) -> Unit,
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    val savedCodes = component?.fields?.map { it.code }.orEmpty()
    // 换组件时重建顺序状态，避免沿用上一个组件的排列。
    val orderState = remember(component?.key) { ItemOrderState(savedCodes) }
    val displayCodes = orderState.order
    val latestOnOrderChange by rememberUpdatedState(onOrderChange)
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val itemsByCode = remember(items) { items.associateBy { it.field.code } }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (orderState.move(from.key, to.key)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    LaunchedEffect(savedCodes) { orderState.updateSaved(savedCodes) }

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (salt) Modifier else Modifier.padding(bottom = 32.dp))
    ) {
        if (salt) {
            SaltText(
                text = stringResource(R.string.edit_field_component_hint),
                style = SaltTheme.textStyles.sub,
                color = SaltTheme.colors.subText,
                modifier = Modifier.innerPadding(vertical = false),
            )
        } else {
            Text(
                text = stringResource(R.string.edit_field_component_hint),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(8.dp))

        val listContent: @Composable () -> Unit = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                overscrollEffect = if (salt) rememberOverscrollEffect() else null,
            ) {
                items(displayCodes, key = { it }) { code ->
                    val item = itemsByCode[code]
                    ReorderableItem(reorderState, key = code) {
                        if (item != null) EditFieldRow(
                            item = item,
                            checked = item.enabled,
                            iconTone = PreferenceIconTone.at(displayCodes.indexOf(code)),
                            onCheckedChange = { onEnabledChange(code, it) },
                            onOpenSongs = { onOpenSongs(item.field) },
                            onDelete = {},
                            dragHandleModifier = Modifier.longPressDraggableHandle(
                                onDragStarted = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragStopped = { latestOnOrderChange(orderState.order) },
                            ),
                            inSheet = true,
                        )
                    }
                }
            }
        }

        if (salt) {
            RoundedColumn { listContent() }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
            ) { listContent() }
        }
    }
}
