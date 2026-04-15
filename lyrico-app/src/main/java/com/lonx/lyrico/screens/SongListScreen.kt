package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.DropdownItem
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.dialog.BatchMatchConfigBottomSheet
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrico.viewmodel.BatchMatchViewModel
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchEditDestination
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDetailDestination
import com.ramcosta.composedestinations.generated.destinations.BatchRenameDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
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
    val songListViewModel: SongListViewModel = koinViewModel()
    val batchMatchViewModel: BatchMatchViewModel = koinViewModel()
    val songListUiState by songListViewModel.uiState.collectAsState()
    val batchMatchUiState by batchMatchViewModel.uiState.collectAsState()
    val sortInfo by songListViewModel.sortInfo.collectAsState()
    val songs by songListViewModel.songs.collectAsState()
    val searchType by songListViewModel.searchType.collectAsState()
    val isSelectionMode by songListViewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongIds by songListViewModel.selectedSongIds.collectAsState()
    val allSelected = selectedSongIds.size == songs.size
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

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
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
    BackHandler(enabled = isSelectionMode || isSearchMode) {
        if (isSelectionMode) {
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
            floatingToolbar = {
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    val hasSelection = selectedSongIds.isNotEmpty()

                    FloatingNavigationBar(
                        mode = FloatingNavigationBarDisplayMode.IconAndText
                    ) {
                        FloatingNavigationBarItem(
                            selected = hasSelection,
                            enabled = hasSelection,
                            label = stringResource(R.string.action_share),
                            onClick = { songListViewModel.batchShare(context, songs) },
                            icon = MiuixIcons.Share
                        )
                        FloatingNavigationBarItem(
                            selected = hasSelection,
                            enabled = hasSelection,
                            label = stringResource(R.string.action_delete),
                            onClick = { songListViewModel.showBatchDeleteDialog() },
                            icon = MiuixIcons.Delete
                        )
                        FloatingNavigationBarItem(
                            selected = hasSelection,
                            enabled = hasSelection,
                            label = stringResource(R.string.action_batch_match),
                            onClick = {
                                songListViewModel.setSelectionUris()
                                batchMatchViewModel.openBatchMatchConfig()
                                      },
                            icon = MiuixIcons.Edit
                        )
                        FloatingNavigationBarItem(
                            selected = hasSelection,
                            enabled = hasSelection,
                            label = stringResource(R.string.batch_edit_title),
                            onClick = {
                                if (songListViewModel.setSelectionUris()) {
                                    navigator.navigate(BatchEditDestination())
                                }
                            },
                            icon = MiuixIcons.Edit
                        )
                        FloatingNavigationBarItem(
                            selected = hasSelection,
                            enabled = hasSelection,
                            label = stringResource(R.string.action_batch_rename),
                            onClick = {
                                if (songListViewModel.setSelectionPaths()) {
                                    navigator.navigate(BatchRenameDestination)
                                }
                            },
                            icon = MiuixIcons.Rename
                        )
                    }
                }
            },
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
                                    animationSpec = tween(animationDuration, easing = FastOutSlowInEasing),
                                    initialOffsetY = { -it / 3 } // 从上方 1/3 处滑入
                                )
                        val exit = fadeOut(tween(animationDuration)) +
                                slideOutVertically(
                                    animationSpec = tween(animationDuration, easing = FastOutSlowInEasing),
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
                            SmallTopAppBar(
                                title = "",
                                scrollBehavior = topAppBarScrollBehavior,
                                navigationIcon = {
                                    Text(
                                        text = stringResource(
                                            R.string.selection_mode_selected_count,
                                            selectedSongIds.size
                                        )
                                    )
                                },
                                actions = {
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
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            songListViewModel.exitSelectionMode()
                                        }
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_close),
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }

                        TopBarState.Search -> {
                            Column(
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(vertical = 8.dp)
                            ) {
                                SearchBar(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    value = songListUiState.searchQuery,
                                    onValueChange = {
                                        songListViewModel.onSearchQueryChanged(it)
                                    },
                                    placeholder = stringResource(id = R.string.local_search_hint),
                                    actions = {
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
                                    },
                                    onSearch = {
                                        songListViewModel.onSearchQueryChanged(songListUiState.searchQuery)
                                    }
                                )
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
                                                            songListViewModel.onSortChange(SortInfo(type, newOrder))
                                                        }
                                                    )
                                                }
                                                HorizontalDivider()
                                                SwitchPreference(
                                                    title = stringResource(R.string.show_scroll_top_button),
                                                    summary = stringResource(R.string.show_scroll_top_button_hint),
                                                    checked = showScrollTopButton,
                                                    onCheckedChange = {
                                                        songListViewModel.setScrollToTopButtonEnabled(it)
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
            PullToRefresh(
                isRefreshing = songListUiState.isLoading,
                onRefresh = { songListViewModel.refreshSongs() },
                modifier = Modifier.padding(paddingValues),
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
                ){
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
                        enabled = !enableIndex,
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
                        overscrollEffect = null
                    ) {
                        if (songs.isNotEmpty()) {
                            items(
                                items = songs,
                                key = { song -> song.mediaId }
                            ) { song ->
                                SongListItem(
                                    song = song,
                                    navigator = navigator,
                                    modifier = Modifier.animateItem(),
                                    isSelectionMode = isSelectionMode,
                                    isSelected = selectedSongIds.contains(song.mediaId),
                                    onToggleSelection = { songListViewModel.toggleSelection(song.mediaId) },
                                    trailingContent = {
                                        Box(modifier = Modifier.padding(end = 8.dp)) {
                                            if (!isSelectionMode) {
                                                IconButton(onClick = { songListViewModel.showMenu(song) }) {
                                                    Icon(
                                                        imageVector = MiuixIcons.More,
                                                        contentDescription = "More"
                                                    )
                                                }
                                            } else {
                                                Checkbox(
                                                    state = if (selectedSongIds.contains(song.mediaId)) ToggleableState.On else ToggleableState.Off,
                                                    onClick = { songListViewModel.toggleSelection(song.mediaId) }
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {

                                }
                            }
                        }
                    }
                }
            }
            if (enableIndex) {
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
            val song = sheetUiState.menuSong

            WindowBottomSheet(
                show = song != null,
                onDismissRequest = { songListViewModel.dismissAll() }
            ) {
                song?.let {
                    SongMenuBottomSheetContent(
                        song = it,
                        onPlay = { songListViewModel.play(context, it) },
                        showInfo = { songListViewModel.showDetail(it) },
                        onDelete = { songListViewModel.showDeleteDialog() },
                        onShare = {
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                it.mediaId
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TITLE, it.title ?: it.fileName)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    context.getString(R.string.share_chooser_title)
                                )
                            )
                        }
                    )
                }
            }
            val detailSong = sheetUiState.detailSong
            WindowBottomSheet(
                show = detailSong != null,
                onDismissRequest = { songListViewModel.dismissDetail() },
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
                            colors = ButtonDefaults.textButtonColorsPrimary()
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
                    selectedSongIds.size
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
                            colors = ButtonDefaults.textButtonColorsPrimary()
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
                onConfirm = {
                    batchMatchViewModel.batchMatch(songs)
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
                                R.string.action_view_results
                            ),
                            onClick = {
                                if (batchMatchUiState.isBatchMatching) {
                                    batchMatchViewModel.abortBatchMatch()
                                } else {
                                    batchMatchViewModel.closeBatchMatchDialog()
                                    navigator.navigate(
                                        BatchMatchHistoryDetailDestination(batchMatchUiState.batchHistoryId)
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
fun SongListItem(
    song: SongEntity,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    isSelectionMode: Boolean? = null,
    isSelected: Boolean? = null,
    onToggleSelection: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val backgroundColor =
        if (isSelected == true) MiuixTheme.colorScheme.primary.copy(alpha = 0.1f) else MiuixTheme.colorScheme.surface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode == true) {
                        onToggleSelection?.let { it() }
                    } else {
                        navigator.navigate(EditMetadataDestination(songFileUri = song.uri))
                    }
                },
                onLongClick = if (isSelectionMode == true) null else {
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleSelection?.let { it() }
                    }
                }
            )
            .padding(vertical = 8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LyricoColors.coverPlaceholder)
            ) {
                AsyncImage(
                    model = CoverRequest(song.getUri, song.fileLastModified),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    ),
                    error = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    )
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MiuixTheme.colorScheme.onSecondaryContainer
                                ),
                            )
                        )
                ) {
                    Text(
                        text = song.fileName.substringAfterLast('.', "").uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() }
                            ?: stringResource(R.string.unknown_artist),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = " - ${song.album}",
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (song.durationMilliseconds > 0) {
                    val minutes = song.durationMilliseconds / 60000
                    val seconds = (song.durationMilliseconds % 60000) / 1000
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 12.sp
                    )
                }

                if (song.bitrate > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.bitrate}kbps",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            trailingContent?.let {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    trailingContent()
                }
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
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .scrollEndHaptic()
            .overScrollVertical(),
        overscrollEffect = null,
    ) {
        item(key = "song_menu_title") {
            val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
            val text =
                song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }

        item(key = "song_menu_actions") {
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
}

@SuppressLint("DefaultLocale")
@Composable
fun SongDetailBottomSheetContent(context: Context, song: SongEntity) {
    val dateFormat = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .scrollEndHaptic()
            .overScrollVertical(),
        overscrollEffect = null,
    ) {
        item {
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
        }

        item {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                SongDetailItem(stringResource(R.string.label_album), song.album)
                SongDetailItem(stringResource(R.string.label_date), song.date)
                SongDetailItem(stringResource(R.string.label_genre), song.genre)
                SongDetailItem(stringResource(R.string.label_track_number), song.trackerNumber)
                SongDetailItem(
                    stringResource(R.string.label_duration),
                    if (song.durationMilliseconds > 0) {
                        val min = song.durationMilliseconds / 60000
                        val sec = (song.durationMilliseconds % 60000) / 1000
                        String.format("%d:%02d", min, sec)
                    } else null
                )

                SongDetailItem(
                    stringResource(R.string.label_bitrate),
                    if (song.bitrate > 0) "${song.bitrate} kbps" else null
                )

                SongDetailItem(
                    stringResource(R.string.label_sample_rate),
                    if (song.sampleRate > 0) "${song.sampleRate} Hz" else null
                )

                SongDetailItem(
                    stringResource(R.string.label_channels),
                    if (song.channels > 0) "${song.channels}" else null
                )
                SongDetailItem(
                    stringResource(R.string.label_date_added),
                    if (song.fileAdded > 0)
                        dateFormat.format(Date(song.fileAdded))
                    else null
                )

                SongDetailItem(
                    stringResource(R.string.label_date_modified),
                    if (song.fileLastModified > 0)
                        dateFormat.format(Date(song.fileLastModified))
                    else null
                )

                SongDetailItem(
                    stringResource(R.string.label_file_path),
                    song.filePath
                )

                SongDetailItem(
                    stringResource(R.string.label_file_size),
                    if (song.fileSize > 0)
                        Formatter.formatFileSize(context, song.fileSize)
                    else null
                )

                if (BuildConfig.DEBUG) {
                    SongDetailItem(
                        label = "文件URI",
                        value = song.uri
                    )
                    SongDetailItem(
                        label = "文件ID",
                        value = song.id.toString()
                    )
                    SongDetailItem(
                        label = "文件名",
                        value = song.fileName
                    )
                }
            }
        }
    }
}

@Composable
fun SongDetailItem(label: String, value: String?) {
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
        Text(
            text = value,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.padding(top = 2.dp)
        )
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
                                ((dragDistance - startThreshold) * speedFactor).coerceAtMost(maxSpeed)
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