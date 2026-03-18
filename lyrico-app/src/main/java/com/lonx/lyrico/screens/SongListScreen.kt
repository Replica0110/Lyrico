package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.dialog.BatchMatchConfigDialog
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonType
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.gestures.cupertino.rememberCupertinoOverscrollEffect
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import com.moriafly.salt.ui.noRippleClickable
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SECTIONS_ASC = listOf(
    "0"
) + ('A'..'Z').map { it.toString() } + listOf("#")

private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class, UnstableSaltUiApi::class
)
@Composable
@Destination<RootGraph>(start = true, route = "song_list")
fun SongListScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SongListViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val sortInfo by viewModel.sortInfo.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState(initial = false)
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val showScrollTopButton by viewModel.showScrollTopButton.collectAsStateWithLifecycle()
    var sortOrderDropdownExpanded by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val sheetUiState by viewModel.sheetState.collectAsStateWithLifecycle()

    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showFab by remember {
        derivedStateOf {
            showScrollTopButton && listState.firstVisibleItemIndex > 0
        }
    }
    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
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
    val enableIndex = sections.isNotEmpty() && sortInfo.sortBy.supportsIndex
    var isSearchMode by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoScrollSpeed) {
        if (autoScrollSpeed != 0f) {
            while (isActive) {
                listState.scrollBy(autoScrollSpeed)
                delay(16) // 大约 60 帧的刷新率
            }
        }
    }
    val dragSelectionModifier = if (isSelectionMode) {
        Modifier.pointerInput(songs, isSelectionMode) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    // 找出手指长按处的 Item
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find {
                        offset.y >= it.offset && offset.y <= (it.offset + it.size)
                    }
                    itemInfo?.let {
                        initialDragIndex = it.index
                        currentDragIndex = it.index
                        viewModel.startDragSelection(it.index, songs)
                    }
                },
                onDrag = { change, _ ->
                    val y = change.position.y
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val topThreshold = 150f // 距离顶部多少像素开始向上滚动
                    val bottomThreshold = viewportHeight - 150f

                    // 计算自动滚动速度
                    autoScrollSpeed = when {
                        y < topThreshold -> - (topThreshold - y) * 0.2f
                        y > bottomThreshold -> (y - bottomThreshold) * 0.2f
                        else -> 0f
                    }

                    // 更新滑动经过的区间
                    val itemInfo = listState.layoutInfo.visibleItemsInfo.find {
                        y >= it.offset && y <= (it.offset + it.size)
                    }
                    if (itemInfo != null && initialDragIndex != null) {
                        if (itemInfo.index != currentDragIndex) {
                            currentDragIndex = itemInfo.index
                            viewModel.updateDragSelection(initialDragIndex!!, currentDragIndex!!, songs)
                        }
                    }
                },
                onDragEnd = {
                    initialDragIndex = null
                    currentDragIndex = null
                    autoScrollSpeed = 0f
                    viewModel.endDragSelection()
                },
                onDragCancel = {
                    initialDragIndex = null
                    currentDragIndex = null
                    autoScrollSpeed = 0f
                    viewModel.endDragSelection()
                }
            )
        }
    } else {
        Modifier
    }
    BackHandler(enabled = isSelectionMode || isSearchMode) {
        if (isSelectionMode) {
            viewModel.exitSelectionMode()
        } else if (isSearchMode) {
            isSearchMode = false
            viewModel.clearSearch() // 清空搜索并恢复全列表
        }
    }
    Box {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background),
            floatingActionButton = {
                AnimatedVisibility(
                    visible = showFab,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 24.dp,
                            // 如果处于选择模式，底部会有 NavigationBar，需要增加底部边距以避让
                            bottom = if (isSelectionMode) 96.dp else 24.dp
                        ),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        shape = CircleShape,
                        containerColor = SaltTheme.colors.highlight,
                        contentColor = SaltTheme.colors.background,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_up_24dp),
                            contentDescription = stringResource(R.string.cd_sort),
                            tint = SaltTheme.colors.onHighlight
                        )
                    }
                }
            },
            bottomBar = {
                if(isSelectionMode) {
                    val hasSelection = selectedSongIds.isNotEmpty()
                    NavigationBar(
                        containerColor = SaltTheme.colors.background
                    ) {
                        NavigationBarItem(
                            selected = false,
                            enabled = hasSelection,
                            onClick = { viewModel.batchShare(context, songs) },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_share_24dp),
                                    contentDescription = stringResource(R.string.action_share),
                                    tint = if (hasSelection) SaltTheme.colors.highlight else SaltTheme.colors.subText
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.action_share),
                                    color = if (hasSelection) SaltTheme.colors.text else SaltTheme.colors.subText,
                                    fontSize = 12.sp
                                )
                            }
                        )
                        NavigationBarItem(
                            selected = false,
                            enabled = hasSelection,
                            onClick = { viewModel.showBatchDeleteDialog() },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = if (hasSelection) SaltTheme.colors.highlight else SaltTheme.colors.subText
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.action_delete),
                                    color = if (hasSelection) SaltTheme.colors.text else SaltTheme.colors.subText,
                                    fontSize = 12.sp
                                )
                            }
                        )
                        NavigationBarItem(
                            selected = false,
                            enabled = hasSelection,
                            onClick = { viewModel.openBatchMatchConfig() },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_match_24dp),
                                    contentDescription = stringResource(R.string.action_batch_match),
                                    tint = if (hasSelection) SaltTheme.colors.highlight else SaltTheme.colors.subText
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.action_batch_match),
                                    color = if (hasSelection) SaltTheme.colors.text else SaltTheme.colors.subText,
                                    fontSize = 12.sp
                                )
                            }
                        )
                        NavigationBarItem(
                            selected = false,
                            enabled = hasSelection,
                            onClick = {
                                val selectedSongs = songs.filter { selectedSongIds.contains(it.mediaId) }
                                if (selectedSongs.isNotEmpty()) {
                                    val filePaths = selectedSongs.map { it.filePath }.toTypedArray()
                                    navigator.navigate(BatchRenameDestination(filePaths = filePaths))
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_rename_24dp),
                                    contentDescription = "Batch Rename",
                                    tint = if (hasSelection) SaltTheme.colors.highlight else SaltTheme.colors.subText
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(R.string.action_batch_rename),
                                    color = if (hasSelection) SaltTheme.colors.text else SaltTheme.colors.subText,
                                    fontSize = 12.sp
                                )
                            }
                        )
                    }
                }
            },
            topBar = {
                if (isSelectionMode) {
                    SelectionModeTopAppBar(
                        selectedCount = selectedSongIds.size,
                        actions = {
                            val allSelected = viewModel.isAllSelected(songs)
                            TextButton(
                                onClick = {
                                    if (allSelected) {
                                        viewModel.deselectAll()
                                    } else {
                                        viewModel.selectAll(songs)
                                    }
                                }
                            ) {
                                Text(
                                    text = stringResource(
                                        if (allSelected) R.string.action_deselect_all
                                        else R.string.action_select_all
                                    ),
                                    color = SaltTheme.colors.highlight
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.exitSelectionMode()
                                }
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    color = SaltTheme.colors.highlight
                                )
                            }
                        }
                    )
                } else if (isSearchMode) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = SaltTheme.colors.background,
                            navigationIconContentColor = SaltTheme.colors.text,
                            actionIconContentColor = SaltTheme.colors.text
                        ),
                        title = {
                            SearchBar(
                                value = uiState.searchQuery,
                                onValueChange = {
                                    viewModel.onSearchQueryChanged(it)
                                },
                                placeholder = stringResource(R.string.local_search_hint),
                                modifier = Modifier
                                    .focusRequester(focusRequester)
                            )

                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    isSearchMode = false
                                    viewModel.clearSearch()
                                }
                            ) {
                                Text(
                                    text = stringResource(R.string.cancel),
                                    color = SaltTheme.colors.highlight
                                )
                            }
                        }
                    )
                } else {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarColors(
                            containerColor = SaltTheme.colors.background,
                            scrolledContainerColor = SaltTheme.colors.background,
                            navigationIconContentColor = SaltTheme.colors.text,
                            titleContentColor = SaltTheme.colors.text,
                            actionIconContentColor = SaltTheme.colors.text,
                            subtitleContentColor = SaltTheme.colors.subText
                        ),
                        title = {
                            Text(
                                text = stringResource(R.string.song_list_title, songs.size),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings_24dp),
                                contentDescription = stringResource(R.string.cd_settings),
                                tint = SaltTheme.colors.text,
                                modifier = Modifier
                                    .size(48.dp)
                                    .noRippleClickable(role = Role.Button) {
                                        navigator.navigate(SettingsDestination())
                                    }
                                    .padding(12.dp)
                            )
                        },
                        actions = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.cd_search),
                                tint = SaltTheme.colors.text,
                                modifier = Modifier
                                    .size(48.dp)
                                    .noRippleClickable(role = Role.Button) {
                                        isSearchMode = true
                                    }
                                    .padding(12.dp)
                            )
                            Box(modifier = Modifier.wrapContentSize()) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sort_24dp),
                                    contentDescription = stringResource(R.string.cd_sort),
                                    tint = SaltTheme.colors.text,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .noRippleClickable(role = Role.Button) {
                                            sortOrderDropdownExpanded = true
                                        }
                                        .padding(8.dp)
                                )

                                PopupMenu(
                                    expanded = sortOrderDropdownExpanded,
                                    onDismissRequest = { sortOrderDropdownExpanded = false }
                                ) {
                                    val sortTypes = SortBy.entries.toList()
                                    sortTypes.forEach { type ->
                                        val isSelected = sortInfo.sortBy == type
                                        PopupMenuItem(
                                            text = stringResource(type.labelRes),
                                            selected = isSelected,
                                            iconPainter = if (isSelected) {
                                                if (sortInfo.order == SortOrder.ASC) {
                                                    painterResource(R.drawable.ic_arrow_down_24dp)
                                                } else {
                                                    painterResource(R.drawable.ic_arrow_up_24dp)
                                                }
                                            } else null,
                                            iconPaddingValues = PaddingValues(2.dp),
                                            onClick = {
                                                val newOrder = if (isSelected) {
                                                    if (sortInfo.order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                                                } else {
                                                    SortOrder.ASC
                                                }
                                                viewModel.onSortChange(SortInfo(type, newOrder))
                                            }
                                        )
                                    }
                                    ItemDivider()
                                    ItemSwitcher(
                                        text = stringResource(R.string.show_scroll_top_button),
                                        state = showScrollTopButton,
                                        sub = stringResource(R.string.show_scroll_top_button_hint),
                                        onChange = {
                                            viewModel.setScrollToTopButtonEnabled(!showScrollTopButton)
                                        }
                                    )
                                }
                            }
                        },
                    )
                }

            }
        ) { paddingValues ->
            PullToRefreshBox(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaltTheme.colors.background)
                    .padding(paddingValues),
                isRefreshing = uiState.isLoading,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier.align(Alignment.TopCenter),
                        isRefreshing = uiState.isLoading,
                        state = pullToRefreshState,
                        color = SaltTheme.colors.highlight,
                        containerColor = SaltTheme.colors.background
                    )
                },
                onRefresh = {
                    viewModel.refreshSongs()
                }
            ) {
                LazyColumnScrollbar(
                    state = listState,
                    settings = ScrollbarSettings.Default.copy(
                        enabled = !enableIndex,
                        alwaysShowScrollbar = !enableIndex,
                        selectionMode = ScrollbarSelectionMode.Full,
                        thumbUnselectedColor = SaltTheme.colors.subText,
                        thumbSelectedColor = SaltTheme.colors.subText
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(dragSelectionModifier),
                        state = listState,
                        overscrollEffect = rememberCupertinoOverscrollEffect(allowTopOverscroll = false)
                    ) {
                        itemsIndexed(
                            items = songs,
                            key = { _, song -> song.mediaId }
                        ) { index, song ->
                            SongListItem(
                                song = song,
                                navigator = navigator,
                                modifier = Modifier.animateItem(),
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedSongIds.contains(song.mediaId),
                                onToggleSelection = { viewModel.toggleSelection(song) },
                                trailingContent = {
                                    if (!isSelectionMode) {
                                        IconButton(onClick = { viewModel.showMenu(song) }) {
                                            Icon(
                                                painterResource(R.drawable.ic_info_24dp),
                                                "Info"
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.toggleSelection(song) }) {
                                            Icon(
                                                imageVector = if (selectedSongIds.containsKey(song.mediaId)) SaltIcons.Check else SaltIcons.Uncheck,
                                                contentDescription = null,
                                                tint = if (selectedSongIds.contains(song.mediaId)) SaltTheme.colors.highlight else SaltTheme.colors.text
                                            )
                                        }
                                    }
                                }
                            )
                            ItemDivider()
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
                        scope.launch {
                            listState.scrollToItem(index)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                )
            }
            sheetUiState.menuSong?.let { song ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissAll() },
                    sheetState = menuSheetState,
                    containerColor = SaltTheme.colors.background,
                    tonalElevation = 0.dp,
                    contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
                ) {
                    SongMenuBottomSheetContent(
                        song = song,
                        onPlay = {
                            viewModel.play(context, song)
                        },
                        showInfo = {
                            viewModel.showDetail(song)
                        },
                        onDelete = {
                            viewModel.showDeleteDialog()
                        },
                        onShare = {
                            val uri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                song.mediaId
                            )

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TITLE, song.title ?: song.fileName)
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
            sheetUiState.detailSong?.let { song ->
                ModalBottomSheet(
                    onDismissRequest = { viewModel.dismissDetail() },
                    sheetState = detailSheetState,
                    dragHandle = null,
                    containerColor = SaltTheme.colors.background,
                    tonalElevation = 0.dp,
                    contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
                ) {
                    SongDetailBottomSheetContent(context = context, song = song)
                }
            }



            if (uiState.showDeleteDialog && sheetUiState.menuSong != null) {
                YesNoDialog(
                    onDismissRequest = { viewModel.dismissDeleteDialog() },
                    onConfirm = {
                        viewModel.dismissDeleteDialog()
                        viewModel.dismissAll()
                        viewModel.delete(sheetUiState.menuSong!!)
                    },
                    title = stringResource(R.string.dialog_delete_file_title),
                    content = stringResource(
                        R.string.dialog_delete_file_content,
                        sheetUiState.menuSong!!.fileName
                    ),
                    cancelText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.confirm)
                )
            }
            // 批量删除确认对话框
            if (uiState.showBatchDeleteDialog) {
                YesNoDialog(
                    onDismissRequest = { viewModel.dismissBatchDeleteDialog() },
                    onConfirm = {
                        viewModel.dismissBatchDeleteDialog()
                        viewModel.batchDelete(songs)
                    },
                    title = stringResource(R.string.dialog_batch_delete_title),
                    content = stringResource(
                        R.string.dialog_batch_delete_content,
                        selectedSongIds.size
                    ),
                    cancelText = stringResource(R.string.cancel),
                    confirmText = stringResource(R.string.confirm)
                )
            }
            // 批量匹配配置对话框
            if (uiState.showBatchConfigDialog) {
                BatchMatchConfigDialog(
                    onDismissRequest = { viewModel.closeBatchMatchConfig() },
                    onConfirm = { config -> viewModel.batchMatch(config) }
                )
            }

            // 批量匹配进度对话框
            if (uiState.isBatchMatching || uiState.batchProgress != null) {
                BatchMatchingDialog(
                    currentFile = uiState.currentFile,
                    progress = uiState.batchProgress,
                    successCount = uiState.successCount,
                    failureCount = uiState.failureCount,
                    skippedCount = uiState.skippedCount,
                    isSaving = uiState.isSaving,
                    isMatching = uiState.isBatchMatching,
                    batchTimeMillis = uiState.batchTimeMillis,
                    onAbort = { viewModel.abortBatchMatch() },
                    historyId = uiState.batchHistoryId,
                    navigator = navigator,
                    onClose = { viewModel.closeBatchMatchDialog() }
                )
            }
        }
    }
}
@OptIn(UnstableSaltUiApi::class)
@Composable
private fun SongListContent(
    songs: List<SongEntity>,
    listState: LazyListState,
    navigator: DestinationsNavigator,
    isSelectionMode: Boolean,
    selectedSongIds: Set<Long>,
    modifier: Modifier,
    viewModel: SongListViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        state = listState,
        overscrollEffect = rememberCupertinoOverscrollEffect(allowTopOverscroll = false)
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.mediaId }
        ) { _, song ->
            SongListItem(
                song = song,
                navigator = navigator,
                modifier = Modifier.animateItem(),
                isSelectionMode = isSelectionMode,
                isSelected = selectedSongIds.contains(song.mediaId),
                onToggleSelection = { viewModel.toggleSelection(song) },
                trailingContent = {
                    if (!isSelectionMode) {
                        IconButton(onClick = { viewModel.showMenu(song) }) {
                            Icon(painterResource(R.drawable.ic_info_24dp), "Info")
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSelection(song) }) {
                            Icon(
                                imageVector = if (selectedSongIds.contains(song.mediaId))
                                    SaltIcons.Check else SaltIcons.Uncheck,
                                contentDescription = null,
                                tint = if (selectedSongIds.contains(song.mediaId))
                                    SaltTheme.colors.highlight else SaltTheme.colors.text
                            )
                        }
                    }
                }
            )
            ItemDivider()
        }
    }
}
@Composable
fun BatchMatchingDialog(
    currentFile: String,
    progress: Pair<Int, Int>?,
    successCount: Int,
    skippedCount: Int,
    failureCount: Int,
    isMatching: Boolean,
    isSaving: Boolean,
    batchTimeMillis: Long,
    onAbort: () -> Unit,
    onClose: () -> Unit,
    historyId: Long,
    navigator: DestinationsNavigator
) {
    BasicDialog(
        onDismissRequest = { if (!isMatching) onClose() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题或加载信息
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    progress?.let { (current, total) ->
                        val progress =
                            if (total > 0) current.toFloat() / total.toFloat() else 0f

                        // 进度文字
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = if (isMatching) {
                                    currentFile
                                } else if(isSaving) {
                                    stringResource(R.string.message_saving_tags)
                                }
                                else
                                    stringResource(
                                        R.string.batch_matching_total_time,
                                        batchTimeMillis / 1000.0
                                    ),
                                style = SaltTheme.textStyles.main,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "$current / $total",
                                style = SaltTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = SaltTheme.colors.highlight,
                            trackColor = SaltTheme.colors.subBackground,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                    }
                }


                // 成功/失败计数
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.batch_matching_success, successCount),
                        style = SaltTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(R.string.batch_matching_skipped, skippedCount),
                        style = SaltTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(R.string.batch_matching_failure, failureCount),
                        style = SaltTheme.textStyles.main
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 操作按钮
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (isMatching) onAbort else onClose,
                    text = if (isMatching) stringResource(R.string.action_abort) else stringResource(
                        R.string.action_close
                    ),
                    type = if (isMatching) ButtonType.Sub else ButtonType.Highlight
                )
                if (!isMatching) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_view_results),
                        onClick = {
                            onClose()
                            navigator.navigate(BatchMatchHistoryDetailDestination(historyId))
                        },
                        type = ButtonType.Highlight
                    )
                }
            }
        }
    )
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
        // 找第一个 >= section
        keys.firstOrNull { it >= section }
            ?.let { sectionIndexMap[it] }
            ?: sectionIndexMap[keys.last()]!!
    } else {
        // DESC：找第一个 <= section
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

    // 计算索引的辅助函数
    fun getSectionIndex(offsetY: Float): Int {
        if (componentHeight == 0 || sections.isEmpty()) return -1
        val step = componentHeight.toFloat() / sections.size
        return (offsetY / step).toInt().coerceIn(0, sections.lastIndex)
    }

    // 更新选中状态和回调的辅助函数
    fun updateSelection(index: Int) {
        if (index != -1) {
            val section = sections[index]
            currentSection = section // 更新气泡显示内容
            if (index != lastSelectedIndex) {
                lastSelectedIndex = index
                onSectionSelected(section)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    // 使用 Row 将气泡和索引栏水平排列
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
                        color = SaltTheme.colors.highlight,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentSection ?: "",
                    style = SaltTheme.textStyles.largeTitle,
                    color = SaltTheme.colors.background,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .width(24.dp)
                .onGloballyPositioned { componentHeight = it.size.height }
                // 拖拽手势
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
                        onTap = {
                        }
                    )
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            sections.forEach { section ->
                Text(
                    text = section,
                    style = SaltTheme.textStyles.sub.copy(fontSize = 12.sp),
                    color = if (currentSection == section) SaltTheme.colors.highlight else SaltTheme.colors.subText,
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
        if (isSelected == true) SaltTheme.colors.highlight.copy(alpha = 0.1f) else SaltTheme.colors.background
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
                .padding(horizontal = 16.dp),
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

                val formatGradientColor = if (SaltTheme.configs.isDarkTheme) {
                    Color.White.copy(alpha = 0.7f)  // 深色模式改为白底
                } else {
                    Color.Black.copy(alpha = 0.7f)  // 浅色模式改为黑底
                }
                val formatTextColor = if (SaltTheme.configs.isDarkTheme) {
                    Color.Black  // 深色模式改为黑字
                } else {
                    Color.White  // 浅色模式改为白字
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, formatGradientColor),
                            )
                        )
                ) {
                    Text(
                        text = song.fileName.substringAfterLast('.', "").uppercase(),
                        color = formatTextColor,
                        fontSize = 8.sp, // 字体缩小
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 1.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp) // 紧凑行间距
            ) {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    fontWeight = FontWeight.Medium, // 稍微降低字重以显得清秀
                    fontSize = 15.sp,
                    color = SaltTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 歌手
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() }
                            ?: stringResource(R.string.unknown_artist),
                        color = SaltTheme.colors.subText,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = " - ${song.album}",
                            color = SaltTheme.colors.subText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }

            // 右侧信息列 (时长 + 音质)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // 时长
                if (song.durationMilliseconds > 0) {
                    val minutes = song.durationMilliseconds / 60000
                    val seconds = (song.durationMilliseconds % 60000) / 1000
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = SaltTheme.colors.subText,
                        fontSize = 12.sp
                    )
                }

                // 音质信息
                if (song.bitrate > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.bitrate}kbps",
                        fontSize = 10.sp,
                        color = LyricoColors.secondaryText,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
            trailingContent?.let {
                Box(
                    modifier = Modifier
                        .size(36.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // 操作列表
        RoundedColumn {
            val songTitle = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName
            val text = song.artist.takeIf { !it.isNullOrBlank() }?.let { "$songTitle - $it" } ?: songTitle
            ItemTip(
                text = text
            )
            Item(
                onClick = { onPlay() },
                text = stringResource(R.string.menu_action_play),
                sub = stringResource(R.string.menu_action_play_sub),
                arrowType = ItemArrowType.None
            )
            Item(
                onClick = { showInfo() },
                text = stringResource(R.string.menu_action_info),
                arrowType = ItemArrowType.None
            )
            Item(
                onClick = { onShare() },
                text = stringResource(R.string.menu_action_share),
                arrowType = ItemArrowType.None
            )
            Item(
                onClick = { onDelete() },
                text = stringResource(R.string.menu_action_delete),
                sub = stringResource(R.string.menu_action_delete_sub),
                textColor = Color.Red,
                arrowType = ItemArrowType.None
            )
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SongDetailBottomSheetContent(context: Context, song: SongEntity) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp) // 底部留白
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(0.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.size(100.dp)
                ) {
                    AsyncImage(
                        model = CoverRequest(song.getUri, song.fileLastModified),
                        contentDescription = stringResource(R.string.cd_cover),
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
                }

                // 标题和艺术家
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                        style = SaltTheme.textStyles.main,
                        fontWeight = FontWeight.Bold,
                        color = SaltTheme.colors.text
                    )
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() }
                            ?: stringResource(R.string.unknown_artist),
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.highlight
                    )
                }
            }
        }


        item { SongDetailItem(label = stringResource(R.string.label_album), value = song.album) }
        item { SongDetailItem(label = stringResource(R.string.label_date), value = song.date) }
        item { SongDetailItem(label = stringResource(R.string.label_genre), value = song.genre) }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_track_number),
                value = song.trackerNumber
            )
        }


        item {
            SongDetailItem(
                label = stringResource(R.string.label_duration),
                value = if (song.durationMilliseconds > 0) {
                    val min = song.durationMilliseconds / 60000
                    val sec = (song.durationMilliseconds % 60000) / 1000
                    String.format("%d:%02d", min, sec)
                } else null
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_bitrate),
                value = if (song.bitrate > 0) "${song.bitrate} kbps" else null
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_sample_rate),
                value = if (song.sampleRate > 0) "${song.sampleRate} Hz" else null
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_channels),
                value = if (song.channels > 0) "${song.channels}" else null
            )
        }

        item {
            SongDetailItem(
                label = stringResource(R.string.label_date_added),
                value = if (song.fileAdded > 0) dateFormat.format(Date(song.fileAdded)) else null
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_date_modified),
                value = if (song.fileLastModified > 0) dateFormat.format(Date(song.fileLastModified)) else null
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_file_path),
                value = song.filePath
            )
        }
        item {
            SongDetailItem(
                label = stringResource(R.string.label_file_size),
                value = if (song.fileSize > 0)
                    Formatter.formatFileSize(context, song.fileSize)
                else null
            )
        }
        if (BuildConfig.DEBUG){
            item {
                SongDetailItem(
                    label = "文件URI",
                    value = song.uri
                )
            }
            item {
                SongDetailItem(
                    label = "文件ID",
                    value = song.id.toString()
                )
            }
            item {
                SongDetailItem(
                    label = "文件名",
                    value = song.fileName
                )
            }
        }

    }
}

@Composable
fun SongDetailItem(label: String, value: String?) {
    if (value.isNullOrBlank()) return

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = label,
            style = SaltTheme.textStyles.sub
        )
        Text(
            text = value,
            style = SaltTheme.textStyles.main,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionModeTopAppBar(
    selectedCount: Int,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SaltTheme.colors.background
        ),

        title = {
            Text(
                text = stringResource(R.string.selection_mode_selected_count, selectedCount),
                style = SaltTheme.textStyles.main,
                color = SaltTheme.colors.text,
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            actions()
        }
    )
}