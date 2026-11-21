@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.editor.SaltEditorField
import com.lonx.lyrico.ui.components.editor.rememberEditorTextFieldState
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.image.PreviewableImage
import com.lonx.lyrico.ui.components.image.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 单首歌的字段编辑控件：文本字段、自定义标签、封面、评级，以及承载它们的卡片。
 * 每项都自带 Salt 与 Miuix 两套实现，调用方只传状态与回调。
 */

/**
 * 单个标签编辑字段
 * value 为 "<keep>" 时表示不修改该字段
 */
@Composable
internal fun BatchEditFieldItem(
    field: BatchEditField,
    value: String,
    onValueChange: (String) -> Unit,
    onSelectFromSongs: () -> Unit,
    isMultiline: Boolean = false
) {
    val fieldState = rememberEditorTextFieldState(value, onValueChange)
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBatchField(
            stringResource(field.labelResId),
            fieldState,
            onKeep = { onValueChange("<keep>") },
            onSelectFromSongs,
            isMultiline
        )
        return
    }
    val isKeep = value == "<keep>"

    if (isMultiline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isKeep) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { /* 无法恢复，已经是 <keep> */ }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onValueChange("<keep>") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(onClick = onSelectFromSongs) {
                    Icon(
                        imageVector = LucideIcons.ListCheck,
                        contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                    )
                }
            }
            TextField(
                textStyle = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth(),
                state = fieldState,
                label = if (isKeep) stringResource(R.string.batch_edit_unchanged_field_label, stringResource(field.labelResId)) else stringResource(field.labelResId),
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 6),
            )
        }
    } else {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            state = fieldState,
            label = if (isKeep) stringResource(R.string.batch_edit_unchanged_field_label, stringResource(field.labelResId)) else stringResource(field.labelResId),
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        onValueChange(if (isKeep) "" else "<keep>")
                    }) {
                        Icon(
                            imageVector = if (isKeep) LucideIcons.Close else LucideIcons.Undo,
                            contentDescription = null,
                            tint = if (isKeep)
                                MiuixTheme.colorScheme.primary
                            else
                                MiuixTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onSelectFromSongs) {
                        Icon(
                            imageVector = LucideIcons.ListCheck,
                            contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                        )
                    }
                }
            },
            lineLimits = TextFieldLineLimits.MultiLine(),
        )
    }
}

/**
 * 自定义标签编辑字段
 */
@Composable
internal fun BatchEditCustomFieldItem(
    keyName: String,
    value: String,
    onValueChange: (String) -> Unit,
    onKeep: () -> Unit,
    onSelectFromSongs: () -> Unit,
) {
    val fieldState = rememberEditorTextFieldState(value, onValueChange)
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBatchField(keyName, fieldState, onKeep, onSelectFromSongs)
        return
    }
    val isKeep = value == "<keep>"

    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        state = fieldState,
        label = if (isKeep) stringResource(R.string.batch_edit_unchanged_field_label, keyName) else keyName,
        trailingIcon = {
            Row {
                IconButton(onClick = {
                    if (isKeep) {
                        onValueChange("")
                    } else {
                        onKeep()
                    }
                }) {
                    Icon(
                        imageVector = if (isKeep) LucideIcons.Close else LucideIcons.Undo,
                        contentDescription = null,
                        tint = if (isKeep)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onSelectFromSongs) {
                    Icon(
                        imageVector = LucideIcons.ListCheck,
                        contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                    )
                }
            }
        },
        lineLimits = TextFieldLineLimits.MultiLine(),
    )
}

/**
 * 封面编辑区
 */
@Composable
internal fun BatchEditCoverSection(
    coverUri: Any?,
    isRemoved: Boolean,
    onCoverClick: () -> Unit
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onCoverClick).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (coverUri != null && !isRemoved) PreviewableImage(coverUri, null, onClick = onCoverClick,
                modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
            else com.moriafly.salt.ui.Icon(androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.Image),
                null, modifier = Modifier.size(40.dp), tint = SaltTheme.colors.subText)
            SaltText(
                stringResource(if (isRemoved) R.string.batch_edit_cover_remove else R.string.batch_edit_cover_hint),
                style = SaltTheme.textStyles.sub,
                color = if (isRemoved) SaltTheme.colors.error else SaltTheme.colors.subText
            )
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onCoverClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isRemoved) {
            Text(
                text = stringResource(R.string.batch_edit_cover_remove),
                color = MiuixTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        } else if (coverUri != null) {
            PreviewableImage(
                model = coverUri,
                onClick = onCoverClick,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                placeholder = rememberTintedPainter(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    tint = LyricoColors.coverPlaceholderIcon
                ),
                error = rememberTintedPainter(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    tint = LyricoColors.coverPlaceholderIcon
                )
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.batch_edit_cover_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
internal fun BatchEditRatingItem(
    rating: Int,
    isModified: Boolean,
    onRatingChange: (Int) -> Unit,
    onRevert: () -> Unit
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f)) {
                (1..5).forEach { value ->
                    SaltIconButton(
                        LucideIcons.Star, "$value",
                        { onRatingChange(if (rating == value) 0 else value) },
                        tint = if (value <= rating) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                        size = 36.dp, iconSize = 24.dp
                    )
                }
            }
            if (isModified) SaltIconButton(LucideIcons.Undo, stringResource(R.string.action_undo_changes), onRevert, size = 36.dp)
            else SaltText("<keep>", style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
        }
        return
    }
    val onSurfaceDim = MiuixTheme.colorScheme.onSurfaceVariantActions

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            endActions = {
                if (isModified) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            content = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isFilled = i <= rating

                        Icon(
                            painter = painterResource(
                                if (isFilled)
                                    R.drawable.ic_filled_star_24dp
                                else
                                    R.drawable.ic_outline_star_24dp
                            ),
                            contentDescription = null,
                            tint = if (isFilled)
                                MiuixTheme.colorScheme.primary
                            else
                                onSurfaceDim.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onRatingChange(
                                        if (rating == i) 0 else i
                                    )
                                }
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun BatchEditorGroup(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) RoundedColumn(content = content)
    else Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun SaltBatchField(
    label: String, state: TextFieldState,
    onKeep: () -> Unit, onSelect: () -> Unit, multiline: Boolean = false,
) {
    val keep = state.text.toString() == "<keep>"
    val fieldLabel = if (keep) stringResource(R.string.batch_edit_unchanged_field_label, label) else label
    SaltEditorField(
        label = fieldLabel,
        state = state,
        lineLimits = if (multiline) {
            TextFieldLineLimits.MultiLine(minHeightInLines = 6, maxHeightInLines = 12)
        } else TextFieldLineLimits.SingleLine,
        grouped = false,
    ) {
        SaltIconButton(
            if (keep) LucideIcons.Close else LucideIcons.Undo,
            stringResource(if (keep) R.string.action_delete else R.string.action_undo_changes),
            { if (keep) state.setTextAndPlaceCursorAtEnd("") else onKeep() },
            tint = if (keep) SaltTheme.colors.highlight else SaltTheme.colors.error,
            size = 32.dp, iconSize = 18.dp
        )
        SaltIconButton(
            LucideIcons.ListCheck,
            stringResource(R.string.batch_edit_select_from_selected_songs), onSelect,
            size = 32.dp, iconSize = 18.dp
        )
    }
}
