package com.lonx.lyrico.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
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
    val folderTree = remember(folders, uiState.songs) { buildFolderTree(folders, uiState.songs) }
    val context = androidx.compose.ui.platform.LocalContext.current

    var currentFolderId by remember { mutableLongStateOf(ROOT_FOLDER_ID) }
    var selectedFolderId by remember { mutableLongStateOf(ROOT_FOLDER_ID) }
    val selectedFolder = remember(selectedFolderId, folders) {
        folders.find { it.id == selectedFolderId }
    }
    val currentFolder = remember(currentFolderId, folders) {
        folders.find { it.id == currentFolderId }
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
    val currentSongs = remember(currentFolder, uiState.songs) {
        currentFolder?.let { folder ->
            uiState.songs.filter { song -> song.folderId == folder.id }
        }.orEmpty()
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
    BackHandler(enabled = currentFolder != null) {
        currentFolderId = parentFolder?.id ?: ROOT_FOLDER_ID
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = if (currentFolderId == ROOT_FOLDER_ID) {
                    stringResource(R.string.folder_manager_title)
                } else {
                    currentFolder?.path?.substringAfterLast("/")?.ifBlank { currentFolder.path }
                        ?: stringResource(R.string.folder_manager_title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentFolder != null) {
                                currentFolderId = parentFolder?.id ?: ROOT_FOLDER_ID
                            } else {
                                navigator.navigateUp()
                            }
                        }) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (currentFolderId == ROOT_FOLDER_ID){
                        IconButton(
                            onClick = { folderPickerLauncher.launch(null) }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.AddFolder,
                                contentDescription = null
                            )
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
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
                                    currentFolderId = parentFolder?.id ?: ROOT_FOLDER_ID
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
                        onClick = {
                            currentFolderId = folder.id
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
                    key = { song -> "song-${song.uri.takeIf { it.isNotBlank() && it != "0" } ?: song.id}" }
                ) { song ->
                    SongFileItem(
                        song = song,
                        onClick = {
                            navigator.navigate(EditMetadataDestination(song.uri))
                        }
                    )
                }
            }
        }
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
    onClick: () -> Unit,
    canRemove: Boolean,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onIgnoredChange: (Boolean) -> Unit
) {
    val folderName = folder.path.substringAfterLast("/").ifBlank { folder.path }
    val isBusy = isScanning || isQueued
    val statusText = when {
        isScanning -> stringResource(R.string.folder_scanning)
        isQueued -> stringResource(R.string.folder_scan_queued)
        folder.isIgnored -> stringResource(
            R.string.folder_hidden_child_song_count_format,
            node.childFolders.size,
            node.subtreeSongCount
        )
        else -> stringResource(
            R.string.folder_child_song_count_format,
            node.childFolders.size,
            node.subtreeSongCount
        )
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
                    if (!isBusy){ onRefresh() }
                }
            )
        )
        if (canRemove) {
            add(
                DropdownItem(
                    text = stringResource(R.string.folder_action_remove),
                    onClick = {
                        if (!isBusy){ onDelete() }
                    }
                )
            )
        }
    }
    val actionEntry = DropdownEntry(items = actionItems)

    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

@Composable
private fun SongFileItem(
    song: SongEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicComponent(
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            title = song.title?.takeIf { it.isNotBlank() } ?: song.fileName,
            summary = song.fileName,
            onClick = onClick
        )
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

private fun buildFolderTree(folders: List<FolderEntity>, songs: List<SongEntity>): FolderTree {
    val normalizedPaths = folders.associate { folder ->
        folder.id to folder.path.normalizeFolderPath()
    }
    val directSongCounts = songs.groupingBy { song -> song.folderId }.eachCount()
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
