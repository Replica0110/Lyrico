package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.ui.theme.Gray200
import com.lonx.lyrico.ui.theme.Gray400
import com.lonx.lyrico.viewmodel.SongInfo
import com.lonx.lyrico.viewmodel.SongListViewModel
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortInfo
import com.lonx.lyrico.viewmodel.SortOrder
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    showSearch: MutableState<Boolean>,
    onSongClick: (SongInfo) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: SongListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortInfo by viewModel.sortInfo.collectAsState()
    val songs by viewModel.songs.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Lyrico") },
                    actions = {
                        IconButton(onClick = {
                            showSearch.value = !showSearch.value
                            if (!showSearch.value) viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(
                                if (showSearch.value) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch.value) "Close Search" else "Search"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Sort")
                            }
                            SortMenu(
                                expanded = showSortMenu,
                                currentSortInfo = sortInfo,
                                onDismissRequest = { showSortMenu = false },
                                onSortSelected = {
                                    viewModel.onSortChange(it)
                                    showSortMenu = false
                                }
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
                AnimatedVisibility(
                    visible = showSearch.value,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 200))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search by title, artist, album...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Gray400)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Gray400)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = songs,
                    key = { song -> song.filePath }
                ) { song ->
                    SongListItem(
                        song = song,
                        onSongClick = {
                            // Convert SongEntity to SongInfo
                            val songInfo = SongInfo(
                                filePath = song.filePath,
                                fileName = song.fileName,
                                tagData = song.let { entity ->
                                    AudioTagData(
                                        title = entity.title,
                                        artist = entity.artist,
                                        album = entity.album,
                                        genre = entity.genre,
                                        date = entity.date,
                                        lyrics = entity.lyrics,
                                        durationMilliseconds = entity.durationMilliseconds,
                                        bitrate = entity.bitrate,
                                        sampleRate = entity.sampleRate,
                                        channels = entity.channels,
                                        rawProperties = emptyMap()
                                    )
                                },
                                coverUri = song.filePath.toUri()
                            )
                            onSongClick(songInfo)
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            // Loading Overlay
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .clickable(enabled = false, onClick = {}), // Block clicks
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.loadingMessage ?: "正在扫描...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SongListItem(
    song: SongEntity,
    onSongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
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
                    text = song.title ?: song.fileName,
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
                        text = song.artist ?: "未知艺术家",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // 使用更标准的次级文字颜色
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // 分隔符 (如果专辑存在)
                    if (!song.album.isNullOrBlank()) {
                        Text(
                            text = " • ${song.album}",
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
            SortInfo(SortBy.DATE_MODIFIED, SortOrder.DESC),
            SortInfo(SortBy.DATE_MODIFIED, SortOrder.ASC),
            SortInfo(SortBy.TITLE, SortOrder.ASC),
            SortInfo(SortBy.TITLE, SortOrder.DESC),
            SortInfo(SortBy.ARTIST, SortOrder.ASC),
            SortInfo(SortBy.ARTIST, SortOrder.DESC)
        )

        sorts.forEach { sortInfo ->
            val text = when (sortInfo.sortBy) {
                SortBy.DATE_MODIFIED -> "修改时间"
                SortBy.TITLE -> "歌曲名"
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