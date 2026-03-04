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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.ui.components.DropdownItem
import com.lonx.lyrico.ui.dialog.BatchMatchConfigDialog
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.coil.CoverRequest
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDetailDestination
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperListPopup
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SECTIONS_ASC = listOf(
    "0"
) + ('A'..'Z').map { it.toString() } + listOf("#")

private val SECTIONS_DESC = SECTIONS_ASC.asReversed()

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class
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
    val sortOrderDropdownExpanded = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val sheetUiState by viewModel.sheetState.collectAsStateWithLifecycle()

    val showDetailSheet = remember(sheetUiState.detailSong) {
        mutableStateOf(sheetUiState.detailSong != null)
    }
    val showMenuSheet = remember(sheetUiState.menuSong) {
        mutableStateOf(sheetUiState.menuSong != null)
    }
    val showDeleteDialog = remember(uiState.showDeleteDialog) {
        mutableStateOf(uiState.showDeleteDialog && sheetUiState.menuSong != null)
    }
    val showBatchDeleteDialog = remember(uiState.showBatchDeleteDialog) {
        mutableStateOf(uiState.showBatchDeleteDialog)
    }
    val showBatchConfigDialog = remember(uiState.showBatchConfigDialog) {
        mutableStateOf(uiState.showBatchConfigDialog)
    }

    val showBatchMatchingDialog = remember(uiState.showBatchConfigDialog) {
        mutableStateOf(uiState.isBatchMatching || uiState.batchProgress != null)
    }

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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            if (isSelectionMode) {
                val allSelected = viewModel.isAllSelected(songs)
                SmallTopAppBar(
                    title = "",
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        Text(
                            modifier = Modifier.padding(start = 12.dp),
                            text = stringResource(
                                R.string.selection_mode_selected_count,
                                selectedPaths.size
                            )
                        )
                    },
                    actions = {
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
                                )
                            )
                        }
                        TextButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = {
                                viewModel.exitSelectionMode()
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.cancel)
                            )
                        }
                    }
                )
            } else {
                SmallTopAppBar(
                    title = stringResource(R.string.song_list_title, songs.size),
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        IconButton(
                            modifier = Modifier.padding(start = 12.dp),
                            onClick = {
                                navigator.navigate(SettingsDestination())
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                navigator.navigate(LocalSearchDestination())
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = stringResource(R.string.cd_search)
                            )
                        }
                        Box() {
                            IconButton(
                                modifier = Modifier.padding(end = 12.dp),
                                onClick = {
                                    sortOrderDropdownExpanded.value = true
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Sort,
                                    contentDescription = stringResource(R.string.cd_sort)
                                )
                            }
                            SuperListPopup(
                                show = sortOrderDropdownExpanded,
                                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                                onDismissRequest = {
                                    sortOrderDropdownExpanded.value = false
                                }
                            ) {
                                ListPopupColumn {
                                    val sortTypes = listOf(
                                        SortBy.TITLE,
                                        SortBy.ARTISTS,
                                        SortBy.DATE_MODIFIED,
                                        SortBy.DATE_ADDED
                                    )
                                    sortTypes.forEach { type ->
                                        val isSelected = sortInfo.sortBy == type
                                        DropdownItem(
                                            text = stringResource(type.labelRes),
                                            optionSize = sortTypes.size,
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
                                                viewModel.onSortChange(SortInfo(type, newOrder))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

        },
        floatingToolbar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                val hasSelection = selectedPaths.isNotEmpty()

                FloatingNavigationBar {

                    FloatingNavigationBarItem(
                        selected = hasSelection,
                        enabled = hasSelection,
                        label = stringResource(R.string.action_share),
                        onClick = { viewModel.batchShare(context, songs) },
                        icon = MiuixIcons.Share
                    )
                    FloatingNavigationBarItem(
                        selected = hasSelection,
                        enabled = hasSelection,
                        label = stringResource(R.string.action_delete),
                        onClick = { viewModel.showBatchDeleteDialog() },
                        icon = MiuixIcons.Delete
                    )
                    FloatingNavigationBarItem(
                        selected = hasSelection,
                        enabled = hasSelection,
                        label = stringResource(R.string.action_batch_match),
                        onClick = { viewModel.openBatchMatchConfig() },
                        icon = MiuixIcons.Edit
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefresh(
                modifier = Modifier.padding(paddingValues),
                isRefreshing = uiState.isLoading,
                onRefresh = {
                    viewModel.refreshSongs()
                }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                        .fillMaxHeight(),
                    state = listState,
                    overscrollEffect = null
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
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    if (!isSelectionMode) {
                                        IconButton(
                                            onClick = { viewModel.showMenu(song) }
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.More,
                                                contentDescription = "More"
                                            )
                                        }
                                    } else {
                                        Checkbox(
                                            checked = selectedPaths.contains(song.mediaId),
                                            onCheckedChange = {
                                                viewModel.toggleSelection(song.mediaId)
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
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
        SuperBottomSheet(
            show = showMenuSheet,
            onDismissRequest = {
                viewModel.dismissAll()
            },
            onDismissFinished = {
                viewModel.dismissAll()
            }
        ) {
            val song = sheetUiState.menuSong
            song?.let {
                SongMenuBottomSheetContent(
                    song = it,
                    onPlay = { viewModel.play(context, it) },
                    showInfo = { viewModel.showDetail(it) },
                    onDelete = { viewModel.showDeleteDialog() },
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
        SuperBottomSheet(
            show = showDetailSheet,
            onDismissRequest = { viewModel.dismissDetail() },
        ) {
            val song = sheetUiState.menuSong
            song?.let {
                SongDetailBottomSheetContent(song = it)
            }
        }


        SuperDialog(
            title = stringResource(R.string.dialog_delete_file_title),
            show = showDeleteDialog,
            summary = stringResource(R.string.dialog_delete_file_content, sheetUiState.menuSong?.fileName?: ""),
            onDismissRequest = { viewModel.dismissDeleteDialog() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            viewModel.dismissDeleteDialog()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            viewModel.dismissDeleteDialog()
                            viewModel.dismissAll()
                            viewModel.delete(sheetUiState.menuSong!!)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
        // 批量删除确认对话框
        SuperDialog(
            show = showBatchDeleteDialog,
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            summary = stringResource(
                R.string.dialog_batch_delete_content,
                selectedPaths.size
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            viewModel.dismissBatchDeleteDialog()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(20.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            viewModel.dismissBatchDeleteDialog()
                            viewModel.batchDelete(songs)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
        // 批量匹配配置对话框
        BatchMatchConfigDialog(
            title = stringResource(R.string.batch_match_config_title),
            show = showBatchConfigDialog,
            onDismissRequest = { viewModel.closeBatchMatchConfig() },
            onConfirm = { config -> viewModel.batchMatch(config) }
        )


        SuperDialog(
            show = showBatchMatchingDialog,
            onDismissRequest = {
                if (!uiState.isBatchMatching) {
                    showBatchMatchingDialog.value = false
                    viewModel.closeBatchMatchDialog()
                }
            },
            title = stringResource(R.string.batch_matching_title),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.batchProgress?.let { (current, total) ->
                        val progress =
                            if (total > 0) current.toFloat() / total.toFloat() else 0f

                        // 进度文字
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = if (uiState.isBatchMatching)
                                    uiState.currentFile
                                else
                                    stringResource(
                                        R.string.batch_matching_total_time,
                                        uiState.batchTimeMillis / 1000.0
                                    ),
                                style = MiuixTheme.textStyles.subtitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f) // 占满剩余空间
                            )

                            Text(
                                text = "$current / $total",
                                style = MiuixTheme.textStyles.main,
                                textAlign = TextAlign.End
                            )
                        }

                        LinearProgressIndicator(
                            progress = progress
                        )
                    }
                }


                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(
                            R.string.batch_matching_success,
                            uiState.successCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_matching_skipped,
                            uiState.skippedCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                    Text(
                        text = stringResource(
                            R.string.batch_matching_failure,
                            uiState.failureCount
                        ),
                        style = MiuixTheme.textStyles.main
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    top.yukonga.miuix.kmp.basic.TextButton(
                        enabled = !uiState.isBatchMatching,
                        text = stringResource(R.string.action_close),
                        onClick = {
                            showBatchMatchingDialog.value = false
                            viewModel.closeBatchMatchDialog()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    top.yukonga.miuix.kmp.basic.TextButton(
                        text = if (uiState.isBatchMatching) stringResource(R.string.action_abort) else stringResource(
                            R.string.action_view_results
                        ),
                        onClick = {
                            if (uiState.isBatchMatching) {
                                viewModel.abortBatchMatch()
                            } else {
                                showBatchMatchingDialog.value = false
                                viewModel.closeBatchMatchDialog()
                                navigator.navigate(
                                    BatchMatchHistoryDetailDestination(uiState.batchHistoryId)
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
                verticalArrangement = Arrangement.spacedBy(2.dp) // 紧凑行间距
            ) {
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    fontWeight = FontWeight.Medium, // 稍微降低字重以显得清秀
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 歌手
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
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 12.sp
                    )
                }

                // 音质信息
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
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
            .scrollEndHaptic()
            .overScrollVertical(),
        overscrollEffect = null,
    ) {
        item(key = "song_menu") {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                SuperArrow(
                    title = stringResource(R.string.menu_action_play),
                    summary = stringResource(R.string.menu_action_play_sub),
                    onClick = { onPlay() }
                )
                SuperArrow(
                    title = stringResource(R.string.menu_action_share),
                    onClick = { onShare() }
                )
                SuperArrow(
                    title = stringResource(R.string.menu_action_info),
                    onClick = { showInfo() }
                )
                SuperArrow(
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
fun SongDetailBottomSheetContent(song: SongEntity) {

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
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_album_24dp),
                    error = painterResource(R.drawable.ic_album_24dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = song.title.takeIf { !it.isNullOrBlank() }
                            ?: song.fileName,
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

