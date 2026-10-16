@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.ColumnScope
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.ui.components.library.LocalLibraryUiEngine
import com.lonx.lyrico.ui.components.library.LibraryDetailPage
import com.lonx.lyrico.ui.components.library.LibraryDetailTabs
import com.lonx.lyrico.ui.components.library.libraryDetailScroll
import com.lonx.lyrico.ui.components.base.SaltIconButton
import com.lonx.lyrico.ui.components.base.SaltBottomSheet
import com.lonx.lyrico.ui.components.base.SaltConfirmDialog
import com.lonx.lyrico.ui.components.base.ItemMenuBottomSheet
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.ui.components.base.SheetProgressAction
import com.lonx.lyrico.ui.components.base.AppLinearProgressIndicator
import com.lonx.lyrico.ui.components.base.SheetStatisticsRow
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.Item
import com.lonx.lyrico.ui.components.editor.SaltEditorField
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.Text as SaltText
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
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.library.EmptyStateKind
import com.lonx.lyrico.ui.components.library.IllustratedEmptyState
import com.lonx.lyrico.data.editfield.toEditFieldBlocks
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.ui.components.CoverRequest
import com.lonx.lyrico.ui.components.base.LyricsOffsetField
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.scaffoldBottomPadding
import com.lonx.lyrico.ui.components.scaffoldTopHorizontalPadding
import com.lonx.lyrico.viewmodel.BatchEditField
import com.lonx.lyrico.viewmodel.BatchEditPreview
import com.lonx.lyrico.viewmodel.BatchEditSelectableCover
import com.lonx.lyrico.viewmodel.BatchEditSelectableValue
import com.lonx.lyrico.viewmodel.BatchEditUiState
import com.lonx.lyrico.viewmodel.BatchEditViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditFieldSettingsDestination
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
    // 字段顺序与显隐来自「编辑字段」配置，页面按这份列表遍历渲染。
    val visibleFields by viewModel.visibleFields.collectAsStateWithLifecycle()
    val visibleFieldCodes = visibleFields.map { it.code }.toSet()
    val fieldBlocks = remember(visibleFields) { visibleFields.toEditFieldBlocks() }

    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showSelectedCoverSheet by remember { mutableStateOf(false) }
    var selectedCoverOptions by remember { mutableStateOf<List<BatchEditSelectableCover>>(emptyList()) }
    var selectedCoverOptionsLoading by remember { mutableStateOf(false) }
    var selectedCoverRequest by remember { mutableStateOf(0) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }
    var showSelectedValueSheet by remember { mutableStateOf(false) }
    var selectedValueField by remember { mutableStateOf<BatchEditField?>(null) }
    var selectedCustomValueKey by remember { mutableStateOf<String?>(null) }
    var selectedValueOptions by remember { mutableStateOf<List<BatchEditSelectableValue>>(emptyList()) }
    var selectedValueOptionsLoading by remember { mutableStateOf(false) }
    var selectedValueRequest by remember { mutableStateOf(0) }
    var expandedFabMenu by remember { mutableStateOf(false) }

    val tabs = remember { BatchEditTab.entries }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val currentTab = tabs[pagerState.currentPage]
    val editPreviews = remember(uiState, visibleFieldCodes) {
        viewModel.buildEditPreviews(visibleFieldCodes)
    }

    // 自定义标签的预览值只需要可见的自定义标签，从同一份字段列表里取。
    val visibleCustomKeys = remember(visibleFields) {
        visibleFields.filter { it.custom }
            .mapNotNull { EditFieldRegistry.customTagKeyOf(it.code) }
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
        val request = ++selectedValueRequest
        selectedValueField = field
        selectedCustomValueKey = null
        selectedValueOptions = emptyList()
        selectedValueOptionsLoading = true
        showSelectedValueSheet = true
        scope.launch {
            val values = viewModel.getSelectedSongFieldValues(field)
            if (request == selectedValueRequest && showSelectedValueSheet) {
                selectedValueOptions = values
                selectedValueOptionsLoading = false
            }
        }
    }

    fun openSelectedCustomValueSheet(key: String) {
        val request = ++selectedValueRequest
        selectedValueField = null
        selectedCustomValueKey = key
        selectedValueOptions = emptyList()
        selectedValueOptionsLoading = true
        showSelectedValueSheet = true
        scope.launch {
            val values = viewModel.getSelectedSongCustomTagValues(key)
            if (request == selectedValueRequest && showSelectedValueSheet) {
                selectedValueOptions = values
                selectedValueOptionsLoading = false
            }
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
        val request = ++selectedCoverRequest
        selectedCoverOptions = emptyList()
        selectedCoverOptionsLoading = true
        showSelectedCoverSheet = true
        scope.launch {
            val covers = viewModel.getSelectedSongCovers()
            if (request == selectedCoverRequest && showSelectedCoverSheet) {
                selectedCoverOptions = covers
                selectedCoverOptionsLoading = false
            }
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

    val salt = LocalUiEngine.current == UiEngine.SALT
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
    CompositionLocalProvider(LocalLibraryUiEngine provides LocalUiEngine.current) {
    Box(
        modifier = Modifier.fillMaxSize()
    ){
        LibraryDetailPage(
            title = stringResource(R.string.batch_edit_title),
            onBack = { if (!uiState.isSaving) navigator.popBackStack() },
            actions = { SaltIconButton(LucideIcons.Check, stringResource(R.string.confirm),
                { viewModel.saveBatchEdit() }, enabled = !uiState.isSaving) },
            miuixTopBar = {
                SmallTopAppBar(
                    title = stringResource(R.string.batch_edit_title),
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!uiState.isSaving) navigator.popBackStack()
                            }
                        ) {
                            Icon(
                                imageVector = LucideIcons.ArrowLeft,
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
                                imageVector = LucideIcons.Check,
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
                    val tabLabels = tabs.map { tab ->
                        if (tab == BatchEditTab.Preview) stringResource(tab.labelRes, editPreviews.size)
                        else stringResource(tab.labelRes)
                    }
                    LibraryDetailTabs(tabLabels, pagerState.currentPage, { index ->
                        scope.launch { expandedFabMenu = false; pagerState.animateScrollToPage(index) }
                    })

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { index -> tabs[index].name }
                    ) { page ->
                        when (tabs[page]) {
                            BatchEditTab.Config ->
                                LazyColumn(
                                    modifier = Modifier
                                        .libraryDetailScroll(topAppBarScrollBehavior)
                                        .fillMaxHeight()
                                        .then(if (salt) Modifier else Modifier.imePadding()),
                                    contentPadding = PaddingValues(
                                        bottom = scaffoldBottomPadding(paddingValues, bottomExtra = 80.dp),
                                    ),
                                ) {
                                    // 歌曲数量信息
                                    item(key = "song_count") {
                                        if (salt) ItemOuterTitle(stringResource(R.string.batch_edit_song_count, uiState.songCount))
                                        else SmallTitle(stringResource(R.string.batch_edit_song_count, uiState.songCount))
                                    }

                                    fieldBlocks.forEach { block ->
                                        val field = block.fields.first()
                                        val fieldCode = field.code

                                        // 自定义标签没有内置字段枚举，单独渲染。
                                        if (field.custom) {
                                            val key = EditFieldRegistry.customTagKeyOf(fieldCode)
                                            if (key != null) {
                                                item(key = fieldCode) {
                                                    BatchEditorGroup {
                                                        Column {
                                                            BatchEditCustomFieldItem(
                                                                keyName = key,
                                                                value = uiState.customFields
                                                                    .firstOrNull { it.key == key }
                                                                    ?.value
                                                                    ?: "<keep>",
                                                                onValueChange = { value ->
                                                                    viewModel.setCustomFieldValue(
                                                                        key = key,
                                                                        value = value
                                                                    )
                                                                },
                                                                onKeep = {
                                                                    viewModel.keepCustomField(key)
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedCustomValueSheet(key)
                                                                },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            return@forEach
                                        }

                                        item(key = block.key) {
                                            BatchEditorGroup {
                                                Column {
                                                    block.fields.forEach { member ->
                                                        val editField = BatchEditField.entries.firstOrNull { it.target == member.target }
                                                        when (member.code) {
                                                            // 封面和评级共用一张卡片，分别保留显隐开关。
                                                            "picture" -> BatchEditCoverSection(
                                                                coverUri = uiState.coverUri,
                                                                isRemoved = uiState.removeCover,
                                                                onCoverClick = {
                                                                    showCoverOptionsSheet = true
                                                                }
                                                            )

                                                            "rating" -> BatchEditRatingItem(
                                                                rating = uiState.rating,
                                                                isModified = uiState.ratingModified,
                                                                onRatingChange = {
                                                                    viewModel.updateRating(it)
                                                                },
                                                                onRevert = { viewModel.resetRating() }
                                                            )

                                                            // 歌词偏移是数值调整控件，不是标签字段；正负号由控件本身表达，无需额外说明。
                                                            "lyrics_offset" -> {
                                                                LyricsOffsetField(
                                                                    offset = uiState.lyricsOffset,
                                                                    onOffsetChange = {
                                                                        viewModel.updateLyricsOffset(it)
                                                                    },
                                                                    enabled = !uiState.isSaving,
                                                                    modifier = Modifier.padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 6.dp
                                                                    )
                                                                )
                                                            }

                                                            else -> BatchEditFieldItem(
                                                                field = requireNotNull(editField),
                                                                value = editField.currentValueIn(uiState),
                                                                onValueChange = { newValue ->
                                                                    editField.applyTo(viewModel, newValue)
                                                                },
                                                                onSelectFromSongs = {
                                                                    openSelectedValueSheet(editField)
                                                                },
                                                                isMultiline = editField == BatchEditField.LYRICS
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
                mainIcon = LucideIcons.Plus
            ),
            itemCount = 3,
            onExpandedChange = { expandedFabMenu = it }
        ) {
            FabMenuItem(
                label = stringResource(R.string.action_add_custom_tag),
                icon = LucideIcons.Plus,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    showAddCustomTagDialog = true
                }
            )
            FabMenuItem(
                label = stringResource(R.string.edit_field_settings_title),
                icon = LucideIcons.Settings,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    navigator.navigate(EditFieldSettingsDestination())
                }
            )
            FabMenuItem(
                label = stringResource(R.string.batch_edit_info_summary),
                icon = LucideIcons.Info,
                enabled = !uiState.isSaving,
                onClick = {
                    expandedFabMenu = false
                    showInfoDialog = true
                }
            )
        }
    }

    }

    if (salt) {
        CompositionLocalProvider(LocalLibraryUiEngine provides UiEngine.SALT) {
        SaltBatchOverlays(
            viewModel, uiState, showCoverOptionsSheet, { showCoverOptionsSheet = false },
            onPickCover = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onSelectCover = { openSelectedCoverSheet() }, onAlbumCover = { loadSameAlbumCovers() },
            showSelectedValueSheet, { showSelectedValueSheet = false },
            selectedValueField?.let { stringResource(it.labelResId) } ?: selectedCustomValueKey.orEmpty(),
            selectedValueOptionsLoading, selectedValueOptions,
            onValue = { value ->
                selectedValueField?.let { applySelectedValue(it, value) }
                selectedCustomValueKey?.let { viewModel.setCustomFieldValue(it, value) }
                showSelectedValueSheet = false
            },
            showSelectedCoverSheet, { showSelectedCoverSheet = false },
            selectedCoverOptionsLoading, selectedCoverOptions,
            onCover = { option -> scope.launch {
                viewModel.getSelectedSongCover(option.sourceUri)?.let { applyCoverSource(it); showSelectedCoverSheet = false }
            } },
            showInfoDialog, { showInfoDialog = false },
            showAddCustomTagDialog, { showAddCustomTagDialog = false },
            onSaved = { if (uiState.saveSuccess == true) navigator.popBackStack() },
        )
        }
    } else {
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
                        startAction = { PreferenceIcon(LucideIcons.ImageAdd, PreferenceIconTone.Library) },
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
                        startAction = { PreferenceIcon(LucideIcons.ImageLibrary, PreferenceIconTone.Library) },
                        title = stringResource(R.string.batch_edit_select_cover_from_selected_songs),
                        onClick = {
                            showCoverOptionsSheet = false
                            openSelectedCoverSheet()
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Disc, PreferenceIconTone.Library) },
                        title = stringResource(R.string.label_select_same_album_cover),
                        onClick = {
                            showCoverOptionsSheet = false
                            loadSameAlbumCovers()
                        }
                    )
                    ArrowPreference(
                        startAction = { PreferenceIcon(LucideIcons.Trash, PreferenceIconTone.Danger) },
                        title = stringResource(R.string.batch_edit_cover_remove),
                        onClick = {
                            showCoverOptionsSheet = false
                            viewModel.removeCover()
                        }
                    )
                    if (uiState.coverUri != null || uiState.removeCover) {
                        ArrowPreference(
                            startAction = { PreferenceIcon(LucideIcons.Undo, PreferenceIconTone.Tools) },
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
        title = selectedValueField?.let { stringResource(it.labelResId) }
            ?: selectedCustomValueKey.orEmpty(),
        onDismissRequest = { showSelectedValueSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {

                when {
                    selectedValueOptionsLoading -> {
                        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                            BasicComponent(title = stringResource(R.string.batch_edit_loading_selected_values))
                        }
                    }
                    selectedValueOptions.isEmpty() -> {
                        IllustratedEmptyState(
                            title = stringResource(R.string.batch_edit_no_selected_values),
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                                        selectedCustomValueKey?.let { key ->
                                            viewModel.setCustomFieldValue(key, option.value)
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
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    if (selectedCoverOptionsLoading) {
                        Card(colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                            BasicComponent(title = stringResource(R.string.batch_edit_loading_selected_values))
                        }
                    } else {
                        IllustratedEmptyState(
                            kind = EmptyStateKind.Cover,
                            title = stringResource(R.string.batch_edit_no_selected_covers),
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
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
    if (LocalUiEngine.current == UiEngine.SALT) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f)) { (1..5).forEach { value ->
                SaltIconButton(LucideIcons.Star, "$value", { onRatingChange(if (rating == value) 0 else value) },
                    tint = if (value <= rating) SaltTheme.colors.highlight else SaltTheme.colors.subText,
                    size = 36.dp, iconSize = 24.dp)
            } }
            if (isModified) SaltIconButton(LucideIcons.Undo, stringResource(R.string.action_undo_changes), onRevert, size = 36.dp)
            else SaltText("<keep>", style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
        }
        return
    }
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
    if (LocalUiEngine.current == UiEngine.SALT) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onCoverClick).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (coverUri != null && !isRemoved) AsyncImage(coverUri, null,
                modifier = Modifier.size(128.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
            else com.moriafly.salt.ui.Icon(androidx.compose.ui.graphics.vector.rememberVectorPainter(LucideIcons.Image),
                null, modifier = Modifier.size(40.dp), tint = SaltTheme.colors.subText)
            SaltText(stringResource(if (isRemoved) R.string.batch_edit_cover_remove else R.string.batch_edit_cover_hint),
                style = SaltTheme.textStyles.sub, color = if (isRemoved) SaltTheme.colors.error else SaltTheme.colors.subText)
        }
        return
    }
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
    if (LocalUiEngine.current == UiEngine.SALT) {
        var expandedKey by remember { mutableStateOf<String?>(null) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding + 12.dp)) {
            if (previews.isEmpty()) item { IllustratedEmptyState(kind = EmptyStateKind.Tasks,
                title = stringResource(R.string.batch_edit_preview_empty), modifier = Modifier.fillMaxWidth()) }
            items(previews, key = { it.songUri }) { preview ->
                RoundedColumn {
                    Item(text = preview.fileName, sub = stringResource(R.string.batch_edit_preview_change_count, preview.changes.size),
                        onClick = { expandedKey = if (expandedKey == preview.songUri) null else preview.songUri })
                    AnimatedVisibility(expandedKey == preview.songUri) {
                        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            preview.changes.forEach { change ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    SaltText(change.labelResId?.let { stringResource(it) } ?: change.customLabel.orEmpty(), color = SaltTheme.colors.highlight)
                                    SaltText(stringResource(R.string.batch_edit_preview_old_value, displayBatchEditPreviewValue(change.oldValue)), style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
                                    SaltText(stringResource(R.string.batch_edit_preview_new_value, displayBatchEditPreviewValue(change.newValue)), style = SaltTheme.textStyles.sub)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }
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
                IllustratedEmptyState(
                    kind = EmptyStateKind.Tasks,
                    title = stringResource(R.string.batch_edit_preview_empty),
                    modifier = Modifier.fillMaxWidth(),
                )
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
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBatchField(stringResource(field.labelResId), value, onValueChange,
            onKeep = { onValueChange("<keep>") }, onSelectFromSongs, isMultiline)
        return
    }
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
                        imageVector = LucideIcons.ArrowDownUp,
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
                            imageVector = if (isKeep) LucideIcons.Close else LucideIcons.Undo,
                            contentDescription = null,
                            tint = if (isKeep)
                                MiuixTheme.colorScheme.primary
                            else
                                MiuixTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onSelectFromSongs) {
                        Icon(
                            imageVector = LucideIcons.ArrowDownUp,
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
    onValueChange: (String) -> Unit,
    onKeep: () -> Unit,
    onSelectFromSongs: () -> Unit,
) {
    if (LocalUiEngine.current == UiEngine.SALT) {
        SaltBatchField(keyName, value, onValueChange, onKeep, onSelectFromSongs)
        return
    }
    val isKeep = value == "<keep>"

    TextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        value = value,
        onValueChange = onValueChange,
        label = keyName + if (isKeep) " (无修改)" else "",
        trailingIcon = {
            Row {
                IconButton(onClick = {
                    if (isKeep) {
                        onValueChange("")
                    } else {
                        onKeep()
                    }
                }) {
                    Icon(
                        imageVector = if (isKeep) LucideIcons.Close else LucideIcons.Undo,
                        contentDescription = null,
                        tint = if (isKeep)
                            MiuixTheme.colorScheme.primary
                        else
                            MiuixTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onSelectFromSongs) {
                    Icon(
                        imageVector = LucideIcons.ArrowDownUp,
                        contentDescription = stringResource(R.string.batch_edit_select_from_selected_songs)
                    )
                }
            }
        }
    )
}

/** 批量编辑页当前值；未修改的字段是 `<keep>`。 */
private fun BatchEditField.currentValueIn(state: BatchEditUiState): String = when (this) {
    BatchEditField.TITLE -> state.title
    BatchEditField.ARTIST -> state.artist
    BatchEditField.ALBUM_ARTIST -> state.albumArtist
    BatchEditField.ALBUM -> state.album
    BatchEditField.DATE -> state.date
    BatchEditField.LANGUAGE -> state.language
    BatchEditField.GENRE -> state.genre
    BatchEditField.TRACK_NUMBER -> state.trackNumber
    BatchEditField.DISC_NUMBER -> state.discNumber
    BatchEditField.COMPOSER -> state.composer
    BatchEditField.LYRICIST -> state.lyricist
    BatchEditField.COPYRIGHT -> state.copyright
    BatchEditField.COMMENT -> state.comment
    BatchEditField.LYRICS -> state.lyrics
    BatchEditField.REPLAY_GAIN_TRACK_GAIN -> state.replayGainTrackGain
    BatchEditField.REPLAY_GAIN_TRACK_PEAK -> state.replayGainTrackPeak
    BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> state.replayGainAlbumGain
    BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> state.replayGainAlbumPeak
    BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> state.replayGainReferenceLoudness
    // 封面与评级走各自的组合件渲染，不会走到这里。
    BatchEditField.COVER, BatchEditField.RATING -> "<keep>"
}

private fun BatchEditField.applyTo(viewModel: BatchEditViewModel, value: String) {
    when (this) {
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
        BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS ->
            viewModel.updateReplayGainReferenceLoudness(value)
        // 封面与评级走各自的组合件回调。
        BatchEditField.COVER, BatchEditField.RATING -> Unit
    }
}


@Composable
private fun BatchEditorGroup(content: @Composable ColumnScope.() -> Unit) {
    if (LocalUiEngine.current == UiEngine.SALT) RoundedColumn(content = content)
    else Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(vertical = 6.dp), content = content)
    }
}

@Composable
private fun SaltBatchField(
    label: String, value: String, onChange: (String) -> Unit,
    onKeep: () -> Unit, onSelect: () -> Unit, multiline: Boolean = false,
) {
    val keep = value == "<keep>"
    SaltEditorField(label, value, onChange, multiline = multiline, grouped = false) {
        SaltIconButton(if (keep) LucideIcons.Close else LucideIcons.Undo,
            stringResource(if (keep) R.string.action_delete else R.string.action_undo_changes),
            { if (keep) onChange("") else onKeep() }, size = 32.dp, iconSize = 18.dp)
        SaltIconButton(LucideIcons.ArrowDownUp,
            stringResource(R.string.batch_edit_select_from_selected_songs), onSelect,
            size = 32.dp, iconSize = 18.dp)
    }
}

@Composable
private fun SaltBatchOverlays(
    viewModel: BatchEditViewModel, state: BatchEditUiState,
    showCoverMenu: Boolean, closeCoverMenu: () -> Unit,
    onPickCover: () -> Unit, onSelectCover: () -> Unit, onAlbumCover: () -> Unit,
    showValues: Boolean, closeValues: () -> Unit, valueTitle: String,
    valuesLoading: Boolean, values: List<BatchEditSelectableValue>, onValue: (String) -> Unit,
    showCovers: Boolean, closeCovers: () -> Unit,
    coversLoading: Boolean, covers: List<BatchEditSelectableCover>, onCover: (BatchEditSelectableCover) -> Unit,
    showInfo: Boolean, closeInfo: () -> Unit,
    showCustom: Boolean, closeCustom: () -> Unit, onSaved: () -> Unit,
) {
    ItemMenuBottomSheet(show = showCoverMenu, label = stringResource(R.string.label_cover), onDismissRequest = closeCoverMenu) {
        ItemMenuAction(stringResource(R.string.batch_edit_cover_change), LucideIcons.ImageAdd) { closeCoverMenu(); onPickCover() }
        ItemMenuAction(stringResource(R.string.batch_edit_select_cover_from_selected_songs), LucideIcons.ImageLibrary) { closeCoverMenu(); onSelectCover() }
        ItemMenuAction(stringResource(R.string.label_select_same_album_cover), LucideIcons.Disc) { closeCoverMenu(); onAlbumCover() }
        ItemMenuAction(stringResource(R.string.batch_edit_cover_remove), LucideIcons.Trash, PreferenceIconTone.Danger) { closeCoverMenu(); viewModel.removeCover() }
        if (state.coverUri != null || state.removeCover) ItemMenuAction(stringResource(R.string.batch_edit_cover_revert), LucideIcons.Undo) { closeCoverMenu(); viewModel.revertCover() }
    }
    SaltBottomSheet(showValues, closeValues, header = { ItemOuterTitle(valueTitle) }) {
        when {
            valuesLoading -> RoundedColumn { SaltText(stringResource(R.string.batch_edit_loading_selected_values), modifier = Modifier.padding(16.dp)) }
            values.isEmpty() -> IllustratedEmptyState(title = stringResource(R.string.batch_edit_no_selected_values), compact = true, modifier = Modifier.fillMaxWidth())
            else -> RoundedColumn {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(values, key = { "${it.sourceUri}\n${it.value}" }) { option ->
                        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable { onValue(option.value) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            SaltText(option.value, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    SaltBottomSheet(showCovers, closeCovers,
        header = { ItemOuterTitle(stringResource(R.string.batch_edit_select_cover_from_selected_songs)) }) {
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.heightIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (coversLoading || covers.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                if (coversLoading) SaltText(stringResource(R.string.batch_edit_loading_selected_values), modifier = Modifier.padding(16.dp))
                else IllustratedEmptyState(kind = EmptyStateKind.Cover, title = stringResource(R.string.batch_edit_no_selected_covers), compact = true, modifier = Modifier.fillMaxWidth())
            }
            if (!coversLoading) gridItems(covers, key = { "${it.sourceUri}\n${it.fileLastModified}" }) { option ->
                com.moriafly.salt.ui.Card {
                    Column(Modifier.clickable { onCover(option) }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AsyncImage(CoverRequest(option.previewUri.toUri(), option.fileLastModified), null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        SaltText(option.title, style = SaltTheme.textStyles.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (option.summary.isNotBlank()) SaltText(option.summary, style = SaltTheme.textStyles.sub,
                            color = SaltTheme.colors.subText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    SaltConfirmDialog(showInfo, stringResource(R.string.batch_edit_info_summary), closeInfo, closeInfo,
        stringResource(R.string.cancel), stringResource(R.string.confirm), summary = stringResource(R.string.batch_edit_info_content))
    SaltBottomSheet(state.saveProgressBottomSheet,
        onDismissRequest = { if (!state.isSaving) viewModel.closeSaveBottomSheet() },
        onDismissFinished = onSaved, allowDismiss = !state.isSaving,
        header = { ItemOuterTitle(stringResource(R.string.batch_edit_title)) },
        groupContent = true, scrollContent = true,
        footer = { SheetProgressAction(state.isSaving, { viewModel.abortSave() }, { viewModel.closeSaveBottomSheet() },
            label = stringResource(if (state.isSaving) R.string.action_abort else R.string.confirm)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SaltText(if (state.isSaving) state.currentFile.ifEmpty { stringResource(R.string.batch_edit_processing) }
                else stringResource(R.string.batch_matching_total_time, state.saveTimeMillis / 1000.0),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            SaltText("${state.saveProgress} / ${state.saveTotal}", style = SaltTheme.textStyles.sub, color = SaltTheme.colors.subText)
            AppLinearProgressIndicator(if (state.saveTotal > 0) state.saveProgress.toFloat() / state.saveTotal else 0f, Modifier.fillMaxWidth())
            SheetStatisticsRow {
                SaltText(stringResource(R.string.batch_matching_success, state.successCount))
                SaltText(stringResource(R.string.batch_replay_gain_skipped, state.skippedCount))
                SaltText(stringResource(R.string.batch_matching_failure, state.failureCount))
            }
        }
    }
    if (showCustom) {
        var key by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        SaltConfirmDialog(true, stringResource(R.string.action_add_custom_tag), closeCustom,
            onConfirm = {
                if (key.isNotBlank()) { viewModel.addCustomFieldAndShow(key, value); closeCustom() }
            }, cancelText = stringResource(R.string.cancel), confirmText = stringResource(R.string.confirm),
            dismissOnConfirm = false, confirmEnabled = key.isNotBlank(), content = {
                RoundedColumn(paddingValues = PaddingValues(0.dp)) {
                    SaltEditorField(stringResource(R.string.label_custom_tag_name), key, { key = it }, grouped = false)
                    SaltEditorField(stringResource(R.string.label_custom_tag_value), value, { value = it }, grouped = false)
                }
            })
    }
}
