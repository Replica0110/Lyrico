package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    val showConfirmDialog = remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = UriUtils.getFileAbsolutePath(context, it)
            if (path != null) {
                viewModel.addFolderByPath(path)
            }
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    BasicScreenBox(
        title = stringResource(R.string.folder_manager_title),
        onBack = { navigator.popBackStack() },
        toolbar = {
            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_addfolder_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        currentFolder?.let { folder ->
            if (showConfirmDialog.value) {
                SuperDialog(
                    title = stringResource(R.string.dialog_remove_folder_title),
                    show = showConfirmDialog,
                    onDismissRequest = { showConfirmDialog.value = false }
                ) {
                    Column {
                        Text(
                            text = folder.path,
                            modifier = Modifier.fillMaxWidth(),
                            color = MiuixTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.dialog_remove_folder_content_tip),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                text = stringResource(R.string.cancel),
                                onClick = { showConfirmDialog.value = false },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                text = stringResource(R.string.confirm),
                                onClick = {
                                    showConfirmDialog.value = false
                                    viewModel.deleteFolder(folder)
                                    showSheet = false
                                    selectedFolderId = -1L
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            }
        }

        currentFolder?.let { folder ->
            if (showSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showSheet = false
                        selectedFolderId = -1L
                    },
                    sheetState = sheetState,
                    containerColor = MiuixTheme.colorScheme.background,
                    contentColor = MiuixTheme.colorScheme.onBackground
                ) {
                    FolderActionSheetContent(
                        folder = folder,
                        onIgnoreChange = { viewModel.toggleFolderIgnore(folder) },
                        onDelete = {
                            coroutineScope.launch {
                                sheetState.hide()
                                showSheet = false
                                showConfirmDialog.value = true
                            }
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.padding(12.dp),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.folder_tip_disabled_logic),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            item {
                SmallTitle(text = stringResource(R.string.section_folder_discovered))
            }

            if (folders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.folder_empty_state_tip),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        folders.forEachIndexed { index, folder ->
                            FolderListItem(
                                folder = folder,
                                onClick = {
                                    navigator.navigate(FolderSongsDestination(folder.id, folder.path))
                                },
                                onShowActions = {
                                    selectedFolderId = folder.id
                                    showSheet = true
                                }
                            )

                            if (index != folders.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                    color = MiuixTheme.colorScheme.dividerLine,
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun FolderListItem(
    folder: FolderEntity,
    onClick: () -> Unit,
    onShowActions: () -> Unit
) {
    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
    val statusText = buildList {
        add(stringResource(R.string.folder_song_count_format, folder.songCount))
        add(
            if (folder.addedBySaf) {
                stringResource(R.string.folder_source_manual)
            } else {
                stringResource(R.string.folder_source_auto)
            }
        )
        if (folder.isIgnored) {
            add(stringResource(R.string.folder_status_ignored))
        }
    }.joinToString(" · ")

    BasicComponent(
        startAction = {
            FolderLeadingIcon(folder = folder)
        },
        endActions = {
            IconButton(onClick = onShowActions) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info_24dp),
                    contentDescription = stringResource(R.string.cd_info),
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
            }
        },
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
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

@Composable
private fun FolderLeadingIcon(folder: FolderEntity) {
    val iconPainter = if (folder.isIgnored) {
        painterResource(id = R.drawable.ic_invisible_24dp)
    } else {
        painterResource(id = R.drawable.ic_visible_24dp)
    }
    val iconContainerColor = if (folder.isIgnored) {
        MiuixTheme.colorScheme.surfaceContainerHighest
    } else {
        MiuixTheme.colorScheme.secondaryContainerVariant
    }
    val iconTint = if (folder.isIgnored) {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    } else {
        MiuixTheme.colorScheme.onBackground
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                color = iconContainerColor,
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
    }
}

@Composable
fun FolderActionSheetContent(
    folder: FolderEntity,
    onIgnoreChange: () -> Unit,
    onDelete: () -> Unit
) {
    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
    val statusText = buildList {
        add(stringResource(R.string.folder_song_count_format, folder.songCount))
        add(
            if (folder.addedBySaf) {
                stringResource(R.string.folder_source_manual)
            } else {
                stringResource(R.string.folder_source_auto)
            }
        )
        if (folder.isIgnored) {
            add(stringResource(R.string.folder_status_ignored))
        }
    }.joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.padding(horizontal = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground
            )
        ) {
            BasicComponent(
                title = folderName,
                summary = folder.path,
                startAction = {
                    FolderLeadingIcon(folder = folder)
                },
                bottomAction = {
                    Text(
                        text = statusText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                }
            )
        }

        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            SuperSwitch(
                title = stringResource(R.string.folder_action_enable),
                summary = stringResource(R.string.folder_action_enable_sub),
                checked = !folder.isIgnored,
                onCheckedChange = { onIgnoreChange() }
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                color = MiuixTheme.colorScheme.dividerLine,
                thickness = 0.5.dp
            )

            FolderSheetActionRow(
                title = stringResource(R.string.folder_action_remove),
                summary = stringResource(R.string.folder_action_remove_sub),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun FolderSheetActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    BasicComponent(
        title = title,
        titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
        summary = summary,
        endActions = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.common_delete),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.error
            )
        },
        onClick = onClick
    )
}
