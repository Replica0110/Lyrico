package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

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

    val showLyricRenderConfigBottomSheet = remember { mutableStateOf(false) }
    val lyricConfig by viewModel.lyricConfigFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current


    val clipboardManager = LocalClipboard.current
    val pagerState = rememberPagerState { uiState.availableSources.size }

    /**
     * 外部 keyword 触发搜索
     */
    LaunchedEffect(keyword) {
        keyword?.let { viewModel.performSearch(it) }
    }

    /**
     * ViewModel → Pager 同步
     */
    LaunchedEffect(uiState.selectedSearchSource) {
        val index = uiState.availableSources.indexOf(uiState.selectedSearchSource)
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    /**
     * Pager → ViewModel 同步
     */
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collectLatest { page ->
                val source = uiState.availableSources.getOrNull(page)
                source?.let { viewModel.onSearchSourceSelected(it) }
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
                    value = uiState.searchKeyword,
                    onValueChange = viewModel::onKeywordChanged,
                    placeholder = stringResource(id = R.string.search_lyrics_placeholder),
                    actions = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.performSearch()
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.action_search),
                                style = MiuixTheme.textStyles.main,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                showLyricRenderConfigBottomSheet.value = true
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary
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

            /**
             * 初始化 loading
             */
            if (uiState.isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (uiState.availableSources.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.plugin_empty))
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
                TabRowWithContour(
                    tabs = uiState.availableSources.map { source ->
                        source.labelRes?.let { stringResource(id = it) } ?: source.name
                    },
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

                val source = uiState.availableSources.getOrNull(page)

                val results =
                    uiState.searchResults[source?.id] ?: emptyList()

                when {
                    uiState.isSearching && source == uiState.selectedSearchSource -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    source != null && uiState.searchErrors[source.id] != null -> {
                        val errorMessage = uiState.searchErrors[source.id]
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                text = errorMessage?.asString().orEmpty(),
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
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                            items(results, key = { "${it.pluginId}_${it.id}" }) { song ->

                                SearchResultItem(
                                    song = song,
                                    onPreviewClick = {
                                        viewModel.loadLyrics(song)
                                    },
                                    onApplyClick = {
                                        scope.launch {
                                            val lyrics =
                                                viewModel.fetchLyrics(song)
                                            if (lyrics != null) {
                                                resultNavigator.navigateBack(
                                                    LyricsSearchResult(
                                                        title = song.title,
                                                        artist = song.artist,
                                                        album = song.album,
                                                        lyrics = lyrics,
                                                        date = song.date,
                                                        trackerNumber = song.trackNumber,
                                                        picUrl = song.picUrl,
                                                        pluginId = song.pluginId,
                                                        pluginName = song.pluginName,
                                                        lyricsOnly = false,
                                                        fields = song.fields
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
                                    onApplyLyricsOnlyClick = {
                                        scope.launch {
                                            val lyrics =
                                                viewModel.fetchLyrics(song)
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
     */

    val lyricsText = uiState.lyricsState.content
    val song = uiState.lyricsState.song
    var showLyricsSheet by remember { mutableStateOf(false) }
    LaunchedEffect(song) {
        if (song != null) {
            showLyricsSheet = true
        }
    }

    WindowBottomSheet(
        show = showLyricsSheet,
        enableNestedScroll = false,
        onDismissRequest = { showLyricsSheet = false },
        onDismissFinished = { viewModel.clearLyrics() },
        title = song?.title ?: "",
        endAction = {
            IconButton(
                onClick = {
                    showLyricRenderConfigBottomSheet.value = true
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = null
                )
            }
        }
    ) {
        song?.let { currentSong ->
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
                    Box {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(min = 30.dp, max = 300.dp)
                                .fillMaxWidth(),
                        ) {
                            when {
                                uiState.lyricsState.isLoading -> item("loading") {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    }
                                }

                                uiState.lyricsState.error != null -> item("error") {
                                    val errorMessage = uiState.lyricsState.error
                                    Text(
                                        modifier = Modifier.padding(12.dp),
                                        text = errorMessage?.asString().orEmpty(),
                                        style = MiuixTheme.textStyles.body2
                                    )
                                }

                                else -> item("lyrics") {
                                    val text = lyricsText
                                        ?.takeIf { it.isNotBlank() }
                                        ?: stringResource(R.string.lyrics_empty)

                                    Text(
                                        modifier = Modifier.padding(12.dp),
                                        text = text,
                                        style = MiuixTheme.textStyles.body2
                                    )
                                }
                            }
                        }
                        if (!lyricsText.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val clipData = ClipData.newPlainText("copy lyrics", lyricsText)
                                        val clipEntry = ClipEntry(clipData)
                                        clipboardManager.setClipEntry(clipEntry)
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(
                                        color = MiuixTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Copy,
                                    contentDescription = "复制歌词"
                                )
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        enabled = lyricsText != null && lyricsText != "",
                        text = stringResource(R.string.apply_lyrics_only_action),
                        onClick = {
                            resultNavigator.navigateBack(
                                LyricsSearchResult(
                                    title = null,
                                    artist = null,
                                    album = null,
                                    lyrics = lyricsText,
                                    date = null,
                                    trackerNumber = null,
                                    picUrl = null,
                                    lyricsOnly = true
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        enabled = lyricsText != null && lyricsText != "",
                        text = stringResource(R.string.apply_action),
                        onClick = {
                            resultNavigator.navigateBack(
                                LyricsSearchResult(
                                    title = currentSong.title,
                                    artist = currentSong.artist,
                                    album = currentSong.album,
                                    lyrics = uiState.lyricsState.content,
                                    date = currentSong.date,
                                    trackerNumber = currentSong.trackNumber,
                                    picUrl = currentSong.picUrl,
                                    pluginId = currentSong.pluginId,
                                    pluginName = currentSong.pluginName,
                                    lyricsOnly = false,
                                    fields = currentSong.fields
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

    WindowBottomSheet(
        show = showLyricRenderConfigBottomSheet.value,
        onDismissRequest = {
            showLyricRenderConfigBottomSheet.value = false
        }
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
                lyricConfig?.let { config ->
                    val lyricFormatItems = LyricFormat.entries.map { stringResource(it.labelRes) }
                    val selectedLyricFormatIndex =
                        LyricFormat.entries.indexOf(config.format).coerceAtLeast(0)

                    val conversionModeItems =
                        ConversionMode.entries.map { stringResource(it.labelRes) }
                    val selectedConversionModeIndex =
                        ConversionMode.entries.indexOf(config.conversionMode).coerceAtLeast(0)

                    WindowDropdownPreference(
                        title = stringResource(R.string.lyric_mode),
                        items = lyricFormatItems,
                        selectedIndex = selectedLyricFormatIndex,
                        onSelectedIndexChange = { index ->
                            viewModel.setLyricFormat(LyricFormat.entries[index])
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = config.showRomanization,
                        onCheckedChange = { viewModel.setRomaEnabled(it) }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = config.showTranslation,
                        onCheckedChange = { viewModel.setTranslationEnabled(it) }
                    )
                    AnimatedVisibility(visible = config.showTranslation) {
                        SwitchPreference(
                            title = stringResource(R.string.only_translation_if_available),
                            summary = stringResource(R.string.only_translation_if_available_hint),
                            enabled = config.showTranslation,
                            checked = config.onlyTranslationIfAvailable,
                            onCheckedChange = { viewModel.setOnlyTranslationIfAvailable(it) }
                        )
                    }
                    SwitchPreference(
                        title = stringResource(R.string.remove_empty_lines),
                        summary = stringResource(R.string.remove_empty_lines_hint),
                        checked = config.removeEmptyLines,
                        onCheckedChange = { viewModel.setRemoveEmptyLines(it) }
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.conversion_mode),
                        items = conversionModeItems,
                        selectedIndex = selectedConversionModeIndex,
                        onSelectedIndexChange = {
                            viewModel.setConversionMode(ConversionMode.entries[it])
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun SearchResultItem(
    song: SongSearchResult,
    onPreviewClick: () -> Unit,
    onApplyClick: () -> Unit,
    onApplyLyricsOnlyClick: () -> Unit
) {

    var imageSize by remember(song.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(song.picUrl) {
        if (song.picUrl.isNotBlank()) {
            imageSize = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeStream(
                        java.net.URL(song.picUrl).openStream(),
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
        }
    }

    Card(
        modifier = Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(CardDefaults.CornerRadius))
            .clickable(onClick = { onPreviewClick() }),
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // 左侧图片
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

                    val textColor = if (isDarkTheme) Color.Black else Color.White

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
                    modifier = Modifier.weight(1f)
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
                        if (song.trackNumber.isNotBlank()) add("Track ${song.trackNumber}")
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

                    Spacer(modifier = Modifier.height(8.dp))



                        // 应用按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.surfaceVariant)
                                    .clickable { onApplyLyricsOnlyClick() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.apply_lyrics_only_action),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MiuixTheme.colorScheme.primary)
                                    .clickable { onApplyClick() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.apply_action),
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                    }
                }
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

