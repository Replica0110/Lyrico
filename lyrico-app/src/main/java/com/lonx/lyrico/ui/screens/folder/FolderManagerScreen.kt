@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.folder

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.FolderEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.song.library.SongSortBy
import com.lonx.lyrico.data.song.library.SongSortInfo
import com.lonx.lyrico.data.song.library.SortOrder
import com.lonx.lyrico.domain.folder.breadcrumbsTo
import com.lonx.lyrico.domain.folder.buildFolderTree
import com.lonx.lyrico.domain.folder.folderDisplayName
import com.lonx.lyrico.platform.storage.UriUtils
import com.lonx.lyrico.ui.components.bar.SelectionTopBarState
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.tabs.AppTabs
import com.lonx.lyrico.ui.screens.batch.SongBatchSelectionActions
import com.lonx.lyrico.ui.screens.library.SongSelectionViewModel
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.popup.rememberPopupState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 顶层目录没有对应实体，用负数占位与数据库自增 id 区分。 */
private const val ROOT_FOLDER_ID = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "folder_manager")
fun FolderManagerScreen(
    navigator: DestinationsNavigator,
) {
    val viewModel: FolderManagerViewModel = koinViewModel()
    val selectionViewModel: SongSelectionViewModel = koinViewModel()

    val uiState by viewModel.uiState.collectAsState()
    val sortInfo by viewModel.sortInfo.collectAsState()
    val currentFolderSongs by viewModel.currentFolderSongs.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsState()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsState()
    val swipeSelectionLabel = stringResource(
        when {
            !isSelectionMode -> R.string.swipe_selection_enter_selection
            swipeAnchorUri == null -> R.string.swipe_selection_range_start
            else -> R.string.swipe_selection_range_end
        }
    )
    val swipeSelectionSecondaryLabel = if (!isSelectionMode) {
        stringResource(R.string.swipe_selection_range_start)
    } else {
        null
    }

    val folders = uiState.folders
    val folderTree = remember(folders) { buildFolderTree(folders) }
    val context = LocalContext.current

    var currentFolderId by rememberSaveable { mutableLongStateOf(ROOT_FOLDER_ID) }
    var folderContentTransition by remember { mutableStateOf<FolderContentTransition?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var savedPagerPage by rememberSaveable { mutableIntStateOf(FOLDER_TAB_INDEX) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = savedPagerPage,
        pageCount = { 2 },
    )
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // 对话框关闭动画期间仍要读到目录内容，因此引用在 show 置回 false 后保留，下次打开时替换。
    var folderToRemove by remember { mutableStateOf<FolderEntity?>(null) }
    var showRemoveFolderDialog by remember { mutableStateOf(false) }

    val currentFolder = remember(currentFolderId, folders) { folders.find { it.id == currentFolderId } }
    val currentNode = remember(currentFolderId, folderTree) { folderTree.nodeOf(currentFolderId) }
    val parentFolder = remember(currentNode, folderTree) {
        currentNode?.parentId?.let(folderTree::nodeOf)?.folder
    }
    val currentChildFolders = remember(currentFolderId, folderTree) {
        folderTree.childFoldersOf(currentFolderId)
    }
    // 目录切换后仓库流可能还停在上一个目录，先确认歌曲都属于当前目录再展示。
    val currentSongs = remember(currentFolder, currentFolderSongs) {
        if (currentFolder != null && currentFolderSongs.all { song -> song.folderId == currentFolder.id }) {
            currentFolderSongs
        } else {
            emptyList()
        }
    }
    val rootPathLabel = stringResource(R.string.folder_root_path)
    val currentBreadcrumbs = remember(currentFolderId, folderTree, rootPathLabel) {
        folderTree.breadcrumbsTo(currentFolderId, ROOT_FOLDER_ID, rootPathLabel)
    }

    LaunchedEffect(currentFolder?.id) {
        viewModel.setCurrentFolderId(currentFolder?.id)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page -> savedPagerPage = page }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            try {
                context.contentResolver.takePersistableUriPermission(it, flags)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }

            val path = UriUtils.getFileAbsolutePath(context, it) ?: it.toString()

            viewModel.addFolder(path = path, treeUri = it.toString())
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    fun navigateToFolder(folderId: Long, forward: Boolean) {
        if (folderId != currentFolderId) {
            folderContentTransition = FolderContentTransition(
                fromFolderId = currentFolderId,
                toFolderId = folderId,
                forward = forward,
            )
        }
        currentFolderId = folderId
    }

    fun removeFolder(folder: FolderEntity) {
        viewModel.deleteFolder(folder)
        if (folder.id == currentFolderId) {
            navigateToFolder(parentFolder?.id ?: ROOT_FOLDER_ID, forward = false)
        }
    }

    BackHandler(enabled = isSelectionMode || currentFolder != null) {
        when {
            isFabMenuExpanded -> isFabMenuExpanded = false
            isSelectionMode -> selectionViewModel.exitSelectionMode()
            currentFolder != null ->
                navigateToFolder(parentFolder?.id ?: ROOT_FOLDER_ID, forward = false)
        }
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val folderTitle = if (currentFolderId == ROOT_FOLDER_ID) {
        stringResource(R.string.folder_manager_title)
    } else {
        currentFolder?.path?.folderDisplayName() ?: stringResource(R.string.folder_manager_title)
    }
    val navigateUpAction: () -> Unit = {
        if (currentFolder != null) {
            navigateToFolder(parentFolder?.id ?: ROOT_FOLDER_ID, forward = false)
        } else {
            navigator.navigateUp()
        }
    }
    val allCurrentSongsSelected =
        currentSongs.isNotEmpty() && selectedSongUris.containsAll(currentSongs.map { it.uri })
    val saltSelectionState = if (salt && isSelectionMode) {
        SelectionTopBarState(
            title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
            allSelected = allCurrentSongsSelected,
            onClose = { selectionViewModel.exitSelectionMode() },
            onToggleAll = {
                if (allCurrentSongsSelected) selectionViewModel.deselectAll()
                else selectionViewModel.selectAll(currentSongs)
            },
        )
    } else null
    val sortPopupState = rememberPopupState()
    val showSortAction =
        currentFolder != null && pagerState.currentPage == SONG_TAB_INDEX && currentSongs.isNotEmpty()

    // 再次选择当前排序方式时切换升降序，其余情况从升序开始。
    fun onSortSelected(sortType: SongSortBy) {
        val isCurrent = sortInfo.sortBy == sortType
        val order = if (isCurrent && sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
        viewModel.onSortChange(SongSortInfo(sortBy = sortType, order = order))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffold(
            title = folderTitle,
            onBack = navigateUpAction,
            selection = saltSelectionState,
            actions = {
                if (currentFolderId == ROOT_FOLDER_ID) {
                    SaltIconButton(
                        icon = LucideIcons.FolderPlus,
                        label = stringResource(R.string.folder_manager_title),
                        onClick = { folderPickerLauncher.launch(null) },
                    )
                }
                if (showSortAction) {
                    SaltIconButton(
                        icon = LucideIcons.Sort,
                        label = stringResource(R.string.cd_sort),
                        onClick = { sortPopupState.expend() },
                    )
                    PopupMenu(
                        expanded = sortPopupState.expend,
                        onDismissRequest = sortPopupState::dismiss,
                    ) {
                        SongSortBy.entries.forEach { sortType ->
                            val isSelected = sortInfo.sortBy == sortType
                            PopupMenuItem(
                                text = stringResource(sortType.labelRes),
                                selected = isSelected,
                                sub = if (isSelected) sortOrderLabel(sortInfo.order) else null,
                                onClick = {
                                    sortPopupState.dismiss()
                                    onSortSelected(sortType)
                                },
                            )
                        }
                    }
                }
            },
            miuixTopBar = {
                AnimatedContent(
                    targetState = isSelectionMode,
                    label = "FolderManagerTopBarAnimation",
                    transitionSpec = {
                        val animationDuration = 300
                        val enter = fadeIn(tween(animationDuration)) +
                            slideInVertically(
                                animationSpec = tween(
                                    durationMillis = animationDuration,
                                    easing = FastOutSlowInEasing,
                                ),
                                initialOffsetY = { -it / 3 },
                            )
                        val exit = fadeOut(tween(animationDuration)) +
                            slideOutVertically(
                                animationSpec = tween(
                                    durationMillis = animationDuration,
                                    easing = FastOutSlowInEasing,
                                ),
                                targetOffsetY = { -it / 3 },
                            )

                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { selectionMode ->
                    if (selectionMode) {
                        SongSelectionTopAppBar(
                            songs = currentSongs,
                            selectedSongUris = selectedSongUris,
                            scrollBehavior = topAppBarScrollBehavior,
                            onSelectAll = selectionViewModel::selectAll,
                            onDeselectAll = selectionViewModel::deselectAll,
                            onClose = selectionViewModel::exitSelectionMode,
                        )
                    } else {
                        SmallTopAppBar(
                            title = folderTitle,
                            navigationIcon = {
                                IconButton(onClick = navigateUpAction) {
                                    Icon(
                                        imageVector = LucideIcons.ArrowLeft,
                                        contentDescription = null,
                                    )
                                }
                            },
                            actions = {
                                if (currentFolderId == ROOT_FOLDER_ID) {
                                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                        Icon(
                                            imageVector = LucideIcons.FolderPlus,
                                            contentDescription = null,
                                        )
                                    }
                                }

                                if (showSortAction) {
                                    OverlayIconDropdownMenu(
                                        entries = listOf(
                                            DropdownEntry(
                                                items = SongSortBy.entries.map { sortType ->
                                                    val isSelected = sortInfo.sortBy == sortType
                                                    DropdownItem(
                                                        text = stringResource(sortType.labelRes),
                                                        selected = isSelected,
                                                        summary = if (isSelected) {
                                                            sortOrderLabel(sortInfo.order)
                                                        } else {
                                                            null
                                                        },
                                                        onClick = { onSortSelected(sortType) },
                                                    )
                                                },
                                            )
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = LucideIcons.Sort,
                                            contentDescription = stringResource(R.string.cd_sort),
                                        )
                                    }
                                }
                            },
                            scrollBehavior = topAppBarScrollBehavior,
                        )
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        if (salt) scaffoldContentPadding(paddingValues)
                        else scaffoldTopHorizontalPadding(paddingValues)
                    ),
            ) {
                FolderPathHeader(
                    breadcrumbs = currentBreadcrumbs,
                    folderCount = currentChildFolders.size,
                    songCount = currentSongs.size,
                    onBreadcrumbClick = { folderId ->
                        navigateToFolder(folderId, forward = false)
                    },
                )

                uiState.error?.let { error ->
                    val message = stringResource(R.string.folder_operation_failed, error)
                    if (salt) com.moriafly.salt.ui.ItemTip(message) else Text(
                        text = message,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                FolderTabs(
                    foldersLabel = stringResource(R.string.folder_tab_folders),
                    songsLabel = stringResource(R.string.folder_tab_songs),
                    folderCount = currentChildFolders.size,
                    songCount = currentSongs.size,
                    selectedIndex = pagerState.currentPage,
                    onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                )

                FolderContentPager(
                    currentFolderId = currentFolderId,
                    currentFolderSongs = currentSongs,
                    transition = folderContentTransition,
                    pagerState = pagerState,
                    onTransitionFinished = { folderContentTransition = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page, snapshot, layerModifier ->
                    when (page) {
                        FOLDER_TAB_INDEX -> FolderChildrenPage(
                            folders = folderTree.childFoldersOf(snapshot.folderId),
                            folderTree = folderTree,
                            scanningFolderIds = uiState.scanningFolderIds,
                            queuedFolderIds = uiState.queuedFolderIds,
                            isSelectionMode = isSelectionMode,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                            onFolderClick = { folder -> navigateToFolder(folder.id, forward = true) },
                            onDeleteFolder = { folder ->
                                folderToRemove = folder
                                showRemoveFolderDialog = true
                            },
                            onRefreshFolder = viewModel::refreshFolder,
                            onIgnoredChange = viewModel::setFolderIgnored,
                            modifier = layerModifier,
                        )

                        else -> FolderSongsPage(
                            songs = snapshot.songs,
                            isSelectionMode = isSelectionMode,
                            selectedSongUris = selectedSongUris,
                            swipeSelectionLabel = swipeSelectionLabel,
                            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                            topAppBarScrollBehavior = topAppBarScrollBehavior,
                            onSongClick = { song ->
                                navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                            },
                            onToggleSelection = { song ->
                                selectionViewModel.toggleSelection(song.uri)
                            },
                            onSwipeSelection = { song ->
                                selectionViewModel.swipeSelect(song, snapshot.songs)
                            },
                            onShowSongMenu = { song ->
                                selectedSong = song
                                showMenuSheet = true
                            },
                            modifier = layerModifier,
                        )
                    }
                }
            }

            SongActionSheets(
                selectedSong = selectedSong,
                showMenuSheet = showMenuSheet,
                showDetailSheet = showDetailSheet,
                showDeleteDialog = showDeleteDialog,
                showRenameDialog = showRenameDialog,
                onDismissMenu = { showMenuSheet = false },
                onDismissMenuFinished = {
                    if (!showMenuSheet && !showDetailSheet && !showDeleteDialog && !showRenameDialog) {
                        selectedSong = null
                    }
                },
                onDismissDetail = { showDetailSheet = false },
                onDismissDelete = { showDeleteDialog = false },
                onDismissRename = { showRenameDialog = false },
                onShowDetail = { showDetailSheet = true },
                onShowDelete = { showDeleteDialog = true },
                onShowRename = { showRenameDialog = true },
                onPlay = { song -> selectionViewModel.play(context, song) },
                onDelete = { song -> selectionViewModel.delete(song) },
                onRename = { song, newFileName -> selectionViewModel.renameSong(song, newFileName) },
            )
        }

        SongBatchSelectionActions(
            navigator = navigator,
            songs = currentSongs,
            show = isSelectionMode && pagerState.currentPage == SONG_TAB_INDEX && currentSongs.isNotEmpty(),
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare,
        )
    }

    RemoveFolderDialog(
        folder = folderToRemove,
        show = showRemoveFolderDialog,
        onDismissRequest = { showRemoveFolderDialog = false },
        onConfirm = {
            showRemoveFolderDialog = false
            folderToRemove?.let(::removeFolder)
        },
    )
}

@Composable
private fun sortOrderLabel(order: SortOrder): String = stringResource(
    when (order) {
        SortOrder.ASC -> R.string.sort_ascending
        SortOrder.DESC -> R.string.sort_descending
    }
)

/** 子目录／歌曲两个分页的标签栏；两套主题的容器不同，标签文案与选中状态一致。 */
@Composable
private fun FolderTabs(
    foldersLabel: String,
    songsLabel: String,
    folderCount: Int,
    songCount: Int,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val labels = listOf("$foldersLabel ($folderCount)", "$songsLabel ($songCount)")
    if (LocalUiEngine.current == UiEngine.SALT) {
        AppTabs(
            tabs = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
        )
        return
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
    ) {
        TabRowWithContour(
            tabs = labels,
            selectedTabIndex = selectedIndex,
            onTabSelected = onSelected,
        )
    }
}

/** 移除已添加目录的确认框；内容在关闭动画期间仍需可见，因此按 [folder] 是否为空渲染。 */
@Composable
private fun RemoveFolderDialog(
    folder: FolderEntity?,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    val target = folder ?: return
    val title = stringResource(R.string.dialog_remove_folder_title)
    if (LocalUiEngine.current == UiEngine.SALT) {
        YesNoDialog(
            show = show,
            title = title,
            summary = target.path + "\n\n" + stringResource(R.string.dialog_remove_folder_content_tip),
            onDismissRequest = onDismissRequest,
            onConfirm = onConfirm,
        )
        return
    }
    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            Text(
                text = target.path,
                modifier = Modifier.fillMaxWidth(),
                color = MiuixTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.dialog_remove_folder_content_tip),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(20.dp))

                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
