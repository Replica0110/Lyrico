package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarColors
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.FolderEntity
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.icons.ArrowBack
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, UnstableSaltUiApi::class)
@Composable
@Destination<RootGraph>(route = "folder_manager")
fun FolderManagerScreen(
    navigator: DestinationsNavigator
) {

    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val folders = uiState.folders
    val scrollState = rememberScrollState()
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
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarColors(
                    containerColor = SaltTheme.colors.background,
                    scrolledContainerColor = SaltTheme.colors.background,
                    navigationIconContentColor = SaltTheme.colors.text,
                    titleContentColor = SaltTheme.colors.text,
                    actionIconContentColor = SaltTheme.colors.text,
                    subtitleContentColor = SaltTheme.colors.subText
                ),
                title = { Text("文件夹管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(imageVector = SaltIcons.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        folderPickerLauncher.launch(null)
                    }) {
                        Icon(
                            painter = rememberTintedPainter(
                                painterResource(id = R.drawable.ic_addfolder_24dp), tint = SaltTheme.colors.text),
                            contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            ItemTip(text = "未启用的文件夹及其子目录中的歌曲将不会显示在库中")

            ItemOuterTitle("已发现的文件夹")
            RoundedColumn {
                if (folders.isEmpty()) {
                    ItemTip(text = "暂无文件夹数据，完成首次扫描后即可管理")
                } else {
                    folders.forEach { folder ->
                        val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
                        val songInfo = "歌曲 ${folder.songCount} 首${if (folder.isIgnored) " (已忽略)" else ""}"
                        val sourceInfo = if (folder.addedBySaf) "手动添加" else "自动发现"

                        Item(
                            onClick = {
                                selectedFolderId = folder.id
                                showSheet = true
                            },
                            iconPainter = if (folder.isIgnored) painterResource(id = R.drawable.ic_invisible_24dp) else painterResource(id = R.drawable.ic_visible_24dp),
                            text = folderName,
                            sub = "${folder.path}\n$songInfo · $sourceInfo",
                            )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        if (showConfirmDialog.value && currentFolder != null){
            YesNoDialog(
                onDismissRequest = { showConfirmDialog.value = false },
                onConfirm = {
                    showConfirmDialog.value = false
                    viewModel.deleteFolder(currentFolder)
                    showSheet = false
                },
                title = "确认要删除文件夹吗？",
                content = "文件夹路径: ${currentFolder.path}\n删除后，该文件夹下的歌曲将从库中隐藏",
                cancelText = "取消",
                confirmText = "确定"
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
            ItemTip("路径: ${folder.path}")

            // 忽略/启用设置
            ItemSwitcher(
                state = !folder.isIgnored,
                onChange = { onIgnoreChange() },
                text = "启用此文件夹",
                sub = "关闭后，该文件夹下的歌曲将从库中隐藏"
            )

            // 删除设置
            Item(
                onClick = onDelete,
                text = "移除记录",
                textColor = Color.Red,
                sub = "从数据库中移除此文件夹记录，不会删除物理文件",
                iconColor = Color.Red,
                arrowType = ItemArrowType.None
            )
        }
    }
}