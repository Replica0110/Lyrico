package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FolderSongsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "folder_manager")
fun FolderManagerScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: FolderManagerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val folders = uiState.folders
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedFolderId by remember { mutableLongStateOf(-1L) }
    val currentFolder = remember(selectedFolderId, folders) {
        folders.find { it.id == selectedFolderId }
    }
    val showConfirmDialog = remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            try {
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            val path = UriUtils.getFileAbsolutePath(context, it)
                ?: it.toString()

            viewModel.addFolder(
                path = path,
                treeUri = it.toString()
            )
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.folder_manager_title),
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { folderPickerLauncher.launch(null) }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.AddFolder,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        currentFolder?.let { folder ->
            WindowDialog(
                title = stringResource(R.string.dialog_remove_folder_title),
                show = showConfirmDialog.value,
                onDismissRequest = { showConfirmDialog.value = false }
            ) {
                Column {
                    Text(
                        text = folder.path,
                        modifier = Modifier.fillMaxWidth(),
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dialog_remove_folder_content_tip),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = {
                                showConfirmDialog.value = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                showConfirmDialog.value = false
                                viewModel.deleteFolder(folder)
                                selectedFolderId = -1L
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Text(
                text = stringResource(R.string.folder_tip_disabled_logic),
                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(12.dp)
            )
            uiState.error?.let { error ->
                Text(
                    text = stringResource(R.string.folder_scan_failed, error),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                    .fillMaxHeight(),
                overscrollEffect = null,
            ) {
                items(items = folders) { folder ->
                    FolderListItem(
                        folder = folder,
                        isScanning = folder.id in uiState.scanningFolderIds,
                        isQueued = folder.id in uiState.queuedFolderIds,
                        onClick = {
                            navigator.navigate(
                                FolderSongsDestination(
                                    folder.id,
                                    folder.path
                                )
                            )
                        },
                        onDelete = {
                            selectedFolderId = folder.id
                            showConfirmDialog.value = true
                        },
                        onRefresh = {
                            viewModel.refreshFolder(folder)
                        },
                        onIgnoredChange = { ignored ->
                            viewModel.setFolderIgnored(folder, ignored)
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun FolderListItem(
    folder: FolderEntity,
    isScanning: Boolean,
    isQueued: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onIgnoredChange: (Boolean) -> Unit
) {
    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
    val isBusy = isScanning || isQueued
    val statusText = when {
        isScanning -> stringResource(R.string.folder_scanning)
        isQueued -> stringResource(R.string.folder_scan_queued)
        folder.isIgnored -> stringResource(R.string.folder_hidden_song_count_format, folder.songCount)
        else -> stringResource(R.string.folder_song_count_format, folder.songCount)
    }
    val actionEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = if (folder.isIgnored) {
                    stringResource(R.string.folder_action_show)
                } else {
                    stringResource(R.string.folder_action_hide)
                },
                selected = folder.isIgnored,
                onClick = {
                    onIgnoredChange(!folder.isIgnored)
                }
            ),
            DropdownItem(
                text = stringResource(R.string.action_refresh_folder),
                onClick = onRefresh
            ),
            DropdownItem(
                text = stringResource(R.string.folder_action_remove),
                onClick = onDelete
            )
        )
    )

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ){
        BasicComponent(
            endActions = {
                OverlayIconDropdownMenu(
                    entry = actionEntry,
                    enabled = !isBusy
                ) {
                    Icon(
                        imageVector = MiuixIcons.More,
                        contentDescription = stringResource(R.string.cd_more_actions)
                    )
                }
            },
            bottomAction = {
                AnimatedVisibility(
                    visible = isScanning
                ) {
                    LinearProgressIndicator()
                }
            },
            enabled = !isBusy,
            onClick = onClick
        ) {
            Text(
                text = folderName,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = folder.path,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = statusText,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                fontSize = MiuixTheme.textStyles.body2.fontSize
            )
        }
    }
}
