@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.sheet.miuix.MiuixBottomSheet
import com.lonx.lyrico.ui.components.sheet.salt.SaltBottomSheet
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.Card as SaltCard
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.Text as SaltText
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 「从已选歌曲取值」的候选列表，供标签字段与自定义标签共用。
 */
@Composable
internal fun BatchEditValueSheet(
    show: Boolean,
    title: String,
    loading: Boolean,
    options: List<BatchEditSelectableValue>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBottomSheet(show, onDismiss, title = title) {
            when {
                loading -> RoundedColumn {
                    SaltText(
                        stringResource(R.string.batch_edit_loading_selected_values),
                        modifier = Modifier.padding(16.dp)
                    )
                }

                options.isEmpty() -> IllustratedEmptyState(
                    title = stringResource(R.string.batch_edit_no_selected_values),
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(options, key = { "${it.sourceUri}\n${it.value}" }) { option ->
                        SaltCard(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { onPick(option.value) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                SaltText(option.value, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        return
    }

    MiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                loading -> {
                    Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                        BasicComponent(title = stringResource(R.string.batch_edit_loading_selected_values))
                    }
                }

                options.isEmpty() -> {
                    IllustratedEmptyState(
                        title = stringResource(R.string.batch_edit_no_selected_values),
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .scrollEndHaptic()
                            .overScrollVertical(),
                        overscrollEffect = null
                    ) {
                        items(
                            items = options,
                            key = { "${it.sourceUri}\n${it.value}" }
                        ) { option ->
                            BatchEditValueOptionItem(
                                option = option,
                                onClick = { onPick(option.value) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchEditValueOptionItem(
    option: BatchEditSelectableValue,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(vertical = 6.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = option.value,
                style = MiuixTheme.textStyles.main,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
