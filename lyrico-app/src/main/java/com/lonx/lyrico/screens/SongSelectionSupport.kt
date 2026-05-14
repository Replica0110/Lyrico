package com.lonx.lyrico.screens

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.batch.BatchLyricsFormatBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchLyricsFormatConfigBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchMatchBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchMatchConfigBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchRGBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchRGConfigBottomSheet
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.viewmodel.BatchLyricsFormatViewModel
import com.lonx.lyrico.viewmodel.BatchMatchViewModel
import com.lonx.lyrico.viewmodel.BatchReplayGainViewModel
import com.ramcosta.composedestinations.generated.destinations.BatchEditDestination
import com.ramcosta.composedestinations.generated.destinations.BatchRenameDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SongSelectionTopAppBar(
    songs: List<SongEntity>,
    selectedSongUris: Set<String>,
    scrollBehavior: ScrollBehavior,
    onSelectAll: (List<SongEntity>) -> Unit,
    onDeselectAll: () -> Unit,
    onClose: () -> Unit
) {
    val allSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })

    BoxWithConstraints {
        val compactTopBar = maxWidth < 360.dp

        SmallTopAppBar(
            title = "",
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                Text(
                    text = stringResource(
                        R.string.selection_mode_selected_count,
                        selectedSongUris.size
                    )
                )
            },
            actions = {
                if (!compactTopBar) {
                    TextButton(
                        onClick = {
                            if (allSelected) {
                                onDeselectAll()
                            } else {
                                onSelectAll(songs)
                            }
                        }
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

@Composable
fun BoxScope.SongBatchSelectionActions(
    navigator: DestinationsNavigator,
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    expanded: Boolean,
    selectedSongUris: Set<String>,
    onExpandedChange: (Boolean) -> Unit,
    onSetSelectionUris: () -> Boolean,
    onBatchDelete: (List<SongEntity>) -> Unit,
    onBatchShare: (Context, List<SongEntity>) -> Unit
) {
    val batchMatchViewModel: BatchMatchViewModel = koinViewModel()
    val batchReplayGainViewModel: BatchReplayGainViewModel = koinViewModel()
    val batchLyricsFormatViewModel: BatchLyricsFormatViewModel = koinViewModel()
    val batchMatchUiState by batchMatchViewModel.uiState.collectAsStateWithLifecycle()
    val batchReplayGainUiState by batchReplayGainViewModel.uiState.collectAsStateWithLifecycle()
    val batchLyricsFormatUiState by batchLyricsFormatViewModel.uiState.collectAsStateWithLifecycle()
    val batchMatchConfig by batchMatchViewModel.batchMatchConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    YesNoDialog(
        show = showBatchDeleteDialog,
        onDismissRequest = { showBatchDeleteDialog = false },
        summary = stringResource(
            R.string.dialog_batch_delete_content,
            selectedSongUris.size
        ),
        onConfirm = {
            showBatchDeleteDialog = false
            onBatchDelete(songs)
        }
    )

    BatchMatchConfigBottomSheet(
        show = batchMatchUiState.showBatchConfigDialog,
        initialConfig = batchMatchConfig,
        onDismissRequest = { config ->
            batchMatchViewModel.saveBatchMatchConfig(config)
            batchMatchViewModel.closeBatchMatchConfig()
        },
        onConfirm = { config ->
            batchMatchViewModel.saveBatchMatchConfig(config)
            batchMatchViewModel.batchMatch(songs, config)
        }
    )

    BatchMatchBottomSheet(
        onDismissRequest = {
            if (!batchMatchUiState.isRunning) batchMatchViewModel.closeBatchMatchDialog()
        },
        onDismissFinished = {
            if (!batchMatchUiState.isRunning) batchMatchViewModel.closeBatchMatchDialog()
        },
        enableNestedScroll = false,
        uiState = batchMatchUiState,
        onAbort = { batchMatchViewModel.abortBatchMatch() }
    )

    BatchRGConfigBottomSheet(
        show = batchReplayGainUiState.showConfigDialog,
        initialConcurrency = batchReplayGainUiState.concurrency,
        onDismissRequest = { concurrency ->
            batchReplayGainViewModel.setConcurrency(concurrency)
            batchReplayGainViewModel.closeReplayGainConfig()
        },
        onConfirm = { concurrency ->
            batchReplayGainViewModel.setConcurrency(concurrency)
            batchReplayGainViewModel.startBatchScan()
        }
    )

    BatchRGBottomSheet(
        batchReplayGainUiState = batchReplayGainUiState,
        onDismissRequest = { batchReplayGainViewModel.closeProgressDialog() },
        onAbort = { batchReplayGainViewModel.abortBatchScan() },
        onDismissFinished = {
            if (batchReplayGainUiState.isSuccess) {
                batchReplayGainViewModel.closeProgressDialog()
            }
        }
    )

    BatchLyricsFormatConfigBottomSheet(
        show = batchLyricsFormatUiState.showConfigDialog,
        initialConcurrency = batchLyricsFormatUiState.concurrency,
        initialTargetFormat = batchLyricsFormatUiState.targetFormat,
        onDismissRequest = { concurrency, targetFormat ->
            batchLyricsFormatViewModel.setConcurrency(concurrency)
            batchLyricsFormatViewModel.setTargetFormat(targetFormat)
            batchLyricsFormatViewModel.closeConfig()
        },
        onConfirm = { concurrency, targetFormat ->
            batchLyricsFormatViewModel.setConcurrency(concurrency)
            batchLyricsFormatViewModel.setTargetFormat(targetFormat)
            batchLyricsFormatViewModel.startBatchConvert()
        }
    )

    BatchLyricsFormatBottomSheet(
        batchLyricsFormatUiState = batchLyricsFormatUiState,
        onDismissRequest = {
            if (!batchLyricsFormatUiState.isRunning) {
                batchLyricsFormatViewModel.closeProgressDialog()
            }
        },
        onDismissFinished = {
            if (batchLyricsFormatUiState.isSuccess) {
                batchLyricsFormatViewModel.clearProgressDialog()
            }
        },
        onAbort = { batchLyricsFormatViewModel.abortBatchConvert() }
    )

    ExpandableFabMenu(
        visible = isSelectionMode,
        expanded = expanded,
        enabled = selectedSongUris.isNotEmpty(),
        style = ExpandableFabMenuStyle.default().copy(
            mainIcon = MiuixIcons.Add
        ),
        onExpandedChange = onExpandedChange
    ) {
        FabMenuItem(
            label = stringResource(R.string.action_batch_replay_gain),
            icon = MiuixIcons.Edit,
            onClick = {
                onExpandedChange(false)
                batchReplayGainViewModel.setSelectionUris(selectedSongUris.toList())
                batchReplayGainViewModel.openReplayGainConfig()
            }
        )

        FabMenuItem(
            label = stringResource(R.string.action_batch_convert_lyrics_format),
            icon = MiuixIcons.Edit,
            onClick = {
                onExpandedChange(false)
                batchLyricsFormatViewModel.setSelectionUris(selectedSongUris.toList())
                batchLyricsFormatViewModel.openConfig(batchReplayGainUiState.concurrency)
            }
        )

        FabMenuItem(
            label = stringResource(R.string.action_batch_rename),
            icon = MiuixIcons.Rename,
            onClick = {
                onExpandedChange(false)
                if (onSetSelectionUris()) {
                    navigator.navigate(BatchRenameDestination)
                }
            }
        )

        FabMenuItem(
            label = stringResource(R.string.batch_edit_title),
            icon = MiuixIcons.Edit,
            onClick = {
                onExpandedChange(false)
                if (onSetSelectionUris()) {
                    navigator.navigate(BatchEditDestination())
                }
            }
        )

        FabMenuItem(
            label = stringResource(R.string.action_batch_match),
            icon = MiuixIcons.Edit,
            onClick = {
                onExpandedChange(false)
                if (onSetSelectionUris()) {
                    batchMatchViewModel.openBatchMatchConfig()
                }
            }
        )

        FabMenuItem(
            label = stringResource(R.string.action_delete),
            icon = MiuixIcons.Delete,
            onClick = {
                onExpandedChange(false)
                showBatchDeleteDialog = true
            }
        )

        FabMenuItem(
            label = stringResource(R.string.action_share),
            icon = MiuixIcons.Share,
            onClick = {
                onExpandedChange(false)
                onBatchShare(context, songs)
            }
        )
    }
}
