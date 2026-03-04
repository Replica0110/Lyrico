package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrics.model.SongSearchResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_results")
fun SearchResultsScreen(
    keyword: String?,
    resultNavigator: ResultBackNavigator<LyricsSearchResult>
) {
    val viewModel: SearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchKeyword by remember { derivedStateOf { uiState.searchKeyword } }

    // 获取源列表
    val sources = uiState.availableSources

    val resultsBySourceId = uiState.searchResultsBySource

    val previewSheetState = remember(uiState.lyricsState.song) {
        mutableStateOf(uiState.lyricsState.song != null)
    }

    /**
     * 外部传入 keyword 时，触发一次搜索
     */
    LaunchedEffect(keyword) {
        keyword?.let {
            viewModel.performSearch(it)
        }
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
                    value = searchKeyword,
                    onValueChange = viewModel::onKeywordChanged,
                    placeholder = stringResource(id = R.string.search_lyrics_placeholder),
                    actionText = stringResource(id = R.string.action_search),
                    onActionClick = {
                        viewModel.performSearch()
                        keyboardController?.hide()
                    },
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val tabs = remember(sources) { sources.map { it.labelRes } }

            val pagerState = rememberPagerState(
                pageCount = { sources.size }
            )


            LaunchedEffect(uiState.selectedSearchSource) {
                val targetIndex = sources.indexOfFirst { it.id == uiState.selectedSearchSource?.id }
                if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
                    pagerState.animateScrollToPage(targetIndex)
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }
                    .collect { page ->
                        // 从 StateFlow 直接读取最新状态，避免闭包捕获过时的 sources
                        val currentState = viewModel.uiState.value
                        val currentSources = currentState.availableSources
                        val source = currentSources.getOrNull(page)
                        // 仅当 ID 不同时更新，避免死循环
                        if (source != null && source.id != currentState.selectedSearchSource?.id) {
                            viewModel.onSearchSourceSelected(source)
                        }
                    }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                TabRowWithContour(
                    tabs = tabs.map { stringResource(it) },
                    selectedTabIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }
                )
            }

            /**
             * 搜索结果区域
             */
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
                key = { index -> sources.getOrNull(index)?.id ?: index }
            ) { page ->

                val source = sources.getOrNull(page)

                val resultsForPage = remember(resultsBySourceId, source) {
                    if (source != null) {
                        resultsBySourceId[source.name] ?: emptyList()
                    } else {
                        emptyList()
                    }
                }

                when {
                    uiState.isSearching && source?.id == uiState.selectedSearchSource?.id -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }

                    uiState.searchError != null && source?.id == uiState.selectedSearchSource?.id -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.search_failed,
                                    uiState.searchError!!
                                ),
                                color = MiuixTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Results
                    else -> {
                        if (resultsForPage.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_searchoff_24dp),
                                    contentDescription = stringResource(id = R.string.cd_no_results),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(id = R.string.search_no_results),
                                    color = MiuixTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 12.dp),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                            ) {
                                items(
                                    items = resultsForPage,
                                    key = { "${it.source.id}_${it.id}" }
                                ) { result ->
                                    SearchResultItem(
                                        song = result,
                                        onPreviewClick = {
                                            viewModel.loadLyrics(result)
                                        },
                                        onApplyClick = {
                                            scope.launch {
                                                val lyrics = viewModel.fetchLyrics(result)
                                                if (lyrics != null) {
                                                    resultNavigator.navigateBack(
                                                        LyricsSearchResult(
                                                            title = result.title,
                                                            artist = result.artist,
                                                            album = result.album,
                                                            lyrics = lyrics,
                                                            date = result.date,
                                                            trackerNumber = result.trackerNumber,
                                                            picUrl = result.picUrl
                                                        )
                                                    )
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.fetch_lyrics_failed),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
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
        }
    }

    /**
     * 歌词 BottomSheet
     */
    SuperBottomSheet(
        show = previewSheetState,
        onDismissRequest = {
            viewModel.clearLyrics()
        },
        title = uiState.lyricsState.song?.title
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier.padding(bottom = 12.dp),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    when {
                        uiState.lyricsState.isLoading -> item(key = "loading") {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }

                        uiState.lyricsState.error != null -> item(key = "error") {
                            Text(
                                modifier = Modifier.padding(12.dp),
                                text = uiState.lyricsState.error!!,
                                style = MiuixTheme.textStyles.body1
                            )
                        }

                        uiState.lyricsState.content != null -> {
                            item(key = "lyrics") {
                                Text(
                                    modifier = Modifier.padding(12.dp),
                                    text = uiState.lyricsState.content!!,
                                    style = MiuixTheme.textStyles.footnote1
                                )
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    enabled = uiState.lyricsState.content != null,
                    text = stringResource(R.string.apply_lyrics_action),
                    onClick = {
                        resultNavigator.navigateBack(
                            LyricsSearchResult(
                                title = null,
                                artist = null,
                                album = null,
                                lyrics = uiState.lyricsState.content,
                                date = null,
                                trackerNumber = null,
                                picUrl = null
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    enabled = uiState.lyricsState.content != null,
                    text = stringResource(R.string.apply_action),
                    onClick = {
                        resultNavigator.navigateBack(
                            LyricsSearchResult(
                                title = uiState.lyricsState.song?.title,
                                artist = uiState.lyricsState.song?.artist,
                                album = uiState.lyricsState.song?.album,
                                lyrics = uiState.lyricsState.content,
                                date = uiState.lyricsState.song?.date,
                                trackerNumber = uiState.lyricsState.song?.trackerNumber,
                                picUrl = uiState.lyricsState.song?.picUrl
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
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
    val context = LocalContext.current

    // 原图尺寸（metadata）
    var imageSize by remember(song.picUrl) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }

    /**
     * 只读取图片头信息，不参与 UI，不解码大图
     */
    LaunchedEffect(song.picUrl) {
        if (song.picUrl.isNotBlank()) {
            val imageLoader = SingletonImageLoader.get(context)

            val request = ImageRequest.Builder(context)
                .data(song.picUrl)
                .size(Size.ORIGINAL)        // 读取原始尺寸
                .allowHardware(false)       // 确保可读取尺寸
                .build()

            val result = imageLoader.execute(request)

            if (result is SuccessResult) {
                val image = result.image
                val w = image.width
                val h = image.height

                if (w > 0 && h > 0) {
                    imageSize = w to h
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {

            /* 左侧：封面 + 原图尺寸 */
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LyricoColors.coverPlaceholder)
                ) {
                    AsyncImage(
                        model = song.picUrl,
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
                }

                Spacer(modifier = Modifier.height(4.dp))

                imageSize?.let { (w, h) ->
                    Text(
                        text = "${w}×${h}",
                        style = MiuixTheme.textStyles.footnote2
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            /* 中间：歌曲信息 */
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (song.date.isNotBlank()) {
                    Text(
                        text = song.date,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }

                if (song.trackerNumber.isNotBlank()) {
                    Text(
                        text = "Track ${song.trackerNumber}",
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            /* 右侧：操作 */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                Button(
                    onClick = onPreviewClick
                ) {
                    Text(
                        text = stringResource(R.string.preview_action),
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = onApplyClick,
                ) {
                    Text(stringResource(R.string.apply_action), fontSize = 13.sp)
                }
            }
        }

        HorizontalDivider()
    }
}