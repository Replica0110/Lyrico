package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.content.ClipData
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton as MaterialTextButton
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.search.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.SearchBar
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.lyrics.LyricRenderConfigBottomSheet
import com.lonx.lyrico.ui.components.lyrics.LyricsPreviewPane
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.viewmodel.LyricsSearchCandidateUi
import com.lonx.lyrico.viewmodel.LyricsSearchViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.result.ResultBackNavigator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Destination<RootGraph>(route = "search_lyrics")
fun SearchLyricsScreen(
    title: String,
    artist: String,
    album: String,
    date: String,
    resultNavigator: ResultBackNavigator<LyricsSearchResult>
) {
    val viewModel: LyricsSearchViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val lyricConfig by viewModel.lyricConfigFlow.collectAsStateWithLifecycle()
    val initialKeyword = remember(title, artist) {
        listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
    }
    val searchFieldState = rememberTextFieldState(initialText = initialKeyword)
    val pagerState = rememberPagerState { uiState.availableSources.size + 1 }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    var showLyricRenderConfig by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var pendingSourceId by remember { mutableStateOf<String?>(null) }
    var pendingSongId by remember { mutableStateOf<String?>(null) }

    val selectedCandidate = remember(
        pendingSourceId,
        pendingSongId,
        uiState.results
    ) {
        val sourceId = pendingSourceId ?: return@remember null
        val songId = pendingSongId ?: return@remember null
        uiState.results[sourceId]
            .orEmpty()
            .firstOrNull { it.song.id == songId }
    }
    val selectedCandidateKey = selectedCandidate?.let { candidate ->
        viewModel.candidateKey(
            sourceId = pendingSourceId.orEmpty(),
            songId = candidate.song.id
        )
    }

    LaunchedEffect(title, artist, album, date) {
        viewModel.initialize(title, artist, album, date)
    }

    LaunchedEffect(uiState.selectedSource?.id, uiState.availableSources) {
        val selectedSource = uiState.selectedSource
        val targetPage = if (selectedSource == null) {
            0
        } else {
            uiState.availableSources.indexOfFirst { it.id == selectedSource.id } + 1
        }
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState, uiState.availableSources.map { it.id }) {
        snapshotFlow { pagerState.currentPage }
            .collectLatest { page ->
                if (page == 0) {
                    viewModel.onAllSourcesSelected()
                } else {
                    uiState.availableSources.getOrNull(page - 1)?.let {
                        viewModel.onSourceSelected(it)
                    }
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
                    state = searchFieldState,
                    placeholder = stringResource(R.string.search_lyrics_placeholder),
                    onSearch = { keyword ->
                        keyboardController?.hide()
                        viewModel.onKeywordChanged(keyword)
                        viewModel.performSearch()
                    },
                    actions = {
                        MaterialTextButton(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.onKeywordChanged(searchFieldState.text.toString())
                                viewModel.performSearch()
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.action_search),
                                style = MiuixTheme.textStyles.main,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showLyricRenderConfig = true }) {
                            Icon(
                                imageVector = MiuixIcons.Settings,
                                contentDescription = stringResource(R.string.search_settings),
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
            when {
                uiState.isInitializing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    return@Column
                }

                uiState.availableSources.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.lyrics_source_empty))
                    }
                    return@Column
                }
            }

            SourcePillTabRow(
                tabs = listOf(
                    SourcePillTab(
                        label = stringResource(R.string.search_type_all),
                        imageVector = MiuixIcons.Search
                    )
                ) + uiState.availableSources.map { source ->
                    SourcePillTab(
                        label = source.labelRes?.let { stringResource(it) } ?: source.name,
                        iconPath = source.iconPath
                    )
                },
                selectedTabIndex = pagerState.targetPage,
                tabStyle = uiState.searchSourceTabStyle,
                onTabSelected = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier.padding(bottom = 10.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val source = if (page == 0) {
                    null
                } else {
                    uiState.availableSources.getOrNull(page - 1)
                }
                val sourceIds = if (source == null) {
                    uiState.availableSources.map { it.id }
                } else {
                    listOf(source.id)
                }
                val results = if (source == null) {
                    uiState.availableSources.flatMap { sourceModel ->
                        uiState.results[sourceModel.id].orEmpty()
                    }
                } else {
                    uiState.results[source.id].orEmpty()
                }
                val isLoading = sourceIds.any { it in uiState.loadingSourceIds }
                val error = sourceIds.firstNotNullOfOrNull { uiState.errors[it] }
                val loadMoreError = sourceIds.firstNotNullOfOrNull { sourceId ->
                    uiState.loadMoreErrors[sourceId]?.let { sourceId to it }
                }
                val isLoadingMore = sourceIds.any { it in uiState.loadingMoreSourceIds }
                val canLoadMore = sourceIds.any { uiState.hasMoreBySource[it] == true }

                when {
                    isLoading && results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    error != null && results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = error.asString().orEmpty(),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }

                    results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.cd_no_results))
                        }
                    }

                    else -> {
                        LyricsSearchResultList(
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
                            onCandidateClick = { candidate ->
                                pendingSourceId = candidate.song.pluginId
                                pendingSongId = candidate.song.id
                                showLyricsSheet = true
                                if (candidate.lyrics == null) {
                                    viewModel.loadLyrics(
                                        sourceId = candidate.song.pluginId,
                                        candidate = candidate
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    ActionBottomSheet(
        show = showLyricsSheet,
        title = selectedCandidate?.song?.title,
        enableNestedScroll = false,
        onDismissRequest = { showLyricsSheet = false },
        onDismissFinished = {
            pendingSourceId = null
            pendingSongId = null
        },
        startAction = {
            IconButton(onClick = { showLyricRenderConfig = true }) {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = stringResource(R.string.search_settings)
                )
            }
        },
        endAction = {
            PillButton(
                text = stringResource(R.string.apply_lyrics_only_action),
                enabled = selectedCandidate?.formattedLyrics?.isNotBlank() == true,
                onClick = {
                    selectedCandidate?.let { candidate ->
                        val song = candidate.song
                        showLyricsSheet = false
                        resultNavigator.navigateBack(
                            LyricsSearchResult(
                                title = song.title,
                                artist = song.artist,
                                album = song.album,
                                lyrics = candidate.formattedLyrics,
                                date = song.date,
                                trackerNumber = null,
                                picUrl = null,
                                pluginId = song.pluginId,
                                pluginName = song.pluginName,
                                applyTargets = setOf(MetadataFieldTarget.LYRICS),
                                fields = emptyMap()
                            )
                        )
                    }
                },
                style = PillButtonDefaults.style(PillButtonSize.Large)
            )
        },
        content = {
            LyricsPreviewPane(
                lyricsText = selectedCandidate?.formattedLyrics,
                isLoading = selectedCandidateKey in uiState.loadingCandidateKeys,
                error = selectedCandidateKey?.let { uiState.candidateErrors[it] },
                loadingText = stringResource(R.string.lyrics_loading),
                failedText = stringResource(R.string.fetch_lyrics_failed),
                emptyText = stringResource(R.string.lyrics_empty),
                onCopyLyrics = {
                    selectedCandidate?.formattedLyrics
                        ?.takeIf { it.isNotBlank() }
                        ?.let { lyrics ->
                            scope.launch {
                                clipboardManager.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("copy lyrics", lyrics))
                                )
                            }
                        }
                },
                modifier = Modifier.height(420.dp)
            )
        }
    )

    LyricRenderConfigBottomSheet(
        show = showLyricRenderConfig,
        config = lyricConfig,
        onDismissRequest = { showLyricRenderConfig = false },
        onLyricFormatChange = viewModel::setLyricFormat,
        onRomaEnabledChange = viewModel::setRomaEnabled,
        onLineOrderChange = viewModel::setLyricLineOrder,
        onTranslationEnabledChange = viewModel::setTranslationEnabled,
        onOnlyTranslationIfAvailableChange = viewModel::setOnlyTranslationIfAvailable,
        onRemoveEmptyLinesChange = viewModel::setRemoveEmptyLines,
        onConversionModeChange = viewModel::setConversionMode
    )
}

@Composable
private fun LyricsSearchResultList(
    results: List<LyricsSearchCandidateUi>,
    searchKeyword: String,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: UiMessage?,
    onLoadMore: () -> Unit,
    onCandidateClick: (LyricsSearchCandidateUi) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(searchKeyword) {
        listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = results,
            key = { candidate ->
                "${candidate.song.pluginId}\u0000${candidate.song.id}"
            }
        ) { candidate ->
            SearchResultItem(
                song = candidate.song,
                showCover = false,
                showExtendedMetadata = false,
                onClick = { onCandidateClick(candidate) }
            )
        }

        when {
            isLoadingMore -> {
                item(key = "lyrics_search_loading_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(size = 20.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.search_loading_more),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            loadMoreError != null -> {
                item(key = "lyrics_search_load_more_error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            text = stringResource(R.string.search_load_more_failed),
                            onClick = onLoadMore,
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }

            canLoadMore -> {
                item(key = "lyrics_search_load_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
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
