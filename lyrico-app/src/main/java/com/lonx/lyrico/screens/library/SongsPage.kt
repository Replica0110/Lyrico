package com.lonx.lyrico.screens.library

import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.UiEngine

import android.content.Intent
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.screens.SECTIONS_ASC
import com.lonx.lyrico.screens.SECTIONS_DESC
import com.lonx.lyrico.screens.TopBarState
import com.lonx.lyrico.ui.components.bar.AlphabetSideBar
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.bar.rememberAlphabetSideBarScrollController
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.library.LocalLibraryBottomContentPadding
import com.lonx.lyrico.ui.components.library.libraryOverlayInsets
import com.lonx.lyrico.ui.components.library.libraryScrollbarOverlay
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffoldPageWindowInsets
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.song.LibraryScanProgressText
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItemContent
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.utils.UriUtils
import com.moriafly.salt.ui.SaltTheme
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import my.nanihadesuka.compose.InternalLazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextButton
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
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()

    val sortInfo by viewModel.sortInfo.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsState()
    val allSongsSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsState(initial = null)
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
    val hasFolders by viewModel.hasFolders.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val alphabetScrollController = rememberAlphabetSideBarScrollController(listState)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.clearSearch()
    }

    val context = LocalContext.current
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
                val key = song.sectionKey(sortInfo.sortBy)
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

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val isSaltEngine = LocalUiEngine.current == UiEngine.SALT
    val topBarBackdrop = rememberBarBlurBackdrop(enabled = LocalUiEngine.current == UiEngine.MIUIX && com.lonx.lyrico.ui.components.blur.rememberBarBlurEnabled())
    val refreshTexts = listOf(
        stringResource(R.string.pull_to_refresh),
        stringResource(R.string.release_to_refresh),
        stringResource(R.string.refreshing),
        stringResource(R.string.refresh_success)
    )
    val pageContent: @Composable (PaddingValues) -> Unit = { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Salt 顶栏用 BasicScreen 自带的 backdrop，这里不再叠第二层模糊
                .then(if (isSaltEngine) Modifier else Modifier.blurSource(topBarBackdrop))
        ) {
            if (songs.isEmpty()) {
                val scanProgress = scanState.progress
                Box(
                    modifier = Modifier
                        .padding(scaffoldTopHorizontalPadding(paddingValues))
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        scanProgress != null -> {
                            LibraryScanProgressText(progress = scanProgress)
                        }

                        !hasFolders -> {
                            IllustratedEmptyState(
                                kind = EmptyStateKind.Songs,
                                title = stringResource(R.string.song_list_empty_title),
                                icon = LucideIcons.FolderPlus,
                                actionText = stringResource(R.string.action_add_folder),
                                onAction = { folderPickerLauncher.launch(null) },
                            )
                        }

                        else -> {
                            IllustratedEmptyState(
                                kind = EmptyStateKind.Songs,
                                title = stringResource(R.string.empty_songs_title),
                                icon = LucideIcons.RefreshCw,
                                actionText = stringResource(R.string.refresh),
                                onAction = { viewModel.refreshSongs() },
                            )
                        }
                    }
                }
            } else {
                LibraryPullToRefresh(
                    isRefreshing = scanState.isScanning,
                    onRefresh = { viewModel.refreshSongs() },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                    refreshTexts = refreshTexts
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .libraryScrollFeedback(topAppBarScrollBehavior)
                            .fillMaxHeight(),
                        state = listState,
                        overscrollEffect = rememberLibraryOverscrollEffect(),
                        contentPadding = scaffoldContentPadding(
                            paddingValues = paddingValues,
                            bottomExtra = LocalLibraryBottomContentPadding.current,
                        ),
                    ) {
                        items(
                            items = songs,
                            key = { song ->
                                song.uri.takeIf { it.isNotBlank() && it != "0" }
                                    ?: "song-${song.id}"
                            }
                        ) { song ->
                            SelectableLibraryItem(
                                itemKey = song.uri,
                                modifier = Modifier.animateItem(),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedSongUris.contains(song.uri),
                                swipeSelectionLabel = swipeSelectionLabel,
                                swipeSelectionSecondaryLabel = swipeSelectionSecondaryLabel,
                                onClick = {
                                    navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                                },
                                onToggleSelection = {
                                    selectionViewModel.toggleSelection(song.uri)
                                },
                                onSwipeSelection = {
                                    selectionViewModel.swipeSelect(song, songs)
                                }
                            ) {
                                SongListItemContent(
                                    song = song,
                                    trailingContent = {
                                        Box(modifier = Modifier.padding(end = 8.dp)) {
                                            SongListItemActions(
                                                isSelectionMode = isSelectionMode,
                                                isSelected = selectedSongUris.contains(song.uri),
                                                onToggleSelection = {
                                                    selectionViewModel.toggleSelection(song.uri)
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
                        }
                    }
                }
            }
            if (!enableIndex && songs.isNotEmpty()) {
                InternalLazyColumnScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .libraryScrollbarOverlay(paddingValues = paddingValues),
                    settings = ScrollbarSettings.Default.copy(
                        alwaysShowScrollbar = true,
                        selectionMode = ScrollbarSelectionMode.Full,
                        thumbUnselectedColor = if (isSaltEngine) {
                            SaltTheme.colors.subText
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        },
                        thumbSelectedColor = if (isSaltEngine) {
                            SaltTheme.colors.subText
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantActions
                        },
                    ),
                )
            }
            if (enableIndex && songs.isNotEmpty()) {
                AlphabetSideBar(
                    sections = sections,
                    sectionIndexMap = sectionIndexMap,
                    order = sortInfo.order,
                    scrollController = alphabetScrollController,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .libraryOverlayInsets(
                            paddingValues = paddingValues,
                            extraTop = 16.dp,
                            extraBottom = 16.dp,
                        )
                        .fillMaxHeight()
                )
            }


        }

    }
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltLibraryPage(
            selection = if (isSelectionMode) LibrarySelectionState(
            allSelected = allSongsSelected,
            title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
            onClose = selectionViewModel::exitSelectionMode,
            onToggleAll = { if (allSongsSelected) selectionViewModel.deselectAll() else selectionViewModel.selectAll(songs) },
            ) else null,
            title = stringResource(R.string.song_list_title, songs.size),
            onSettings = { navigator.navigate(SettingsDestination()) },
            onSearch = { navigator.navigate(LocalSearchDestination) },
            menu = SortBy.entries.map { sortBy ->
                val selected = sortInfo.sortBy == sortBy
                LibraryMenuAction(
                    title = stringResource(sortBy.labelRes),
                    selected = selected,
                    summary = if (selected) stringResource(
                        if (sortInfo.order == SortOrder.ASC) R.string.sort_ascending else R.string.sort_descending
                    ) else null,
                ) {
                    viewModel.onSortChange(SortInfo(sortBy, if (selected && sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC))
                }
            }, content = pageContent,
        )
    } else Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = scaffoldPageWindowInsets(),
        topBar = {
            BlurredTopBar(backdrop = topBarBackdrop) {
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
                                color = Color.Transparent,
                                applyInsets = false,
                                onSelectAll = selectionViewModel::selectAll,
                                onDeselectAll = selectionViewModel::deselectAll,
                                onClose = selectionViewModel::exitSelectionMode
                            )
                        }

                        TopBarState.Default -> {
                            SmallTopAppBar(
                                title = stringResource(R.string.song_list_title, songs.size),
                                color = Color.Transparent,
                                modifier = Modifier,
                                scrollBehavior = topAppBarScrollBehavior,
                                defaultWindowInsetsPadding = false,
                                navigationIcon = {
                                    IconButton(
                                        onClick = { navigator.navigate(SettingsDestination()) }
                                    ) {
                                        Icon(
                                            imageVector = LucideIcons.Settings,
                                            contentDescription = null
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        navigator.navigate(LocalSearchDestination)
                                    }) {
                                        Icon(
                                            imageVector = LucideIcons.Search,
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
                                    OverlayIconDropdownMenu(
                                        entries = listOf(sortEntries),
                                    ) {
                                        Icon(
                                            imageVector = LucideIcons.Sort,
                                            contentDescription = stringResource(R.string.cd_sort)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues -> pageContent(paddingValues) }
            SongActionSheets(
                selectedSong = selectedSong,
                showMenuSheet = showMenuSheet,
                showDetailSheet = showDetailSheet,
                showDeleteDialog = showDeleteDialog,
                showRenameDialog = showRenameDialog,
                onDismissMenu = { showMenuSheet = false },
                onDismissMenuFinished = { if (!showMenuSheet && !showDetailSheet && !showDeleteDialog && !showRenameDialog) selectedSong = null },
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

private fun SongEntity.sectionKey(sortBy: SortBy): String {
    return when (sortBy) {
        SortBy.ARTISTS -> artistGroupKey
        SortBy.ALBUM -> albumGroupKey
        else -> titleGroupKey
    }
}
