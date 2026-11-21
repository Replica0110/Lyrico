package com.lonx.lyrico.ui.components.bar

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopAppBarInsetsPadding
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SongSelectionTopAppBar(
    songs: List<SongEntity>,
    selectedSongUris: Set<String>,
    scrollBehavior: ScrollBehavior,
    color: Color = Color.Unspecified,
    applyInsets: Boolean = true,
    onSelectAll: (List<SongEntity>) -> Unit,
    onDeselectAll: () -> Unit,
    onClose: () -> Unit
) {
    val allSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })
    SelectionTopAppBar(
        selectedCount = selectedSongUris.size,
        allSelected = allSelected,
        scrollBehavior = scrollBehavior, color = color, applyInsets = applyInsets,
        onToggleAll = { if (allSelected) onDeselectAll() else onSelectAll(songs) },
        onClose = onClose
    )
}

@Composable
fun SelectionTopAppBar(
    selectedCount: Int,
    allSelected: Boolean,
    scrollBehavior: ScrollBehavior,
    color: Color = Color.Unspecified,
    applyInsets: Boolean = true,
    onToggleAll: () -> Unit,
    onClose: () -> Unit
) {
    BoxWithConstraints {
        val compactTopBar = maxWidth < 360.dp

        SmallTopAppBar(
            title = "",
            color = color,
            modifier = if (applyInsets) {
                Modifier.scaffoldTopAppBarInsetsPadding()
            } else {
                Modifier
            },
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = false,
            navigationIcon = {
                Text(
                    text = stringResource(
                        R.string.selection_mode_selected_count,
                        selectedCount
                    )
                )
            },
            actions = {
                if (!compactTopBar) {
                    TextButton(
                        onClick = onToggleAll
                    ) {
                        Text(
                            text = stringResource(
                                if (allSelected) {
                                    R.string.action_deselect_all
                                } else {
                                    R.string.action_select_all
                                }
                            ),
                            color = MiuixTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }

                TextButton(
                    onClick = onClose
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        )
    }
}
