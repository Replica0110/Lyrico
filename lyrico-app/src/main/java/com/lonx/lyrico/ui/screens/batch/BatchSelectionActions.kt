package com.lonx.lyrico.ui.screens.batch

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.model.batch.BatchTaskType
import com.lonx.lyrico.data.model.export.ExportDestination
import com.lonx.lyrico.ui.components.base.ExportDestinationBottomSheet
import com.lonx.lyrico.ui.components.base.YesNoDialog

import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.lyrics.LyricsOrganizationMenu
import com.ramcosta.composedestinations.generated.destinations.BatchEditDestination
import com.ramcosta.composedestinations.generated.destinations.BatchRenameDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@Composable
fun BoxScope.SongBatchSelectionActions(
    navigator: DestinationsNavigator,
    songs: List<SongEntity>,
    show: Boolean,
    expanded: Boolean,
    selectedSongUris: Set<String>,
    modifier: Modifier = Modifier,
    onExpandedChange: (Boolean) -> Unit,
    onSetSelectionUris: () -> Boolean,
    onBatchDelete: (List<SongEntity>) -> Unit,
    onBatchShare: (Context, List<SongEntity>) -> Unit
) {
    val batchMatchViewModel: BatchMatchViewModel = koinViewModel()
    val batchReplayGainViewModel: BatchReplayGainViewModel = koinViewModel()
    val batchLyricsFormatViewModel: BatchLyricsFormatViewModel = koinViewModel()
    val batchExportViewModel: BatchExportViewModel = koinViewModel()
    val batchMatchUiState by batchMatchViewModel.uiState.collectAsStateWithLifecycle()
    val batchReplayGainUiState by batchReplayGainViewModel.uiState.collectAsStateWithLifecycle()
    val batchLyricsFormatUiState by batchLyricsFormatViewModel.uiState.collectAsStateWithLifecycle()
    val batchExportUiState by batchExportViewModel.uiState.collectAsStateWithLifecycle()
    val matchTargets by batchMatchViewModel.visibleTargets.collectAsStateWithLifecycle()
    val batchMatchConfig by batchMatchViewModel.batchMatchConfig.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showExportDestinationSheet by remember { mutableStateOf(false) }
    var pendingExportTaskType by remember { mutableStateOf<BatchTaskType?>(null) }
    val batchExportFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val taskType = pendingExportTaskType
        pendingExportTaskType = null
        if (uri != null && taskType != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            batchExportViewModel.setSelectionUris(selectedSongUris.toList())
            batchExportViewModel.startBatchExport(
                taskType = taskType,
                destination = ExportDestination.SELECTED_DIRECTORY,
                destinationTreeUri = uri
            )
        }
    }

    ExportDestinationBottomSheet(
        show = showExportDestinationSheet,
        onDismissRequest = {
            showExportDestinationSheet = false
            pendingExportTaskType = null
        },
        onConfirm = { destination ->
            val taskType = pendingExportTaskType ?: return@ExportDestinationBottomSheet
            showExportDestinationSheet = false
            when (destination) {
                ExportDestination.SELECTED_DIRECTORY -> {
                    batchExportFolderPickerLauncher.launch(null)
                }

                ExportDestination.AUDIO_DIRECTORY -> {
                    pendingExportTaskType = null
                    batchExportViewModel.setSelectionUris(selectedSongUris.toList())
                    batchExportViewModel.startBatchExport(
                        taskType = taskType,
                        destination = ExportDestination.AUDIO_DIRECTORY
                    )
                }
            }
        }
    )

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
        matchType = batchMatchUiState.matchType,
        initialConfig = batchMatchConfig,
        visibleTargets = matchTargets,
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
        state = batchLyricsFormatUiState,
        onDismiss = batchLyricsFormatViewModel::closeConfig,
        onFormat = batchLyricsFormatViewModel::setTargetFormat,
        onConcurrency = batchLyricsFormatViewModel::setConcurrency,
        onTwoColumnMapping = batchLyricsFormatViewModel::setTwoColumnMapping,
        onThreeColumnMapping = batchLyricsFormatViewModel::setThreeColumnMapping,
        onConfirm = batchLyricsFormatViewModel::startBatchConvert
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

    BatchExportBottomSheet(
        batchExportUiState = batchExportUiState,
        onDismissRequest = {
            if (!batchExportUiState.isRunning) {
                batchExportViewModel.closeProgressDialog()
            }
        },
        onDismissFinished = {
            if (batchExportUiState.isSuccess) {
                batchExportViewModel.clearProgressDialog()
            }
        },
        onAbort = { batchExportViewModel.abortBatchExport() }
    )

ExpandableFabMenu(
    visible = show,
    expanded = expanded,
    enabled = selectedSongUris.isNotEmpty(),
    modifier = modifier,
    style = ExpandableFabMenuStyle.default().copy(
        mainIcon = LucideIcons.Plus
    ),
    itemCount = 12,
    onExpandedChange = onExpandedChange
) {
    FabMenuItem(
        label = stringResource(R.string.action_batch_replay_gain),
        icon = LucideIcons.Volume2,
        onClick = {
            onExpandedChange(false)
            batchReplayGainViewModel.setSelectionUris(selectedSongUris.toList())
            batchReplayGainViewModel.openReplayGainConfig()
        }
    )

    FabMenuItem(
        label = stringResource(R.string.lyrics_convert),
        icon = LucideIcons.Convert,
        onClick = {
            onExpandedChange(false)
            batchLyricsFormatViewModel.setSelectionUris(selectedSongUris.toList())
            batchLyricsFormatViewModel.openConfig(batchReplayGainUiState.concurrency)
        }
    )

    LyricsOrganizationMenu(onSelect = { operation ->
        onExpandedChange(false)
        batchLyricsFormatViewModel.setSelectionUris(selectedSongUris.toList())
        batchLyricsFormatViewModel.openConfig(batchReplayGainUiState.concurrency, operation)
    }) { open ->
        FabMenuItem(label = stringResource(R.string.lyrics_organize), icon = LucideIcons.Sort, onClick = open)
    }

    FabMenuItem(
        label = stringResource(R.string.action_batch_export_lyrics),
        icon = LucideIcons.FileUp,
        onClick = {
            onExpandedChange(false)
            pendingExportTaskType = BatchTaskType.EXPORT_LYRICS
            showExportDestinationSheet = true
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_batch_export_cover),
        icon = LucideIcons.Image,
        onClick = {
            onExpandedChange(false)
            pendingExportTaskType = BatchTaskType.EXPORT_COVER
            showExportDestinationSheet = true
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_batch_rename),
        icon = LucideIcons.Type,
        onClick = {
            onExpandedChange(false)
            if (onSetSelectionUris()) {
                navigator.navigate(BatchRenameDestination)
            }
        }
    )

    FabMenuItem(
        label = stringResource(R.string.batch_edit_title),
        icon = LucideIcons.Pencil,
        onClick = {
            onExpandedChange(false)
            if (onSetSelectionUris()) {
                navigator.navigate(BatchEditDestination())
            }
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_batch_match_cover),
        icon = LucideIcons.ImageSearch,
        onClick = {
            onExpandedChange(false)
            if (onSetSelectionUris()) {
                batchMatchViewModel.openBatchMatchConfig(BatchMatchType.COVER)
            }
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_batch_match_lyrics),
        icon = LucideIcons.TextSearch,
        onClick = {
            onExpandedChange(false)
            if (onSetSelectionUris()) {
                batchMatchViewModel.openBatchMatchConfig(BatchMatchType.LYRICS)
            }
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_batch_match_metadata),
        icon = LucideIcons.Tags,
        onClick = {
            onExpandedChange(false)
            if (onSetSelectionUris()) {
                batchMatchViewModel.openBatchMatchConfig(BatchMatchType.METADATA)
            }
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_delete),
        icon = LucideIcons.Trash,
        onClick = {
            onExpandedChange(false)
            showBatchDeleteDialog = true
        }
    )

    FabMenuItem(
        label = stringResource(R.string.action_share),
        icon = LucideIcons.Share2,
        onClick = {
            onExpandedChange(false)
            onBatchShare(context, songs)
        }
    )
}
}
