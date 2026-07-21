package com.lonx.lyrico.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Sort
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
    val selectionViewModel: SongSelectionViewModel = koinViewModel()

    val uiState by viewModel.uiState.collectAsState()
    val sortInfo by viewModel.sortInfo.collectAsState()
    val currentFolderSongs by viewModel.currentFolderSongs.collectAsState()
    val dedupResult by viewModel.dedupResult.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(dedupResult) {
        dedupResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearDedupResult()
        }
    }

    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsState()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsState()
    val swipeSelectionLabel = stringResource(
        if (!isSelectionMode) {
            R.string.swipe_selection_enter_selection
        } else if (swipeAnchorUri == null) {
            R.string.swipe_selection_range_start
        } else {
            R.string.swipe_selection_range_end
        }
    )
    val swipeSelectionSecondaryLabel = if (!isSelectionMode) {
        stringResource(R.string.swipe_selection_range_start)
    } else {
        null
    }

    val folders = uiState.folders
    val folderTree = remember(folders) {
        buildFolderTree(folders)
    }
    var currentFolderId by rememberSaveable { mutableLongStateOf(ROOT_FOLDER_ID) }
    var selectedFolderId by rememberSaveable { mutableLongStateOf(ROOT_FOLDER_ID) }
    var folderContentTransition by remember { mutableStateOf<FolderContentTransition?>(null) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var savedPagerPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = savedPagerPage,
        pageCount = { 2 }
    )
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val selectedFolder = remember(selectedFolderId, folders) {
        folders.find { it.id == selectedFolderId }
    }
    val currentFolder = remember(currentFolderId, folders) {
        folders.find { it.id == currentFolderId }
    }
    LaunchedEffect(currentFolder?.id) {
        viewModel.setCurrentFolderId(currentFolder?.id)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { page -> savedPagerPage = page }
    }
    val currentNode = remember(currentFolder, folderTree) {
        currentFolder?.let { folderTree.nodesById[it.id] }
    }
    val parentFolder = remember(currentNode, folderTree) {
        currentNode?.parentId?.let { folderTree.nodesById[it]?.folder }
    }
    val currentChildFolders = remember(currentNode, folderTree) {
        currentNode?.childFolders ?: folderTree.rootFolders
    }
    val currentSongs = if (
        currentFolder != null &&
        currentFolderSongs.all { song -> song.folderId == currentFolder.id }
    ) {
        currentFolderSongs
    } else {
        emptyList()
    }
    val rootPathLabel = stringResource(R.string.folder_root_path)
    val currentBreadcrumbs = remember(currentFolder, currentNode, folderTree, rootPathLabel) {
        buildFolderBreadcrumbs(currentFolder, currentNode, folderTree, rootPathLabel)
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

    fun navigateToFolder(folderId: Long, forward: Boolean) {
        if (folderId != currentFolderId) {
            folderContentTransition = FolderContentTransition(
                fromFolderId = currentFolderId,
                toFolderId = folderId,
                forward = forward
            )
        }
        currentFolderId = folderId
    }

    BackHandler(enabled = isSelectionMode || currentFolder != null) {
        when {
            isFabMenuExpanded -> {
                isFabMenuExpanded = false
            }

            isSelectionMode -> {
                selectionViewModel.exitSelectionMode()
            }

            currentFolder != null -> {
                navigateToFolder(parentFolder?.id ?: ROOT_FOLDER_ID, forward = false)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AnimatedContent(
                    targetState = isSelectionMode,
                    label = "FolderManagerTopBarAnimation",
                    transitionSpec = {
                        val animationDuration = 300
                        val enter = fadeIn(tween(animationDuration)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        durationMillis = animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetY = { -it / 3 }
                                )
                        val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(
                                        durationMillis = animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetY = { -it / 3 }
                                )

                        (enter togetherWith exit).using(SizeTransform(clip = false))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { selectionMode ->
                    if (selectionMode) {
                        SongSelectionTopAppBar(
                            songs = currentSongs,
                            selectedSongUris = selectedSongUris,
                            scrollBehavior = topAppBarScrollBehavior,
                            onSelectAll = selectionViewModel::selectAll,
                            onDeselectAll = selectionViewModel::deselectAll,
                            onClose = selectionViewModel::exitSelectionMode
                        )
                    } else {
                        SmallTopAppBar(
                            title = if (currentFolderId == ROOT_FOLDER_ID) {
                                stringResource(R.string.folder_manager_title)
                            } else {
                                currentFolder?.path
                                    ?.substringAfterLast("/")
                                    ?.ifBlank { currentFolder.path }
                                    ?: stringResource(R.string.folder_manager_title)
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = {
                                        if (currentFolder != null) {
                                            navigateToFolder(
                                                parentFolder?.id ?: ROOT_FOLDER_ID,
                                                forward = false
                                            )
                                        } else {
                                            navigator.navigateUp()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = null
                                    )
                                }
                            },
                            actions = {
                                if (currentFolderId == ROOT_FOLDER_ID) {
                                    IconButton(
                                        onClick = { folderPickerLauncher.launch(null) }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.AddFolder,
                                            contentDescription = null
                                        )
                                    }
                                }

                                if (pagerState.currentPage == 1 && currentSongs.isNotEmpty()) {
                                    TextButton(
                                        text = stringResource(R.string.action_dedup),
                                        onClick = {
                                            viewModel.runDedup()
                                        },
                                        colors = ButtonDefaults.textButtonColorsPrimary()
                                    )
                                }

                                if (
                                    currentFolder != null &&
                                    pagerState.currentPage == 1 &&
                                    currentSongs.isNotEmpty()
                                ) {
                                    val sortTypes = SortBy.entries.toList()
                                    val sortEntries = DropdownEntry(
                                        items = sortTypes.map { sortType ->
                                            val isSelected = sortInfo.sortBy == sortType

                                            DropdownItem(
                                                text = stringResource(sortType.labelRes),
                                                selected = isSelected,
                                                summary = if (isSelected) {
                                                    stringResource(
                                                        when (sortInfo.order) {
                                                            SortOrder.ASC -> R.string.sort_ascending
                                                            SortOrder.DESC -> R.string.sort_descending
                                                        }
                                                    )
                                                } else {
                                                    null
                                                },
                                                onClick = {
                                                    val newOrder = if (isSelected) {
                                                        if (sortInfo.order == SortOrder.ASC) {
                                                            SortOrder.DESC
                                                        } else {
                                                            SortOrder.ASC
                                                        }
                                                    } else {
                                                        SortOrder.ASC
                                                    }

                                                    viewModel.onSortChange(
                                                        SortInfo(
                                                            sortBy = sortType,
                                                            order = newOrder
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    )

                                    OverlayIconDropdownMenu(
                                        entries = listOf(sortEntries)
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Sort,
                                            contentDescription = stringResource(R.string.cd_sort)
                                        )
                                    }
                                }
                            },
                            scrollBehavior = topAppBarScrollBehavior
                        )
                    }
                }
            }
        ) { paddingValues ->
            selectedFolder?.let { folder ->
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
                                    selectedFolderId = ROOT_FOLDER_ID
                                    if (folder.id == currentFolderId) {
                                        navigateToFolder(
                                            parentFolder?.id ?: ROOT_FOLDER_ID,
                                            forward = false
                                        )
                                    }
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
                    .padding(scaffoldTopHorizontalPadding(paddingValues))
            ) {
                FolderPathHeader(
                    breadcrumbs = currentBreadcrumbs,
                    folderCount = currentChildFolders.size,
                    songCount = currentSongs.size,
                    onBreadcrumbClick = { folderId ->
                        navigateToFolder(folderId, forward = false)
                    }
                )

                uiState.error?.let { error ->
                    Text(
                        text = stringResource(R.string.folder_scan_failed, error),
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    TabRowWithContour(
                        tabs = listOf(
                            "${stringResource(R.string.folder_tab_folders)} (${currentChildFolders.size})",
                            "${stringResource(R.string.folder_tab_songs)} (${currentSongs.size})"
                        ),
                        selectedTabIndex = pagerState.currentPage,
                        onTabSelected = { index ->
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }

                FolderPagerSlideContent(
                    currentFolderId = currentFolderId,
                    transition = folderContentTransition,
                    folders = folders,
                    folderTree = folderTree,
                    currentFolderSongs = currentSongs,
                    pagerState = pagerState,
                    scanningFolderIds = uiState.scanningFolderIds,
                    queuedFolderIds = uiState.queuedFolderIds,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    navigator = navigator,
                    selectionViewModel = selectionViewModel,
                    onFolderClick = { folder ->
                        navigateToFolder(folder.id, forward = true)
                    },
                    onDeleteFolder = { folder ->
                        selectedFolderId = folder.id
                        showConfirmDialog.value = true
                    },
                    onRefreshFolder = viewModel::refreshFolder,
                    onIgnoredChange = viewModel::setFolderIgnored,
                    onShowSongMenu = { song ->
                        selectedSong = song
                        showMenuSheet = true
                    },
                    onTransitionFinished = {
                        folderContentTransition = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            SongActionSheets(
                selectedSong = selectedSong,
                showMenuSheet = showMenuSheet,
                showDetailSheet = showDetailSheet,
                showDeleteDialog = showDeleteDialog,
                showRenameDialog = showRenameDialog,
                onDismissMenu = { showMenuSheet = false },
                onDismissMenuFinished = { selectedSong = null },
                onDismissDetail = { showDetailSheet = false },
                onDismissDelete = { showDeleteDialog = false },
                onDismissRename = { showRenameDialog = false },
                onShowDetail = { showDetailSheet = true },
                onShowDelete = { showDeleteDialog = true },
                onShowRename = { showRenameDialog = true },
                onPlay = { song ->
                    selectionViewModel.play(context, song)
                },
                onDelete = { song ->
                    selectionViewModel.delete(song)
                },
                onRename = { song, newFileName ->
                    selectionViewModel.renameSong(song, newFileName)
                }
            )
        }

        SongBatchSelectionActions(
            navigator = navigator,
            songs = currentSongs,
            show = isSelectionMode && pagerState.currentPage == 1 && currentSongs.isNotEmpty(),
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare
        )
    }
}

private const val ROOT_FOLDER_ID = -1L

private data class FolderContentTransition(
    val fromFolderId: Long,
    val toFolderId: Long,
    val forward: Boolean
)

private data class FolderContentSnapshot(
    val folderId: Long,
    val childFolders: List<FolderEntity>,
    val songs: List<SongEntity>
)

private data class FolderBreadcrumb(
    val folderId: Long,
    val label: String
)

@Composable
private fun FolderPagerSlideContent(
    currentFolderId: Long,
    transition: FolderContentTransition?,
    folders: List<FolderEntity>,
    folderTree: FolderTree,
    currentFolderSongs: List<SongEntity>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    scanningFolderIds: Set<Long>,
    queuedFolderIds: Set<Long>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    navigator: DestinationsNavigator,
    selectionViewModel: SongSelectionViewModel,
    onFolderClick: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRefreshFolder: (FolderEntity) -> Unit,
    onIgnoredChange: (FolderEntity, Boolean) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
    onTransitionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(transition) {
        if (transition != null) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = FastOutSlowInEasing
                )
            )
            onTransitionFinished()
        } else {
            progress.snapTo(1f)
        }
    }

    val currentSnapshot = remember(
        currentFolderId,
        folders,
        folderTree,
        currentFolderSongs
    ) {
        buildFolderContentSnapshot(
            folderId = currentFolderId,
            folders = folders,
            folderTree = folderTree,
            currentFolderId = currentFolderId,
            currentFolderSongs = currentFolderSongs,
            useCurrentFolderSongs = true
        )
    }

    val activeTransition = transition
    val fromSnapshot = remember(
        activeTransition?.fromFolderId,
        folders,
        folderTree
    ) {
        activeTransition?.let {
            buildFolderContentSnapshot(
                folderId = it.fromFolderId,
                folders = folders,
                folderTree = folderTree,
                currentFolderId = currentFolderId,
                currentFolderSongs = currentFolderSongs,
                useCurrentFolderSongs = false
            )
        }
    }
    val toSnapshot = remember(
        activeTransition?.toFolderId,
        currentFolderId,
        folders,
        folderTree,
        currentFolderSongs
    ) {
        activeTransition?.let {
            buildFolderContentSnapshot(
                folderId = it.toFolderId,
                folders = folders,
                folderTree = folderTree,
                currentFolderId = currentFolderId,
                currentFolderSongs = currentFolderSongs,
                useCurrentFolderSongs = it.toFolderId == currentFolderId
            )
        }
    }

    HorizontalPager(
        state = pagerState,
        userScrollEnabled =  false,
        modifier = modifier
    ) { page ->
        FolderPageSlideContent(
            page = page,
            currentSnapshot = currentSnapshot,
            fromSnapshot = fromSnapshot,
            toSnapshot = toSnapshot,
            transition = activeTransition,
            progress = progress.value,
            folderTree = folderTree,
            scanningFolderIds = scanningFolderIds,
            queuedFolderIds = queuedFolderIds,
            isSelectionMode = isSelectionMode,
            selectedSongUris = selectedSongUris,
            swipeSelectionLabel = swipeSelectionLabel,
            swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
            topAppBarScrollBehavior = topAppBarScrollBehavior,
            navigator = navigator,
            selectionViewModel = selectionViewModel,
            onFolderClick = onFolderClick,
            onDeleteFolder = onDeleteFolder,
            onRefreshFolder = onRefreshFolder,
            onIgnoredChange = onIgnoredChange,
            onShowSongMenu = onShowSongMenu,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun FolderPageSlideContent(
    page: Int,
    currentSnapshot: FolderContentSnapshot,
    fromSnapshot: FolderContentSnapshot?,
    toSnapshot: FolderContentSnapshot?,
    transition: FolderContentTransition?,
    progress: Float,
    folderTree: FolderTree,
    scanningFolderIds: Set<Long>,
    queuedFolderIds: Set<Long>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    navigator: DestinationsNavigator,
    selectionViewModel: SongSelectionViewModel,
    onFolderClick: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRefreshFolder: (FolderEntity) -> Unit,
    onIgnoredChange: (FolderEntity, Boolean) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds()
    ) {
        val density = LocalDensity.current
        val travelPx = with(density) { maxWidth.toPx() * 0.45f }

        if (transition == null || fromSnapshot == null || toSnapshot == null) {
            FolderPage(
                page = page,
                snapshot = currentSnapshot,
                folderTree = folderTree,
                scanningFolderIds = scanningFolderIds,
                queuedFolderIds = queuedFolderIds,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                navigator = navigator,
                selectionViewModel = selectionViewModel,
                onFolderClick = onFolderClick,
                onDeleteFolder = onDeleteFolder,
                onRefreshFolder = onRefreshFolder,
                onIgnoredChange = onIgnoredChange,
                onShowSongMenu = onShowSongMenu,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val fromOffset = if (transition.forward) {
                -travelPx * progress
            } else {
                travelPx * progress
            }
            val toOffset = if (transition.forward) {
                travelPx * (1f - progress)
            } else {
                -travelPx * (1f - progress)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                FolderPage(
                    page = page,
                    snapshot = fromSnapshot,
                    folderTree = folderTree,
                    scanningFolderIds = scanningFolderIds,
                    queuedFolderIds = queuedFolderIds,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    navigator = navigator,
                    selectionViewModel = selectionViewModel,
                    onFolderClick = onFolderClick,
                    onDeleteFolder = onDeleteFolder,
                    onRefreshFolder = onRefreshFolder,
                    onIgnoredChange = onIgnoredChange,
                    onShowSongMenu = onShowSongMenu,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = fromOffset
                            alpha = 1f - progress
                        }
                )

                FolderPage(
                    page = page,
                    snapshot = toSnapshot,
                    folderTree = folderTree,
                    scanningFolderIds = scanningFolderIds,
                    queuedFolderIds = queuedFolderIds,
                    isSelectionMode = isSelectionMode,
                    selectedSongUris = selectedSongUris,
                    swipeSelectionLabel = swipeSelectionLabel,
                    swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    navigator = navigator,
                    selectionViewModel = selectionViewModel,
                    onFolderClick = onFolderClick,
                    onDeleteFolder = onDeleteFolder,
                    onRefreshFolder = onRefreshFolder,
                    onIgnoredChange = onIgnoredChange,
                    onShowSongMenu = onShowSongMenu,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = toOffset
                            alpha = progress
                        }
                )
            }
        }
    }
}

@Composable
private fun FolderPage(
    page: Int,
    snapshot: FolderContentSnapshot,
    folderTree: FolderTree,
    scanningFolderIds: Set<Long>,
    queuedFolderIds: Set<Long>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    navigator: DestinationsNavigator,
    selectionViewModel: SongSelectionViewModel,
    onFolderClick: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRefreshFolder: (FolderEntity) -> Unit,
    onIgnoredChange: (FolderEntity, Boolean) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    when (page) {
        0 -> {
            FolderChildrenPage(
                folders = snapshot.childFolders,
                folderTree = folderTree,
                scanningFolderIds = scanningFolderIds,
                queuedFolderIds = queuedFolderIds,
                isSelectionMode = isSelectionMode,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                onFolderClick = onFolderClick,
                onDeleteFolder = onDeleteFolder,
                onRefreshFolder = onRefreshFolder,
                onIgnoredChange = onIgnoredChange,
                modifier = modifier
            )
        }

        1 -> {
            FolderCurrentSongsPage(
                songs = snapshot.songs,
                isSelectionMode = isSelectionMode,
                selectedSongUris = selectedSongUris,
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                onSongClick = { song ->
                    navigator.navigate(
                        EditMetadataDestination(songFileUri = song.uri)
                    )
                },
                onToggleSelection = { song ->
                    selectionViewModel.toggleSelection(song.uri)
                },
                onSwipeSelection = { song ->
                    selectionViewModel.swipeSelect(song, snapshot.songs)
                },
                onShowSongMenu = onShowSongMenu,
                modifier = modifier
            )
        }
    }
}

private fun buildFolderContentSnapshot(
    folderId: Long,
    folders: List<FolderEntity>,
    folderTree: FolderTree,
    currentFolderId: Long,
    currentFolderSongs: List<SongEntity>,
    useCurrentFolderSongs: Boolean
): FolderContentSnapshot {
    val folder = folders.find { it.id == folderId }
    val node = folder?.let { folderTree.nodesById[it.id] }
    val songs = when {
        folderId == ROOT_FOLDER_ID -> emptyList()
        useCurrentFolderSongs && folderId == currentFolderId -> currentFolderSongs
        else -> emptyList()
    }

    return FolderContentSnapshot(
        folderId = folderId,
        childFolders = node?.childFolders ?: folderTree.rootFolders,
        songs = songs
    )
}

@Composable
private fun FolderChildrenPage(
    folders: List<FolderEntity>,
    folderTree: FolderTree,
    scanningFolderIds: Set<Long>,
    queuedFolderIds: Set<Long>,
    isSelectionMode: Boolean,
    topAppBarScrollBehavior: ScrollBehavior,
    onFolderClick: (FolderEntity) -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRefreshFolder: (FolderEntity) -> Unit,
    onIgnoredChange: (FolderEntity, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        items(
            items = folders,
            key = { folder -> "folder-${folder.id}" }
        ) { folder ->
            FolderListItem(
                folder = folder,
                node = folderTree.nodesById.getValue(folder.id),
                isScanning = folder.id in scanningFolderIds,
                isQueued = folder.id in queuedFolderIds,
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (!isSelectionMode) {
                        onFolderClick(folder)
                    }
                },
                canRemove = folder.addedBySaf,
                onDelete = {
                    onDeleteFolder(folder)
                },
                onRefresh = {
                    onRefreshFolder(folder)
                },
                onIgnoredChange = { ignored ->
                    onIgnoredChange(folder, ignored)
                }
            )
        }
    }
}

@Composable
private fun FolderCurrentSongsPage(
    songs: List<SongEntity>,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeSelectionLabel: String,
    swipeSelectionSecondaryLabel: String?,
    topAppBarScrollBehavior: ScrollBehavior,
    onSongClick: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity) -> Unit,
    onShowSongMenu: (SongEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
            .fillMaxHeight(),
        state = listState,
        contentPadding = PaddingValues(bottom = 12.dp),
        overscrollEffect = null
    ) {
        items(
            items = songs,
            key = { song ->
                song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}"
            }
        ) { song ->
            SongListItem(
                song = song,
                modifier = Modifier.animateItem(),
                isSelectionMode = isSelectionMode,
                isSelected = selectedSongUris.contains(song.uri),
                swipeSelectionLabel = swipeSelectionLabel,
                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                onClick = {
                    onSongClick(song)
                },
                onToggleSelection = {
                    onToggleSelection(song)
                },
                onSwipeSelection = {
                    onSwipeSelection(song)
                },
                trailingContent = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        SongListItemActions(
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedSongUris.contains(song.uri),
                            onToggleSelection = {
                                onToggleSelection(song)
                            },
                            onShowMenu = {
                                onShowSongMenu(song)
                            }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun FolderPathHeader(
    breadcrumbs: List<FolderBreadcrumb>,
    folderCount: Int,
    songCount: Int,
    onBreadcrumbClick: (Long) -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                breadcrumbs.forEachIndexed { index, breadcrumb ->
                    if (index > 0) {
                        Text(
                            text = ">",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.body2.fontSize,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    val isCurrent = index == breadcrumbs.lastIndex
                    Text(
                        text = breadcrumb.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isCurrent) {
                            MiuixTheme.colorScheme.onBackground
                        } else {
                            MiuixTheme.colorScheme.primary
                        },
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier
                            .widthIn(max = 180.dp)
                            .then(
                                if (isCurrent) {
                                    Modifier
                                } else {
                                    Modifier.clickable {
                                        onBreadcrumbClick(breadcrumb.folderId)
                                    }
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.folder_browser_count_format, folderCount, songCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize
            )
        }
    }
}

@Composable
private fun FolderListItem(
    folder: FolderEntity,
    node: FolderTreeNode,
    isScanning: Boolean,
    isQueued: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    canRemove: Boolean,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onIgnoredChange: (Boolean) -> Unit
) {
    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
    val isBusy = isScanning || isQueued || isSelectionMode
    val statusText = when {
        isScanning -> {
            stringResource(R.string.folder_scanning)
        }

        isQueued -> {
            stringResource(R.string.folder_scan_queued)
        }

        folder.isIgnored -> {
            stringResource(
                R.string.folder_hidden_child_song_count_format,
                node.childFolders.size,
                node.subtreeSongCount
            )
        }

        else -> {
            stringResource(
                R.string.folder_child_song_count_format,
                node.childFolders.size,
                node.subtreeSongCount
            )
        }
    }

    val actionItems = buildList {
        add(
            DropdownItem(
                text = if (folder.isIgnored) {
                    stringResource(R.string.folder_action_show)
                } else {
                    stringResource(R.string.folder_action_hide)
                },
                onClick = {
                    onIgnoredChange(!folder.isIgnored)
                }
            )
        )

        add(
            DropdownItem(
                text = stringResource(R.string.action_refresh_folder),
                onClick = {
                    if (!isBusy) {
                        onRefresh()
                    }
                }
            )
        )

        if (canRemove) {
            add(
                DropdownItem(
                    text = stringResource(R.string.folder_action_remove),
                    onClick = {
                        if (!isBusy) {
                            onDelete()
                        }
                    }
                )
            )
        }
    }

    val actionEntry = DropdownEntry(items = actionItems)

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
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

private data class FolderTree(
    val nodesById: Map<Long, FolderTreeNode>,
    val rootFolders: List<FolderEntity>
)

private data class FolderTreeNode(
    val folder: FolderEntity,
    val parentId: Long?,
    val childFolders: List<FolderEntity>,
    val directSongCount: Int,
    val subtreeSongCount: Int
)

private fun buildFolderBreadcrumbs(
    currentFolder: FolderEntity?,
    currentNode: FolderTreeNode?,
    folderTree: FolderTree,
    rootLabel: String
): List<FolderBreadcrumb> {
    if (currentFolder == null || currentNode == null) {
        return listOf(FolderBreadcrumb(ROOT_FOLDER_ID, rootLabel))
    }

    val ancestors = mutableListOf<FolderTreeNode>()
    var node: FolderTreeNode? = currentNode
    while (node != null) {
        ancestors += node
        node = node.parentId?.let { parentId -> folderTree.nodesById[parentId] }
    }

    return buildList {
        add(FolderBreadcrumb(ROOT_FOLDER_ID, rootLabel))
        ancestors.asReversed().forEach { ancestor ->
            add(
                FolderBreadcrumb(
                    folderId = ancestor.folder.id,
                    label = ancestor.folder.path.substringAfterLast("/")
                        .ifBlank { ancestor.folder.path }
                )
            )
        }
    }
}

private fun buildFolderTree(
    folders: List<FolderEntity>
): FolderTree {
    val normalizedPaths = folders.associate { folder ->
        folder.id to folder.path.normalizeFolderPath()
    }

    val directSongCounts = folders.associate { folder ->
        folder.id to folder.songCount
    }

    val parentIds = folders.associate { folder ->
        val path = normalizedPaths.getValue(folder.id)
        val parent = folders
            .filter { candidate ->
                val candidatePath = normalizedPaths.getValue(candidate.id)
                candidate.id != folder.id &&
                        candidatePath.isNotBlank() &&
                        path.startsWith("$candidatePath/")
            }
            .maxByOrNull { candidate ->
                normalizedPaths.getValue(candidate.id).length
            }

        folder.id to parent?.id
    }

    val childrenByParentId = folders
        .groupBy { folder -> parentIds.getValue(folder.id) }
        .mapValues { (_, children) ->
            children.sortedBy { it.path.normalizeFolderPath() }
        }

    val subtreeSongCounts = mutableMapOf<Long, Int>()

    fun subtreeSongCount(folderId: Long): Int {
        return subtreeSongCounts.getOrPut(folderId) {
            directSongCounts[folderId].orZero() +
                    childrenByParentId[folderId].orEmpty().sumOf { child ->
                        subtreeSongCount(child.id)
                    }
        }
    }

    val nodesById = folders.associate { folder ->
        folder.id to FolderTreeNode(
            folder = folder,
            parentId = parentIds.getValue(folder.id),
            childFolders = childrenByParentId[folder.id].orEmpty(),
            directSongCount = directSongCounts[folder.id].orZero(),
            subtreeSongCount = subtreeSongCount(folder.id)
        )
    }

    return FolderTree(
        nodesById = nodesById,
        rootFolders = childrenByParentId[null].orEmpty()
    )
}

private fun Int?.orZero(): Int = this ?: 0

private fun String.normalizeFolderPath(): String {
    return replace('\\', '/')
        .trim()
        .trimEnd('/')
}
