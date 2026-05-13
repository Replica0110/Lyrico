package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.DropdownItem
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.bar.findScrollIndex
import com.lonx.lyrico.ui.components.base.YesNoDialog
import com.lonx.lyrico.ui.components.batch.BatchLyricsFormatBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchLyricsFormatConfigBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchMatchBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchMatchConfigBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchRGBottomSheet
import com.lonx.lyrico.ui.components.batch.BatchRGConfigBottomSheet
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.fab.ScrollToTopButton
import com.lonx.lyrico.ui.components.search.LocalSearchTypeTabs
import com.lonx.lyrico.ui.components.selection.dragSelection
import com.lonx.lyrico.ui.components.song.LibraryScanProgressText
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListEmptyState
import com.lonx.lyrico.ui.components.song.SongListItem
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.utils.UriUtils
import com.lonx.lyrico.viewmodel.BatchLyricsFormatViewModel
import com.lonx.lyrico.viewmodel.BatchMatchViewModel
import com.lonx.lyrico.viewmodel.BatchReplayGainViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchEditDestination
import com.ramcosta.composedestinations.generated.destinations.BatchRenameDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SECTIONS_ASC = listOf(
    "0"
) + ('A'..'Z').map { it.toString() } + listOf("#")

private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

enum class TopBarState {
    Selection, Search, Default
}

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(start = true, route = "song_list")
fun SongListScreen(
    navigator: DestinationsNavigator
) {
    val songListViewModel: SongListViewModel = koinActivityViewModel()
    val batchMatchViewModel: BatchMatchViewModel = koinViewModel()
    val batchReplayGainViewModel: BatchReplayGainViewModel = koinViewModel()
    val batchLyricsFormatViewModel: BatchLyricsFormatViewModel = koinViewModel()
    val songListUiState by songListViewModel.uiState.collectAsState()
    val scanState by songListViewModel.scanState.collectAsStateWithLifecycle()
    val batchMatchUiState by batchMatchViewModel.uiState.collectAsState()
    val batchReplayGainUiState by batchReplayGainViewModel.uiState.collectAsStateWithLifecycle()
    val batchLyricsFormatUiState by batchLyricsFormatViewModel.uiState.collectAsStateWithLifecycle()
    val sortInfo by songListViewModel.sortInfo.collectAsState()
    val songs by songListViewModel.songs.collectAsState()
    val searchType by songListViewModel.searchType.collectAsState()
    val isSelectionMode by songListViewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongUris by songListViewModel.selectedSongUris.collectAsState()
    val hasFolders by songListViewModel.hasFolders.collectAsStateWithLifecycle()
    val allSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })
    val showScrollTopButton by songListViewModel.showScrollTopButton.collectAsStateWithLifecycle()
    val batchMatchConfig by batchMatchViewModel.batchMatchConfig.collectAsState()
    var sortOrderDropdownExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }

    val showFab by remember {
        derivedStateOf {
            showScrollTopButton && listState.firstVisibleItemIndex > 0
        }
    }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    val hasSelection = selectedSongUris.isNotEmpty()
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
            songListViewModel.addSafFolderAndRefresh(
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
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    val enableIndex = sections.isNotEmpty() && sortInfo.sortBy.supportsIndex
    val topPadding by animateDpAsState(
        targetValue = if (isSearchMode) {
            135.dp
        } else {
            TopAppBarDefaults.SmallTopAppBarCenterHeight + 12.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "backToTopPadding"
    )
    BackHandler(enabled = isSelectionMode || isSearchMode || isFabMenuExpanded) {
        if (isFabMenuExpanded) {
            isFabMenuExpanded = false
        } else if (isSelectionMode) {
            songListViewModel.exitSelectionMode()
        } else if (isSearchMode) {
            isSearchMode = false
            songListViewModel.clearSearch()
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_success)
    )
    Box {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                val topBarState = when {
                    isSelectionMode -> TopBarState.Selection
                    isSearchMode -> TopBarState.Search
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
                            BoxWithConstraints {
                                val compactTopBar = maxWidth < 360.dp

                                SmallTopAppBar(
                                    title = "",
                                    scrollBehavior = topAppBarScrollBehavior,
                                    navigationIcon = {
                                        Text(
                                            text = stringResource(
                                                R.string.selection_mode_selected_count,
                                                selectedSongUris.size
                                            )
                                        )
                                    },
                                    actions = {
                                        if (!compactTopBar) {
                                            TextButton(
                                                onClick = {
                                                    if (allSelected) {
                                                        songListViewModel.deselectAll()
                                                    } else {
                                                        songListViewModel.selectAll(songs)
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        if (allSelected) {
                                                            R.string.action_deselect_all
                                                        } else {
                                                            R.string.action_select_all
                                                        }
                                                    ),
                                                    color = MiuixTheme.colorScheme.primary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        TextButton(
                                            onClick = {
                                                songListViewModel.exitSelectionMode()
                                            }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.action_close),
                                                color = MiuixTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        TopBarState.Search -> {
                            Column(
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(vertical = 8.dp)
                            ) {
                                BoxWithConstraints {
                                    val compactTopBar = maxWidth < 360.dp
                                    SearchBar(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        value = songListUiState.searchQuery,
                                        onValueChange = {
                                            songListViewModel.onSearchQueryChanged(it)
                                        },
                                        placeholder = stringResource(id = R.string.local_search_hint),
                                        actions = if (compactTopBar) null else {
                                            {
                                                TextButton(
                                                    onClick = {
                                                        isSearchMode = false
                                                        songListViewModel.clearSearch()
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.action_close),
                                                        color = MiuixTheme.colorScheme.primary,
                                                        style = MiuixTheme.textStyles.main
                                                    )
                                                }
                                            }
                                        },
                                        onSearch = {
                                            songListViewModel.onSearchQueryChanged(songListUiState.searchQuery)
                                        }
                                    )
                                }
                            }
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
                                    IconButton(onClick = { isSearchMode = true }) {
                                        Icon(
                                            imageVector = MiuixIcons.Search,
                                            contentDescription = stringResource(R.string.cd_search)
                                        )
                                    }
                                    Box {
                                        IconButton(
                                            onClick = { sortOrderDropdownExpanded = true }
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Sort,
                                                contentDescription = stringResource(R.string.cd_sort)
                                            )
                                        }
                                        OverlayListPopup(
                                            show = sortOrderDropdownExpanded,
                                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                            onDismissRequest = { sortOrderDropdownExpanded = false }
                                        ) {
                                            ListPopupColumn {
                                                val sortTypes = SortBy.entries.toList()
                                                sortTypes.forEach { type ->
                                                    val isSelected = sortInfo.sortBy == type
                                                    DropdownItem(
                                                        text = stringResource(type.labelRes),
                                                        optionSize = sortTypes.size + 1,
                                                        index = sortTypes.indexOf(type),
                                                        isSelected = isSelected,
                                                        iconPainter = if (isSelected) {
                                                            if (sortInfo.order == SortOrder.ASC) {
                                                                painterResource(R.drawable.ic_arrow_down_24dp)
                                                            } else {
                                                                painterResource(R.drawable.ic_arrow_up_24dp)
                                                            }
                                                        } else null,
                                                        onSelectedIndexChange = {
                                                            val newOrder = if (isSelected) {
                                                                if (sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                                            } else {
                                                                SortOrder.ASC
                                                            }
                                                            songListViewModel.onSortChange(
                                                                SortInfo(
                                                                    type,
                                                                    newOrder
                                                                )
                                                            )
                                                        }
                                                    )
                                                }
                                                HorizontalDivider()
                                                SwitchPreference(
                                                    title = stringResource(R.string.show_scroll_top_button),
                                                    summary = stringResource(R.string.show_scroll_top_button_hint),
                                                    checked = showScrollTopButton,
                                                    onCheckedChange = {
                                                        songListViewModel.setScrollToTopButtonEnabled(
                                                            it
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            val navigationBarBottomInset =
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            PullToRefresh(
                isRefreshing = scanState.isScanning,
                onRefresh = { songListViewModel.refreshSongs() },
                modifier = Modifier.padding(
                    start = paddingValues.calculateStartPadding(layoutDirection),
                    top = paddingValues.calculateTopPadding(),
                    end = paddingValues.calculateEndPadding(layoutDirection)
                ),
                topAppBarScrollBehavior = topAppBarScrollBehavior,
                refreshTexts = refreshTexts
            ) {
                AnimatedVisibility(
                    visible = isSearchMode,
                    enter = slideInVertically(
                        initialOffsetY = { -it }
                    ) + fadeIn(),

                    exit = slideOutVertically(
                        targetOffsetY = { -it }
                    ) + fadeOut()
                ) {
                    LocalSearchTypeTabs(
                        selectedType = searchType,
                        onTypeSelected = { songListViewModel.onSearchTypeChanged(it) }
                    )
                }
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
                                    songListViewModel.startDragSelection(index, songs)
                                },
                                onDragSelectionChange = { startIndex, endIndex ->
                                    songListViewModel.updateDragSelection(
                                        startIndex,
                                        endIndex,
                                        songs
                                    )
                                },
                                onDragSelectionEnd = {
                                    songListViewModel.endDragSelection()
                                }
                            ),
                        state = listState,
                        overscrollEffect = null,
                        contentPadding = PaddingValues(bottom = navigationBarBottomInset)
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
                                        songListViewModel.toggleSelection(song.uri)
                                    },
                                    trailingContent = {
                                        Box(modifier = Modifier.padding(end = 8.dp)) {
                                            SongListItemActions(
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedSongUris.contains(song.uri),
                                                onToggleSelection = {
                                                    songListViewModel.toggleSelection(song.uri)
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

                                    !hasFolders && songListUiState.searchQuery.isBlank() -> {
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
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
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
                    songListViewModel.play(context, song)
                },
                onDelete = { song ->
                    songListViewModel.delete(song)
                },
                onRename = { song, newFileName ->
                    songListViewModel.renameSong(song, newFileName)
                }
            )
            // 批量删除
            YesNoDialog(
                show = showBatchDeleteDialog,
                onDismissRequest = {
                    showBatchDeleteDialog = false
                },
                summary = stringResource(
                    R.string.dialog_batch_delete_content,
                    selectedSongUris.size
                ),
                onConfirm = {
                    showBatchDeleteDialog = false
                    songListViewModel.batchDelete(songs)
                }
            )
            // 批量匹配配置BottomSheet
            BatchMatchConfigBottomSheet(
                show = batchMatchUiState.showBatchConfigDialog,
                initialConfig = batchMatchConfig,
                onDismissRequest = { config ->
                    batchMatchViewModel.saveBatchMatchConfig(config)
                    batchMatchViewModel.closeBatchMatchConfig()
                },
                onConfirm = { config ->
                    batchMatchViewModel.saveBatchMatchConfig(config)
                    batchMatchViewModel.batchMatch(songs, config)
                }
            )

            // 批量匹配进度
            BatchMatchBottomSheet(
                onDismissRequest = {
                    if (!batchMatchUiState.isRunning) batchMatchViewModel.closeBatchMatchDialog()
                },
                onDismissFinished = {
                    if (!batchMatchUiState.isRunning) batchMatchViewModel.closeBatchMatchDialog()
                },
                enableNestedScroll = false,
                uiState = batchMatchUiState,
                onAbort = {
                    batchMatchViewModel.abortBatchMatch()
                }
            )
            // ReplayGain配置BottomSheet
            BatchRGConfigBottomSheet(
                show = batchReplayGainUiState.showConfigDialog,
                initialConcurrency = batchReplayGainUiState.concurrency,
                onDismissRequest = { concurrency ->
                    batchReplayGainViewModel.setConcurrency(concurrency)
                    batchReplayGainViewModel.closeReplayGainConfig()
                },
                onConfirm = { concurrency ->
                    batchReplayGainViewModel.setConcurrency(concurrency)
                    batchReplayGainViewModel.startBatchScan()
                }
            )

            // 批量歌词格式转换配置BottomSheet
            BatchLyricsFormatConfigBottomSheet(
                show = batchLyricsFormatUiState.showConfigDialog,
                initialConcurrency = batchLyricsFormatUiState.concurrency,
                initialTargetFormat = batchLyricsFormatUiState.targetFormat,
                onDismissRequest = { concurrency, targetFormat ->
                    batchReplayGainViewModel.setConcurrency(concurrency)
                    batchLyricsFormatViewModel.setConcurrency(concurrency)
                    batchLyricsFormatViewModel.setTargetFormat(targetFormat)
                    batchLyricsFormatViewModel.closeConfig()
                },
                onConfirm = { concurrency, targetFormat ->
                    batchReplayGainViewModel.setConcurrency(concurrency)
                    batchLyricsFormatViewModel.setConcurrency(concurrency)
                    batchLyricsFormatViewModel.setTargetFormat(targetFormat)
                    batchLyricsFormatViewModel.startBatchConvert()
                }
            )

            // 批量计算ReplayGain进度
            BatchRGBottomSheet(
                batchReplayGainUiState = batchReplayGainUiState,
                onDismissRequest = {
                    batchReplayGainViewModel.closeProgressDialog()
                },
                onAbort = {
                    batchReplayGainViewModel.abortBatchScan()
                },
                onDismissFinished = {
                    if (batchReplayGainUiState.isSuccess) {
                        batchReplayGainViewModel.closeProgressDialog()
                    }
                },
            )

            // 批量歌词格式转换进度
            BatchLyricsFormatBottomSheet(
                batchLyricsFormatUiState = batchLyricsFormatUiState,
                onDismissRequest = {
                    if (!batchLyricsFormatUiState.isRunning) {
                        batchLyricsFormatViewModel.closeProgressDialog()
                    }
                },
                onDismissFinished = {
                    if (batchLyricsFormatUiState.isSuccess) {
                        batchLyricsFormatViewModel.clearProgressDialog()
                    }
                },
                onAbort = {
                    batchLyricsFormatViewModel.abortBatchConvert()
                }
            )
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

        ExpandableFabMenu(
            visible = isSelectionMode,
            expanded = isFabMenuExpanded,
            icon = MiuixIcons.Add,
            enabled = hasSelection,
            onExpandedChange = { isFabMenuExpanded = it }
        ) {
            FabMenuItem(
                label = stringResource(R.string.action_batch_replay_gain),
                icon = MiuixIcons.Edit,
                onClick = {
                    isFabMenuExpanded = false
                    batchReplayGainViewModel.setSelectionUris(
                        songListViewModel.selectedSongUris.value.toList()
                    )
                    batchReplayGainViewModel.openReplayGainConfig()
                }
            )

            FabMenuItem(
                label = stringResource(R.string.action_batch_convert_lyrics_format),
                icon = MiuixIcons.Edit,
                onClick = {
                    isFabMenuExpanded = false
                    batchLyricsFormatViewModel.setSelectionUris(
                        songListViewModel.selectedSongUris.value.toList()
                    )
                    batchLyricsFormatViewModel.openConfig(batchReplayGainUiState.concurrency)
                }
            )

            FabMenuItem(
                label = stringResource(R.string.action_batch_rename),
                icon = MiuixIcons.Rename,
                onClick = {
                    isFabMenuExpanded = false
                    if (songListViewModel.setSelectionUris()) {
                        navigator.navigate(BatchRenameDestination)
                    }
                }
            )

            FabMenuItem(
                label = stringResource(R.string.batch_edit_title),
                icon = MiuixIcons.Edit,
                onClick = {
                    isFabMenuExpanded = false
                    if (songListViewModel.setSelectionUris()) {
                        navigator.navigate(BatchEditDestination())
                    }
                }
            )

            FabMenuItem(
                label = stringResource(R.string.action_batch_match),
                icon = MiuixIcons.Edit,
                onClick = {
                    isFabMenuExpanded = false
                    songListViewModel.setSelectionUris()
                    batchMatchViewModel.openBatchMatchConfig()
                }
            )

            FabMenuItem(
                label = stringResource(R.string.action_delete),
                icon = MiuixIcons.Delete,
                onClick = {
                    isFabMenuExpanded = false
                    showBatchDeleteDialog = true
                }
            )

            FabMenuItem(
                label = stringResource(R.string.action_share),
                icon = MiuixIcons.Share,
                onClick = {
                    isFabMenuExpanded = false
                    songListViewModel.batchShare(context, songs)
                }
            )
        }
    }
}
