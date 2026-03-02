package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.ui.components.ItemExt
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.RoundedColumnType
import com.moriafly.salt.ui.SaltDimens
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.ext.safeMainCompat
import com.moriafly.salt.ui.lazy.LazyColumn
import com.moriafly.salt.ui.lazy.items
import com.moriafly.salt.ui.outerPadding
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FolderSongsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "folder_manager")
fun FolderManagerScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: FolderManagerViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val folders = uiState.folders

    val context = LocalContext.current

    // 底部弹窗
    var selectedFolderId by remember { mutableLongStateOf(-1L) }
    val currentFolder = remember(selectedFolderId, uiState.folders) {
        uiState.folders.find { it.id == selectedFolderId }
    }
    val sheetState = rememberModalBottomSheetState()
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
            // 持久化权限
            context.contentResolver.takePersistableUriPermission(
                it, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    BasicScreenBox(
        title = stringResource(R.string.folder_manager_title),
        onBack = {
            navigator.popBackStack()
        },
        toolbar = {
            IconButton(
                onClick = {
                    folderPickerLauncher.launch(null)
                },
                modifier = Modifier
                    .size(56.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_addfolder_24dp),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SaltTheme.dimens.itemIcon)
                )
            }
        }
    ) {
        if (showConfirmDialog.value && currentFolder != null){
            YesNoDialog(
                onDismissRequest = { showConfirmDialog.value = false },
                onConfirm = {
                    showConfirmDialog.value = false
                    viewModel.deleteFolder(currentFolder)
                    showSheet = false
                },
                title = stringResource(R.string.dialog_remove_folder_title),
                content = currentFolder.path,
                drawContent = {
                    Column(modifier = Modifier.fillMaxWidth().padding(SaltTheme.dimens.padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.dialog_remove_folder_content_tip),
                            style = SaltTheme.textStyles.sub
                        )
                    }
                },
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.confirm),
            )
        }

        if (showSheet && currentFolder != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSheet = false
                    selectedFolderId = -1L
                },
                sheetState = sheetState,
                containerColor = SaltTheme.colors.background,
                contentColor = SaltTheme.colors.text
            ) {
                FolderActionSheetContent(
                    folder = currentFolder,
                    onIgnoreChange = {
                        viewModel.toggleFolderIgnore(currentFolder)
                    },
                    onDelete = {
                        showConfirmDialog.value = true
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            item {
                // TODO Replace ItemOuterTip when Salt UI 2.9.0-alpha07 released
                ItemTip(
                    text = stringResource(R.string.folder_tip_disabled_logic),
                    modifier = Modifier
                        .outerPadding(vertical = false)
                )
            }

            item {
                ItemOuterTitle(stringResource(R.string.section_folder_discovered))
            }

            item {
                Spacer(Modifier.height(SaltDimens.RoundedColumnInListEdgePadding))
            }

            if (folders.isEmpty()) {
                item {
                    RoundedColumn(
                        type = RoundedColumnType.InList
                    ) {
                        ItemTip(stringResource(R.string.folder_empty_state_tip))
                    }
                }
            } else {
                items(folders) { folder ->
                    val ignoredText = if (folder.isIgnored) stringResource(R.string.folder_status_ignored) else ""
                    val songCountText = stringResource(R.string.folder_song_count_format, folder.songCount)
                    val songInfo = "$songCountText$ignoredText"

                    val sourceInfo = if (folder.addedBySaf)
                        stringResource(R.string.folder_source_manual)
                    else
                        stringResource(R.string.folder_source_auto)
                    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }

                    RoundedColumn(
                        type = RoundedColumnType.InList
                    ) {
                        ItemExt(
                            onClick = {
                                navigator.navigate(FolderSongsDestination(folder.id,folder.path))
                            },
                            iconPainter = if (folder.isIgnored)
                                painterResource(id = R.drawable.ic_invisible_24dp)
                            else
                                painterResource(id = R.drawable.ic_visible_24dp),
                            text = folderName,
                            sub = "${folder.path}\n$songInfo · $sourceInfo",
                            iconEnd = {
                                IconButton(
                                    onClick = {
                                        selectedFolderId = folder.id
                                        showSheet = true
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_info_24dp),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(SaltTheme.dimens.itemIcon)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(SaltDimens.RoundedColumnInListEdgePadding))
            }

            item {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeMainCompat))
            }
        }
    }
}
@OptIn(UnstableSaltUiApi::class)
@Composable
fun FolderActionSheetContent(
    folder: FolderEntity,
    onIgnoreChange: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // 操作列表
        RoundedColumn {
            ItemTip(stringResource(R.string.folder_sheet_path_prefix, folder.path))

            // 忽略/启用设置
            ItemSwitcher(
                state = !folder.isIgnored,
                onChange = { onIgnoreChange() },
                text = stringResource(R.string.folder_action_enable),
                sub = stringResource(R.string.folder_action_enable_sub)
            )

            // 删除设置
            Item(
                onClick = onDelete,
                text = stringResource(R.string.folder_action_remove),
                textColor = Color.Red,
                sub = stringResource(R.string.folder_action_remove_sub),
                iconColor = Color.Red,
                arrowType = ItemArrowType.None
            )
        }
    }
}