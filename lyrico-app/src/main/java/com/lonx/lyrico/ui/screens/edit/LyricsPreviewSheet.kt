package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.editor.EditorSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.lyrics.saltLyricsReadingStyle
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.RoundedColumn
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricsPreviewSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    plainLyrics: String?,
    showRomanization: Boolean,
    onShowRomanizationChange: (Boolean) -> Unit,
    showTranslation: Boolean,
    onShowTranslationChange: (Boolean) -> Unit,
    onCopyLyrics: () -> Unit,
) {
    val salt = LocalUiEngine.current == UiEngine.SALT

    EditorSheet(
        show = show,
        enableNestedScroll = false,
        tools = {
            if (salt) {
                SaltIconButton(
                    icon = LucideIcons.Copy,
                    label = stringResource(R.string.action_copy_lyrics),
                    onClick = onCopyLyrics
                )
            } else {
                IconButton(onClick = onCopyLyrics) {
                    Icon(
                        imageVector = LucideIcons.Copy,
                        contentDescription = null
                    )
                }
            }
        },
        title = stringResource(R.string.label_plain_lyrics),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            val plainLyricsScrollState = rememberScrollState()
            // 歌词正文自成一组卡片；音译/译文是独立按钮，留在卡片外。
            val lyricsBody: @Composable ColumnScope.() -> Unit = {
                Box(
                    modifier = Modifier
                        .then(if (salt) Modifier.weight(1f, fill = false) else Modifier)
                        .heightIn(min = 30.dp, max = 420.dp)
                        .fillMaxWidth()
                        .verticalScroll(plainLyricsScrollState)
                ) {
                    SelectionContainer(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .fillMaxWidth()
                    ) {
                        if (salt) {
                            com.moriafly.salt.ui.Text(
                                text = plainLyrics ?: stringResource(R.string.lyrics_empty),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                style = saltLyricsReadingStyle(),
                            )
                        } else {
                            Text(
                                style = MiuixTheme.textStyles.body2,
                                text = plainLyrics ?: stringResource(R.string.lyrics_empty),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            if (salt) {
                RoundedColumn { lyricsBody() }
            } else {
                lyricsBody()
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (salt) Modifier.padding(horizontal = 16.dp, vertical = 8.dp) else Modifier)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlainLyricsToggleChip(
                    text = stringResource(R.string.roma),
                    selected = showRomanization,
                    onClick = { onShowRomanizationChange(!showRomanization) }
                )
                PlainLyricsToggleChip(
                    text = stringResource(R.string.translation),
                    selected = showTranslation,
                    onClick = { onShowTranslationChange(!showTranslation) }
                )
            }
        }
    }
}

@Composable
private fun PlainLyricsToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        // Salt 原生 Small 是 40dp + 正文字号，和 Miuix 分支的 30dp 胶囊差太多。
        PillButton(
            text = text,
            selected = selected,
            onClick = onClick,
            style = PillButtonDefaults.style(PillButtonSize.Medium)
        )
        return
    }

    val background = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    }

    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
