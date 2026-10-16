package com.lonx.lyrico.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 胶囊换行网格，规则类页面共用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGrid(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/**
 * 自定义标签、非歌词内容过滤规则、艺术家拆分规则三个页面共用的胶囊。
 *
 * [checked] 为 null 时不渲染勾选圈，胶囊始终按"已生效"着色，适合只有一个动作
 * （点击编辑或直接删除）的规则；传入 true/false 时会渲染勾选圈，并按状态切换高亮，
 * 适合可开关的规则。
 */
@Composable
fun ManagedChip(
    text: String,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    editDescription: String = stringResource(R.string.common_edit),
) {
    if (com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        SaltManagedChip(text, modifier, checked, onClick, onDelete, onEdit, editDescription)
        return
    }
    val showCheckbox = checked != null
    val highlighted = checked != false
    val colorScheme = MiuixTheme.colorScheme

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (highlighted) {
                    colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    colorScheme.secondaryContainer
                }
            )
            .border(
                width = 1.dp,
                color = if (highlighted) {
                    colorScheme.primary.copy(alpha = 0.48f)
                } else {
                    colorScheme.onSurfaceVariantActions.copy(alpha = 0.16f)
                },
                shape = RoundedCornerShape(50),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(
                start = if (showCheckbox) 8.dp else 10.dp,
                top = 6.dp,
                end = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheckbox) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) {
                            colorScheme.primary
                        } else {
                            colorScheme.onSurfaceVariantActions.copy(alpha = 0.16f)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (highlighted) {
                    Text(
                        text = "✓",
                        color = colorScheme.onPrimary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                }
            }
        }

        Text(
            text = text,
            modifier = Modifier.padding(
                start = if (showCheckbox) 8.dp else 0.dp,
                end = 8.dp,
            ),
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        onEdit?.let { edit ->
            Icon(
                imageVector = LucideIcons.Pencil,
                contentDescription = editDescription,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = edit),
                tint = colorScheme.onSurfaceVariantActions,
            )
        }

        if (onDelete != null) {
            Icon(
                imageVector = LucideIcons.Trash,
                contentDescription = stringResource(R.string.common_delete),
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                tint = colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** Compact rule controls; Salt has no chip component in the pinned version. */
@Composable
private fun SaltManagedChip(
    text: String, modifier: Modifier, checked: Boolean?, onClick: (() -> Unit)?,
    onDelete: (() -> Unit)?, onEdit: (() -> Unit)?, editDescription: String,
) {
    val colors = com.moriafly.salt.ui.SaltTheme.colors
    val active = checked != false
    val shape = RoundedCornerShape(50)
    Row(
        modifier.heightIn(min = 36.dp).clip(shape)
            .background(if (active) colors.highlight.copy(alpha = 0.1f) else colors.subBackground)
            .border(1.dp, if (active) colors.highlight.copy(alpha = 0.4f) else colors.stroke, shape)
            .then(if (checked != null) Modifier.semantics {
                role = Role.Checkbox
                toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
            } else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, top = 2.dp, end = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checked != null) {
            Box(Modifier.size(16.dp).clip(CircleShape)
                .background(if (active) colors.highlight else colors.stroke), contentAlignment = Alignment.Center) {
                if (active) com.moriafly.salt.ui.Icon(
                    androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.Check), null,
                    modifier = Modifier.size(12.dp), tint = colors.onHighlight)
            }
        }
        com.moriafly.salt.ui.Text(text,
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 180.dp).padding(horizontal = 8.dp),
            style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
            color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        onEdit?.let { SaltChipAction(LucideIcons.Pencil, editDescription, it) }
        onDelete?.let { SaltChipAction(LucideIcons.Trash, stringResource(R.string.common_delete), it) }
    }
}

@Composable
private fun SaltChipAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(CircleShape)
        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        com.moriafly.salt.ui.Icon(androidx.compose.ui.graphics.vector.rememberVectorPainter(icon), label,
            modifier = Modifier.size(16.dp), tint = com.moriafly.salt.ui.SaltTheme.colors.subText)
    }
}
