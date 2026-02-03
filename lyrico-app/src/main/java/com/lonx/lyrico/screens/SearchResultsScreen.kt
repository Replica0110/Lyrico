package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import kotlinx.coroutines.launch
import coil3.ImageLoader
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
import com.lonx.lyrico.viewmodel.LyricsUiState
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrics.model.SongSearchResult
import com.moriafly.salt.ui.ItemDivider
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import org.koin.androidx.compose.koinViewModel

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

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * 外部传入 keyword 时，触发一次搜索
     */
    LaunchedEffect(keyword) {
        keyword?.let {
            viewModel.performSearch(it)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxWidth()
                    .background(SaltTheme.colors.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                SearchBar(
                    value = uiState.searchKeyword,
                    onValueChange = viewModel::onKeywordChanged,
                    placeholder = "搜索歌词...",
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "搜索",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaltTheme.colors.highlight,
                    modifier = Modifier.clickable {
                        viewModel.performSearch()
                        keyboardController?.hide()
                    }
                )
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
        ) {

            /**
             * 搜索源选择
             */
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.availableSources) { source ->
                    val isSelected = source == uiState.selectedSearchSource

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSearchSourceSelected(source) },
                        label = {
                            Text(
                                source.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = SaltIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = SaltTheme.colors.highlight
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SaltTheme.colors.subBackground,
                            selectedContainerColor = SaltTheme.colors.highlight.copy(alpha = 0.1f),
                            labelColor = SaltTheme.colors.text,
                            selectedLabelColor = SaltTheme.colors.highlight
                        ),
                        border = null
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = SaltTheme.colors.stroke
            )

            /**
             * 搜索结果区域
             */
            Box(modifier = Modifier.fillMaxSize()) {

                when {
                    uiState.isSearching -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = SaltTheme.colors.highlight
                            )
                        }
                    }

                    uiState.searchError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.searchError!!,
                                color = SaltTheme.colors.highlight,
                                fontSize = 14.sp
                            )
                        }
                    }

                    uiState.searchResults.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_searchoff_24dp),
                                contentDescription = "No results",
                                modifier = Modifier.size(48.dp),
                                tint = SaltTheme.colors.subText
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "未找到相关结果",
                                color = SaltTheme.colors.subText
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.searchResults) { result ->
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
                                                Toast.makeText(context, "获取歌词失败，请重试", Toast.LENGTH_SHORT).show()
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

    /**
     * 歌词 BottomSheet
     * 只要 lyricsState.song != null 即显示
     */
    val lyricsState = uiState.lyricsState

    if (lyricsState.song != null) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { viewModel.clearLyrics() },
            containerColor = SaltTheme.colors.background,
            tonalElevation = 0.dp
        ) {
            LyricsBottomSheetContent(
                lyricsState = lyricsState,
                onApply = { lyrics ->
                    val song = lyricsState.song
                    resultNavigator.navigateBack(
                        LyricsSearchResult(
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            lyrics = lyrics,
                            date = song.date,
                            trackerNumber = song.trackerNumber,
                            picUrl = song.picUrl
                        )
                    )
                    viewModel.clearLyrics()
                }
            )
        }
    }

}
@Composable
private fun LyricsBottomSheetContent(
    lyricsState: LyricsUiState,
    onApply: (String) -> Unit
) {
    val song = lyricsState.song ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(song.title, color = SaltTheme.colors.text, style = SaltTheme.textStyles.main)
        Text(song.artist, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)
        Text(song.album, color = SaltTheme.colors.subText, style = SaltTheme.textStyles.sub)

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                lyricsState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = SaltTheme.colors.highlight
                    )
                }

                lyricsState.error != null -> {
                    Text(
                        lyricsState.error,
                        color = SaltTheme.colors.highlight
                    )
                }

                lyricsState.content != null -> {
                    Text(
                        text = lyricsState.content,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = SaltTheme.colors.text
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { lyricsState.content?.let(onApply) },
            enabled = lyricsState.content != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SaltTheme.colors.highlight
            )
        ) {
            Text("使用此歌词", color = SaltTheme.colors.onHighlight)
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
            val imageLoader = ImageLoader(context)

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        fontSize = 11.sp,
                        color = SaltTheme.colors.subText
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
                    color = SaltTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    fontSize = 13.sp,
                    color = SaltTheme.colors.subText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        fontSize = 13.sp,
                        color = SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (song.date.isNotBlank()) {
                    Text(
                        text = song.date,
                        fontSize = 12.sp,
                        color = SaltTheme.colors.subText
                    )
                }

                if (song.trackerNumber.isNotBlank()) {
                    Text(
                        text = "Track ${song.trackerNumber}",
                        fontSize = 12.sp,
                        color = SaltTheme.colors.subText
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            /* 右侧：操作 */
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                TextButton(
                    onClick = onPreviewClick,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "预览",
                        fontSize = 13.sp,
                        color = SaltTheme.colors.highlight
                    )
                }

                Button(
                    onClick = onApplyClick,
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaltTheme.colors.highlight.copy(alpha = 0.12f),
                        contentColor = SaltTheme.colors.highlight
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("应用", fontSize = 13.sp)
                }
            }
        }

        ItemDivider()
    }
}