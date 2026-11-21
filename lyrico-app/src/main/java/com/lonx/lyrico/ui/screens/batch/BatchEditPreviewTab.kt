@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 预览页：每首歌列出将被改写的字段。两套主题各自展开同一份 [BatchEditPreview]。
 */
@Composable
internal fun BatchEditPreviewTab(
    previews: List<BatchEditPreview>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    topAppBarNestedScrollConnection: NestedScrollConnection
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        var expandedKey by remember { mutableStateOf<String?>(null) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 12.dp)) {
            if (previews.isEmpty()) item {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Tasks,
                    title = stringResource(R.string.batch_edit_preview_empty),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(previews, key = { it.songUri }) { preview ->
                RoundedColumn {
                    Item(
                        text = preview.fileName,
                        sub = stringResource(R.string.batch_edit_preview_change_count, preview.changes.size),
                        onClick = { expandedKey = if (expandedKey == preview.songUri) null else preview.songUri }
                    )
                    AnimatedVisibility(expandedKey == preview.songUri) {
                        Column(
                            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            preview.changes.forEach { change ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SaltText(
                                        change.labelResId?.let { stringResource(it) } ?: change.customLabel.orEmpty(),
                                        color = SaltTheme.colors.highlight
                                    )
                                    SaltText(
                                        stringResource(R.string.batch_edit_preview_old_value, displayBatchEditPreviewValue(change.oldValue)),
                                        style = SaltTheme.textStyles.sub,
                                        color = SaltTheme.colors.subText
                                    )
                                    SaltText(
                                        stringResource(R.string.batch_edit_preview_new_value, displayBatchEditPreviewValue(change.newValue)),
                                        style = SaltTheme.textStyles.sub
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }
    var expandedPreviewKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarNestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        overscrollEffect = null,
    ) {
        if (previews.isEmpty()) {
            item {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Tasks,
                    title = stringResource(R.string.batch_edit_preview_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(
                items = previews,
                key = { it.songUri }
            ) { preview ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .animateContentSize()
                ) {
                    BasicComponent(
                        onClick = {
                            expandedPreviewKey =
                                if (expandedPreviewKey == preview.songUri) null else preview.songUri
                        }
                    ) {
                        Text(
                            text = preview.fileName,
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.batch_edit_preview_change_count, preview.changes.size),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )

                        AnimatedVisibility(visible = expandedPreviewKey == preview.songUri) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                preview.changes.forEach { change ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = change.labelResId?.let { stringResource(it) } ?: change.customLabel.orEmpty(),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.batch_edit_preview_old_value,
                                                displayBatchEditPreviewValue(change.oldValue)
                                            ),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.batch_edit_preview_new_value,
                                                displayBatchEditPreviewValue(change.newValue)
                                            ),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun displayBatchEditPreviewValue(value: String): String {
    return when (value) {
        "<current_cover>" -> stringResource(R.string.batch_edit_preview_current_cover)
        "<remove_cover>" -> stringResource(R.string.batch_edit_preview_remove_cover)
        "" -> stringResource(R.string.batch_edit_preview_empty_value)
        else -> value
    }
}
