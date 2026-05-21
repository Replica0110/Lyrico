package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.viewmodel.CoverSearchResult
import com.lonx.lyrico.viewmodel.CoverSearchViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_cover")
fun SearchCoverScreen(
    keyword: String?,
    resultNavigator: ResultBackNavigator<String>
) {
    val viewModel: CoverSearchViewModel = koinViewModel()
    val uiState by viewModel.coverUiState.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { uiState.availableSources.size + 1 }

    // 用于缓存图片尺寸的Map
    val imageSizeCache = remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }

    LaunchedEffect(keyword) {
        keyword?.let { viewModel.performCoverSearch(it) }
    }
    val localSegments = remember(uiState.searchKeyword) {
        MusicMatchUtils.splitToSegments(uiState.searchKeyword)
            .ifEmpty { listOf(uiState.searchKeyword.trim()) }
            .filter { it.isNotBlank() }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(vertical = 8.dp)
            ) {
                SearchBar(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    value = uiState.searchKeyword,
                    onValueChange = viewModel::onCoverKeywordChanged,
                    placeholder = stringResource(id = R.string.search_cover_placeholder),
                    actions = {
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.performCoverSearch()
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_search),
                                style = MiuixTheme.textStyles.main,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            /**
             * Tabs
             */
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                val tabs = listOf(stringResource(id = R.string.search_type_all)) + uiState.availableSources.map { source ->
                    source.labelRes?.let { stringResource(id = it) } ?: source.name
                }
                TabRowWithContour(
                    tabs = tabs,
                    minWidth = 80.dp,
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }

            /**
             * Pager
             */
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val source = if (page == 0) {
                    null
                } else {
                    uiState.availableSources.getOrNull(page - 1)
                }
                val results = if (page == 0) {

                    uiState.coverResults
                        .mapIndexed { index, cover ->
                            val score = MusicMatchUtils.calculateCoverMatchScore(
                                localSegments = localSegments,
                                coverTitle = cover.title,
                                coverArtist = cover.artist,
                                rankIndex = index
                            )

                            cover to score
                        }
                        .sortedWith(
                            compareByDescending<Pair<CoverSearchResult, Double>> { (_, score) ->
                                score
                            }.thenByDescending { (cover, _) ->
                                imageSizeCache.value[cover.url]?.let { it.first * it.second } ?: 0
                            }
                        )
                        .map { it.first }
                } else {
                    uiState.coverResults.filter { it.source == source }
                }

                when {
                    uiState.isSearching -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    page == 0 && uiState.searchError != null -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                text = uiState.searchError?.asString().orEmpty(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(stringResource(id = R.string.cd_no_results))
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(results, key = { it.id + it.url }) { cover ->
                                CoverGridItem(
                                    cover = cover,
                                    onClick = {
                                        resultNavigator.navigateBack(cover.url)
                                    },
                                    onImageSizeLoaded = { url, size ->
                                        imageSizeCache.value = imageSizeCache.value + (url to size)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoverGridItem(
    cover: CoverSearchResult,
    onClick: () -> Unit,
    onImageSizeLoaded: (String, Pair<Int, Int>) -> Unit = { _, _ -> }
) {
    var imageSize by remember(cover.url) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(cover.url) {
        if (cover.url.isNotBlank()) {
            imageSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeStream(
                        java.net.URL(cover.url).openStream(),
                        null,
                        options
                    )
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth to options.outHeight
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            imageSize?.let { size ->
                onImageSizeLoaded(cover.url, size)
            }
        }
    }

    Card(
        modifier = Modifier,
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(CardDefaults.CornerRadius))
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(LyricoColors.coverPlaceholder)
            ) {
                AsyncImage(
                    model = cover.url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    placeholder = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    ),
                    error = rememberTintedPainter(
                        painter = painterResource(R.drawable.ic_album_24dp),
                        tint = LyricoColors.coverPlaceholderIcon
                    )
                )

                val textColor = if (isDarkTheme) Color.Black else Color.White

                // 来源标签 - 左上角
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(
                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cover.source.labelRes?.let { stringResource(it) } ?: cover.source.name,
                        color = textColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 尺寸标签 - 右下角
                imageSize?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${it.first}×${it.second}",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 歌曲信息
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // 标题
                Text(
                    text = cover.title.ifBlank { "未知标题" },
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                // 歌手
                Text(
                    text = cover.artist.ifBlank { "未知歌手" },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                // 专辑
                Text(
                    text = cover.album.ifBlank { "未知专辑" },
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
