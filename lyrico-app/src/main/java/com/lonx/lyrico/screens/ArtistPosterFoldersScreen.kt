@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.SaltConfirmDialog
import com.lonx.lyrico.ui.components.base.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.ArtistPosterFoldersViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.ArtistDetailDestination
import dev.jeziellago.compose.markdowntext.MarkdownText
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.viewmodel.ArtistPosterFile
import com.lonx.lyrico.ui.components.base.YesNoDialog

@Composable
@Destination<RootGraph>(route = "artist_poster_folders")
fun ArtistPosterFoldersScreen(navigator: DestinationsNavigator) {
    val viewModel: ArtistPosterFoldersViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            viewModel.addFolder(it)
        }
    }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showFolderDetails by rememberSaveable { mutableStateOf(false) }
    var actionFile by remember { mutableStateOf<ArtistPosterFile?>(null) }
    var showFileMenu by remember { mutableStateOf(false) }
    var previewUri by remember(state.folder?.uri) { mutableStateOf<String?>(null) }
    var showDeleteFile by remember { mutableStateOf(false) }
    var showRenameFile by remember { mutableStateOf(false) }
    var renameStem by remember { mutableStateOf("") }
    var cropFile by remember { mutableStateOf<ArtistPosterFile?>(null) }
    var cropBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showCrop by remember { mutableStateOf(false) }
    LaunchedEffect(state.operationMessage) {
        state.operationMessage?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
        viewModel.clearOperationMessage()
    }
    val salt = LocalUiEngine.current == UiEngine.SALT
    val scrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
    val topMenu = com.moriafly.salt.ui.popup.rememberPopupState()
    val menuEnabled = !state.isLoading && !state.isEditing

    LibraryDetailPage(
        title = stringResource(R.string.artist_poster_folders),
        onBack = { navigator.navigateUp() },
        actions = {
            // Salt 原生顶栏槽位：右侧操作图标与弹出菜单。
            Box {
                SaltIconButton(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions),
                    enabled = menuEnabled, onClick = { topMenu.expend() })
                com.moriafly.salt.ui.popup.PopupMenu(
                    expanded = topMenu.expend,
                    onDismissRequest = topMenu::dismiss
                ) {
                    com.moriafly.salt.ui.popup.PopupMenuItem(
                        text = stringResource(R.string.artist_poster_naming),
                        iconPainter = rememberVectorPainter(LucideIcons.Info),
                        onClick = { topMenu.dismiss(); showHelp = true }
                    )
                    if (state.folder != null) {
                        com.moriafly.salt.ui.popup.PopupMenuItem(
                            text = stringResource(R.string.artist_poster_folder_details),
                            iconPainter = rememberVectorPainter(LucideIcons.Info),
                            onClick = { topMenu.dismiss(); showFolderDetails = true }
                        )
                        com.moriafly.salt.ui.popup.PopupMenuItem(
                            text = stringResource(R.string.action_refresh_folder),
                            iconPainter = rememberVectorPainter(LucideIcons.RefreshCw),
                            onClick = { topMenu.dismiss(); viewModel.refresh() }
                        )
                        com.moriafly.salt.ui.popup.PopupMenuItem(
                            text = stringResource(R.string.artist_poster_folder_change),
                            iconPainter = rememberVectorPainter(LucideIcons.FolderPlus),
                            onClick = { topMenu.dismiss(); picker.launch(null) }
                        )
                        com.moriafly.salt.ui.popup.PopupMenuItem(
                            text = stringResource(R.string.folder_action_remove),
                            iconPainter = rememberVectorPainter(LucideIcons.Folder),
                            onClick = { topMenu.dismiss(); showRemoveDialog = true }
                        )
                    }
                }
            }
        },
        miuixTopBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.artist_poster_folders),
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(onClick = navigator::navigateUp) {
                            Icon(LucideIcons.ArrowLeft, stringResource(R.string.artist_poster_back))
                        }
                    },
                    actions = {
                        OverlayIconDropdownMenu(
                            entry = DropdownEntry(
                                items = buildList {
                                    add(DropdownItem(
                                        icon = { modifier -> PreferenceIcon(LucideIcons.Info, PreferenceIconTone.Tools, modifier = modifier) },
                                        text = stringResource(R.string.artist_poster_naming),
                                        onClick = { showHelp = true }
                                    ))
                                    if (state.folder != null) {
                                        add(DropdownItem(
                                            icon = { modifier -> PreferenceIcon(LucideIcons.Info, PreferenceIconTone.Tools, modifier = modifier) },
                                            text = stringResource(R.string.artist_poster_folder_details),
                                            onClick = { showFolderDetails = true }
                                        ))
                                        add(DropdownItem(
                                            icon = { modifier -> PreferenceIcon(LucideIcons.RefreshCw, PreferenceIconTone.Tools, modifier = modifier) },
                                            text = stringResource(R.string.action_refresh_folder),
                                            onClick = viewModel::refresh
                                        ))
                                        add(DropdownItem(
                                            icon = { modifier -> PreferenceIcon(LucideIcons.FolderPlus, PreferenceIconTone.Library, modifier = modifier) },
                                            text = stringResource(R.string.artist_poster_folder_change),
                                            onClick = { picker.launch(null) }
                                        ))
                                        add(DropdownItem(
                                            icon = { modifier -> PreferenceIcon(LucideIcons.Folder, PreferenceIconTone.Tools, modifier = modifier) },
                                            text = stringResource(R.string.folder_action_remove),
                                            onClick = { showRemoveDialog = true }
                                        ))
                                    }
                                }
                            ),
                            enabled = menuEnabled
                        ) {
                            Icon(LucideIcons.EllipsisVertical, stringResource(R.string.cd_more_actions))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().then(if (salt) Modifier else Modifier.blurSource(topBarBackdrop))) {
            val folder = state.folder
            if (folder != null) {
                ArtistPosterFolderContents(
                    folder = folder,
                    revision = state.revision,
                    isLoading = state.isLoading,
                    enabled = !state.isEditing,
                    hasError = state.error,
                    onChangeFolder = { picker.launch(null) },
                    onRefresh = viewModel::refresh,
                    onFileMenu = { previewUri = null; actionFile = it; showFileMenu = true },
                    onCrop = viewModel::cropFile,
                    onRename = { target ->
                        actionFile = target
                        renameStem = target.name.substringBeforeLast('.')
                        showRenameFile = true
                    },
                    onArtistDetails = { target ->
                        target.artistId?.let { navigator.navigate(ArtistDetailDestination(artistId = it)) }
                    },
                    onPreviewSelection = { previewUri = it },
                    previewUri = previewUri,
                    onDismissPreview = { previewUri = null },
                    padding = scaffoldContentPadding(padding, horizontalExtra = 12.dp, bottomExtra = 12.dp),
                    scrollBehavior = scrollBehavior
                )
            } else {
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize().padding(scaffoldContentPadding(padding)), contentAlignment = Alignment.Center) {
                        if (salt) androidx.compose.material3.CircularProgressIndicator(
                            color = com.moriafly.salt.ui.SaltTheme.colors.highlight,
                            trackColor = com.moriafly.salt.ui.SaltTheme.colors.stroke)
                        else CircularProgressIndicator()
                    }
                } else {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Folders,
                        title = stringResource(R.string.artist_poster_folder_not_selected),
                        summary = stringResource(if (state.error) R.string.artist_poster_folder_error else R.string.artist_poster_folders_empty),
                        icon = LucideIcons.FolderPlus,
                        actionText = stringResource(R.string.artist_poster_folder_add),
                        onAction = { picker.launch(null) },
                        modifier = Modifier.fillMaxSize().padding(scaffoldContentPadding(padding))
                    )
                }
            }
        }
        val file = actionFile
        com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet(
            show = showFileMenu, label = file?.name.orEmpty(), enableNestedScroll = false,
            onDismissRequest = { showFileMenu = false }
        ) {
            ItemMenuAction(
                icon = LucideIcons.Image, tone = PreferenceIconTone.Library,
                title = stringResource(R.string.poster_view_artwork),
                enabled = file != null && !state.isEditing,
                onClick = { showFileMenu = false; previewUri = file?.uri }
            )
            ItemMenuAction(
                icon = LucideIcons.Users, tone = PreferenceIconTone.Library,
                title = stringResource(R.string.poster_artist_details),
                summary = if (file?.artistId == null) stringResource(R.string.poster_artist_unmatched) else null,
                enabled = file?.artistId != null && !state.isLoading && !state.isEditing,
                onClick = {
                    file?.artistId?.let { artistId ->
                        showFileMenu = false
                        navigator.navigate(ArtistDetailDestination(artistId = artistId))
                    }
                }
            )
            ItemMenuAction(
                icon = LucideIcons.Pencil, tone = PreferenceIconTone.Tools,
                title = stringResource(R.string.action_rename), enabled = !state.isEditing, onClick = {
                renameStem = file?.name?.substringBeforeLast('.').orEmpty()
                showFileMenu = false; showRenameFile = true
            })
            if (file?.name?.substringAfterLast('.')?.lowercase() in listOf("jpg", "jpeg", "png", "webp")) {
                ItemMenuAction(
                    icon = LucideIcons.Crop, tone = PreferenceIconTone.Library,
                    title = stringResource(R.string.label_crop_artist_image), enabled = !state.isEditing, onClick = {
                    showFileMenu = false
                    cropFile = file
                    file?.let { viewModel.readBitmap(it) { bitmap -> cropBitmap = bitmap; showCrop = true } }
                })
            }
            ItemMenuAction(
                icon = LucideIcons.Trash, tone = PreferenceIconTone.Danger,
                title = stringResource(R.string.action_delete), enabled = !state.isEditing,
                onClick = { showFileMenu = false; showDeleteFile = true })
        }
        val proposedName = file?.let { target ->
            com.lonx.lyrico.domain.poster.PosterFileEditNames.rename(target.name, renameStem,
                state.folder?.posters.orEmpty().filter { it.uri != target.uri }.map { it.name })
        }
        val fileExtension = file?.name?.substringAfterLast('.')?.takeIf { it.isNotEmpty() && it != file.name }.orEmpty()
        if (salt) {
            // Salt 原生弹窗：保留取消、点击外部、关闭完成与校验失败不关窗的语义。
            val folder = state.folder
            if (showDeleteFile && file != null) {
                com.moriafly.salt.ui.dialog.YesNoDialog(
                    title = stringResource(R.string.dialog_delete_file_title),
                    content = stringResource(R.string.dialog_delete_file_content, file.name),
                    onDismissRequest = { showDeleteFile = false },
                    onConfirm = { file.let(viewModel::deleteFile); showDeleteFile = false },
                    cancelText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.confirm),
                )
            }
            // 重命名需要固定扩展名、校验提示与禁用确认，Salt 的 InputDialog 没有这些槽位，走共享扩展弹窗。
            SaltConfirmDialog(
                show = showRenameFile && file != null,
                title = stringResource(R.string.dialog_rename_title),
                confirmEnabled = proposedName != null && proposedName != file.name && !state.isEditing,
                onDismissRequest = { showRenameFile = false },
                onConfirm = {
                    file?.let { target ->
                        viewModel.renameFile(target, renameStem) { renamed ->
                            actionFile = renamed
                            if (previewUri == target.uri) previewUri = renamed.uri
                        }
                    }
                },
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.confirm),
            ) {
                com.moriafly.salt.ui.RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            com.moriafly.salt.ui.ItemEdit(text = renameStem, onChange = { renameStem = it })
                        }
                        if (fileExtension.isNotEmpty()) com.moriafly.salt.ui.Text(
                            "." + fileExtension, style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                }
                com.moriafly.salt.ui.Text(
                    stringResource(R.string.poster_rename_hint),
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                    modifier = Modifier.padding(top = 8.dp))
                if (proposedName == null) com.moriafly.salt.ui.Text(
                    stringResource(R.string.poster_rename_invalid),
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.error,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (showHelp) {
                com.moriafly.salt.ui.dialog.BasicDialog(onDismissRequest = { showHelp = false }) {
                    com.moriafly.salt.ui.dialog.DialogTitle(stringResource(R.string.artist_poster_naming))
                    Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        com.moriafly.salt.ui.Text(
                            stringResource(R.string.artist_poster_folders_summary),
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        MarkdownText(
                            modifier = Modifier.fillMaxWidth(),
                            markdown = stringResource(R.string.artist_poster_folders_help),
                            linkColor = com.moriafly.salt.ui.SaltTheme.colors.highlight,
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.main.copy(
                                color = com.moriafly.salt.ui.SaltTheme.colors.text
                            )
                        )
                    }
                }
            }
            if (folder != null && showFolderDetails) {
                com.moriafly.salt.ui.dialog.BasicDialog(onDismissRequest = { showFolderDetails = false }) {
                    com.moriafly.salt.ui.dialog.DialogTitle(stringResource(R.string.artist_poster_folder_details))
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        com.moriafly.salt.ui.Text(folder.name, style = com.moriafly.salt.ui.SaltTheme.textStyles.largeTitle)
                        com.moriafly.salt.ui.Text(
                            folder.path,
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        com.moriafly.salt.ui.Text(
                            stringResource(R.string.artist_poster_folder_status, folder.posters.orEmpty().size, folder.matchedPosterCount),
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
            if (showRemoveDialog && folder != null) {
                com.moriafly.salt.ui.dialog.YesNoDialog(
                    title = stringResource(R.string.dialog_remove_folder_title),
                    content = folder.path + "\n\n" + stringResource(R.string.artist_poster_folder_remove_tip),
                    onDismissRequest = { showRemoveDialog = false },
                    onConfirm = { viewModel.removeFolder(); showRemoveDialog = false },
                    cancelText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.confirm),
                )
            }
        } else {
            val folder = state.folder
            YesNoDialog(
                show = showDeleteFile && file != null, title = stringResource(R.string.dialog_delete_file_title),
                summary = stringResource(R.string.dialog_delete_file_content, file?.name.orEmpty()),
                onDismissRequest = { showDeleteFile = false },
                onConfirm = { file?.let(viewModel::deleteFile) }
            )
            YesNoDialog(
                show = showRenameFile && file != null, title = stringResource(R.string.dialog_rename_title),
                confirmEnabled = proposedName != null && proposedName != file.name && !state.isEditing,
                onDismissRequest = { showRenameFile = false },
                onConfirm = { file?.let { target ->
                    viewModel.renameFile(target, renameStem) { renamed ->
                        actionFile = renamed
                        if (previewUri == target.uri) previewUri = renamed.uri
                    }
                } },
                content = {
                    TextField(value = renameStem, onValueChange = { renameStem = it }, maxLines = 1,
                        modifier = Modifier.fillMaxWidth(), trailingIcon = {
                            Text("." + file?.name?.substringAfterLast('.').orEmpty(), modifier = Modifier.padding(horizontal = 12.dp))
                        })
                    Text(stringResource(R.string.poster_rename_hint), style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
                    if (proposedName == null) Text(stringResource(R.string.poster_rename_invalid),
                        style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                }
            )
            WindowDialog(
                title = stringResource(R.string.artist_poster_naming),
                show = showHelp,
                onDismissRequest = { showHelp = false }
            ) {
                Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.artist_poster_folders_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    MarkdownText(
                        modifier = Modifier.fillMaxWidth(),
                        markdown = stringResource(R.string.artist_poster_folders_help),
                        linkColor = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.body2.copy(
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    )
                }
            }
            if (folder != null) {
                WindowDialog(
                    title = stringResource(R.string.artist_poster_folder_details),
                    show = showFolderDetails,
                    onDismissRequest = { showFolderDetails = false }
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(folder.name, style = MiuixTheme.textStyles.title4)
                        Text(
                            folder.path,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            stringResource(R.string.artist_poster_folder_status, folder.posters.orEmpty().size, folder.matchedPosterCount),
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
            if (showRemoveDialog && folder != null) {
                WindowDialog(
                    title = stringResource(R.string.dialog_remove_folder_title),
                    show = true,
                    onDismissRequest = { showRemoveDialog = false }
                ) {
                    Column {
                        Text(folder.path, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.artist_poster_folder_remove_tip),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(
                                text = stringResource(R.string.cancel),
                                onClick = { showRemoveDialog = false },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(20.dp))
                            TextButton(
                                text = stringResource(R.string.confirm),
                                onClick = {
                                    viewModel.removeFolder()
                                    showRemoveDialog = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColorsPrimary()
                            )
                        }
                    }
                }
            }
        }
        com.lonx.lyrico.ui.components.crop.ImageCropperSheet(
            show = showCrop, bitmap = cropBitmap, title = stringResource(R.string.label_crop_artist_image),
            onCrop = { bitmap -> cropFile?.let { viewModel.cropFile(it, bitmap) } },
            onDismissRequest = { showCrop = false },
            onDismissFinished = { if (!showCrop) { cropBitmap = null; cropFile = null } }
        )
    }
}
