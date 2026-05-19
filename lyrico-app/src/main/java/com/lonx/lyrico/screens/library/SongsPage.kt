package com.lonx.lyrico.screens.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.screens.SECTIONS_ASC
import com.lonx.lyrico.screens.SECTIONS_DESC
import com.lonx.lyrico.screens.TopBarState
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.bar.findScrollIndex
import com.lonx.lyrico.ui.components.fab.ScrollToTopButton
import com.lonx.lyrico.ui.components.selection.dragSelection
import com.lonx.lyrico.ui.components.song.LibraryScanProgressText
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListEmptyState
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.compose.viewmodel.koinActivityViewModel
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun SongsPage(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel: SongListViewModel = koinActivityViewModel()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val sortInfo by viewModel.sortInfo.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongUris by viewModel.selectedSongUris.collectAsState()
    val hasFolders by viewModel.hasFolders.collectAsStateWithLifecycle()
    val showScrollTopButton by viewModel.showScrollTopButton.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.clearSearch()
    }

    val showFab by remember {
        derivedStateOf {
            showScrollTopButton && listState.firstVisibleItemIndex > 0
        }
    }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
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

            val path = UriUtils.getFileAbsolutePath(context, it) ?: it.toString()
            viewModel.addSafFolderAndRefresh(
                path = path,
                treeUri = it.toString()
            )
        }
    }
    val sectionIndexMap = remember(songs, sortInfo) {
        val map = mutableMapOf<String, Int>()
        if (sortInfo.sortBy.supportsIndex) {
            songs.forEachIndexed { index, song ->
                val key =
                    if (sortInfo.sortBy == SortBy.ARTISTS) song.artistGroupKey else song.titleGroupKey
                if (!map.containsKey(key)) {
                    map[key] = index
                }
            }
        }
        map
    }
    val sections = remember(sortInfo.order) {
        if (sortInfo.order == SortOrder.ASC) {
            SECTIONS_ASC
        } else {
            SECTIONS_DESC
        }
    }
    val enableIndex = sections.isNotEmpty() && sortInfo.sortBy.supportsIndex
    val topPadding by animateDpAsState(
        targetValue = TopAppBarDefaults.SmallTopAppBarCenterHeight + 12.dp,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "backToTopPadding"
    )
    BackHandler(enabled = isSelectionMode || isFabMenuExpanded) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else if (isSelectionMode) {
            viewModel.exitSelectionMode()
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_success)
    )
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val topBarState = when {
                    isSelectionMode -> TopBarState.Selection
                    else -> TopBarState.Default
                }

                AnimatedContent(
                    targetState = topBarState,
                    label = "TopBarAnimation",
                    transitionSpec = {
                        // 定义过渡动画：淡入淡出 + 轻微的垂直滑动 + 尺寸自适应平滑过渡
                        val animationDuration = 300
                        val enter = fadeIn(tween(animationDuration)) +
                                slideInVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetY = { -it / 3 } // 从上方 1/3 处滑入
                                )
                        val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(
                                        animationDuration,
                                        easing = FastOutSlowInEasing
                                    ),
                                    targetOffsetY = { -it / 3 } // 向上方 1/3 处滑出
                                )

                        (enter togetherWith exit).using(
                            // SizeTransform 保证了如果搜索栏和默认导航栏高度不同时，高度变化也是平滑的
                            SizeTransform(clip = false)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { state ->
                    when (state) {
                        TopBarState.Selection -> {
                            SongSelectionTopAppBar(
                                songs = songs,
                                selectedSongUris = selectedSongUris,
                                scrollBehavior = topAppBarScrollBehavior,
                                onSelectAll = viewModel::selectAll,
                                onDeselectAll = viewModel::deselectAll,
                                onClose = viewModel::exitSelectionMode
                            )
                        }

                        TopBarState.Default -> {
                            SmallTopAppBar(
                                title = stringResource(R.string.song_list_title, songs.size),
                                scrollBehavior = topAppBarScrollBehavior,
                                navigationIcon = {
                                    IconButton(
                                        onClick = { navigator.navigate(SettingsDestination()) }
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Settings,
                                            contentDescription = null
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        navigator.navigate(LocalSearchDestination)
                                    }) {
                                        Icon(
                                            imageVector = MiuixIcons.Search,
                                            contentDescription = stringResource(R.string.cd_search)
                                        )
                                    }
                                    val sortTypes = SortBy.entries.toList()
                                    val sortEntries = DropdownEntry(
                                        items = sortTypes.mapIndexed { _, sortBy ->
                                            val isSelected = sortInfo.sortBy == sortBy
                                            DropdownItem(
                                                text = stringResource(sortBy.labelRes),
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
                                                        if (sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                                    } else {
                                                        SortOrder.ASC
                                                    }
                                                    viewModel.onSortChange(
                                                        SortInfo(
                                                            sortBy,
                                                            newOrder
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    val buttonEntry = DropdownEntry(
                                        items = listOf(
                                            DropdownItem(
                                                text = stringResource(R.string.show_scroll_top_button),
                                                selected = showScrollTopButton,
                                                onClick = {
                                                    viewModel.setScrollToTopButtonEnabled(
                                                        !showScrollTopButton
                                                    )
                                                }
                                            )
                                        )
                                    )
                                    OverlayIconDropdownMenu(
                                        entries = listOf(sortEntries, buttonEntry),
                                    ) {
                                        Icon(
                                            imageVector = MiuixIcons.Sort,
                                            contentDescription = stringResource(R.string.cd_sort)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier.padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection)
                ).fillMaxSize()
            ) {
                PullToRefresh(
                    isRefreshing = scanState.isScanning,
                    onRefresh = { viewModel.refreshSongs() },
                    modifier = Modifier.fillMaxSize(),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    refreshTexts = refreshTexts
                ) {
                    LazyColumnScrollbar(
                        state = listState,
                        settings = ScrollbarSettings.Default.copy(
                            enabled = !enableIndex && songs.isNotEmpty(),
                            alwaysShowScrollbar = !enableIndex,
                            selectionMode = ScrollbarSelectionMode.Full,
                            thumbUnselectedColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            thumbSelectedColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                .fillMaxHeight()
                                .dragSelection(
                                    listState = listState,
                                    itemCount = songs.size,
                                    isSelectionMode = isSelectionMode,
                                    onDragSelectionStart = { index ->
                                        viewModel.startDragSelection(index, songs)
                                    },
                                    onDragSelectionChange = { startIndex, endIndex ->
                                        viewModel.updateDragSelection(
                                            startIndex,
                                            endIndex,
                                            songs
                                        )
                                    },
                                    onDragSelectionEnd = {
                                        viewModel.endDragSelection()
                                    }
                                ),
                            state = listState,
                            overscrollEffect = null,
                            contentPadding = PaddingValues()
                        ) {
                            if (songs.isNotEmpty()) {
                                items(
                                    items = songs,
                                    key = { song ->
                                        song.uri.takeIf { it.isNotBlank() && it != "0" }
                                            ?: "song-${song.id}"
                                    }
                                ) { song ->
                                    SongListItem(
                                        song = song,
                                        modifier = Modifier.animateItem(),
                                        isSelectionMode = isSelectionMode,
                                        isSelected = selectedSongUris.contains(song.uri),
                                        onClick = {
                                            navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                                        },
                                        onToggleSelection = {
                                            viewModel.toggleSelection(song.uri)
                                        },
                                        trailingContent = {
                                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                                SongListItemActions(
                                                    isSelectionMode = isSelectionMode,
                                                    isSelected = selectedSongUris.contains(song.uri),
                                                    onToggleSelection = {
                                                        viewModel.toggleSelection(song.uri)
                                                    },
                                                    onShowMenu = {
                                                        showMenuSheet = true
                                                        selectedSong = song
                                                    }
                                                )
                                            }
                                        }
                                    )
                                }
                            } else {
                                item {
                                    val scanProgress = scanState.progress
                                    when {
                                        scanProgress != null -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(420.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                LibraryScanProgressText(
                                                    progress = scanProgress
                                                )
                                            }
                                        }

                                        !hasFolders -> {
                                            SongListEmptyState(
                                                onAddFolder = { folderPickerLauncher.launch(null) }
                                            )
                                        }

                                        else -> {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(240.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (enableIndex && songs.isNotEmpty()) {
                    AlphabetSideBar(
                        sections = sections,
                        onSectionSelected = { section ->
                            val index = findScrollIndex(
                                section = section,
                                sectionIndexMap = sectionIndexMap,
                                order = sortInfo.order
                            )
                            scope.launch { listState.scrollToItem(index) }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
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
                        viewModel.play(context, song)
                    },
                    onDelete = { song ->
                        viewModel.delete(song)
                    },
                    onRename = { song, newFileName ->
                        viewModel.renameSong(song, newFileName)
                    }
                )
            }
        }
        ScrollToTopButton(
            visible = showFab,
            topPadding = topPadding,
            text = stringResource(R.string.action_scroll_to_top),
            icon = painterResource(R.drawable.ic_arrow_up_24dp),
            onClick = {
                scope.launch {
                    listState.animateScrollToItem(0)
                }
            }
        )

        SongBatchSelectionActions(
            navigator = navigator,
            songs = songs,
            isSelectionMode = isSelectionMode,
            expanded = isFabMenuExpanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { isFabMenuExpanded = it },
            onSetSelectionUris = viewModel::setSelectionUris,
            onBatchDelete = viewModel::batchDelete,
            onBatchShare = viewModel::batchShare
        )
    }
}
