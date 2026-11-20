@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.settings.editing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldBlock
import com.lonx.lyrico.data.editfield.EditFieldListItem
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Switcher
import com.moriafly.salt.ui.Text as SaltText
import com.moriafly.salt.ui.innerPadding
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * 「编辑字段」列表的一行：字段或组件名称、类型标记、操作菜单与显隐开关。
 *
 * 主列表与组件成员弹层共用这一行；[component] 不为空时表示该行代表组件本身，开关作用于整个组件。
 */
@Composable
internal fun EditFieldRow(
    item: EditFieldListItem,
    checked: Boolean,
    iconTone: PreferenceIconTone,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSongs: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier,
    component: EditFieldBlock? = null,
    onManageComponent: () -> Unit = {},
    inSheet: Boolean = false,
) {
    val field = item.field
    val title = if (component != null) component.displayTitle() else field.displayTitle()
    val icon = if (component != null) LucideIcons.Volume2 else field.settingsIcon()
    val tag = when {
        component != null -> stringResource(R.string.edit_field_component_short_tag)
        field.custom -> stringResource(R.string.edit_field_custom_short_tag)
        else -> null
    }
    val actions = buildList {
        if (component != null) {
            add(
                EditFieldRowAction(
                    label = stringResource(R.string.edit_field_manage_members),
                    icon = LucideIcons.Settings,
                    onClick = onManageComponent,
                )
            )
        }
        add(
            EditFieldRowAction(
                label = stringResource(R.string.edit_field_view_songs),
                icon = LucideIcons.ListMusic,
                onClick = onOpenSongs,
            )
        )
        if (field.custom) {
            add(
                EditFieldRowAction(
                    label = stringResource(R.string.common_delete),
                    icon = LucideIcons.Trash,
                    destructive = true,
                    onClick = onDelete,
                )
            )
        }
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltEditFieldRow(
            title = title,
            icon = icon,
            iconTone = iconTone,
            tag = tag,
            actions = actions,
            checked = checked,
            onCheckedChange = onCheckedChange,
            dragHandleModifier = dragHandleModifier,
            inSheet = inSheet,
        )
    } else {
        MiuixEditFieldRow(
            title = title,
            icon = icon,
            iconTone = iconTone,
            tag = tag,
            actions = actions,
            checked = checked,
            onCheckedChange = onCheckedChange,
            dragHandleModifier = dragHandleModifier,
            inSheet = inSheet,
        )
    }
}

/** 行内操作；[destructive] 决定是否使用错误色。 */
private class EditFieldRowAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** 菜单项配色：删除用错误色，其余按位置轮换，与列表图标的色序一致。 */
private fun actionTone(action: EditFieldRowAction, index: Int): PreferenceIconTone =
    if (action.destructive) PreferenceIconTone.Danger else PreferenceIconTone.at(index)

@Composable
private fun SaltEditFieldRow(
    title: String,
    icon: ImageVector,
    iconTone: PreferenceIconTone,
    tag: String?,
    actions: List<EditFieldRowAction>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dragHandleModifier: Modifier,
    inSheet: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }

    // 拖拽把手覆盖整行；操作菜单换成 Salt 原生弹出菜单，密度按 Salt 的 item 规格。
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier)
                .innerPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.moriafly.salt.ui.Icon(
                painter = rememberVectorPainter(icon),
                contentDescription = null,
                modifier = Modifier.size(SaltTheme.dimens.itemIcon),
                tint = iconTone.color(),
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SaltText(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    style = SaltTheme.textStyles.main,
                    color = SaltTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                tag?.let { FieldTagChip(it) }
            }
            Box {
                SaltIconButton(
                    icon = LucideIcons.EllipsisVertical,
                    label = stringResource(R.string.cd_more_actions),
                    onClick = { showMenu = true },
                    size = 36.dp,
                    iconSize = 20.dp,
                )
                PopupMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    actions.forEachIndexed { index, action ->
                        PopupMenuItem(
                            text = action.label,
                            iconPainter = rememberVectorPainter(action.icon),
                            iconColor = actionTone(action, index).color(),
                            onClick = {
                                showMenu = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
            Switcher(state = checked, onChange = onCheckedChange)
        }
    }

    if (inSheet) {
        content()
    } else {
        com.moriafly.salt.ui.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
        ) { content() }
    }
}

@Composable
private fun MiuixEditFieldRow(
    title: String,
    icon: ImageVector,
    iconTone: PreferenceIconTone,
    tag: String?,
    actions: List<EditFieldRowAction>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    dragHandleModifier: Modifier,
    inSheet: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(dragHandleModifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceIcon(
                imageVector = icon,
                tone = iconTone,
                modifier = Modifier,
            )
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                tag?.let { FieldTagChip(it) }
            }
            Box {
                PillButton(
                    text = stringResource(R.string.cd_more_actions),
                    showText = false,
                    style = PillButtonDefaults.style(PillButtonSize.Small),
                    onClick = { showMenu = true },
                    leading = {
                        Icon(
                            imageVector = LucideIcons.EllipsisVertical,
                            contentDescription = stringResource(R.string.cd_more_actions),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                WindowListPopup(
                    show = showMenu,
                    popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
                    onDismissRequest = { showMenu = false },
                    alignment = PopupPositionProvider.Align.End,
                ) {
                    ListPopupColumn {
                        actions.forEachIndexed { index, action ->
                            DropdownImpl(
                                item = DropdownItem(
                                    text = action.label,
                                    icon = { modifier ->
                                        PreferenceIcon(
                                            imageVector = action.icon,
                                            tone = actionTone(action, index),
                                            modifier = modifier,
                                        )
                                    },
                                ),
                                isSelected = false,
                                optionSize = actions.size,
                                index = index,
                                onSelectedIndexChange = {
                                    showMenu = false
                                    action.onClick()
                                },
                            )
                        }
                    }
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    if (inSheet) {
        content()
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            insideMargin = PaddingValues(0.dp),
        ) { content() }
    }
}

/** 字段行上的小标记，用来把自定义标签和内置字段区分开。 */
@Composable
private fun FieldTagChip(text: String) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltText(
            text = text,
            style = SaltTheme.textStyles.sub,
            color = SaltTheme.colors.highlight,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SaltTheme.colors.highlight.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        return
    }
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
