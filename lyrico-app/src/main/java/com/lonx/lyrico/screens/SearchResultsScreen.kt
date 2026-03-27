package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.util.Log
import android.view.HapticFeedbackConstants
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode.*
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

    val resultsBySourceId = uiState.searchResults

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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                items(
                                    items = resultsForPage,
                                    key = { "${it.source.id}_${it.id}" }
                                ) { result ->
                                    SearchResultItem(
                                        song = result,
                                        onPreviewClick = { offset ->
                                            viewModel.loadLyrics(result, offset)
                                        },
                                        onApplyClick = { offset ->
                                            scope.launch {
                                                val lyrics = viewModel.fetchLyrics(
                                                    result,
                                                    offset
                                                ) // 关键：传入 offset
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
                                        },
                                        // 3. 仅应用歌词时传入 offset
                                        onApplyLyricsOnlyClick = { offset ->
                                            scope.launch {
                                                val lyrics = viewModel.fetchLyrics(
                                                    result,
                                                    offset
                                                ) // 关键：传入 offset
                                                if (lyrics != null) {
                                                    resultNavigator.navigateBack(
                                                        LyricsSearchResult(
                                                            title = null,
                                                            artist = null,
                                                            album = null,
                                                            lyrics = lyrics,
                                                            date = null,
                                                            trackerNumber = null,
                                                            picUrl = null,
                                                            lyricsOnly = true
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
     * 只要 lyricsState.song != null 即显示
     */

    SuperBottomSheet(
        show = uiState.lyricsState.song != null,
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
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    when {
                        uiState.lyricsState.isLoading -> item("loading") {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }

                        uiState.lyricsState.error != null -> item("error") {
                            val errorMsg = uiState.lyricsState.error!!

                            Text(
                                modifier = Modifier.padding(12.dp),
                                text = errorMsg,
                                style = MiuixTheme.textStyles.body1
                            )
                        }

                        else -> item("lyrics") {
                            val text = uiState.lyricsState.content
                                ?.takeIf { it.isNotBlank() }
                                ?: "no lyrics"

                            Text(
                                modifier = Modifier.padding(12.dp),
                                text = text,
                                style = MiuixTheme.textStyles.footnote1
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    enabled = uiState.lyricsState.content != null && uiState.lyricsState.content != "",
                    text = stringResource(R.string.apply_lyrics_only_action),
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
                    enabled = uiState.lyricsState.content != null && uiState.lyricsState.content != "",
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
    onPreviewClick: (Long) -> Unit,
    onApplyClick: (Long) -> Unit,
    onApplyLyricsOnlyClick: (Long) -> Unit
) {
    val context = LocalContext.current
    var offset by remember { mutableLongStateOf(0L) } // 偏移量（毫秒）
    var isOffsetVisible by remember { mutableStateOf(false) } // 是否展开调节面板

    // 原图尺寸状态
    var imageSize by remember(song.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(song.picUrl) {
        if (song.picUrl.isNotBlank()) {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(song.picUrl)
                .size(Size.ORIGINAL)
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val image = result.image
                if (image.width > 0 && image.height > 0) {
                    imageSize = image.width to image.height
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(CardDefaults.CornerRadius))
            .clickable(onClick = { onPreviewClick(offset) }),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
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

                    val isDark = when (MiuixTheme.colorSchemeMode) {
                        Dark,
                        MonetDark -> true

                        Light,
                        MonetLight -> false

                        System,
                        MonetSystem -> isSystemInDarkTheme()

                        null -> false
                    }
                    val textColor = if (isDark) Color.Black else Color.White

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
                        imageSize?.let {
                            Text(
                                text = "${it.first}×${it.second}",
                                color = textColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(bottom = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val artistAlbum = buildList {
                        if (song.artist.isNotBlank()) add(song.artist)
                        if (song.album.isNotBlank()) add(song.album)
                    }.joinToString(" • ")

                    if (artistAlbum.isNotEmpty()) {
                        Text(
                            text = artistAlbum,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val extraInfo = buildList {
                        if (song.date.isNotBlank()) add(song.date)
                        if (song.trackerNumber.isNotBlank()) add("Track ${song.trackerNumber}")
                    }.joinToString(" • ")

                    if (extraInfo.isNotEmpty()) {
                        Text(
                            text = extraInfo,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { isOffsetVisible = !isOffsetVisible }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Settings,
                        contentDescription = "Offset",
                        modifier = Modifier.size(12.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                    )
                    Text(
                        text = if (offset == 0L) stringResource(R.string.offset_adjust_hint)
                        else "${if (offset > 0) "+" else ""}${offset}ms",
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .clickable { onApplyLyricsOnlyClick(offset) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.apply_lyrics_only_action),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary)
                            .clickable { onApplyClick(offset) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.apply_action),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isOffsetVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OffsetAdjustPanel(
                    currentOffset = offset,
                    onOffsetChange = { offset = it },
                    onReset = { offset = 0L }
                )
            }
        }
    }
}

/**
 * 专为 Miuix 重新设计的偏移面板组件
 */
@Composable
fun OffsetAdjustPanel(
    currentOffset: Long,
    onOffsetChange: (Long) -> Unit,
    onReset: () -> Unit
) {
    val view = LocalView.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 减少侧
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OffsetStepButton("-500") { onOffsetChange(currentOffset - 500) }
            OffsetStepButton("-100") { onOffsetChange(currentOffset - 100) }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onReset()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${if (currentOffset > 0) "+" else ""}${currentOffset}ms",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.action_reset),
                fontSize = 9.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
            )
        }

        // 增加侧
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OffsetStepButton("+100") { onOffsetChange(currentOffset + 100) }
            OffsetStepButton("+500") { onOffsetChange(currentOffset + 500) }
        }
    }
}

/**
 * Miuix 风格的微小功能按钮
 */
@Composable
fun OffsetStepButton(text: String, onClick: () -> Unit) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MiuixTheme.colorScheme.surface)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

