package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.data.model.getUri
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.Button
import com.moriafly.salt.ui.ButtonType
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.BasicDialog
import com.moriafly.salt.ui.gestures.cupertino.rememberCupertinoOverscrollEffect
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import com.moriafly.salt.ui.noRippleClickable
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sectionIndexMap = remember(songs, sortInfo) {
        val map = mutableMapOf<String, Int>()
        if (sortInfo.sortBy == SortBy.TITLE || sortInfo.sortBy == SortBy.ARTIST) {
            songs.forEachIndexed { index, song ->
                val key =
                    if (sortInfo.sortBy == SortBy.ARTIST) song.artistGroupKey else song.titleGroupKey
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
                        TextButton(
                            onClick = {
                                viewModel.selectAll(songs)
                            }
                        ) {
                            Text(text = "全选", color = SaltTheme.colors.highlight)
                        }
                        TextButton(
                            enabled = selectedPaths.isNotEmpty(),
                            onClick = {
                                viewModel.openBatchMatchConfig()
                            }
                        ) {
                            Text(
                                text = "匹配标签",
                                color = if (selectedPaths.isNotEmpty()) SaltTheme.colors.highlight else SaltTheme.colors.subText
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.exitSelectionMode()
                            }
                        ) {
                            Text(text = "取消", color = SaltTheme.colors.highlight)
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
                            text = "歌曲(${songs.size}首)",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings_24dp),
                            contentDescription = "Settings",
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
                            contentDescription = "Search",
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
                                contentDescription = "Sort",
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
                                    SortBy.ARTIST,
                                    SortBy.DATE_MODIFIED,
                                    SortBy.DATE_ADDED
                                )

                                sortTypes.forEach { type ->
                                    val isSelected = sortInfo.sortBy == type
                                    PopupMenuItem(
                                        text = type.displayName,
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
                                IconButton(onClick = { viewModel.selectedSong(song) }) {
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
        uiState.selectedSongs?.let { selectedSongs ->
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelectedSong() },
                sheetState = sheetState,
                containerColor = SaltTheme.colors.background,
                tonalElevation = 0.dp,
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
            ) {
                SongDetailBottomSheetContent(selectedSongs)
            }
        }

        // 批量匹配配置对话框
        if (uiState.showBatchConfigDialog) {
            com.lonx.lyrico.ui.dialog.BatchMatchConfigDialog(
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
                isMatching = uiState.isBatchMatching,
                loadingMessage = uiState.loadingMessage,
                batchTimeMillis = uiState.batchTimeMillis,
                onAbort = { viewModel.abortBatchMatch() },
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
    failureCount: Int,
    isMatching: Boolean,
    loadingMessage: String,
    batchTimeMillis: Long,
    onAbort: () -> Unit,
    onClose: () -> Unit
) {
    BasicDialog(
        onDismissRequest = { if (!isMatching) onClose() },
        properties = androidx.compose.ui.window.DialogProperties(
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
                    text = if (isMatching) "批量匹配中" else loadingMessage,
                    style = SaltTheme.textStyles.main
                )

                // 进度信息
                progress?.let { (current, total) ->
                    Text(
                        text = "进度: $current / $total",
                        style = SaltTheme.textStyles.main
                    )
                }

                // 当前文件
                if (currentFile.isNotEmpty()) {
                    Text(
                        text = "正在匹配: $currentFile",
                        style = SaltTheme.textStyles.main
                    )
                }

                // 成功/失败计数
                Text(
                    text = "成功: $successCount",
                    style = SaltTheme.textStyles.main
                )
                Text(
                    text = "失败: $failureCount",
                    style = SaltTheme.textStyles.main
                )

                // 总用时显示（仅当任务完成时显示）
                if (!isMatching && batchTimeMillis > 0) {
                    val seconds = batchTimeMillis / 1000.0
                    Text(
                        text = "总用时: %.2f 秒".format(seconds),
                        style = SaltTheme.textStyles.main
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 操作按钮
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (isMatching) onAbort else onClose,
                    text = if (isMatching) "中止" else "关闭",
                    type = if (isMatching) ButtonType.Sub else ButtonType.Highlight
                )
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
        androidx.compose.animation.AnimatedVisibility(
            visible = currentSection != null,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(50.dp)
                    .background(
                        color = SaltTheme.colors.highlight,
                        shape = androidx.compose.foundation.shape.CircleShape
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
                        text = song.artist.takeIf { !it.isNullOrBlank() } ?: "未知艺术家",
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
                        contentDescription = "Cover",
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
                        text = song.artist.takeIf { !it.isNullOrBlank() } ?: "未知艺术家",
                        style = SaltTheme.textStyles.sub,
                        color = SaltTheme.colors.highlight
                    )
                }
            }
        }


        item { SongDetailItem(label = "专辑", value = song.album) }
        item { SongDetailItem(label = "年份/日期", value = song.date) }
        item { SongDetailItem(label = "流派", value = song.genre) }
        item { SongDetailItem(label = "音轨号", value = song.trackerNumber) }


        item {
            SongDetailItem(
                label = "时长",
                value = if (song.durationMilliseconds > 0) {
                    val min = song.durationMilliseconds / 60000
                    val sec = (song.durationMilliseconds % 60000) / 1000
                    String.format("%d:%02d", min, sec)
                } else null
            )
        }
        item {
            SongDetailItem(
                label = "比特率",
                value = if (song.bitrate > 0) "${song.bitrate} kbps" else null
            )
        }
        item {
            SongDetailItem(
                label = "采样率",
                value = if (song.sampleRate > 0) "${song.sampleRate} Hz" else null
            )
        }
        item {
            SongDetailItem(
                label = "声道",
                value = if (song.channels > 0) "${song.channels}" else null
            )
        }

        item {
            SongDetailItem(
                label = "添加时间",
                value = if (song.fileAdded > 0) dateFormat.format(Date(song.fileAdded)) else null
            )
        }
        item {
            SongDetailItem(
                label = "修改时间",
                value = if (song.fileLastModified > 0) dateFormat.format(Date(song.fileLastModified)) else null
            )
        }
        item {
            SongDetailItem(
                label = "文件路径",
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
                text = "已选择 $selectedCount 项",
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