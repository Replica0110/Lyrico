package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.lyrics.visibleLyricLineTracks
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.StandardPluginField
import com.lonx.lyrico.data.model.search.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonColors
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.plugin.PluginIcon
import com.lonx.lyrico.ui.components.lyrics.LyricLineOrderBottomSheetContent
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.viewmodel.LyricsUiState
import com.lonx.lyrico.viewmodel.SearchSourceUiModel
import com.lonx.lyrico.viewmodel.SearchViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import java.net.URL
import androidx.compose.material3.CircularProgressIndicator as MaterialCircularProgressIndicator
import androidx.compose.material3.TextButton as MaterialTextButton

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
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    var pendingApplySong by remember { mutableStateOf<SongSearchResult?>(null) }
    var showApplyBottomSheet by remember { mutableStateOf(false) }

    val songSearchState = rememberTextFieldState(initialText = keyword ?: uiState.searchKeyword)
    val pagerState = rememberPagerState { uiState.availableSources.size + 1 }

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
        val selectedSource = uiState.selectedSearchSource
        val index = if (selectedSource == null) {
            0
        } else {
            uiState.availableSources.indexOf(selectedSource) + 1
        }
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
                if (page == 0) {
                    viewModel.onAllSourcesSelected()
                } else {
                    val source = uiState.availableSources.getOrNull(page - 1)
                    source?.let { viewModel.onSearchSourceSelected(it) }
                }
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
                    state = songSearchState,
                    placeholder = stringResource(id = R.string.search_lyrics_placeholder),
                    onSearch = { keyword ->
                        keyboardController?.hide()
                        viewModel.onKeywordChanged(keyword)
                        viewModel.performSearch()
                    },
                    actions = {
                        MaterialTextButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.onKeywordChanged(songSearchState.text.toString())
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
                .padding(scaffoldTopHorizontalPadding(paddingValues))
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
            SourcePillTabRow(
                tabs = listOf(
                    SourcePillTab(
                        label = stringResource(id = R.string.search_type_all),
                        imageVector = MiuixIcons.Search
                    )
                ) + uiState.availableSources.map { source ->
                    SourcePillTab(
                        label = source.labelRes?.let { stringResource(id = it) } ?: source.name,
                        iconPath = source.iconPath
                    )
                },
                selectedTabIndex = pagerState.targetPage,
                tabStyle = uiState.searchSourceTabStyle,
                onTabSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val allSourceResults = remember(
                uiState.searchKeyword,
                uiState.availableSources,
                uiState.searchResults
            ) {
                optimizedAllSourceResults(
                    keyword = uiState.searchKeyword,
                    sources = uiState.availableSources,
                    searchResults = uiState.searchResults
                )
            }

            /**
             * Pager
             */
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->

                val source = if (page == 0) null else uiState.availableSources.getOrNull(page - 1)

                val results = if (page == 0) {
                    allSourceResults
                } else {
                    uiState.searchResults[source?.id].orEmpty()
                }

                when {
                    uiState.isSearching && (page == 0 || source == uiState.selectedSearchSource) -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    page == 0 && uiState.searchErrors.isNotEmpty() && results.isEmpty() -> {
                        val errorMessage = uiState.searchErrors.values.firstOrNull()
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                text = errorMessage?.asString().orEmpty(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    page != 0 && source != null && uiState.searchErrors[source.id] != null -> {
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
                                    onClick = {
                                        pendingApplySong = song
                                        showApplyBottomSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    SearchResultApplyBottomSheet(
        show = showApplyBottomSheet,
        song = pendingApplySong,
        lyricsState = uiState.lyricsState,
        showAllFields = uiState.showAllSearchResultFields,
        onLoadLyrics = viewModel::loadLyrics,
        onOpenLyricsConfig = { showLyricRenderConfigBottomSheet.value = true },
        onDismissRequest = { showApplyBottomSheet = false },
        onDismissFinished = {
            pendingApplySong = null
            viewModel.clearLyrics()
        },
        onApply = { songToApply, lyrics, targets ->
            showApplyBottomSheet = false
            resultNavigator.navigateBack(
                LyricsSearchResult(
                    title = songToApply.title,
                    artist = songToApply.artist,
                    album = songToApply.album,
                    lyrics = lyrics,
                    date = songToApply.date,
                    trackerNumber = songToApply.trackNumber,
                    picUrl = songToApply.picUrl,
                    pluginId = songToApply.pluginId,
                    pluginName = songToApply.pluginName,
                    applyTargets = targets,
                    fields = songToApply.fields
                )
            )
        }
    )

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
            lyricConfig?.let { config ->
                LyricLineOrderBottomSheetContent(
                    lineOrder = config.normalizedLineOrder,
                    visibleTracks = visibleLyricLineTracks(
                        showRomanization = config.showRomanization,
                        showTranslation = config.showTranslation,
                        onlyTranslationIfAvailable = config.onlyTranslationIfAvailable
                    ),
                    onLineOrderChange = viewModel::setLyricLineOrder
                )
            }
        }
    }
}


@Composable
fun SearchResultItem(
    song: SongSearchResult,
    onClick: () -> Unit
) {

    var imageSize by remember(song.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(song.picUrl) {
        if (song.picUrl.isNotBlank()) {
            imageSize = withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(
                        URL(song.picUrl).openStream(),
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
            .clickable(onClick = onClick),
    ) {
        val formattedDuration = formatSearchResultDuration(song.duration)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        SearchResultBadge(
                            text = song.pluginName.ifBlank { song.pluginId }
                        )
                    }

                    val fields = song.normalizedFields()
                    val discNumber = fields["disc_number"].orEmpty()
                    val composer = fields["composer"].orEmpty()
                    val lyricist = fields["lyricist"].orEmpty()
                    val comment = fields["comment"].orEmpty()
                    val trackInfo = when {
                        song.trackNumber.isNotBlank() && discNumber.isNotBlank() ->
                            stringResource(
                                R.string.search_result_track_of_disc,
                                song.trackNumber,
                                discNumber
                            )

                        song.trackNumber.isNotBlank() ->
                            stringResource(R.string.label_track_number) + ": " + song.trackNumber

                        discNumber.isNotBlank() ->
                            stringResource(R.string.label_disc_number) + ": " + discNumber

                        else -> ""
                    }

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
                        if (trackInfo.isNotBlank()) add(trackInfo)
                    }.joinToString(" • ")

                    if (extraInfo.isNotEmpty()) {
                        SearchResultMetadataText(text = extraInfo)
                    }

                    if (lyricist.isNotEmpty()) {
                        SearchResultMetadataText(text = stringResource(R.string.label_lyricist) + ": " + lyricist)
                    }
                    if (composer.isNotBlank()) {
                        SearchResultMetadataText(text = stringResource(R.string.label_composer) + ": " + composer)
                    }

                    if (comment.isNotBlank()) {
                        SearchResultMetadataText(
                            text = stringResource(R.string.label_comment) + ": " + comment,
                            maxLines = 2
                        )
                    }
                }
            }

            if (formattedDuration.isNotBlank()) {
                SearchResultBadge(
                    text = formattedDuration,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun SearchResultMetadataText(
    text: String,
    maxLines: Int = 1
) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatSearchResultDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""

    val totalSeconds = durationMs / 1000
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(totalMinutes, seconds)
    }
}

@Composable
private fun SearchResultBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class SearchResultApplyOption(
    val target: MetadataFieldTarget,
    val value: String,
    val imageSize: Pair<Int, Int>? = null,
    val enabled: Boolean = true
)

@Composable
private fun SearchResultApplyBottomSheet(
    show: Boolean,
    song: SongSearchResult?,
    lyricsState: LyricsUiState,
    showAllFields: Boolean,
    onLoadLyrics: (SongSearchResult) -> Unit,
    onOpenLyricsConfig: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onApply: (song: SongSearchResult, lyrics: String?, targets: Set<MetadataFieldTarget>) -> Unit
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val applyPagerState = rememberPagerState { 2 }
    var imageSize by remember(song?.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }
    var knownTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }

    LaunchedEffect(song?.id, song?.pluginId) {
        val targetSong = song ?: return@LaunchedEffect
        applyPagerState.scrollToPage(0)
        onLoadLyrics(targetSong)
    }

    LaunchedEffect(song?.picUrl) {
        val picUrl = song?.picUrl.orEmpty()
        if (picUrl.isNotBlank()) {
            imageSize = withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(
                        URL(picUrl).openStream(),
                        null,
                        options
                    )
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        options.outWidth to options.outHeight
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val lyricsText =
        if (lyricsState.song?.id == song?.id && lyricsState.song?.pluginId == song?.pluginId) {
            lyricsState.content?.takeIf { it.isNotBlank() }
        } else {
            null
        }
    val lyricsLoadingText = stringResource(R.string.lyrics_loading)
    val lyricsLoadFailedText = stringResource(R.string.fetch_lyrics_failed)
    val lyricsEmptyText = stringResource(R.string.lyrics_empty)
    val emptyFieldText = stringResource(R.string.search_result_empty_value)
    val lyricsStatusText = when {
        lyricsState.isLoading -> lyricsLoadingText
        lyricsState.error != null -> lyricsState.error.asString()?.ifBlank { lyricsLoadFailedText }
            ?: lyricsLoadFailedText

        else -> lyricsEmptyText
    }

    val options = remember(
        song,
        lyricsText,
        lyricsState.isLoading,
        lyricsState.error,
        lyricsStatusText,
        imageSize,
        showAllFields,
        emptyFieldText
    ) {
        val targetSong = song ?: return@remember emptyList()
        val fields = targetSong.normalizedFields().toMutableMap()
        lyricsText?.let { fields["lyrics"] = it }

        val fieldOptions = StandardPluginField.entries
            .asSequence()
            .filter { it.target != MetadataFieldTarget.LYRICS }
            .sortedBy { field -> if (field.target == MetadataFieldTarget.COVER) 0 else 1 }
            .mapNotNull { field ->
                val value = fields[field.key].orEmpty()
                when {
                    value.isNotBlank() -> SearchResultApplyOption(
                        target = field.target,
                        value = value,
                        imageSize = if (field.target == MetadataFieldTarget.COVER) imageSize else null
                    )

                    showAllFields -> SearchResultApplyOption(
                        target = field.target,
                        value = emptyFieldText
                    )

                    else -> null
                }
            }
            .distinctBy { it.target }
            .toMutableList()

        if (lyricsText != null) {
            fieldOptions += SearchResultApplyOption(
                target = MetadataFieldTarget.LYRICS,
                value = lyricsText
            )
        } else if (
            showAllFields ||
            lyricsState.isLoading ||
            lyricsState.error != null ||
            lyricsState.song != null
        ) {
            fieldOptions += SearchResultApplyOption(
                target = MetadataFieldTarget.LYRICS,
                value = if (showAllFields && lyricsState.song == null) {
                    emptyFieldText
                } else {
                    lyricsStatusText
                }
            )
        }

        fieldOptions.sortedBy { if (it.target == MetadataFieldTarget.COVER) 0 else 1 }
    }

    LaunchedEffect(options) {
        val targets = options.filter { it.enabled }.map { it.target }.toSet()
        val newTargets = targets - knownTargets
        selectedTargets = when {
            knownTargets.isEmpty() -> targets
            else -> (selectedTargets intersect targets) + newTargets
        }
        knownTargets = targets
    }

    val enabledTargets = options.filter { it.enabled }.map { it.target }.toSet()
    val allSelected = enabledTargets.isNotEmpty() && selectedTargets.containsAll(enabledTargets)
    val dataOptions = options.filter { it.target != MetadataFieldTarget.LYRICS }
    val lyricsOption = options.firstOrNull { it.target == MetadataFieldTarget.LYRICS }

    ActionBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = false,
        startAction = {
            PillButton(
                text = stringResource(
                    if (allSelected) R.string.action_deselect_all else R.string.action_select_all
                ),
                onClick = {
                    selectedTargets = if (allSelected) {
                        emptySet()
                    } else {
                        enabledTargets
                    }
                },
                style = PillButtonDefaults.style(PillButtonSize.Large),
            )
        },
        endAction = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PillButton(
                    text = stringResource(R.string.apply_lyrics_only_action),
                    enabled = !lyricsState.isLoading && song != null,
                    leading = if (lyricsState.isLoading) {
                        {
                            MaterialCircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        song?.let {
                            selectedTargets = setOf(MetadataFieldTarget.LYRICS)
                            if (lyricsText.isNullOrBlank()) {
                                onLoadLyrics(it)
                            } else {
                                onApply(
                                    it,
                                    lyricsText,
                                    setOf(MetadataFieldTarget.LYRICS)
                                )
                            }
                        }
                    },
                    style = PillButtonDefaults.style(PillButtonSize.Large)
                )
                PillButton(
                    text = stringResource(R.string.apply_action),
                    onClick = {
                        song?.let {
                            onApply(
                                it,
                                lyricsText?.takeIf { MetadataFieldTarget.LYRICS in selectedTargets },
                                selectedTargets
                            )
                        }
                    },
                    style = PillButtonDefaults.style(PillButtonSize.Large)
                )
            }
        },
        content = {
            Column(){
                ApplySheetPillTabs(
                    selectedTabIndex = applyPagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch {
                            applyPagerState.animateScrollToPage(index)
                        }
                    }
                )
                HorizontalPager(
                    state = applyPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) { page ->
                    when (page) {
                        0 -> SearchResultApplyDataPage(
                            options = dataOptions,
                            selectedTargets = selectedTargets,
                            onSelectedTargetsChange = { selectedTargets = it }
                        )

                        1 -> SearchResultApplyLyricsPage(
                            option = lyricsOption,
                            selected = MetadataFieldTarget.LYRICS in selectedTargets,
                            lyricsText = lyricsText,
                            lyricsState = lyricsState,
                            loadingText = lyricsLoadingText,
                            failedText = lyricsLoadFailedText,
                            onSelectedChange = { selected ->
                                if (lyricsOption?.enabled == true) {
                                    selectedTargets = if (selected) {
                                        selectedTargets + MetadataFieldTarget.LYRICS
                                    } else {
                                        selectedTargets - MetadataFieldTarget.LYRICS
                                    }
                                }
                            },
                            onOpenLyricsConfig = onOpenLyricsConfig,
                            onCopyLyrics = {
                                scope.launch {
                                    val clipData = ClipData.newPlainText("copy lyrics", lyricsText)
                                    val clipEntry = ClipEntry(clipData)
                                    clipboardManager.setClipEntry(clipEntry)
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    )
}

@Composable
private fun ApplySheetPillTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        stringResource(R.string.search_apply_tab_metadata),
        stringResource(R.string.label_lyrics)
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedTabIndex
            PillButton(
                text = label,
                selected = selected,
                onClick = { onTabSelected(index) },
                style = PillButtonDefaults.style(PillButtonSize.Medium).copy(
                    textStyle = MiuixTheme.textStyles.body2.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    selectedTextStyle = MiuixTheme.textStyles.body2.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            )
        }
    }
}

@Composable
private fun SearchResultApplyDataPage(
    options: List<SearchResultApplyOption>,
    selectedTargets: Set<MetadataFieldTarget>,
    onSelectedTargetsChange: (Set<MetadataFieldTarget>) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (options.isEmpty()) {
            item("empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_no_results),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                    )
                }
            }
        } else {
            items(options, key = { it.target.name }) { option ->
                SearchResultApplyOptionItem(
                    option = option,
                    selected = option.enabled && option.target in selectedTargets,
                    onSelectedChange = { selected ->
                        if (!option.enabled) return@SearchResultApplyOptionItem
                        onSelectedTargetsChange(
                            if (selected) {
                                selectedTargets + option.target
                            } else {
                                selectedTargets - option.target
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchResultApplyLyricsPage(
    option: SearchResultApplyOption?,
    selected: Boolean,
    lyricsText: String?,
    lyricsState: LyricsUiState,
    loadingText: String,
    failedText: String,
    onSelectedChange: (Boolean) -> Unit,
    onOpenLyricsConfig: () -> Unit,
    onCopyLyrics: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_lyrics),
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurfaceContainer
                )
                Text(
                    text = option?.value ?: stringResource(R.string.lyrics_empty),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onOpenLyricsConfig) {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = null
                )
            }
            Checkbox(
                state = if (selected && option?.enabled == true) ToggleableState.On else ToggleableState.Off,
                onClick = {
                    if (option?.enabled == true) {
                        onSelectedChange(!selected)
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    lyricsState.isLoading -> item("loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Text(
                                    text = loadingText,
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                                )
                            }
                        }
                    }

                    lyricsState.error != null -> item("error") {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = lyricsState.error.asString() ?: failedText,
                            style = MiuixTheme.textStyles.body2
                        )
                    }

                    else -> item("lyrics") {
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = lyricsText ?: stringResource(R.string.lyrics_empty),
                            style = MiuixTheme.textStyles.body2
                        )
                    }
                }
            }

            if (!lyricsText.isNullOrBlank()) {
                IconButton(
                    onClick = onCopyLyrics,
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
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultApplyOptionItem(
    option: SearchResultApplyOption,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val isCover = option.target == MetadataFieldTarget.COVER

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (option.enabled) {
                    onSelectedChange(!selected)
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCover) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LyricoColors.coverPlaceholder)
            ) {
                AsyncImage(
                    model = option.value,
                    contentDescription = null,
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
                option.imageSize?.let { size ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MiuixTheme.colorScheme.onSecondaryContainer
                                    )
                                )
                            )
                    ) {
                        Text(
                            text = "${size.first}×${size.second}",
                            color = if (isDarkTheme) Color.Black else Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(option.target.labelRes),
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.SemiBold,
                color = if (option.enabled) {
                    MiuixTheme.colorScheme.onSurfaceContainer
                } else {
                    MiuixTheme.colorScheme.onSurfaceContainerVariant
                }
            )
            Text(
                text = option.value,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                maxLines = if (option.target == MetadataFieldTarget.LYRICS) 4 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Checkbox(
            state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = {
                if (option.enabled) {
                    onSelectedChange(!selected)
                }
            }
        )
    }
}

data class SourcePillTab(
    val label: String,
    val iconPath: String? = null,
    val imageVector: ImageVector? = null
)

private fun optimizedAllSourceResults(
    keyword: String,
    sources: List<SearchSourceUiModel>,
    searchResults: Map<String, List<SongSearchResult>>
): List<SongSearchResult> {
    val localSegments = MusicMatchUtils.splitToSegments(keyword)

    return sources
        .flatMap { source ->
            searchResults[source.id]
                .orEmpty()
                .mapIndexed { index, result ->
                    result to MusicMatchUtils.calculateCoverMatchScore(
                        localSegments = localSegments,
                        coverTitle = result.title,
                        coverArtist = result.artist,
                        rankIndex = index
                    )
                }
                .sortedByDescending { (_, score) -> score }
                .take(ALL_SOURCE_RESULT_LIMIT)
        }
        .sortedByDescending { (_, score) -> score }
        .map { (result, _) -> result }
}

@Composable
fun SourcePillTabRow(
    tabs: List<SourcePillTab>,
    selectedTabIndex: Int,
    tabStyle: SearchSourceTabStyle,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val bringIntoViewRequesters = remember(tabs.size) {
        List(tabs.size) { BringIntoViewRequester() }
    }

    LaunchedEffect(selectedTabIndex, bringIntoViewRequesters) {
        bringIntoViewRequesters.getOrNull(selectedTabIndex)?.bringIntoView()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTabIndex
            val hasIcon = tab.imageVector != null || tab.iconPath != null
            val showIcon = tabStyle != SearchSourceTabStyle.TEXT_ONLY && hasIcon
            val showText = tabStyle != SearchSourceTabStyle.ICON_ONLY || !showIcon

            val iconTint = if (selected) {
                MiuixTheme.colorScheme.onPrimary
            } else {
                MiuixTheme.colorScheme.onSurface
            }

            PillButton(
                text = tab.label,
                selected = selected,
                showText = showText,
                style = PillButtonDefaults.style(PillButtonSize.Medium),
                modifier = Modifier.bringIntoViewRequester(
                    bringIntoViewRequesters[index]
                ),
                colors = PillButtonDefaults.colors(
                    containerColor = MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                ),
                onClick = {
                    onTabSelected(index)
                },
                leading = if (showIcon) {
                    {
                        SourcePillTabIcon(
                            tab = tab,
                            tint = iconTint
                        )
                    }
                } else {
                    null
                }
            )
        }
    }
}

@Composable
private fun SourcePillTabIcon(
    tab: SourcePillTab,
    tint: Color
) {
    when {
        tab.imageVector != null -> {
            Icon(
                imageVector = tab.imageVector,
                contentDescription = tab.label,
                modifier = Modifier.size(16.dp),
                tint = tint
            )
        }

        else -> {
            PluginIcon(
                iconPath = tab.iconPath,
                contentDescription = tab.label,
                size = 18.dp
            )
        }
    }
}

private const val ALL_SOURCE_RESULT_LIMIT = 3
