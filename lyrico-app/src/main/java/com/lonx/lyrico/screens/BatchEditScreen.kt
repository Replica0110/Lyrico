package com.lonx.lyrico.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.components.CoverRequest
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.scaffoldBottomPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.viewmodel.BatchEditField
import com.lonx.lyrico.viewmodel.BatchEditPreview
import com.lonx.lyrico.viewmodel.BatchEditSelectableCover
import com.lonx.lyrico.viewmodel.BatchEditSelectableValue
import com.lonx.lyrico.viewmodel.BatchEditViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditFieldVisibilityDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.lazy.grid.items as gridItems

private enum class BatchEditTab(val labelRes: Int) {
    Config(R.string.batch_edit_tab_config),
    Preview(R.string.batch_edit_tab_preview)
}

@Composable
@Destination<RootGraph>(route = "batch_edit")
fun BatchEditScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: BatchEditViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visibleFieldGroups by viewModel.visibleFieldGroups.collectAsStateWithLifecycle()
    val visibleCustomKeys by viewModel.visibleCustomKeys.collectAsStateWithLifecycle()

    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showSelectedCoverSheet by remember { mutableStateOf(false) }
    var selectedCoverOptions by remember { mutableStateOf<List<BatchEditSelectableCover>>(emptyList()) }
    var selectedCoverOptionsLoading by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }
    var showSelectedValueSheet by remember { mutableStateOf(false) }
    var selectedValueField by remember { mutableStateOf<BatchEditField?>(null) }
    var selectedValueOptions by remember { mutableStateOf<List<BatchEditSelectableValue>>(emptyList()) }
    var selectedValueOptionsLoading by remember { mutableStateOf(false) }
    var expandedFabMenu by remember { mutableStateOf(false) }

    val tabs = remember { BatchEditTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val visibleFieldCodes = visibleFieldGroups
        .flatMap { it.fields }
        .map { it.code }
        .toSet()
    val visibleGroupCodes = visibleFieldGroups
        .map { it.group.code }
        .toSet()
    val editPreviews = remember(uiState, visibleFieldCodes) {
        viewModel.buildEditPreviews(visibleFieldCodes)
    }

    LaunchedEffect(visibleCustomKeys, uiState.selectedSongsVersion) {
        viewModel.loadCustomTagPreviewValues(visibleCustomKeys)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.updateCover(it) } }

    val scope = rememberCoroutineScope()

    fun applySelectedValue(field: BatchEditField, value: String) {
        when (field) {
            BatchEditField.TITLE -> viewModel.updateTitle(value)
            BatchEditField.ARTIST -> viewModel.updateArtist(value)
            BatchEditField.ALBUM_ARTIST -> viewModel.updateAlbumArtist(value)
            BatchEditField.ALBUM -> viewModel.updateAlbum(value)
            BatchEditField.DATE -> viewModel.updateDate(value)
            BatchEditField.LANGUAGE -> viewModel.updateLanguage(value)
            BatchEditField.GENRE -> viewModel.updateGenre(value)
            BatchEditField.TRACK_NUMBER -> viewModel.updateTrackNumber(value)
            BatchEditField.DISC_NUMBER -> viewModel.updateDiscNumber(value)
            BatchEditField.COMPOSER -> viewModel.updateComposer(value)
            BatchEditField.LYRICIST -> viewModel.updateLyricist(value)
            BatchEditField.COPYRIGHT -> viewModel.updateCopyright(value)
            BatchEditField.COMMENT -> viewModel.updateComment(value)
            BatchEditField.LYRICS -> viewModel.updateLyrics(value)
            BatchEditField.REPLAY_GAIN_TRACK_GAIN -> viewModel.updateReplayGainTrackGain(value)
            BatchEditField.REPLAY_GAIN_TRACK_PEAK -> viewModel.updateReplayGainTrackPeak(value)
            BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> viewModel.updateReplayGainAlbumGain(value)
            BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> viewModel.updateReplayGainAlbumPeak(value)
            BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> viewModel.updateReplayGainReferenceLoudness(value)
            BatchEditField.RATING -> Unit
            BatchEditField.COVER -> Unit
        }
    }

    fun openSelectedValueSheet(field: BatchEditField) {
        selectedValueField = field
        selectedValueOptions = emptyList()
        selectedValueOptionsLoading = true
        showSelectedValueSheet = true
        scope.launch {
            selectedValueOptions = viewModel.getSelectedSongFieldValues(field)
            selectedValueOptionsLoading = false
        }
    }

    fun applyCoverSource(cover: Any) {
        when (cover) {
            is String -> viewModel.updateCover(cover.toUri())
            is ByteArray -> {
                val tempFile = java.io.File.createTempFile("cover", ".jpg")
                tempFile.writeBytes(cover)
                viewModel.updateCover(android.net.Uri.fromFile(tempFile))
            }
        }
    }

    fun openSelectedCoverSheet() {
        selectedCoverOptions = emptyList()
        selectedCoverOptionsLoading = true
        showSelectedCoverSheet = true
        scope.launch {
            selectedCoverOptions = viewModel.getSelectedSongCovers()
            selectedCoverOptionsLoading = false
        }
    }

    // 加载同专辑封面（直接使用第一张）
    fun loadSameAlbumCovers() {
        scope.launch {
            try {
                val covers = viewModel.getSameAlbumCovers()
                if (covers.isNotEmpty()) {
                    val (_, cover) = covers.first()
                    cover?.let { applyCoverSource(it) }
                }
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    BackHandler(
        enabled = !uiState.isSaving
    ) {
        if (!uiState.isSaving) {
            if (expandedFabMenu){
                expandedFabMenu = false
            } else {
                navigator.popBackStack()
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ){
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.batch_edit_title),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!uiState.isSaving) navigator.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.saveBatchEdit() },
                            enabled = !uiState.isSaving
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = null
                            )
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldTopHorizontalPadding(paddingValues))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        val tabLabels = tabs.map { tab ->
                            when (tab) {
                                BatchEditTab.Config -> stringResource(tab.labelRes)
                                BatchEditTab.Preview -> stringResource(
                                    tab.labelRes,
                                    editPreviews.size
                                )
                            }
                        }
                        TabRowWithContour(
                            tabs = tabLabels,
                            selectedTabIndex = pagerState.currentPage,
                            onTabSelected = { index ->
                                scope.launch {
                                    expandedFabMenu = false
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        )
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { index -> tabs[index].name }
                    ) { page ->
                        when (tabs[page]) {
                            BatchEditTab.Config ->
                                LazyColumn(
                                    modifier = Modifier
                                        .scrollEndHaptic()
                                        .overScrollVertical()
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                        .fillMaxHeight()
                                        .imePadding(),
                                    contentPadding = PaddingValues(
                                        bottom = scaffoldBottomPadding(paddingValues, bottomExtra = 80.dp),
                                    ),
                                    overscrollEffect = null,
                                ) {
                                    // 歌曲数量信息
                                    item(key = "song_count") {
                                        SmallTitle(
                                            text = stringResource(
                                                R.string.batch_edit_song_count,
                                                uiState.songCount
                                            )
                                        )
                                    }

                                    // 封面编辑区
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_COVER)) {
                                        item(key = "cover_editor") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.edit_field_group_cover))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("cover.picture")) {
                                                            BatchEditCoverSection(
                                                                coverUri = uiState.coverUri,
                                                                isRemoved = uiState.removeCover,
                                                                onCoverClick = {
                                                                    showCoverOptionsSheet = true
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("cover.rating")) {
                                                            BatchEditRatingItem(
                                                                rating = uiState.rating,
                                                                isModified = uiState.ratingModified,
                                                                onRatingChange = {
                                                                    viewModel.updateRating(
                                                                        it
                                                                    )
                                                                },
                                                                onRevert = { viewModel.resetRating() }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // 基础信息组
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_BASIC_INFO)) {
                                        item(key = "basic_info") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.group_basic_info))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("basic_info.title")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.TITLE,
                                                                value = uiState.title,
                                                                onValueChange = {
                                                                    viewModel.updateTitle(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.TITLE
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.artist")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.ARTIST,
                                                                value = uiState.artist,
                                                                onValueChange = {
                                                                    viewModel.updateArtist(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.ARTIST
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.album_artist")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.ALBUM_ARTIST,
                                                                value = uiState.albumArtist,
                                                                onValueChange = {
                                                                    viewModel.updateAlbumArtist(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.ALBUM_ARTIST
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.album")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.ALBUM,
                                                                value = uiState.album,
                                                                onValueChange = {
                                                                    viewModel.updateAlbum(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.ALBUM
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.date")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.DATE,
                                                                value = uiState.date,
                                                                onValueChange = {
                                                                    viewModel.updateDate(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.DATE
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.language")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.LANGUAGE,
                                                                value = uiState.language,
                                                                onValueChange = {
                                                                    viewModel.updateLanguage(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.LANGUAGE
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("basic_info.genre")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.GENRE,
                                                                value = uiState.genre,
                                                                onValueChange = {
                                                                    viewModel.updateGenre(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.GENRE
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 曲目详情组
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_TRACK_DETAILS)) {
                                        item(key = "track_details") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.group_track_details))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("track_details.track_number")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.TRACK_NUMBER,
                                                                value = uiState.trackNumber,
                                                                onValueChange = {
                                                                    viewModel.updateTrackNumber(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.TRACK_NUMBER
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("track_details.disc_number")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.DISC_NUMBER,
                                                                value = uiState.discNumber,
                                                                onValueChange = {
                                                                    viewModel.updateDiscNumber(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.DISC_NUMBER
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 制作人员和其他信息组
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_CREDITS_OTHER)) {
                                        item(key = "credits_other") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.group_credits_other))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("credits_other.composer")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.COMPOSER,
                                                                value = uiState.composer,
                                                                onValueChange = {
                                                                    viewModel.updateComposer(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.COMPOSER
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("credits_other.lyricist")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.LYRICIST,
                                                                value = uiState.lyricist,
                                                                onValueChange = {
                                                                    viewModel.updateLyricist(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.LYRICIST
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("credits_other.copyright")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.COPYRIGHT,
                                                                value = uiState.copyright,
                                                                onValueChange = {
                                                                    viewModel.updateCopyright(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.COPYRIGHT
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("credits_other.comment")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.COMMENT,
                                                                value = uiState.comment,
                                                                onValueChange = {
                                                                    viewModel.updateComment(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.COMMENT
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 回放增益组
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_REPLAY_GAIN)) {
                                        item(key = "replay_gain") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.group_replay_gain))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("replay_gain.track_gain")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.REPLAY_GAIN_TRACK_GAIN,
                                                                value = uiState.replayGainTrackGain,
                                                                onValueChange = {
                                                                    viewModel.updateReplayGainTrackGain(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.REPLAY_GAIN_TRACK_GAIN
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("replay_gain.track_peak")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.REPLAY_GAIN_TRACK_PEAK,
                                                                value = uiState.replayGainTrackPeak,
                                                                onValueChange = {
                                                                    viewModel.updateReplayGainTrackPeak(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.REPLAY_GAIN_TRACK_PEAK
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("replay_gain.album_gain")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.REPLAY_GAIN_ALBUM_GAIN,
                                                                value = uiState.replayGainAlbumGain,
                                                                onValueChange = {
                                                                    viewModel.updateReplayGainAlbumGain(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.REPLAY_GAIN_ALBUM_GAIN
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("replay_gain.album_peak")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.REPLAY_GAIN_ALBUM_PEAK,
                                                                value = uiState.replayGainAlbumPeak,
                                                                onValueChange = {
                                                                    viewModel.updateReplayGainAlbumPeak(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.REPLAY_GAIN_ALBUM_PEAK
                                                                    )
                                                                }
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("replay_gain.reference_loudness")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS,
                                                                value = uiState.replayGainReferenceLoudness,
                                                                onValueChange = {
                                                                    viewModel.updateReplayGainReferenceLoudness(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (visibleCustomKeys.isNotEmpty()) {
                                        item(key = "custom_fields") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.group_custom_tags))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        visibleCustomKeys.forEach { key ->
                                                            BatchEditCustomFieldItem(
                                                                keyName = key,
                                                                value = uiState.customFields
                                                                    .firstOrNull { it.key == key }
                                                                    ?.value
                                                                    ?: "<keep>",
                                                                clear = key in uiState.customFieldClearKeys,
                                                                onValueChange = { value ->
                                                                    viewModel.setCustomFieldValue(
                                                                        key = key,
                                                                        value = value
                                                                    )
                                                                },
                                                                onKeep = {
                                                                    viewModel.keepCustomField(key)
                                                                },
                                                                onClear = {
                                                                    viewModel.clearCustomField(key)
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 歌词组
                                    if (visibleGroupCodes.contains(EditFieldRegistry.GROUP_LYRICS)) {
                                        item(key = "lyrics") {
                                            Column {
                                                SmallTitle(text = stringResource(R.string.label_lyrics))
                                                Card(
                                                    modifier = Modifier.padding(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp
                                                    )
                                                ) {
                                                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        if (visibleFieldCodes.contains("lyrics.lyrics_offset")) {
                                                            TextField(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 6.dp
                                                                    ),
                                                                value = uiState.lyricsOffset,
                                                                onValueChange = {
                                                                    viewModel.updateLyricsOffset(
                                                                        it
                                                                    )
                                                                },
                                                                label = stringResource(R.string.label_lyrics_offset),
                                                            )
                                                            Text(
                                                                text = stringResource(R.string.batch_edit_lyrics_offset_hint),
                                                                style = MiuixTheme.textStyles.footnote1,
                                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                                modifier = Modifier
                                                                    .padding(horizontal = 12.dp)
                                                            )
                                                        }
                                                        if (visibleFieldCodes.contains("lyrics.lyrics")) {
                                                            BatchEditFieldItem(
                                                                field = BatchEditField.LYRICS,
                                                                value = uiState.lyrics,
                                                                onValueChange = {
                                                                    viewModel.updateLyrics(
                                                                        it
                                                                    )
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(
                                                                        BatchEditField.LYRICS
                                                                    )
                                                                },
                                                                isMultiline = true
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                }

                            BatchEditTab.Preview -> BatchEditPreviewTab(
                                previews = editPreviews,
                                bottomPadding = scaffoldBottomPadding(paddingValues),
                                topAppBarNestedScrollConnection = topAppBarScrollBehavior.nestedScrollConnection
                            )
                        }
                    }
                }
            }
        }
        ExpandableFabMenu(
            visible = currentTab == BatchEditTab.Config,
            expanded = expandedFabMenu,
            enabled = !uiState.isSaving,
            style = ExpandableFabMenuStyle.default().copy(
                mainIcon = MiuixIcons.Add
            ),
            itemCount = 3,
            onExpandedChange = { expandedFabMenu = it }
        ) {
            FabMenuItem(
                label = stringResource(R.string.action_add_custom_tag),
                icon = MiuixIcons.Add,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    showAddCustomTagDialog = true
                }
            )
            FabMenuItem(
                label = stringResource(R.string.edit_field_visibility_settings),
                icon = MiuixIcons.Settings,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    navigator.navigate(EditFieldVisibilityDestination())
                }
            )
            FabMenuItem(
                label = stringResource(R.string.batch_edit_info_summary),
                icon = MiuixIcons.Info,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    showInfoDialog = true
                }
            )
        }
    }

    // 封面操作 BottomSheet
    WindowBottomSheet(
        show = showCoverOptionsSheet,
        onDismissRequest = { showCoverOptionsSheet = false }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .scrollEndHaptic()
                .overScrollVertical(),
            overscrollEffect = null
        ) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) {
                    ArrowPreference(
                        title = stringResource(R.string.batch_edit_cover_change),
                        onClick = {
                            showCoverOptionsSheet = false
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.batch_edit_select_cover_from_selected_songs),
                        onClick = {
                            showCoverOptionsSheet = false
                            openSelectedCoverSheet()
                        }
                    )
                    ArrowPreference(
                        title = "选择同专辑歌曲封面",
                        onClick = {
                            showCoverOptionsSheet = false
                            loadSameAlbumCovers()
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.batch_edit_cover_remove),
                        onClick = {
                            showCoverOptionsSheet = false
                            viewModel.removeCover()
                        }
                    )
                    if (uiState.coverUri != null || uiState.removeCover) {
                        ArrowPreference(
                            title = stringResource(R.string.batch_edit_cover_revert),
                            onClick = {
                                showCoverOptionsSheet = false
                                viewModel.revertCover()
                            }
                        )
                    }
                }
            }
        }
    }

    WindowBottomSheet(
        show = showSelectedValueSheet,
        enableNestedScroll = false,
        title = selectedValueField?.let { stringResource(it.labelResId) }.orEmpty(),
        onDismissRequest = { showSelectedValueSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {

                when {
                    selectedValueOptionsLoading || selectedValueOptions.isEmpty() -> {
                        Card(
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                        ) {
                            BasicComponent(
                                title = stringResource(
                                    if (selectedValueOptionsLoading)
                                        R.string.batch_edit_loading_selected_values
                                    else
                                        R.string.batch_edit_no_selected_values
                                )
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .scrollEndHaptic()
                                .overScrollVertical(),
                            overscrollEffect = null
                        ) {
                            items(
                                items = selectedValueOptions,
                                key = { "${it.sourceUri}\n${it.value}" }
                            ) { option ->
                                BatchEditValueOptionItem(
                                    option = option,
                                    onClick = {
                                        selectedValueField?.let { field ->
                                            applySelectedValue(field, option.value)
                                        }
                                        showSelectedValueSheet = false
                                    }
                                )
                            }
                        }
                    }
                }

        }
    }

    WindowBottomSheet(
        show = showSelectedCoverSheet,
        enableNestedScroll = false,
        title = stringResource(R.string.batch_edit_select_cover_from_selected_songs),
        onDismissRequest = { showSelectedCoverSheet = false }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            overscrollEffect = null
        ) {
            if (selectedCoverOptionsLoading || selectedCoverOptions.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
                    ) {
                        BasicComponent(
                            title = stringResource(
                                if (selectedCoverOptionsLoading)
                                    R.string.batch_edit_loading_selected_values
                                else
                                    R.string.batch_edit_no_selected_covers
                            )
                        )
                    }
                }
            }

            if (!selectedCoverOptionsLoading && selectedCoverOptions.isNotEmpty()) {
                gridItems(
                    items = selectedCoverOptions,
                    key = { "${it.sourceUri}\n${it.fileLastModified}" }
                ) { option ->
                    BatchEditCoverOptionItem(
                        option = option,
                        onClick = {
                            scope.launch {
                                viewModel.getSelectedSongCover(option.sourceUri)?.let { cover ->
                                    applyCoverSource(cover)
                                    showSelectedCoverSheet = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // 批量编辑说明对话框
    WindowDialog(
        show = showInfoDialog,
        title = stringResource(R.string.batch_edit_info_summary),
        onDismissRequest = { showInfoDialog = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_edit_info_content),
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.confirm),
                onClick = { showInfoDialog = false },
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }

    // 保存进度对话框
    WindowBottomSheet(
        show = uiState.saveProgressBottomSheet,
        onDismissRequest = {
            if (!uiState.isSaving) viewModel.closeSaveBottomSheet()
        },
        onDismissFinished = {
            if (uiState.saveSuccess == true) {
                navigator.popBackStack()
            }
        },
        allowDismiss = !uiState.isSaving,
        title = stringResource(R.string.batch_edit_title),
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 第一行：显示 标题/文件名 或 总用时
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isSaving) {
                            uiState.currentFile.ifEmpty { stringResource(R.string.batch_edit_processing) }
                        } else {
                            // 保存完成后显示总用时
                            stringResource(R.string.batch_matching_total_time, uiState.saveTimeMillis / 1000.0)
                        },
                        style = MiuixTheme.textStyles.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${uiState.saveProgress} / ${uiState.saveTotal}",
                        style = MiuixTheme.textStyles.main,
                        textAlign = TextAlign.End
                    )
                }

                LinearProgressIndicator(
                    progress = if (uiState.saveTotal > 0)
                        uiState.saveProgress.toFloat() / uiState.saveTotal
                    else 0f
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.batch_matching_success,
                        uiState.successCount
                    ),
                    style = MiuixTheme.textStyles.main
                )
                Text(
                    text = stringResource(
                        R.string.batch_replay_gain_skipped,
                        uiState.skippedCount
                    ),
                    style = MiuixTheme.textStyles.main
                )
                Text(
                    text = stringResource(
                        R.string.batch_matching_failure,
                        uiState.failureCount
                    ),
                    style = MiuixTheme.textStyles.main
                )
            }

            TextButton(
                text = if (uiState.isSaving) stringResource(R.string.action_abort) else stringResource(
                    R.string.confirm
                ),
                onClick = {
                    if (uiState.isSaving) {
                        viewModel.abortSave()
                    } else {
                        viewModel.closeSaveBottomSheet()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )

        }
    }

    WindowDialog(
        show = showAddCustomTagDialog,
        title = stringResource(R.string.action_add_custom_tag),
        onDismissRequest = { showAddCustomTagDialog = false }
    ) {
        var newCustomTagKey by remember { mutableStateOf("") }
        var newCustomTagValue by remember { mutableStateOf("") }
        Column(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = newCustomTagKey,
                onValueChange = { newCustomTagKey = it },
                label = stringResource(R.string.label_custom_tag_name),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = newCustomTagValue,
                onValueChange = { newCustomTagValue = it },
                label = stringResource(R.string.label_custom_tag_value),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showAddCustomTagDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        if (newCustomTagKey.isNotBlank()) {
                            viewModel.addCustomFieldAndShow(
                                key = newCustomTagKey,
                                value = newCustomTagValue,
                            )
                            showAddCustomTagDialog = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

}

@Composable
private fun BatchEditCoverOptionItem(
    option: BatchEditSelectableCover,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = CoverRequest(option.previewUri.toUri(), option.fileLastModified),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(LyricoColors.coverPlaceholder),
            placeholder = rememberTintedPainter(
                painter = painterResource(id = R.drawable.ic_album_24dp),
                tint = LyricoColors.coverPlaceholderIcon
            ),
            error = rememberTintedPainter(
                painter = painterResource(id = R.drawable.ic_album_24dp),
                tint = LyricoColors.coverPlaceholderIcon
            )
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = option.title,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (option.summary.isNotBlank()) {
            Text(
                text = option.summary,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatchEditValueOptionItem(
    option: BatchEditSelectableValue,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.padding(vertical = 6.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = option.value,
                style = MiuixTheme.textStyles.main,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatchEditRatingItem(
    rating: Int,
    isModified: Boolean,
    onRatingChange: (Int) -> Unit,
    onRevert: () -> Unit
) {
    val onSurfaceDim = MiuixTheme.colorScheme.onSurfaceVariantActions

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            endActions = {
                if (isModified) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            content = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        val isFilled = i <= rating

                        Icon(
                            painter = painterResource(
                                if (isFilled)
                                    R.drawable.ic_filled_star_24dp
                                else
                                    R.drawable.ic_outline_star_24dp
                            ),
                            contentDescription = null,
                            tint = if (isFilled)
                                MiuixTheme.colorScheme.primary
                            else
                                onSurfaceDim.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(28.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onRatingChange(
                                        if (rating == i) 0 else i
                                    )
                                }
                        )
                    }
                }
            }
        )
    }
}

/**
 * 封面编辑区
 */
@Composable
private fun BatchEditCoverSection(
    coverUri: Any?,
    isRemoved: Boolean,
    onCoverClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable { onCoverClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isRemoved) {
            Text(
                text = stringResource(R.string.batch_edit_cover_remove),
                color = MiuixTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
        } else if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                placeholder = rememberTintedPainter(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    tint = LyricoColors.coverPlaceholderIcon
                ),
                error = rememberTintedPainter(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    tint = LyricoColors.coverPlaceholderIcon
                )
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_album_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.batch_edit_cover_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    fontSize = 13.sp
                )
            }
        }
    }
}


@Composable
private fun BatchEditPreviewTab(
    previews: List<BatchEditPreview>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    topAppBarNestedScrollConnection: NestedScrollConnection
) {
    var expandedPreviewKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .scrollEndHaptic()
            .overScrollVertical()
            .nestedScroll(topAppBarNestedScrollConnection)
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = bottomPadding + 12.dp),
        overscrollEffect = null,
    ) {
        if (previews.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.batch_edit_preview_empty),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        } else {
            items(
                items = previews,
                key = { it.songUri }
            ) { preview ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .animateContentSize()
                ) {
                    BasicComponent(
                        onClick = {
                            expandedPreviewKey =
                                if (expandedPreviewKey == preview.songUri) null else preview.songUri
                        }
                    ) {
                        Text(
                            text = preview.fileName,
                            style = MiuixTheme.textStyles.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.batch_edit_preview_change_count, preview.changes.size),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                        )

                        AnimatedVisibility(visible = expandedPreviewKey == preview.songUri) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                preview.changes.forEach { change ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = change.labelResId?.let { stringResource(it) } ?: change.customLabel.orEmpty(),
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.batch_edit_preview_old_value,
                                                displayBatchEditPreviewValue(change.oldValue)
                                            ),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.batch_edit_preview_new_value,
                                                displayBatchEditPreviewValue(change.newValue)
                                            ),
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantActions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}



@Composable
private fun displayBatchEditPreviewValue(value: String): String {
    return when (value) {
        "<current_cover>" -> stringResource(R.string.batch_edit_preview_current_cover)
        "<remove_cover>" -> stringResource(R.string.batch_edit_preview_remove_cover)
        "" -> stringResource(R.string.batch_edit_preview_empty_value)
        else -> value
    }
}


/**
 * 单个标签编辑字段
 * value 为 "<keep>" 时表示不修改该字段
 */
@Composable
private fun BatchEditFieldItem(
    field: BatchEditField,
    value: String,
    onValueChange: (String) -> Unit,
    onSelectFromSongs: () -> Unit,
    isMultiline: Boolean = false
) {
    val isKeep = value == "<keep>"

    if (isMultiline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (isKeep) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { /* 无法恢复，已经是 <keep> */ }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<keep>",
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.error.copy(alpha = 0.1f))
                            .clickable { onValueChange("<keep>") }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                IconButton(onClick = onSelectFromSongs) {
                    Icon(
                        imageVector = MiuixIcons.Basic.ArrowUpDown,
                        contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                    )
                }
            }
            TextField(
                textStyle = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth(),
                value = value,
                label = stringResource(field.labelResId) + if (isKeep) " (无修改)" else "",
                onValueChange = onValueChange,
                singleLine = false,
                minLines = 6
            )
        }
    } else {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            value = value,
            onValueChange = onValueChange,
            label = stringResource(field.labelResId) + if (isKeep) " (无修改)" else "",
            trailingIcon = {
                Row {
                    IconButton(onClick = {
                        onValueChange(if (isKeep) "" else "<keep>")
                    }) {
                        Icon(
                            imageVector = if (isKeep) MiuixIcons.Close else MiuixIcons.Undo,
                            contentDescription = null,
                            tint = if (isKeep)
                                MiuixTheme.colorScheme.primary
                            else
                                MiuixTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onSelectFromSongs) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowUpDown,
                            contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                        )
                    }
                }
            }
        )
    }
}

/**
 * 自定义标签编辑字段
 */
@Composable
private fun BatchEditCustomFieldItem(
    keyName: String,
    value: String,
    clear: Boolean,
    onValueChange: (String) -> Unit,
    onKeep: () -> Unit,
    onClear: () -> Unit,
) {
    val isKeep = value == "<keep>"
    val displayedValue = if (clear) "" else value
    val labelSuffix = when {
        clear -> " (" + stringResource(R.string.batch_custom_tag_clear) + ")"
        isKeep -> " (" + stringResource(R.string.batch_custom_tag_keep) + ")"
        else -> " (" + stringResource(R.string.batch_custom_tag_set) + ")"
    }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = keyName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row {
                IconButton(onClick = {
                    if (isKeep) onValueChange("") else onKeep()
                }) {
                    Icon(
                        imageVector = if (isKeep) MiuixIcons.Close else MiuixIcons.Undo,
                        contentDescription = null,
                        tint = if (isKeep)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.batch_custom_tag_clear),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        }

        if (clear) {
            Text(
                text = stringResource(R.string.batch_custom_tag_clear_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        } else {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                value = displayedValue,
                onValueChange = onValueChange,
                label = keyName + labelSuffix,
            )
        }
    }
}

