package com.lonx.lyrico.ui.screens.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.editor.RatingStars
import com.lonx.lyrico.ui.components.editor.SaltEditorField
import com.lonx.lyrico.ui.components.editor.SaltEditorUndoAction
import com.lonx.lyrico.ui.components.editor.rememberEditorTextFieldState
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.theme.LyricoColors
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LIMITED_LYRICS_INPUT_MAX_LINES = 30

@Composable
internal fun RatingField(rating: Int?, onRatingChange: (Int) -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        RoundedColumn {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                com.moriafly.salt.ui.Text(
                    stringResource(R.string.label_rating),
                    style = SaltTheme.textStyles.sub.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = SaltTheme.colors.subText,
                )
                RatingStars(rating, onRatingChange)
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.label_rating),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextFieldDefaults.textFieldColors().labelColor,
                )
                RatingStars(rating, onRatingChange)
            }
        }
    }
}

@Composable
internal fun MetadataInputField(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    isModified: Boolean = false,
    onRevert: () -> Unit,
    isMultiline: Boolean = false,
    limitMultilineLines: Boolean = false,
    actionButtons: @Composable RowScope.() -> Unit = {}
) {
    val fieldLabel = label + if (isModified) {
        "(" + stringResource(R.string.status_modified) + ")"
    } else {
        ""
    }

    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltEditorField(
            label = fieldLabel,
            state = state,
            modifier = modifier,
            lineLimits = if (isMultiline) TextFieldLineLimits.MultiLine(
                minHeightInLines = 10,
                maxHeightInLines = if (limitMultilineLines) LIMITED_LYRICS_INPUT_MAX_LINES else Int.MAX_VALUE
            ) else TextFieldLineLimits.SingleLine,
            actions = {
                SaltEditorUndoAction(visible = isModified, onUndo = onRevert)
                actionButtons()
            }
        )
        return
    }

    val colors = TextFieldDefaults.textFieldColors(
        borderColor = if (isModified) {
            LyricoColors.modifiedBorder
        } else {
            MiuixTheme.colorScheme.primary
        }
    )

    if (isMultiline) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                AnimatedVisibility(
                    visible = isModified,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = LyricoColors.modifiedText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                actionButtons()
            }

            TextField(
                state = state,
                textStyle = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth(),
                label = fieldLabel,
                colors = colors,
                lineLimits = TextFieldLineLimits.MultiLine(
                    minHeightInLines = 10,
                    maxHeightInLines = if (limitMultilineLines) {
                        LIMITED_LYRICS_INPUT_MAX_LINES
                    } else {
                        Int.MAX_VALUE
                    }
                )
            )
        }
    } else {
        TextField(
            state = state,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            label = fieldLabel,
            colors = colors,
            trailingIcon = if (isModified) {
                {
                    IconButton(onClick = onRevert) {
                        Icon(
                            imageVector = LucideIcons.Undo,
                            contentDescription = "Undo"
                        )
                    }
                }
            } else {
                null
            }
        )
    }
}

@Composable
internal fun CustomMetadataFieldEditor(
    field: CustomTagField,
    isModified: Boolean,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    onRevert: () -> Unit
) {
    val fieldState = rememberEditorTextFieldState(
        value = field.value,
        onValueChange = onValueChange
    )

    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltEditorField(label = field.key, state = fieldState, actions = {
            SaltEditorUndoAction(visible = isModified, onUndo = onRevert)
            SaltIconButton(
                icon = LucideIcons.Trash,
                label = stringResource(R.string.action_delete),
                onClick = onRemove,
                tint = SaltTheme.colors.error,
                size = 32.dp,
                iconSize = 18.dp
            )
        })
        return
    }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isModified) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f))
                        .clickable { onRevert() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_undo_changes),
                        fontSize = 11.sp,
                        color = LyricoColors.modifiedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    6.dp,
                    Alignment.End
                )
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                        .clickable {
                            onRemove()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        MetadataInputField(
            label = field.key,
            state = fieldState,
            isModified = false,
            onRevert = onRevert
        )
    }
}
