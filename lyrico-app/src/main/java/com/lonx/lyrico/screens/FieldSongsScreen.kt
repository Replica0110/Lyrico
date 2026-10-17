@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.icons.LucideIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.LibraryDetailTabs
import com.lonx.lyrico.screens.library.LibrarySelectionState
import com.lonx.lyrico.ui.components.library.SelectableLibraryItem
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.ui.components.bar.SongBatchSelectionActions
import com.lonx.lyrico.ui.components.bar.SongSelectionTopAppBar
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.ui.components.scaffoldContentPadding
import com.lonx.lyrico.ui.components.song.SongActionSheets
import com.lonx.lyrico.ui.components.song.SongListItemContent
import com.lonx.lyrico.ui.components.song.SongListItemActions
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.viewmodel.FieldSongsViewModel
import com.lonx.lyrico.viewmodel.SongSelectionViewModel
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonAppearance
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
@Destination<RootGraph>(route = "field_songs")
fun FieldSongsScreen(
    navigator: DestinationsNavigator,
    fieldCode: String,
    component: Boolean = false,
) {
    val viewModel: FieldSongsViewModel = koinViewModel(parameters = { parametersOf(fieldCode, component) })
    val selectionViewModel: SongSelectionViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSelectionMode by selectionViewModel.isSelectionMode.collectAsStateWithLifecycle()
    val selectedSongUris by selectionViewModel.selectedSongUris.collectAsStateWithLifecycle()
    val swipeAnchorUri by selectionViewModel.swipeAnchorUri.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val selectedTab = pagerState.targetPage
    val songs = if (selectedTab == 0) uiState.songsWithField else uiState.songsWithoutField
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBarBlurBackdrop()
    val context = LocalContext.current
    val salt = LocalUiEngine.current == UiEngine.SALT
    var expanded by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<SongEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    val title = when {
        component -> stringResource(R.string.group_replay_gain)
        viewModel.field?.custom == true -> EditFieldRegistry.customTagKeyOf(fieldCode).orEmpty()
        viewModel.field != null -> stringResource(viewModel.field.titleRes)
        else -> stringResource(R.string.edit_field_view_songs)
    }
    val tabLabels = listOf(
        stringResource(R.string.edit_field_songs_with),
        stringResource(R.string.edit_field_songs_without),
    )
    val swipeLabel = stringResource(when {
        !isSelectionMode -> R.string.swipe_selection_enter_selection
        swipeAnchorUri == null -> R.string.swipe_selection_range_start
        else -> R.string.swipe_selection_range_end
    })
    val swipeSecondaryLabel = if (!isSelectionMode) stringResource(R.string.swipe_selection_range_start) else null
    val allSelected = songs.isNotEmpty() && selectedSongUris.containsAll(songs.map { it.uri })

    BackHandler(enabled = isSelectionMode) {
        if (expanded) expanded = false else selectionViewModel.exitSelectionMode()
    }

    Box(Modifier.fillMaxSize()) {
        // Salt 的选择态由 LibraryDetailPage 的原生槽位承担；Miuix 继续用原来的动画顶栏。
        val selectionState = if (salt && isSelectionMode) {
            LibrarySelectionState(
                title = stringResource(R.string.selection_mode_selected_count, selectedSongUris.size),
                allSelected = allSelected,
                onClose = {
                    expanded = false
                    selectionViewModel.exitSelectionMode()
                },
                onToggleAll = {
                    if (allSelected) selectionViewModel.deselectAll()
                    else selectionViewModel.selectAll(songs)
                },
            )
        } else null

        LibraryDetailPage(
            title = title,
            onBack = { navigator.popBackStack() },
            selection = selectionState,
            miuixTopBar = {
                BlurredTopBar(backdrop = backdrop) {
                    Column {
                        AnimatedContent(
                            targetState = isSelectionMode,
                            label = "FieldSongsTopBarAnimation",
                            transitionSpec = {
                                val enter = fadeIn(tween(300)) + slideInVertically(
                                    tween(300, easing = FastOutSlowInEasing), initialOffsetY = { -it / 3 },
                                )
                                val exit = fadeOut(tween(300)) + slideOutVertically(
                                    tween(300, easing = FastOutSlowInEasing), targetOffsetY = { -it / 3 },
                                )
                                (enter togetherWith exit).using(SizeTransform(clip = false))
                            },
                        ) { selectionMode ->
                            if (selectionMode) {
                                SongSelectionTopAppBar(
                                    songs = songs,
                                    selectedSongUris = selectedSongUris,
                                    scrollBehavior = scrollBehavior,
                                    color = Color.Transparent,
                                    applyInsets = false,
                                    onSelectAll = selectionViewModel::selectAll,
                                    onDeselectAll = selectionViewModel::deselectAll,
                                    onClose = {
                                        expanded = false
                                        selectionViewModel.exitSelectionMode()
                                    },
                                )
                            } else {
                                SmallTopAppBar(
                                    title = title,
                                    color = Color.Transparent,
                                    defaultWindowInsetsPadding = false,
                                    scrollBehavior = scrollBehavior,
                                    navigationIcon = {
                                        IconButton(onClick = { navigator.popBackStack() }) {
                                            Icon(LucideIcons.ArrowLeft, stringResource(R.string.action_back))
                                        }
                                    },
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = !isSelectionMode,
                            enter = expandVertically(tween(300, easing = FastOutSlowInEasing)) + fadeIn(tween(300)),
                            exit = shrinkVertically(tween(300, easing = FastOutSlowInEasing)) + fadeOut(tween(300)),
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
                            ) {
                                TabRowWithContour(
                                    tabs = tabLabels,
                                    selectedTabIndex = pagerState.currentPage,
                                    onTabSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            if (salt) {
                // Salt 顶栏是透明模糊的 BasicScreen，内容顶部会被标题栏区域裁掉：
                // 整列先让开标题栏高度，Tab 才显示得出来（与插件管理页同一做法）。
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldContentPadding(paddingValues = padding))
                ) {
                    if (!isSelectionMode) {
                        LibraryDetailTabs(
                            tabs = tabLabels,
                            selectedIndex = pagerState.currentPage,
                            onSelected = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                        )
                    }
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        FieldSongsPageContent(
                            songs = if (page == 0) uiState.songsWithField else uiState.songsWithoutField,
                            isLoading = uiState.isLoading,
                            loadFailed = uiState.loadFailed,
                            isSelectionMode = isSelectionMode,
                            selectedSongUris = selectedSongUris,
                            swipeLabel = swipeLabel,
                            swipeSecondaryLabel = swipeSecondaryLabel,
                            // 顶部内边距已由外层 Column 消费，这里只补列表底部（多选 FAB 让位）。
                            padding = PaddingValues(bottom = if (isSelectionMode) 88.dp else 12.dp),
                            scrollConnection = scrollBehavior.nestedScrollConnection,
                            salt = true,
                            onRefresh = viewModel::refresh,
                            onOpenSong = { song -> navigator.navigate(EditMetadataDestination(songFileUri = song.uri)) },
                            onToggleSelection = { song -> selectionViewModel.toggleSelection(song.uri) },
                            onSwipeSelection = { song, pageSongs -> selectionViewModel.swipeSelect(song, pageSongs) },
                            onShowMenu = { song ->
                                selectedSong = song
                                showMenu = true
                            },
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxSize().blurSource(backdrop),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    FieldSongsPageContent(
                        songs = if (page == 0) uiState.songsWithField else uiState.songsWithoutField,
                        isLoading = uiState.isLoading,
                        loadFailed = uiState.loadFailed,
                        isSelectionMode = isSelectionMode,
                        selectedSongUris = selectedSongUris,
                        swipeLabel = swipeLabel,
                        swipeSecondaryLabel = swipeSecondaryLabel,
                        padding = scaffoldContentPadding(
                            paddingValues = padding,
                            bottomExtra = if (isSelectionMode) 88.dp else 12.dp,
                        ),
                        scrollConnection = scrollBehavior.nestedScrollConnection,
                        salt = false,
                        onRefresh = viewModel::refresh,
                        onOpenSong = { song -> navigator.navigate(EditMetadataDestination(songFileUri = song.uri)) },
                        onToggleSelection = { song -> selectionViewModel.toggleSelection(song.uri) },
                        onSwipeSelection = { song, pageSongs -> selectionViewModel.swipeSelect(song, pageSongs) },
                        onShowMenu = { song ->
                            selectedSong = song
                            showMenu = true
                        },
                    )
                }
            }
        }

        SongActionSheets(
            selectedSong = selectedSong,
            showMenuSheet = showMenu,
            showDetailSheet = showDetail,
            showDeleteDialog = showDelete,
            showRenameDialog = showRename,
            onDismissMenu = { showMenu = false },
            onDismissMenuFinished = {},
            onDismissDetail = { showDetail = false },
            onDismissDelete = { showDelete = false },
            onDismissRename = { showRename = false },
            onShowDetail = { showDetail = true },
            onShowDelete = { showDelete = true },
            onShowRename = { showRename = true },
            onPlay = { selectionViewModel.play(context, it) },
            onDelete = { showDelete = false; selectionViewModel.delete(it) },
            onRename = { song, name -> showRename = false; selectionViewModel.renameSong(song, name) },
        )
        SongBatchSelectionActions(
            navigator = navigator,
            songs = uiState.allSongs,
            show = isSelectionMode && !uiState.isLoading,
            expanded = expanded,
            selectedSongUris = selectedSongUris,
            onExpandedChange = { expanded = it },
            onSetSelectionUris = selectionViewModel::setSelectionUris,
            onBatchDelete = selectionViewModel::batchDelete,
            onBatchShare = selectionViewModel::batchShare,
        )
    }
}

/** 单页歌曲列表：两引擎共用加载/空/失败与多选逻辑，只换滚动与文案样式。 */
@Composable
private fun FieldSongsPageContent(
    songs: List<SongEntity>,
    isLoading: Boolean,
    loadFailed: Boolean,
    isSelectionMode: Boolean,
    selectedSongUris: Set<String>,
    swipeLabel: String,
    swipeSecondaryLabel: String?,
    padding: PaddingValues,
    scrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    salt: Boolean,
    onRefresh: () -> Unit,
    onOpenSong: (SongEntity) -> Unit,
    onToggleSelection: (SongEntity) -> Unit,
    onSwipeSelection: (SongEntity, List<SongEntity>) -> Unit,
    onShowMenu: (SongEntity) -> Unit,
) {
    val listState = rememberLazyListState()
    when {
        isLoading -> Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (salt) androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = SaltTheme.colors.highlight,
                trackColor = SaltTheme.colors.stroke,
            ) else CircularProgressIndicator(size = 32.dp)
        }

        songs.isEmpty() -> IllustratedEmptyState(
            kind = EmptyStateKind.Songs,
            title = stringResource(if (loadFailed) R.string.load_failed else R.string.edit_field_songs_empty),
            summary = if (loadFailed) stringResource(R.string.edit_field_songs_load_failed_hint) else null,
            icon = LucideIcons.RefreshCw,
            actionText = if (loadFailed) stringResource(R.string.edit_field_songs_retry) else null,
            onAction = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        )

        else -> LazyColumn(
            state = listState,
            modifier = (if (salt) Modifier else Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollConnection))
                .fillMaxSize(),
            contentPadding = padding,
            overscrollEffect = if (salt) rememberOverscrollEffect() else null,
        ) {
            if (loadFailed) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (salt) com.moriafly.salt.ui.Text(
                            text = stringResource(R.string.edit_field_songs_load_failed_hint),
                            style = SaltTheme.textStyles.sub,
                            color = SaltTheme.colors.subText,
                            modifier = Modifier.weight(1f),
                        ) else Text(
                            text = stringResource(R.string.edit_field_songs_load_failed_hint),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        if (salt) Button(
                            onClick = onRefresh,
                            text = stringResource(R.string.edit_field_songs_retry),
                            appearance = ButtonAppearance.Plain,
                        ) else TextButton(
                            text = stringResource(R.string.edit_field_songs_retry),
                            onClick = onRefresh,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
            items(songs, key = { it.id }) { song ->
                SelectableLibraryItem(
                    itemKey = song.uri,
                    modifier = Modifier.animateItem(),
                    isSelectionMode = isSelectionMode,
                    isSelected = song.uri in selectedSongUris,
                    swipeSelectionLabel = swipeLabel,
                    swipeSelectionSecondaryLabel = swipeSecondaryLabel,
                    onClick = { onOpenSong(song) },
                    onToggleSelection = { onToggleSelection(song) },
                    onSwipeSelection = { onSwipeSelection(song, songs) }
                ) {
                    SongListItemContent(
                        song = song,
                        trailingContent = {
                            Box(Modifier.padding(end = 8.dp)) {
                                SongListItemActions(
                                    isSelectionMode = isSelectionMode,
                                    isSelected = song.uri in selectedSongUris,
                                    onToggleSelection = { onToggleSelection(song) },
                                    onShowMenu = { onShowMenu(song) },
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
