package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.ui.components.bar.TopBar
import com.lonx.lyrico.ui.theme.Gray200
import com.lonx.lyrico.ui.theme.Gray400
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.noRippleClickable
import com.moriafly.salt.ui.popup.PopupMenu
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditMetadataDestination
import com.ramcosta.composedestinations.generated.destinations.LocalSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, UnstableSaltUiApi::class,
    ExperimentalHazeMaterialsApi::class
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
    var sortOrderDropdownExpanded by remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    // 创建 Haze 状态
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = Modifier.background(SaltTheme.colors.background),
        topBar = {
            Column(
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(containerColor = SaltTheme.colors.background)
                )
            ) {
                TopBar(
                    backgroundColor = Color.Transparent,
                    text = "歌曲(${songs.size}首)",
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
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
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = SaltTheme.colors.text,
                            modifier = Modifier
                                .size(48.dp)
                                .noRippleClickable(role = Role.Button) {
                                    navigator.navigate(
                                        LocalSearchDestination()
                                    )
                                }
                                .padding(12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .wrapContentSize()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
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
                                onDismissRequest = {
                                    sortOrderDropdownExpanded = false
                                }
                            ) {
                                val sorts = listOf(
                                    SortInfo(SortBy.TITLE, SortOrder.ASC),
                                    SortInfo(SortBy.TITLE, SortOrder.DESC),
                                    SortInfo(SortBy.DATE_MODIFIED, SortOrder.ASC),
                                    SortInfo(SortBy.DATE_MODIFIED, SortOrder.DESC),
                                    SortInfo(SortBy.ARTIST, SortOrder.ASC),
                                    SortInfo(SortBy.ARTIST, SortOrder.DESC)
                                )

                                sorts.forEach { info ->
                                    val text = when (info.sortBy) {
                                        SortBy.TITLE -> "歌曲名"
                                        SortBy.DATE_MODIFIED -> "修改时间"
                                        SortBy.ARTIST -> "歌手"
                                    } + if (info.order == SortOrder.ASC) " (升序)" else " (降序)"

                                    PopupMenuItem(
                                        text = text,
                                        onClick = {
                                            viewModel.onSortChange(info)
                                            sortOrderDropdownExpanded = false
                                        },
                                        selected = info == sortInfo
                                    )
                                }
                            }
                        }
                    }
                )

            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
        ) {

            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = uiState.isLoading,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = paddingValues.calculateTopPadding()),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                ) {
                    items(
                        items = songs,
                        key = { song -> song.filePath }
                    ) { song ->
                        SongListItem(
                            song = song,
                            navigator = navigator,
                            modifier = Modifier.animateItem()
                        )
                        ItemDivider()
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SongListItem(
    song: SongEntity,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = {
                navigator.navigate(
                    EditMetadataDestination(
                        songFilePath = song.filePath
                    )
                )
            })
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Gray200)
            ) {
                AsyncImage(
                    model = song.filePath.toUri(),
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )

                // 格式角标 (保持但字体缩小)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            )
                        )
                ) {
                    Text(
                        text = song.fileName.substringAfterLast('.', "").uppercase(),
                        color = Color.White,
                        fontSize = 8.sp, // 字体缩小
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 1.dp)
                    )
                }
            }

            // 中间信息列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp) // 紧凑行间距
            ) {
                // 第一行: 标题
                Text(
                    text = song.title.takeIf { !it.isNullOrBlank() } ?: song.fileName,
                    fontWeight = FontWeight.Medium, // 稍微降低字重以显得清秀
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 第二行: 歌手 • 专辑 (合并显示)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 歌手
                    Text(
                        text = song.artist.takeIf { !it.isNullOrBlank() } ?: "未知艺术家",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用更标准的次级文字颜色
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // 分隔符 (如果专辑存在)
                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = " - ${song.album}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                // 音质信息
                if (song.bitrate > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.bitrate}kbps",
                        fontSize = 10.sp,
                        color = Gray400,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    currentSortInfo: SortInfo,
    onDismissRequest: () -> Unit,
    onSortSelected: (SortInfo) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        val sorts = listOf(
            SortInfo(SortBy.TITLE, SortOrder.ASC),
            SortInfo(SortBy.TITLE, SortOrder.DESC),
            SortInfo(SortBy.DATE_MODIFIED, SortOrder.ASC),
            SortInfo(SortBy.DATE_MODIFIED, SortOrder.DESC),
            SortInfo(SortBy.ARTIST, SortOrder.ASC),
            SortInfo(SortBy.ARTIST, SortOrder.DESC)
        )

        sorts.forEach { sortInfo ->
            val text = when (sortInfo.sortBy) {
                SortBy.TITLE -> "歌曲名"
                SortBy.DATE_MODIFIED -> "修改时间"
                SortBy.ARTIST -> "歌手"
            } + if (sortInfo.order == SortOrder.ASC) " (升序)" else " (降序)"

            DropdownMenuItem(
                text = { Text(text) },
                onClick = { onSortSelected(sortInfo) },
                trailingIcon = {
                    if (currentSortInfo == sortInfo) {
                        Icon(Icons.Default.Check, contentDescription = "Selected")
                    }
                }
            )
        }
    }
}