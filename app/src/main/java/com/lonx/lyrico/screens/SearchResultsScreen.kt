package com.lonx.lyrico.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrics.model.SongSearchResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultBackNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_results")
fun SearchResultsScreen(
    keyword: String?,
//    navigator: DestinationsNavigator,
    resultNavigator: ResultBackNavigator<LyricsSearchResult>
//    onResultSelect: (song: SongSearchResult, lyrics: String?) -> Unit
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(keyword) {
        if (keyword != null) {
            viewModel.search(keyword)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. 顶部搜索区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { resultNavigator.navigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }

                TextField(
                    value = uiState.searchKeyword,
                    onValueChange = { viewModel.onKeywordChanged(it) },
                    placeholder = { Text("搜索歌词...", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.5f
                        )
                    ),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = if (uiState.searchKeyword.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { viewModel.onKeywordChanged("") },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
                )
            }

            // 2. 搜索源选择
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.availableSources) { source ->
                    val isSelected = source == uiState.selectedSearchSource
                    FilterChip(
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        selected = isSelected,
                        onClick = { viewModel.switchSource(source) },
                        label = { Text(source.name) },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.Transparent
                        )
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // 3. 结果列表
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isSearching) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else if (uiState.searchError != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.searchError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    }
                } else if (uiState.searchResults.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = "No results",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("未找到相关结果", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.searchResults) { result ->
                            SearchResultItem(
                                song = result,
                                onPreviewClick = { viewModel.fetchLyricsForPreview(result) },
                                onApplyClick = {
                                    viewModel.fetchLyricsDirectly(result) { lyrics ->
                                        Log.d("Lyrics", "Lyrics: $lyrics")
                                        if (lyrics != null) {
                                            resultNavigator.navigateBack(
                                                LyricsSearchResult(
                                                    title = result.title,
                                                    artist = result.artist,
                                                    album = result.album,
                                                    lyrics = lyrics
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // 歌词预览 BottomSheet
    if (uiState.previewingSong != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.clearPreview() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.previewingSong!!.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    uiState.previewingSong!!.artist,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    uiState.previewingSong!!.album,
                    style = MaterialTheme.typography.bodyMedium,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isPreviewLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else if (uiState.lyricsPreviewError != null) {
                        Text(
                            uiState.lyricsPreviewError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp
                        )
                    } else if (uiState.lyricsPreviewContent != null) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = uiState.lyricsPreviewContent!!,
                            modifier = Modifier.verticalScroll(scrollState),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val song = uiState.previewingSong
                        val lyrics = uiState.lyricsPreviewContent
                        if (song != null && lyrics != null) {
                            resultNavigator.navigateBack(
                                LyricsSearchResult(
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    lyrics = lyrics
                                )
                            )
                        }
                        viewModel.clearPreview()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.lyricsPreviewContent != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("使用此歌词")
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    song: SongSearchResult,
    onPreviewClick: () -> Unit,
    onApplyClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp), // 适当的内边距，不拥挤
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：信息区域 (占据剩余空间)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (song.album.isNotBlank()) {
                        Text(
                            text = " • ${song.album}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 右侧：按钮区域
            // 使用 TextButton 和 Button (Filled) 区分主次操作
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onPreviewClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("预览", fontSize = 13.sp)
                }

                Button(
                    onClick = onApplyClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("应用", fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}