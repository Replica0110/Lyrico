package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsState()

    val folders = uiState.folders
    val folderTree = remember(folders, uiState.songs) {
        buildFolderTree(folders, uiState.songs)
    }
    val context = LocalContext.current

    var currentFolderId by remember { mutableLongStateOf(ROOT_FOLDER_ID) }
    var selectedFolderId by remember { mutableLongStateOf(ROOT_FOLDER_ID) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }

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
    val currentNode = remember(currentFolder, folderTree) {
        currentFolder?.let { folderTree.nodesById[it.id] }
    }
    val parentFolder = remember(currentNode, folderTree) {
        currentNode?.parentId?.let { folderTree.nodesById[it]?.folder }
    }
    val currentChildFolders = remember(currentNode, folderTree) {
        currentNode?.childFolders ?: folderTree.rootFolders
    }
    val currentSongs = if (currentFolder != null) {
        currentFolderSongs
    } else {
        emptyList()
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

    BackHandler(enabled = isSelectionMode || currentFolder != null) {
        when {
            isFabMenuExpanded -> {
                isFabMenuExpanded = false
            }

            isSelectionMode -> {
                selectionViewModel.exitSelectionMode()
            }

            currentFolder != null -> {
                currentFolderId = parentFolder?.id ?: ROOT_FOLDER_ID
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
                                            currentFolderId =
                                                parentFolder?.id ?: ROOT_FOLDER_ID
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

                                if (currentFolder != null && currentSongs.isNotEmpty()) {
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
                                        currentFolderId =
                                            parentFolder?.id ?: ROOT_FOLDER_ID
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
                    currentPath = currentFolder?.path
                        ?: stringResource(R.string.folder_root_path),
                    folderCount = currentChildFolders.size,
                    songCount = currentSongs.size
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
                    contentPadding = PaddingValues(bottom = 12.dp),
                    overscrollEffect = null,
                ) {
                    items(
                        items = currentChildFolders,
                        key = { folder -> "folder-${folder.id}" }
                    ) { folder ->
                        FolderListItem(
                            folder = folder,
                            node = folderTree.nodesById.getValue(folder.id),
                            isScanning = folder.id in uiState.scanningFolderIds,
                            isQueued = folder.id in uiState.queuedFolderIds,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (!isSelectionMode) {
                                    currentFolderId = folder.id
                                }
                            },
                            canRemove = folder.addedBySaf,
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

                    items(
                        items = currentSongs,
                        key = { song ->
                            song.uri.takeIf { it.isNotBlank() && it != "0" }
                                ?: "song-${song.id}"
                        }
                    ) { song ->
                        SongListItem(
                            song = song,
                            isSelectionMode = isSelectionMode,
                            modifier = Modifier.animateItem(),
                            isSelected = selectedSongUris.contains(song.uri),
                            onClick = {
                                navigator.navigate(
                                    EditMetadataDestination(songFileUri = song.uri)
                                )
                            },
                            onToggleSelection = {
                                selectionViewModel.toggleSelection(song.uri)
                            },
                            trailingContent = {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    SongListItemActions(
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedSongUris.contains(song.uri),
                                        onToggleSelection = {
                                            selectionViewModel.toggleSelection(song.uri)
                                        },
                                        onShowMenu = {
                                            selectedSong = song
                                            showMenuSheet = true
                                        }
                                    )
                                }
                            }
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
            isSelectionMode = isSelectionMode,
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

@Composable
private fun FolderPathHeader(
    currentPath: String,
    folderCount: Int,
    songCount: Int
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicComponent(
            title = currentPath,
            summary = stringResource(R.string.folder_browser_count_format, folderCount, songCount)
        )
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

private fun buildFolderTree(
    folders: List<FolderEntity>,
    songs: List<SongEntity>
): FolderTree {
    val normalizedPaths = folders.associate { folder ->
        folder.id to folder.path.normalizeFolderPath()
    }

    val directSongCounts = songs.groupingBy { song ->
        song.folderId
    }.eachCount()

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