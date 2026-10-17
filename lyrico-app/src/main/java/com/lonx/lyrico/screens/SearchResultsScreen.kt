@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.lonx.lyrico.screens

import com.lonx.lyrico.ui.components.icons.LucideIcons
import android.annotation.SuppressLint
import android.content.ClipData
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.StandardPluginField
import com.lonx.lyrico.data.model.search.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.moriafly.salt.ui.icons.Check
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.icons.Uncheck
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonColors
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.lyrics.LyricRenderConfigBottomSheet
import com.lonx.lyrico.ui.components.lyrics.LyricsPreviewPane
import com.lonx.lyrico.ui.components.plugin.PluginIcon
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.components.scaffoldTopAppBarInsetsPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.theme.isDarkTheme
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.viewmodel.LyricsUiState
import com.lonx.lyrico.viewmodel.SearchSourceUiModel
import com.lonx.lyrico.viewmodel.SearchUiState
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT

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

    val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
        SearchBar(
            modifier = fieldModifier,
            state = songSearchState,
            placeholder = stringResource(id = R.string.search_main_placeholder),
            onSearch = { keyword ->
                keyboardController?.hide()
                viewModel.onKeywordChanged(keyword)
                viewModel.performSearch()
            },
            actions = {
                val submit = {
                    keyboardController?.hide()
                    viewModel.onKeywordChanged(songSearchState.text.toString())
                    viewModel.performSearch()
                }
                if (salt) {
                    com.moriafly.salt.ui.Button(
                        onClick = submit,
                        text = stringResource(id = R.string.action_search),
                        modifier = Modifier.heightIn(min = 32.dp),
                        size = com.moriafly.salt.ui.ControlSize.Small,
                        appearance = com.moriafly.salt.ui.ButtonAppearance.Plain,
                    )
                } else {
                    MaterialTextButton(onClick = submit) {
                        Text(
                            text = stringResource(id = R.string.action_search),
                            style = MiuixTheme.textStyles.main,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                }

                val selectedSourceSupportsLyrics = if (pagerState.currentPage == 0) {
                    uiState.availableSources.any { it.supportsLyrics }
                } else {
                    uiState.availableSources.getOrNull(pagerState.currentPage - 1)
                        ?.supportsLyrics == true
                }
                if (selectedSourceSupportsLyrics) {
                    if (salt) {
                        com.lonx.lyrico.ui.components.salt.SaltIconButton(
                            icon = LucideIcons.Settings,
                            label = stringResource(R.string.search_settings),
                            onClick = { showLyricRenderConfigBottomSheet.value = true }
                        )
                    } else IconButton(
                        onClick = {
                            showLyricRenderConfigBottomSheet.value = true
                        }
                    ) {
                        Icon(
                            imageVector = LucideIcons.Settings,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            }
        )
    }

    if (salt) {
        // Salt 该页没有标题栏：固定搜索行为首屏。
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalIndication provides
                    com.moriafly.salt.ui.SaltTheme.configs.indication,
            androidx.compose.foundation.LocalOverscrollFactory provides
                    com.moriafly.salt.ui.gestures.cupertino.CupertinoOverscrollEffectFactory(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(com.moriafly.salt.ui.SaltTheme.colors.background)
                    .windowInsetsPadding(
                        com.lonx.lyrico.ui.components.scaffoldPageWindowInsets().only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Horizontal +
                                androidx.compose.foundation.layout.WindowInsetsSides.Bottom
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(
                            androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
                                androidx.compose.foundation.layout.WindowInsetsSides.Top
                            )
                        )
                        .padding(vertical = 8.dp)
                ) {
                    searchField(Modifier.padding(horizontal = 12.dp))
                }
                SearchResultsContent(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    uiState = uiState,
                    pagerState = pagerState,
                    salt = true,
                    onSongClick = { song ->
                        pendingApplySong = song
                        showApplyBottomSheet = true
                    }
                )
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier
                        .scaffoldTopAppBarInsetsPadding()
                        .padding(vertical = 8.dp)
                ) {
                    searchField(Modifier.padding(horizontal = 12.dp))
                }
            }
        ) { paddingValues ->
            SearchResultsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldTopHorizontalPadding(paddingValues)),
                viewModel = viewModel,
                uiState = uiState,
                pagerState = pagerState,
                salt = false,
                onSongClick = { song ->
                    pendingApplySong = song
                    showApplyBottomSheet = true
                }
            )
        }
    }


    SearchResultApplyBottomSheet(
        show = showApplyBottomSheet,
        song = pendingApplySong,
        supportsLyrics = uiState.availableSources.firstOrNull {
            it.id == pendingApplySong?.pluginId
        }?.supportsLyrics == true,
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

    LyricRenderConfigBottomSheet(
        show = showLyricRenderConfigBottomSheet.value,
        config = lyricConfig,
        onDismissRequest = { showLyricRenderConfigBottomSheet.value = false },
        onLyricFormatChange = viewModel::setLyricFormat,
        onRomaEnabledChange = viewModel::setRomaEnabled,
        onTranslationEnabledChange = viewModel::setTranslationEnabled,
        onOnlyTranslationIfAvailableChange = viewModel::setOnlyTranslationIfAvailable,
        onRemoveEmptyLinesChange = viewModel::setRemoveEmptyLines,
        onConversionModeChange = viewModel::setConversionMode
    )
}

/** 结果分页：来源胶囊、加载/错误/空状态与列表在两种引擎下共用同一套状态。 */
@Composable
private fun SearchResultsContent(
    modifier: Modifier,
    viewModel: SearchViewModel,
    uiState: SearchUiState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    salt: Boolean,
    onSongClick: (SongSearchResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier) {
            /**
             * 初始化 loading
             */
            if (uiState.isInitializing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (salt) SaltResultsProgress() else CircularProgressIndicator()
                }
                return@Column
            }

            if (uiState.availableSources.isEmpty()) {
                IllustratedEmptyState(
                    kind = EmptyStateKind.Plugins,
                    title = stringResource(R.string.plugin_empty),
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            /**
             * Tabs
             */
            SourcePillTabRow(
                tabs = listOf(
                    SourcePillTab(
                        label = stringResource(id = R.string.search_type_all),
                        imageVector = LucideIcons.Search
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

            val allSourceResults = rememberOptimizedAllSourceResults(
                keyword = uiState.searchKeyword,
                sources = uiState.availableSources,
                searchResultPages = uiState.searchResultPages
            )

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
                val pageSourceIds = if (page == 0) {
                    uiState.availableSources.map { it.id }
                } else {
                    listOfNotNull(source?.id)
                }
                val loadMoreError = pageSourceIds
                    .firstNotNullOfOrNull { sourceId ->
                        uiState.loadMoreErrors[sourceId]?.let { sourceId to it }
                    }
                val isLoadingMore = pageSourceIds.any { it in uiState.loadingMoreSourceIds }
                val canLoadMore = pageSourceIds.any { uiState.hasMoreBySource[it] == true }

                when {
                    uiState.isSearching && (page == 0 || source == uiState.selectedSearchSource) -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (salt) SaltResultsProgress() else CircularProgressIndicator()
                        }
                    }

                    page == 0 && uiState.searchErrors.isNotEmpty() && results.isEmpty() -> {
                        val errorMessage = uiState.searchErrors.values.firstOrNull()
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (salt) com.moriafly.salt.ui.Text(
                                text = errorMessage?.asString().orEmpty(),
                                style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                                color = com.moriafly.salt.ui.SaltTheme.colors.error
                            ) else Text(
                                text = errorMessage?.asString().orEmpty(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    page != 0 && source != null && uiState.searchErrors[source.id] != null -> {
                        val errorMessage = uiState.searchErrors[source.id]
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (salt) com.moriafly.salt.ui.Text(
                                text = errorMessage?.asString().orEmpty(),
                                style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                                color = com.moriafly.salt.ui.SaltTheme.colors.error
                            ) else Text(
                                text = errorMessage?.asString().orEmpty(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    results.isEmpty() -> {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Search,
                            title = stringResource(R.string.search_no_results),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        PaginatedSearchResultList(
                            results = results,
                            searchKeyword = uiState.searchKeyword,
                            canLoadMore = canLoadMore,
                            isLoadingMore = isLoadingMore,
                            loadMoreError = loadMoreError?.second,
                            onLoadMore = {
                                viewModel.loadNextPage(
                                    sourceId = loadMoreError?.first ?: source?.id
                                )
                            },
                            salt = salt,
                            onSongClick = onSongClick
                        )
                    }
                }
            }
    }
}

/** SaltUI 没有加载指示器，统一使用 Salt 配色的回退控件。 */
@Composable
private fun SaltResultsProgress(size: androidx.compose.ui.unit.Dp = 32.dp) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = Modifier.width(size).height(size),
        color = com.moriafly.salt.ui.SaltTheme.colors.highlight,
        trackColor = com.moriafly.salt.ui.SaltTheme.colors.stroke,
    )
}

@Composable
private fun PaginatedSearchResultList(
    results: List<SongSearchResult>,
    searchKeyword: String,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: UiMessage?,
    salt: Boolean,
    onLoadMore: () -> Unit,
    onSongClick: (SongSearchResult) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(searchKeyword) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        items(results, key = { "${it.pluginId.length}:${it.pluginId}${it.id}" }) { song ->
            SearchResultItem(
                song = song,
                onClick = { onSongClick(song) }
            )
        }

        when {
            isLoadingMore -> {
                item(key = "search_loading_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (salt) SaltResultsProgress(size = 20.dp)
                        else CircularProgressIndicator(size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        if (salt) com.moriafly.salt.ui.Text(
                            text = stringResource(R.string.search_loading_more),
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText
                        ) else Text(
                            text = stringResource(R.string.search_loading_more),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            loadMoreError != null -> {
                item(key = "search_load_more_error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (salt) PillButton(
                            onClick = onLoadMore,
                            text = stringResource(R.string.search_load_more_failed),
                            style = PillButtonDefaults.style(PillButtonSize.Medium),
                            colors = PillButtonDefaults.plainColors(),
                        ) else TextButton(
                            text = stringResource(R.string.search_load_more_failed),
                            onClick = onLoadMore,
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }

            canLoadMore -> {
                item(key = "search_load_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (salt) PillButton(
                            onClick = onLoadMore,
                            text = stringResource(R.string.search_load_more),
                            style = PillButtonDefaults.style(PillButtonSize.Medium),
                            colors = PillButtonDefaults.plainColors(),
                        ) else TextButton(
                            text = stringResource(R.string.search_load_more),
                            onClick = onLoadMore,
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SearchResultItem(
    song: SongSearchResult,
    showCover: Boolean = true,
    showExtendedMetadata: Boolean = true,
    onClick: () -> Unit
) {

    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    var imageSize by remember(song.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(song.picUrl) {
        if (showCover && song.picUrl.isNotBlank()) {
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

    val itemContent: @Composable () -> Unit = {
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
                if (showCover) {
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
                                            // Salt 取卡片的前景色，浅色主题下压暗才衬得住白色尺寸文字。
                                            if (salt) com.moriafly.salt.ui.SaltTheme.colors.text
                                            else MiuixTheme.colorScheme.onSecondaryContainer
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
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (salt) com.moriafly.salt.ui.Text(
                            text = song.title,
                            style = searchResultTitleStyle(),
                            fontWeight = FontWeight.SemiBold,
                            color = com.moriafly.salt.ui.SaltTheme.colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        ) else Text(
                            text = song.title,
                            style = searchResultTitleStyle(),
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
                        if (salt) com.moriafly.salt.ui.Text(
                            text = artistAlbum,
                            style = searchResultSecondaryStyle(),
                            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) else Text(
                            text = artistAlbum,
                            style = searchResultSecondaryStyle(),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val extraInfo = buildList {
                        if (song.date.isNotBlank()) add(song.date)
                        if (showExtendedMetadata && trackInfo.isNotBlank()) add(trackInfo)
                    }.joinToString(" • ")

                    if (extraInfo.isNotEmpty()) {
                        SearchResultMetadataText(text = extraInfo)
                    }

                    if (showExtendedMetadata && lyricist.isNotEmpty()) {
                        SearchResultMetadataText(text = stringResource(R.string.label_lyricist) + ": " + lyricist)
                    }
                    if (showExtendedMetadata && composer.isNotBlank()) {
                        SearchResultMetadataText(text = stringResource(R.string.label_composer) + ": " + composer)
                    }

                    if (showExtendedMetadata && comment.isNotBlank()) {
                        SearchResultMetadataText(
                            text = stringResource(R.string.label_comment) + ": " + comment,
                            maxLines = 2
                        )
                    }
                }
            }

            if (showExtendedMetadata && formattedDuration.isNotBlank()) {
                SearchResultBadge(
                    text = formattedDuration,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }

    if (salt) {
        // Salt 用原生 Card 承载，但内部排列与 Miuix 完全一致。
        com.moriafly.salt.ui.Card(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            // 卡片底色在 modifier 之后绘制，点击涟漪必须画在 Card 内容里才看得见。
            Box(Modifier.clickable(onClick = onClick)) { itemContent() }
        }
    } else {
        Card(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(CardDefaults.CornerRadius))
                .clickable(onClick = onClick),
        ) {
            itemContent()
        }
    }
}

/**
 * 搜索结果条目的字号。
 *
 * Salt 的语义样式（main 16sp / sub 12sp）比 Miuix 对应层级（body1 16sp / footnote2 11sp）大一档，
 * 共用同一套排列后 Salt 的结果列表会显得更松；这里把 Salt 收一档：标题 15sp、副文 11sp。
 */
@Composable
private fun searchResultTitleStyle(): TextStyle {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    return if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.main.copy(fontSize = 15.sp)
    else MiuixTheme.textStyles.body1
}

@Composable
private fun searchResultSecondaryStyle(): TextStyle {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    return if (salt) com.moriafly.salt.ui.SaltTheme.textStyles.sub.copy(fontSize = 11.sp)
    else MiuixTheme.textStyles.footnote2
}

@Composable
private fun SearchResultMetadataText(
    text: String,
    maxLines: Int = 1
) {
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
        com.lonx.lyrico.data.model.UiEngine.SALT
    ) {
        com.moriafly.salt.ui.Text(
            text = text,
            style = searchResultSecondaryStyle(),
            color = com.moriafly.salt.ui.SaltTheme.colors.subText,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    Text(
        text = text,
        style = searchResultSecondaryStyle(),
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
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (salt) com.moriafly.salt.ui.SaltTheme.colors.highlight.copy(alpha = 0.12f)
                else MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (salt) com.moriafly.salt.ui.Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = com.moriafly.salt.ui.SaltTheme.colors.highlight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        ) else Text(
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
    supportsLyrics: Boolean,
    lyricsState: LyricsUiState,
    showAllFields: Boolean,
    onLoadLyrics: (SongSearchResult) -> Unit,
    onOpenLyricsConfig: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onApply: (song: SongSearchResult, lyrics: String?, targets: Set<MetadataFieldTarget>) -> Unit
) {
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
        com.lonx.lyrico.data.model.UiEngine.SALT
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val applyPagerState = rememberPagerState { if (supportsLyrics) 2 else 1 }
    var imageSize by remember(song?.picUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }
    var knownTargets by remember(song?.id, song?.pluginId) {
        mutableStateOf(emptySet<MetadataFieldTarget>())
    }

    LaunchedEffect(song?.id, song?.pluginId, supportsLyrics) {
        val targetSong = song ?: return@LaunchedEffect
        applyPagerState.scrollToPage(0)
        if (supportsLyrics) {
            onLoadLyrics(targetSong)
        }
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
        emptyFieldText,
        supportsLyrics
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

        if (supportsLyrics && lyricsText != null) {
            fieldOptions += SearchResultApplyOption(
                target = MetadataFieldTarget.LYRICS,
                value = lyricsText
            )
        } else if (supportsLyrics && (
            showAllFields ||
            lyricsState.isLoading ||
            lyricsState.error != null ||
            lyricsState.song != null
        )) {
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

    val toggleAll = {
        selectedTargets = if (allSelected) emptySet() else enabledTargets
    }
    val applySelection = {
        song?.let { onApply(it, lyricsText?.takeIf { MetadataFieldTarget.LYRICS in selectedTargets }, selectedTargets) }
        Unit
    }
    val applyLyricsOnly = {
        song?.let {
            selectedTargets = setOf(MetadataFieldTarget.LYRICS)
            if (lyricsText.isNullOrBlank()) onLoadLyrics(it)
            else onApply(it, lyricsText, setOf(MetadataFieldTarget.LYRICS))
        }
        Unit
    }
    val renderPage: @Composable (Int) -> Unit = { page ->
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
    }
    if (salt) {
        com.lonx.lyrico.ui.components.salt.SaltBottomSheet(
            show = show, onDismissRequest = onDismissRequest, onDismissFinished = onDismissFinished,
            header = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.moriafly.salt.ui.Text(song?.title.orEmpty(), modifier = Modifier.weight(1f),
                            style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        PillButton(
                            text = stringResource(if (allSelected) R.string.action_deselect_all else R.string.action_select_all),
                            onClick = toggleAll, style = PillButtonDefaults.style(PillButtonSize.Medium),
                        )
                    }
                    if (supportsLyrics) ApplySheetPillTabs(
                        selectedTabIndex = applyPagerState.targetPage,
                        onTabSelected = { index -> scope.launch { applyPagerState.animateScrollToPage(index) } },
                    )
                }
            },
            footer = {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) 1 else 2,
                ) {
                    if (supportsLyrics) com.moriafly.salt.ui.Button(
                        text = stringResource(R.string.apply_lyrics_only_action), onClick = applyLyricsOnly,
                        enabled = !lyricsState.isLoading && song != null,
                        modifier = Modifier.weight(1f), size = com.moriafly.salt.ui.ControlSize.Small,
                        appearance = com.moriafly.salt.ui.ButtonAppearance.Subtle,
                    )
                    com.moriafly.salt.ui.Button(
                        text = stringResource(R.string.apply_action), onClick = applySelection,
                        enabled = song != null && selectedTargets.isNotEmpty(),
                        modifier = Modifier.weight(1f), size = com.moriafly.salt.ui.ControlSize.Small,
                    )
                }
            },
        ) {
            HorizontalPager(state = applyPagerState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) { renderPage(it) }
        }
        return
    }

    ActionBottomSheet(
        show = show,
        saltScrollContent = false,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        enableNestedScroll = false,
        startAction = {
            PillButton(
                text = stringResource(
                    if (allSelected) R.string.action_deselect_all else R.string.action_select_all
                ),
                onClick = toggleAll,
                style = PillButtonDefaults.style(PillButtonSize.Large),
            )
        },
        endAction = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (supportsLyrics) {
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
                        onClick = applyLyricsOnly,
                        style = PillButtonDefaults.style(PillButtonSize.Large)
                    )
                }
                PillButton(
                    text = stringResource(R.string.apply_action),
                    onClick = applySelection,
                    style = PillButtonDefaults.style(PillButtonSize.Large)
                )
            }
        },
        content = {
            Column(Modifier.fillMaxWidth()) {
                if (supportsLyrics) {
                    ApplySheetPillTabs(
                        selectedTabIndex = applyPagerState.targetPage,
                        onTabSelected = { index ->
                            scope.launch {
                                applyPagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
                HorizontalPager(
                    state = applyPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (salt) Modifier.weight(1f, fill = false).heightIn(max = 420.dp)
                        else Modifier.height(420.dp))
                ) { page ->
                    renderPage(page)
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
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT

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
                style = if (salt) PillButtonDefaults.style(PillButtonSize.Medium)
                else PillButtonDefaults.style(PillButtonSize.Medium).copy(
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
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT
    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (salt) 0.dp else 8.dp)
        ) {
            if (options.isEmpty()) {
                item("empty") {
                    IllustratedEmptyState(
                        kind = EmptyStateKind.Search,
                        title = stringResource(R.string.search_no_results),
                        modifier = Modifier.fillMaxWidth(),
                    )
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
    if (salt) com.moriafly.salt.ui.RoundedColumn(Modifier.fillMaxSize()) { listContent() }
    else listContent()

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
    if (com.lonx.lyrico.ui.theme.LocalUiEngine.current == com.lonx.lyrico.data.model.UiEngine.SALT) {
        // 歌词选中行和歌词正文是同一个 Item：同一个 RoundedColumn，不再拆成两块。
        com.moriafly.salt.ui.RoundedColumn(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.moriafly.salt.ui.ItemCheck(
                    text = stringResource(R.string.label_lyrics), state = selected && option?.enabled == true,
                    enabled = option?.enabled == true, onChange = onSelectedChange,
                    modifier = Modifier.weight(1f),
                )
                com.lonx.lyrico.ui.components.salt.SaltIconButton(
                    icon = LucideIcons.Copy, label = stringResource(R.string.action_copy_lyrics),
                    onClick = onCopyLyrics, enabled = !lyricsText.isNullOrBlank(),
                    size = 32.dp, iconSize = 18.dp,
                )
                Box(Modifier.padding(end = 8.dp)) {
                    com.lonx.lyrico.ui.components.salt.SaltIconButton(
                        icon = LucideIcons.Settings, label = stringResource(R.string.search_settings),
                        onClick = onOpenLyricsConfig, size = 32.dp, iconSize = 18.dp,
                    )
                }
            }
            LyricsPreviewPane(
                lyricsText = lyricsText, isLoading = lyricsState.isLoading, error = lyricsState.error,
                loadingText = loadingText, failedText = failedText,
                emptyText = stringResource(R.string.lyrics_empty), onCopyLyrics = onCopyLyrics,
                // 与上方 ItemCheck 的文字左缘对齐；RoundedColumn 不提供内容内边距。
                modifier = Modifier.weight(1f).fillMaxWidth(),
                showCopyAction = false,
                contentPadding = PaddingValues(horizontal = 16.dp),
            )
        }
        return
    }
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
            val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                    com.lonx.lyrico.data.model.UiEngine.SALT
            Column(modifier = Modifier.weight(1f)) {
                if (salt) {
                    com.moriafly.salt.ui.Text(
                        text = stringResource(R.string.label_lyrics),
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                        fontWeight = FontWeight.SemiBold,
                        color = com.moriafly.salt.ui.SaltTheme.colors.text
                    )
                    com.moriafly.salt.ui.Text(
                        text = option?.value ?: stringResource(R.string.lyrics_empty),
                        style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                        color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
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
            }
            if (salt) {
                com.lonx.lyrico.ui.components.salt.SaltIconButton(
                    icon = LucideIcons.Settings,
                    label = "",
                    onClick = onOpenLyricsConfig,
                    size = 36.dp,
                    iconSize = 20.dp
                )
            } else IconButton(onClick = onOpenLyricsConfig) {
                Icon(
                    imageVector = LucideIcons.Settings,
                    contentDescription = null
                )
            }
            if (salt) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = option?.enabled == true) {
                            if (option?.enabled == true) {
                                onSelectedChange(!selected)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    com.moriafly.salt.ui.Icon(
                        painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                            if (selected && option?.enabled == true) com.moriafly.salt.ui.icons.SaltIcons.Check
                            else com.moriafly.salt.ui.icons.SaltIcons.Uncheck
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = com.moriafly.salt.ui.SaltTheme.colors.highlight
                    )
                }
            } else Checkbox(
                state = if (selected && option?.enabled == true) ToggleableState.On else ToggleableState.Off,
                onClick = {
                    if (option?.enabled == true) {
                        onSelectedChange(!selected)
                    }
                }
            )
        }

        LyricsPreviewPane(
            lyricsText = lyricsText,
            isLoading = lyricsState.isLoading,
            error = lyricsState.error,
            loadingText = loadingText,
            failedText = failedText,
            emptyText = stringResource(R.string.lyrics_empty),
            onCopyLyrics = onCopyLyrics,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SearchResultApplyOptionItem(
    option: SearchResultApplyOption,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val isCover = option.target == MetadataFieldTarget.COVER
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT

    if (salt) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = selected, enabled = option.enabled, role = Role.Checkbox,
                    onValueChange = onSelectedChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCover) AsyncImage(
                model = option.value, contentDescription = stringResource(R.string.cd_cover),
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(LyricoColors.coverPlaceholder), contentScale = ContentScale.Crop,
                placeholder = rememberTintedPainter(painterResource(R.drawable.ic_album_24dp), LyricoColors.coverPlaceholderIcon),
                error = rememberTintedPainter(painterResource(R.drawable.ic_album_24dp), LyricoColors.coverPlaceholderIcon),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.moriafly.salt.ui.Text(stringResource(option.target.labelRes),
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                    color = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.text
                        else com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val valueText = if (isCover && option.imageSize != null)
                    "${option.imageSize.first}×${option.imageSize.second}" else option.value
                com.moriafly.salt.ui.Text(valueText,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = if (isCover) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            com.moriafly.salt.ui.Icon(
                painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                    if (selected) SaltIcons.Check else SaltIcons.Uncheck),
                contentDescription = null, modifier = Modifier.size(20.dp),
                tint = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.highlight
                    else com.moriafly.salt.ui.SaltTheme.colors.subText,
            )
        }
        return
    }

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
                                        if (salt) com.moriafly.salt.ui.SaltTheme.colors.subBackground
                                        else MiuixTheme.colorScheme.onSecondaryContainer
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
            val labelText = stringResource(option.target.labelRes)
            if (salt) {
                com.moriafly.salt.ui.Text(
                    text = labelText,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = if (option.enabled) com.moriafly.salt.ui.SaltTheme.colors.text
                    else com.moriafly.salt.ui.SaltTheme.colors.subText
                )
                com.moriafly.salt.ui.Text(
                    text = option.value,
                    style = com.moriafly.salt.ui.SaltTheme.textStyles.sub,
                    color = com.moriafly.salt.ui.SaltTheme.colors.subText,
                    maxLines = if (option.target == MetadataFieldTarget.LYRICS) 4 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = labelText,
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
        }

        if (salt) {
            // SaltUI 没有独立 Checkbox；沿用官方 SaltIcons 勾选图标，语义与 Miuix 一致。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(enabled = option.enabled) {
                        if (option.enabled) {
                            onSelectedChange(!selected)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                com.moriafly.salt.ui.Icon(
                    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(
                        if (selected) com.moriafly.salt.ui.icons.SaltIcons.Check
                        else com.moriafly.salt.ui.icons.SaltIcons.Uncheck
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = com.moriafly.salt.ui.SaltTheme.colors.highlight
                )
            }
        } else Checkbox(
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

@Composable
private fun rememberOptimizedAllSourceResults(
    keyword: String,
    sources: List<SearchSourceUiModel>,
    searchResultPages: Map<String, List<List<SongSearchResult>>>
): List<SongSearchResult> {
    val sourceIds = sources.map { it.id }
    val pageCount = sourceIds.maxOfOrNull { searchResultPages[it].orEmpty().size } ?: 0

    return buildList {
        for (pageIndex in 0 until pageCount) {
            val sourcePageResults = sourceIds.map { sourceId ->
                searchResultPages[sourceId]?.getOrNull(pageIndex).orEmpty()
            }
            val optimizedPage = androidx.compose.runtime.key(pageIndex) {
                remember(keyword, sourceIds, sourcePageResults) {
                    optimizedAllSourcePage(
                        keyword = keyword,
                        sourcePageResults = sourcePageResults
                    )
                }
            }
            addAll(optimizedPage)
        }
    }
}

private fun optimizedAllSourcePage(
    keyword: String,
    sourcePageResults: List<List<SongSearchResult>>
): List<SongSearchResult> {
    val localSegments = MusicMatchUtils.splitToSegments(keyword)

    return sourcePageResults
        .flatMap { results ->
            results
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
    val salt = com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
            com.lonx.lyrico.data.model.UiEngine.SALT
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

            val iconTint = when {
                salt && selected -> com.moriafly.salt.ui.SaltTheme.colors.onHighlight
                salt -> com.moriafly.salt.ui.SaltTheme.colors.text
                selected -> MiuixTheme.colorScheme.onPrimary
                else -> MiuixTheme.colorScheme.onSurface
            }

            PillButton(
                text = tab.label,
                selected = selected,
                showText = showText,
                style = PillButtonDefaults.style(PillButtonSize.Medium),
                modifier = Modifier.bringIntoViewRequester(
                    bringIntoViewRequesters[index]
                ),
                colors = if (salt) {
                    PillButtonDefaults.colors()
                } else PillButtonDefaults.colors(
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
            if (com.lonx.lyrico.ui.theme.LocalUiEngine.current ==
                com.lonx.lyrico.data.model.UiEngine.SALT
            ) {
                com.moriafly.salt.ui.Icon(
                    painter = androidx.compose.ui.graphics.vector.rememberVectorPainter(tab.imageVector),
                    contentDescription = tab.label,
                    modifier = Modifier.size(16.dp),
                    tint = tint
                )
            } else Icon(
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
