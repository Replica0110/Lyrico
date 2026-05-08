package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.LibraryScanProgress
import com.lonx.lyrico.data.repository.LibraryScanStage
import com.lonx.lyrico.ui.components.DropdownItem
import com.lonx.lyrico.ui.components.FabMenuItem
import com.lonx.lyrico.ui.components.SongListItem
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.dialog.BatchLyricsFormatConfigBottomSheet
import com.lonx.lyrico.ui.dialog.BatchMatchConfigBottomSheet
import com.lonx.lyrico.ui.dialog.ReplayGainConfigBottomSheet
import com.lonx.lyrico.utils.coil.CoverRequest
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
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.viewmodel.koinActivityViewModel
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults

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
    val sheetUiState by songListViewModel.sheetState.collectAsStateWithLifecycle()

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
    val dragSelectionModifier = rememberDragSelectionModifier(
        listState = listState,
        songs = songs,
        songListViewModel = songListViewModel,
        isSelectionMode = isSelectionMode
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        TabRowWithContour(
                            tabs = LocalSearchType.entries.map { stringResource(it.labelRes) },
                            selectedTabIndex = LocalSearchType.entries.indexOf(searchType),
                            onTabSelected = {
                                songListViewModel.onSearchTypeChanged(LocalSearchType.entries[it])
                            }
                        )
                    }
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
                            .then(dragSelectionModifier),
                        state = listState,
                        overscrollEffect = null,
                        contentPadding = PaddingValues(bottom = navigationBarBottomInset)
                    ) {
                        if (songs.isNotEmpty()) {
                            items(
                                items = songs,
                                key = { song -> song.uri.takeIf { it.isNotBlank() && it != "0" } ?: "song-${song.id}" }
                            ) { song ->
                                SongListItem(
                                    song = song,
                                    navigator = navigator,
                                    modifier = Modifier.animateItem(),
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedSongUris.contains(song.uri),
                                    onToggleSelection = { songListViewModel.toggleSelection(song.uri) },
                                    trailingContent = {
                                        Box(modifier = Modifier.padding(end = 8.dp)) {
                                            if (!isSelectionMode) {
                                                IconButton(onClick = {
                                                    songListViewModel.showMenu(
                                                        song
                                                    )
                                                }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.More,
                                                        contentDescription = "More"
                                                    )
                                                }
                                            } else {
                                                Checkbox(
                                                    state = if (selectedSongUris.contains(song.uri)) ToggleableState.On else ToggleableState.Off,
                                                    onClick = {
                                                        songListViewModel.toggleSelection(
                                                            song.uri
                                                        )
                                                    }
                                                )
                                            }
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
            val song = sheetUiState.menuSong
            var showMenuSheet by remember { mutableStateOf(false) }
            LaunchedEffect(song) {
                showMenuSheet = song != null
            }

            WindowBottomSheet(
                show = showMenuSheet,
                onDismissRequest = { showMenuSheet = false },
                onDismissFinished = { songListViewModel.dismissAll() }
            ) {
                song?.let {
                    SongMenuBottomSheetContent(
                        song = it,
                        onPlay = { songListViewModel.playQueue(songs, it) },
                        showInfo = { songListViewModel.showDetail(it) },
                        onDelete = { songListViewModel.showDeleteDialog() },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, it.uri.toUri())
                                putExtra(Intent.EXTRA_TITLE, it.title ?: it.fileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    context.getString(R.string.share_chooser_title)
                                )
                            )
                        },
                        onRename = { songListViewModel.showRenameDialog() }
                    )
                }
            }
            val detailSong = sheetUiState.detailSong
            var showDetailSheet by remember { mutableStateOf(false) }
            LaunchedEffect(detailSong) {
                if (detailSong != null) {
                    showDetailSheet = true
                }
            }
            WindowBottomSheet(
                show = showDetailSheet,
                enableNestedScroll = false,
                onDismissRequest = { showDetailSheet = false },
                onDismissFinished = { songListViewModel.dismissDetail() },
            ) {
                detailSong?.let {
                    SongDetailBottomSheetContent(context = context, song = it)
                }
            }

            WindowDialog(
                title = stringResource(R.string.dialog_delete_file_title),
                show = songListUiState.showDeleteDialog && sheetUiState.menuSong != null,
                summary = stringResource(
                    R.string.dialog_delete_file_content,
                    sheetUiState.menuSong?.fileName ?: ""
                ),
                onDismissRequest = { songListViewModel.dismissDeleteDialog() },
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { songListViewModel.dismissDeleteDialog() },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                songListViewModel.dismissDeleteDialog()
                                songListViewModel.dismissAll()
                                songListViewModel.delete(sheetUiState.menuSong!!)
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
            WindowDialog(
                title = stringResource(R.string.dialog_rename_title),
                show = songListUiState.showRenameDialog && sheetUiState.menuSong != null,
                onDismissRequest = { songListViewModel.dismissRenameDialog() },
            ) {
                val renameSong = sheetUiState.menuSong
                val fileExtension = renameSong?.fileName?.substringAfterLast('.', "") ?: ""
                val extensionDot = if (fileExtension.isNotEmpty()) ".$fileExtension" else ""
                val oldName = renameSong?.fileName?.substringBeforeLast('.') ?: ""
                var newName by remember(renameSong) {
                    mutableStateOf(oldName)
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (extensionDot.isNotEmpty()) {
                                Text(
                                    text = extensionDot,
                                    style = MiuixTheme.textStyles.footnote1,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { songListViewModel.dismissRenameDialog() },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                val fullNewName = newName.trim() + extensionDot
                                if (newName.isNotBlank() && fullNewName != renameSong?.fileName) {
                                    songListViewModel.renameSong(fullNewName)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
            // 批量删除确认对话框
            WindowDialog(
                show = songListUiState.showBatchDeleteDialog,
                onDismissRequest = { songListViewModel.dismissBatchDeleteDialog() },
                summary = stringResource(
                    R.string.dialog_batch_delete_content,
                    selectedSongUris.size
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.cancel),
                            onClick = { songListViewModel.dismissBatchDeleteDialog() },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(20.dp))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = stringResource(R.string.confirm),
                            onClick = {
                                songListViewModel.dismissBatchDeleteDialog()
                                songListViewModel.batchDelete(songs)
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
            // 批量匹配配置BottomSheet
            BatchMatchConfigBottomSheet(
                show = batchMatchUiState.showBatchConfigDialog,
                initialConfig = batchMatchConfig,
                onDismissRequest = { config ->
                    batchMatchViewModel.saveBatchMatchConfig(config)
                    batchMatchViewModel.closeBatchMatchConfig()
                },
                onConfirm = { config ->
                    batchMatchViewModel.batchMatch(songs, config)
                }
            )

            WindowBottomSheet(
                show = batchMatchUiState.isBatchMatching || batchMatchUiState.batchProgress != null,
                onDismissRequest = {
                    if (!batchMatchUiState.isBatchMatching) batchMatchViewModel.closeBatchMatchDialog()
                },
                title = stringResource(R.string.batch_matching_title),
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        batchMatchUiState.batchProgress?.let { (current, total) ->
                            val progress =
                                if (total > 0) current.toFloat() / total.toFloat() else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (batchMatchUiState.isBatchMatching) {
                                        batchMatchUiState.currentFile
                                    } else {
                                        stringResource(
                                            R.string.batch_matching_total_time,
                                            batchMatchUiState.batchTimeMillis / 1000.0
                                        )
                                    },
                                    style = MiuixTheme.textStyles.subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "$current / $total",
                                    style = MiuixTheme.textStyles.main,
                                    textAlign = TextAlign.End
                                )
                            }

                            LinearProgressIndicator(progress = progress)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.batch_matching_success,
                                batchMatchUiState.successCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_matching_skipped,
                                batchMatchUiState.skippedCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_matching_failure,
                                batchMatchUiState.failureCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            enabled = !batchMatchUiState.isBatchMatching,
                            text = stringResource(R.string.action_close),
                            onClick = { batchMatchViewModel.closeBatchMatchDialog() },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = if (batchMatchUiState.isBatchMatching) stringResource(R.string.action_abort) else stringResource(
                                R.string.confirm
                            ),
                            onClick = {
                                if (batchMatchUiState.isBatchMatching) {
                                    batchMatchViewModel.abortBatchMatch()
                                } else {
                                    batchMatchViewModel.closeBatchMatchDialog()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }

            // ReplayGain配置BottomSheet
            ReplayGainConfigBottomSheet(
                show = batchReplayGainUiState.showConfigDialog,
                initialConcurrency = batchReplayGainUiState.concurrency,
                onDismissRequest = { concurrency ->
                    batchReplayGainViewModel.setConcurrency(concurrency)
                    batchReplayGainViewModel.closeReplayGainConfig()
                },
                onConfirm = { _ ->
                    batchReplayGainViewModel.startBatchScan()
                }
            )

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

            WindowBottomSheet(
                show = batchReplayGainUiState.showProgressDialog,
                onDismissRequest = {
                    if (!batchReplayGainUiState.isRunning) batchReplayGainViewModel.closeProgressDialog()
                },
                onDismissFinished = {
                    if (batchReplayGainUiState.isSuccess) {
                        batchReplayGainViewModel.closeProgressDialog()
                    }
                },
                allowDismiss = !batchReplayGainUiState.isRunning,
                title = stringResource(R.string.action_batch_replay_gain),
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        batchReplayGainUiState.progress?.let { (current, total) ->
                            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (batchReplayGainUiState.isRunning) {
                                        stringResource(R.string.batch_edit_processing)
                                    } else {
                                        stringResource(
                                            R.string.batch_replay_gain_total_time,
                                            batchReplayGainUiState.totalTimeMillis / 1000.0
                                        )
                                    },
                                    style = MiuixTheme.textStyles.subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "$current / $total",
                                    style = MiuixTheme.textStyles.main,
                                    textAlign = TextAlign.End
                                )
                            }

                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // 显示并发任务的进度条
                            if (batchReplayGainUiState.isRunning && batchReplayGainUiState.fileProgressMap.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    batchReplayGainUiState.fileProgressMap.forEach { (fileName, fileProgress) ->
                                        val progressPercent = (fileProgress * 100).toInt()
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = fileName,
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "$progressPercent%",
                                                style = MiuixTheme.textStyles.footnote1,
                                                color = MiuixTheme.colorScheme.onSurfaceContainer
                                            )
                                        }
                                        LinearProgressIndicator(
                                            progress = fileProgress,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_success,
                                batchReplayGainUiState.successCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_skipped,
                                batchReplayGainUiState.skippedCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_failure,
                                batchReplayGainUiState.failureCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = if (batchReplayGainUiState.isRunning) stringResource(R.string.action_abort) else stringResource(R.string.action_close),
                            onClick = {
                                if (batchReplayGainUiState.isRunning) {
                                    batchReplayGainViewModel.abortBatchScan()
                                } else {
                                    batchReplayGainViewModel.closeProgressDialog()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!batchReplayGainUiState.isRunning) {
                            Spacer(Modifier.width(20.dp))
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = stringResource(R.string.confirm),
                                onClick = { batchReplayGainViewModel.closeProgressDialog() },
                                modifier = Modifier.weight(1f),
                                colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }

            WindowBottomSheet(
                show = batchLyricsFormatUiState.showProgressDialog,
                onDismissRequest = {
                    if (!batchLyricsFormatUiState.isRunning) {
                        batchLyricsFormatViewModel.closeProgressDialog()
                    }
                },
                onDismissFinished = {
                    if (batchLyricsFormatUiState.isSuccess) {
                        batchLyricsFormatViewModel.closeProgressDialog()
                    }
                },
                allowDismiss = !batchLyricsFormatUiState.isRunning,
                title = stringResource(R.string.action_batch_convert_lyrics_format),
            ) {
                Column(
                    modifier = Modifier
                        .padding(bottom = 32.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.current_target_format,
                                stringResource(batchLyricsFormatUiState.targetFormat.labelRes)
                            ),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                        )

                        batchLyricsFormatUiState.progress?.let { (current, total) ->
                            val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (batchLyricsFormatUiState.isRunning) {
                                        stringResource(R.string.batch_edit_processing)
                                    } else {
                                        stringResource(
                                            R.string.batch_replay_gain_total_time,
                                            batchLyricsFormatUiState.totalTimeMillis / 1000.0
                                        )
                                    },
                                    style = MiuixTheme.textStyles.subtitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "$current / $total",
                                    style = MiuixTheme.textStyles.main,
                                    textAlign = TextAlign.End
                                )
                            }

                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_success,
                                batchLyricsFormatUiState.successCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_skipped,
                                batchLyricsFormatUiState.skippedCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                        Text(
                            text = stringResource(
                                R.string.batch_replay_gain_failure,
                                batchLyricsFormatUiState.failureCount
                            ),
                            style = MiuixTheme.textStyles.main
                        )
                    }

                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        top.yukonga.miuix.kmp.basic.TextButton(
                            text = if (batchLyricsFormatUiState.isRunning) {
                                stringResource(R.string.action_abort)
                            } else {
                                stringResource(R.string.action_close)
                            },
                            onClick = {
                                if (batchLyricsFormatUiState.isRunning) {
                                    batchLyricsFormatViewModel.abortBatchConvert()
                                } else {
                                    batchLyricsFormatViewModel.closeProgressDialog()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!batchLyricsFormatUiState.isRunning) {
                            Spacer(Modifier.width(20.dp))
                            top.yukonga.miuix.kmp.basic.TextButton(
                                text = stringResource(R.string.confirm),
                                onClick = { batchLyricsFormatViewModel.closeProgressDialog() },
                                modifier = Modifier.weight(1f),
                                colors = MiuixButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showFab,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topPadding)
        ) {
            Surface(
                modifier = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                shape = CircleShape,
                color = MiuixTheme.colorScheme.primary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_up_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_scroll_to_top),
                        style = MiuixTheme.textStyles.button,
                        color = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = isSelectionMode && isFabMenuExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)) // 加上淡淡的暗色遮罩层，让用户的视觉聚焦在菜单上
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isFabMenuExpanded = false }
            )
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 向上展开的子菜单
                AnimatedVisibility(
                    visible = isFabMenuExpanded && hasSelection,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        FabMenuItem(
                            label = stringResource(R.string.action_batch_replay_gain),
                            icon = MiuixIcons.Edit,
                            onClick = {
                                isFabMenuExpanded = false
                                batchReplayGainViewModel.setSelectionUris(songListViewModel.selectedSongUris.value.toList())
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
                                songListViewModel.showBatchDeleteDialog()
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

                // 主 FAB 按钮
                FloatingActionButton(
                    onClick = {
                        if (hasSelection) {
                            isFabMenuExpanded = !isFabMenuExpanded
                        }
                    }
                ) {
                    // 添加旋转动画
                    val rotation by animateFloatAsState(targetValue = if (isFabMenuExpanded) 45f else 0f)
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "Batch Actions",
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryScanProgressText(
    progress: LibraryScanProgress,
    modifier: Modifier = Modifier
) {
    val title = when (progress.stage) {
        LibraryScanStage.LISTING_FILES -> stringResource(R.string.scan_progress_listing)
        LibraryScanStage.READING_METADATA -> stringResource(
            R.string.scan_progress_reading,
            progress.current,
            progress.total
        )
        LibraryScanStage.WRITING_DATABASE -> stringResource(R.string.scan_progress_writing)
        LibraryScanStage.FINISHED -> stringResource(R.string.scan_progress_finished)
    }

    Text(
        text = title,
        modifier = modifier
            .padding(horizontal = 24.dp),
        style = MiuixTheme.textStyles.title4,
        color = MiuixTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SongListEmptyState(
    onAddFolder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = MiuixIcons.AddFolder,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = stringResource(R.string.song_list_empty_title),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.song_list_empty_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center
            )
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.action_add_folder),
                onClick = onAddFolder,
                colors = MiuixButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

fun findScrollIndex(
    section: String,
    sectionIndexMap: Map<String, Int>,
    order: SortOrder
): Int {
    if (sectionIndexMap.isEmpty()) return 0
    sectionIndexMap[section]?.let { return it }
    val keys = sectionIndexMap.keys.sorted()

    return if (order == SortOrder.ASC) {
        keys.firstOrNull { it >= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.last()]!!
    } else {
        keys.lastOrNull { it <= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.first()]!!
    }
}

@Composable
fun AlphabetSideBar(
    sections: List<String>,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var componentHeight by remember { mutableIntStateOf(0) }
    var currentSection by remember { mutableStateOf<String?>(null) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    fun getSectionIndex(offsetY: Float): Int {
        if (componentHeight == 0 || sections.isEmpty()) return -1
        val step = componentHeight.toFloat() / sections.size
        return (offsetY / step).toInt().coerceIn(0, sections.lastIndex)
    }

    fun updateSelection(index: Int) {
        if (index != -1) {
            val section = sections[index]
            currentSection = section
            if (index != lastSelectedIndex) {
                lastSelectedIndex = index
                onSectionSelected(section)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        AnimatedVisibility(
            visible = currentSection != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(50.dp)
                    .background(
                        color = MiuixTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentSection ?: "",
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .width(24.dp)
                .onGloballyPositioned { componentHeight = it.size.height }
                .pointerInput(sections) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            val index = getSectionIndex(offset.y)
                            updateSelection(index)
                        },
                        onDragEnd = {
                            currentSection = null
                            lastSelectedIndex = -1
                        },
                        onDragCancel = {
                            currentSection = null
                            lastSelectedIndex = -1
                        }
                    ) { change, _ ->
                        change.consume()
                        val index = getSectionIndex(change.position.y)
                        updateSelection(index)
                    }
                }
                .pointerInput(sections) {
                    detectTapGestures(
                        onPress = { offset ->
                            val index = getSectionIndex(offset.y)
                            updateSelection(index)
                            tryAwaitRelease()
                            currentSection = null
                            lastSelectedIndex = -1
                        },
                        onTap = {}
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            sections.forEach { section ->
                Text(
                    text = section,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 12.sp),
                    color = if (currentSection == section) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}



@SuppressLint("DefaultLocale")
@Composable
fun SongMenuBottomSheetContent(
    song: SongEntity,
    onPlay: () -> Unit = {},
    showInfo: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
        val text =
            song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(12.dp)
        )


        Card(
            modifier = Modifier.padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer,
            )
        ) {
            ArrowPreference(
                title = stringResource(R.string.menu_action_play),
                summary = stringResource(R.string.menu_action_play_sub),
                onClick = { onPlay() }
            )
            ArrowPreference(
                title = stringResource(R.string.menu_action_info),
                onClick = { showInfo() }
            )
            ArrowPreference(
                title = stringResource(R.string.menu_action_share),
                onClick = { onShare() }
            )
            ArrowPreference(
                title = stringResource(R.string.menu_action_rename),
                onClick = { onRename() }
            )
            ArrowPreference(
                title = stringResource(R.string.menu_action_delete),
                summary = stringResource(R.string.menu_action_delete_sub),
                titleColor = BasicComponentColors(
                    MiuixTheme.colorScheme.error,
                    MiuixTheme.colorScheme.disabledOnSecondaryVariant
                ),
                onClick = { onDelete() }
            )
        }

    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SongDetailBottomSheetContent(context: Context, song: SongEntity) {
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = CoverRequest(song.getUri, song.fileLastModified),
                contentDescription = stringResource(R.string.cd_cover),
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.ic_album_24dp),
                error = painterResource(R.drawable.ic_album_24dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = song.artist.takeIf { !it.isNullOrBlank() }
                        ?: stringResource(R.string.unknown_artist),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }


        Card(
            modifier = Modifier.padding(bottom = 12.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.secondaryContainer,
            )
        ) {
            val copyToClipboard: (String) -> Unit = { text ->
                clipboardManager.setText(AnnotatedString(text))
                Toast.makeText(
                    context,
                    context.getString(R.string.msg_copied_to_clipboard),
                    Toast.LENGTH_SHORT
                ).show()
            }
            SongDetailItem(
                stringResource(R.string.label_album),
                song.album,
                onCopy = copyToClipboard
            )
            SongDetailItem(stringResource(R.string.label_date), song.date, onCopy = copyToClipboard)
            SongDetailItem(
                stringResource(R.string.label_genre),
                song.genre,
                onCopy = copyToClipboard
            )
            SongDetailItem(
                stringResource(R.string.label_track_number),
                song.trackerNumber,
                onCopy = copyToClipboard
            )
            SongDetailItem(
                stringResource(R.string.label_duration),
                if (song.durationMilliseconds > 0) {
                    val min = song.durationMilliseconds / 60000
                    val sec = (song.durationMilliseconds % 60000) / 1000
                    String.format("%d:%02d", min, sec)
                } else null,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_bitrate),
                if (song.bitrate > 0) "${song.bitrate} kbps" else null,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_sample_rate),
                if (song.sampleRate > 0) "${song.sampleRate} Hz" else null,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_channels),
                if (song.channels > 0) "${song.channels}" else null,
                onCopy = copyToClipboard
            )
            SongDetailItem(
                stringResource(R.string.label_date_added),
                if (song.fileAdded > 0)
                    dateFormat.format(Date(song.fileAdded))
                else null,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_date_modified),
                if (song.fileLastModified > 0)
                    dateFormat.format(Date(song.fileLastModified))
                else null,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_file_path),
                song.filePath,
                onCopy = copyToClipboard
            )

            SongDetailItem(
                stringResource(R.string.label_file_size),
                if (song.fileSize > 0)
                    Formatter.formatFileSize(context, song.fileSize)
                else null,
                onCopy = copyToClipboard
            )

            if (BuildConfig.DEBUG) {
                SongDetailItem(
                    label = "文件URI",
                    value = song.uri,
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    label = "文件ID",
                    value = song.id.toString(),
                    onCopy = copyToClipboard
                )
                SongDetailItem(
                    label = "文件名",
                    value = song.fileName,
                    onCopy = copyToClipboard
                )
            }

        }
    }
}

@Composable
fun SongDetailItem(
    label: String,
    value: String?,
    onCopy: ((String) -> Unit)? = null
) {
    if (value.isNullOrBlank()) return

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.main,
                modifier = Modifier.weight(1f)
            )

            if (onCopy != null) {
                IconButton(
                    modifier = Modifier.size(20.dp),
                    onClick = { onCopy(value) }
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = MiuixIcons.Copy,
                        contentDescription = "复制"
                    )
                }
            }
        }
    }
}

enum class ScrollDirection {
    UP, DOWN, NONE
}

@Composable
fun rememberDragSelectionModifier(
    listState: LazyListState,
    songs: List<SongEntity>,
    songListViewModel: SongListViewModel,
    isSelectionMode: Boolean
): Modifier {

    var initialDragY by remember { mutableStateOf<Float?>(null) }
    var currentDragY by remember { mutableStateOf<Float?>(null) }

    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }

    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableFloatStateOf(0f) }


    var peakDragY by remember { mutableStateOf<Float?>(null) }
    var scrollDirection by remember { mutableStateOf(ScrollDirection.NONE) }

    LaunchedEffect(Unit) {
        while (isActive) {

            // 平滑速度
            currentSpeed += (autoScrollSpeed - currentSpeed) * 0.2f

            if (autoScrollSpeed == 0f) {
                currentSpeed *= 0.85f
            }

            if (kotlin.math.abs(currentSpeed) < 0.5f) {
                currentSpeed = 0f
            }

            if (currentSpeed != 0f) {
                listState.scrollBy(currentSpeed)

                currentDragY?.let { y ->
                    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                    val clampedY = y.coerceIn(0f, viewportHeight - 1f)

                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find {
                        clampedY >= it.offset && clampedY <= (it.offset + it.size)
                    }

                    if (itemInfo != null && initialDragIndex != null) {
                        if (itemInfo.index != currentDragIndex) {
                            currentDragIndex = itemInfo.index
                            songListViewModel.updateDragSelection(
                                initialDragIndex!!,
                                currentDragIndex!!,
                                songs
                            )
                        }
                    }
                }
            }

            delay(16)
        }
    }

    return if (isSelectionMode) {
        Modifier.pointerInput(songs, isSelectionMode) {
            detectDragGesturesAfterLongPress(

                onDragStart = { offset ->
                    initialDragY = offset.y
                    currentDragY = offset.y

                    scrollDirection = ScrollDirection.NONE // 重置方向

                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find {
                        offset.y >= it.offset && offset.y <= (it.offset + it.size)
                    }

                    itemInfo?.let {
                        initialDragIndex = it.index
                        currentDragIndex = it.index
                        songListViewModel.startDragSelection(it.index, songs)
                    }
                },

                onDrag = { change, _ ->
                    val y = change.position.y
                    currentDragY = y

                    val retreatThreshold = 20f // 反向滑动的灵敏度阈值（回退多少就停）
                    val startThreshold = 180f
                    val speedFactor = 0.1f
                    val maxSpeed = 60f

                    initialDragY?.let { startY ->
                        val dragDistance = y - startY

                        when (scrollDirection) {
                            ScrollDirection.NONE -> {
                                if (dragDistance > startThreshold) {
                                    scrollDirection = ScrollDirection.DOWN
                                    peakDragY = y
                                } else if (dragDistance < -startThreshold) {
                                    scrollDirection = ScrollDirection.UP
                                    peakDragY = y
                                }
                            }

                            ScrollDirection.DOWN -> {
                                peakDragY = maxOf(peakDragY ?: y, y)

                                if (y < (peakDragY!! - retreatThreshold)) {
                                    scrollDirection = ScrollDirection.NONE
                                    peakDragY = null
                                } else if (dragDistance < 0) {
                                    scrollDirection = ScrollDirection.NONE
                                }
                            }

                            ScrollDirection.UP -> {
                                peakDragY = minOf(peakDragY ?: y, y)

                                if (y > (peakDragY!! + retreatThreshold)) {
                                    scrollDirection = ScrollDirection.NONE
                                    peakDragY = null
                                } else if (dragDistance > 0) {
                                    scrollDirection = ScrollDirection.NONE
                                }
                            }
                        }

                        autoScrollSpeed = when (scrollDirection) {
                            ScrollDirection.DOWN -> {
                                ((dragDistance - startThreshold) * speedFactor).coerceAtMost(
                                    maxSpeed
                                )
                            }

                            ScrollDirection.UP -> {
                                ((dragDistance + startThreshold) * speedFactor).coerceAtLeast(-maxSpeed)
                            }

                            ScrollDirection.NONE -> 0f
                        }
                    }

                    //  更新选中项
                    val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
                    val clampedY = y.coerceIn(0f, viewportHeight - 1f)

                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find {
                        clampedY >= it.offset && clampedY <= (it.offset + it.size)
                    }

                    if (itemInfo != null && initialDragIndex != null) {
                        if (itemInfo.index != currentDragIndex) {
                            currentDragIndex = itemInfo.index
                            songListViewModel.updateDragSelection(
                                initialDragIndex!!,
                                currentDragIndex!!,
                                songs
                            )
                        }
                    }
                },

                onDragEnd = {
                    initialDragIndex = null
                    currentDragIndex = null
                    initialDragY = null
                    currentDragY = null

                    autoScrollSpeed = 0f
                    currentSpeed = 0f
                    scrollDirection = ScrollDirection.NONE

                    songListViewModel.endDragSelection()
                },

                onDragCancel = {
                    initialDragIndex = null
                    currentDragIndex = null
                    initialDragY = null
                    currentDragY = null

                    autoScrollSpeed = 0f
                    currentSpeed = 0f
                    scrollDirection = ScrollDirection.NONE

                    songListViewModel.endDragSelection()
                }
            )
        }
    } else {
        Modifier
    }
}
