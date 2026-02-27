package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.dialog.BatchMatchConfigDialog
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.BottomBar
import com.moriafly.salt.ui.BottomBarItem
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonType
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemArrowType
import com.moriafly.salt.ui.ItemDivider
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
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
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
    val selectedPaths by viewModel.selectedSongIds.collectAsState()
    var sortOrderDropdownExpanded by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val sheetUiState by viewModel.sheetState.collectAsStateWithLifecycle()

    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sectionIndexMap = remember(songs, sortInfo) {
        val map = mutableMapOf<String, Int>()
        if (sortInfo.sortBy == SortBy.TITLE || sortInfo.sortBy == SortBy.ARTISTS) {
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

    BackHandler(enabled = isSelectionMode) {
        viewModel.exitSelectionMode()
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            if (isSelectionMode) {
                SelectionModeTopAppBar(
                    selectedCount = selectedPaths.size,
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
                            Text(text = stringResource(id = R.string.cancel), color = SaltTheme.colors.highlight)
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
                                    navigator.navigate(LocalSearchDestination())
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
                                val sortTypes = listOf(
                                    SortBy.TITLE,
                                    SortBy.ARTISTS,
                                    SortBy.DATE_MODIFIED,
                                    SortBy.DATE_ADDED
                                )

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
                            }
                        }
                    },
                )
            }

        },
        bottomBar = {
            if (isSelectionMode) {
                val hasSelection = selectedPaths.isNotEmpty()
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
                }
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                overscrollEffect = rememberCupertinoOverscrollEffect(allowTopOverscroll = false)
            ) {
                items(
                    items = songs,
                    key = { song -> song.mediaId }
                ) { song ->
                    SongListItem(
                        song = song,
                        navigator = navigator,
                        modifier = Modifier.animateItem(),
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedPaths.contains(song.mediaId),
                        onToggleSelection = { viewModel.toggleSelection(song.mediaId) },
                        trailingContent = {
                            if (!isSelectionMode) {
                                IconButton(onClick = { viewModel.showMenu(song) }) {
                                    Icon(painterResource(R.drawable.ic_info_24dp), "Info")
                                }
                            } else {
                                IconButton(onClick = { viewModel.toggleSelection(song.mediaId) }) {
                                    Icon(
                                        imageVector = if (selectedPaths.contains(song.mediaId)) SaltIcons.Check else SaltIcons.Uncheck,
                                        contentDescription = null,
                                        tint = if (selectedPaths.contains(song.mediaId)) SaltTheme.colors.highlight else SaltTheme.colors.text
                                    )
                                }
                            }
                        }
                    )
                    ItemDivider()
                }
            }
            if (sections.isNotEmpty() && sortInfo.sortBy.supportsIndex) {
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
                            Intent.createChooser(intent, context.getString(R.string.share_chooser_title))
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
                SongDetailBottomSheetContent(song = song)
            }
        }



        if (uiState.showDeleteDialog && sheetUiState.menuSong != null) {
            YesNoDialog(
                onDismissRequest = {viewModel.dismissDeleteDialog()},
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
                    selectedPaths.size
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

@Composable
fun BatchMatchingDialog(
    currentFile: String,
    progress: Pair<Int, Int>?,
    successCount: Int,
    skippedCount: Int,
    failureCount: Int,
    isMatching: Boolean,
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
                Text(
                    text = stringResource(R.string.batch_matching_title),
                    style = SaltTheme.textStyles.main
                )

                // 进度信息
                progress?.let { (current, total) ->
                    Text(
                        text = stringResource(R.string.batch_matching_progress, current, total),
                        style = SaltTheme.textStyles.main
                    )
                }

                // 当前文件
                if (currentFile.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.batch_matching_current, currentFile),
                        style = SaltTheme.textStyles.main
                    )
                }

                // 成功/失败计数
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

                // 总用时显示（仅当任务完成时显示）
                if (!isMatching && batchTimeMillis > 0) {
                    val seconds = batchTimeMillis / 1000.0
                    Text(
                        text = stringResource(R.string.batch_matching_total_time, seconds),
                        style = SaltTheme.textStyles.main
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 操作按钮
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (isMatching) onAbort else onClose,
                    text = if (isMatching) stringResource(R.string.action_abort) else stringResource(R.string.action_close),
                    type = if (isMatching) ButtonType.Sub else ButtonType.Highlight
                )
                if (!isMatching) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_view_results),
                        onClick = {
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
                        navigator.navigate(EditMetadataDestination(songFilePath = song.filePath))
                    }
                },
                onLongClick = {
                    isSelectionMode?.let {
                        if (!it) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onToggleSelection?.let { it1 -> it1() }
                        }
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
                        text = song.artist.takeIf { !it.isNullOrBlank() } ?: stringResource(R.string.unknown_artist),
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
            ItemTip(
                text = stringResource(
                    R.string.menu_song_tip_format,
                    song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    song.artist.takeIf { !it.isNullOrBlank() } ?: stringResource(R.string.unknown_artist)
                )
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
fun SongDetailBottomSheetContent(song: SongEntity) {
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
                        text = song.artist.takeIf { !it.isNullOrBlank() } ?: stringResource(R.string.unknown_artist),
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.highlight
                    )
                }
            }
        }


        item { SongDetailItem(label = stringResource(R.string.label_album), value = song.album) }
        item { SongDetailItem(label = stringResource(R.string.label_date), value = song.date) }
        item { SongDetailItem(label = stringResource(R.string.label_genre), value = song.genre) }
        item { SongDetailItem(label = stringResource(R.string.label_track_number), value = song.trackerNumber) }


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