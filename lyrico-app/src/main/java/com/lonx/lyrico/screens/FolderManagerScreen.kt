package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
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
                                showSheet = false
                                selectedFolderId = -1L
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        }

        currentFolder?.let { folder ->
            WindowBottomSheet(
                show = showSheet,
                onDismissRequest = {
                    showSheet = false
                    selectedFolderId = -1L
                }
            ) {
                FolderActionSheetContent(
                    folder = folder,
                    onIgnoreChange = { viewModel.toggleFolderIgnore(folder) },
                    onDelete = {
                        coroutineScope.launch {
                            showSheet = false
                            showConfirmDialog.value = true
                        }
                    }
                )
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
            if (folders.isEmpty()) {
                Text(
                    text = stringResource(R.string.folder_empty_state_tip),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(12.dp)
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


                item {
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        folders.forEachIndexed { index, folder ->
                            FolderListItem(
                                folder = folder,
                                onClick = {
                                    navigator.navigate(
                                        FolderSongsDestination(
                                            folder.id,
                                            folder.path
                                        )
                                    )
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
            if (folder.isIgnored) {
                Icon(
                    imageVector = MiuixIcons.Hide,
                    contentDescription = null
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Show,
                    contentDescription = null
                )
            }
        },
        endActions = {
            IconButton(onClick = onShowActions) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = stringResource(R.string.cd_info)
                )
            }
        },
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
            .padding(bottom = 32.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Card(
            modifier = Modifier.padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer,
            )
        ) {
            BasicComponent(
                title = folderName,
                summary = folder.path,
                startAction = {
                    if (folder.isIgnored) {
                        Icon(
                            imageVector = MiuixIcons.Hide,
                            contentDescription = null
                        )
                    } else {
                        Icon(
                            imageVector = MiuixIcons.Show,
                            contentDescription = null
                        )
                    }
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


        Card(
            modifier = Modifier.padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer,
            )
        ) {
            SwitchPreference(
                title = stringResource(R.string.folder_action_enable),
                summary = stringResource(R.string.folder_action_enable_sub),
                checked = !folder.isIgnored,
                onCheckedChange = { onIgnoreChange() }
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
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MiuixTheme.colorScheme.error
            )
        },
        onClick = onClick
    )
}
