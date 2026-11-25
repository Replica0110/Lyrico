@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.batch

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.editfield.toEditFieldBlocks
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.components.lyrics.LyricsOffsetField
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.dialog.salt.SaltConfirmDialog
import com.lonx.lyrico.ui.components.editor.CustomTagInputDialog
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.pageScroll
import com.lonx.lyrico.ui.components.scaffold.scaffoldBottomPadding
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.components.tabs.AppTabs
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemOuterTitle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditFieldSettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class BatchEditTab(val labelRes: Int) {
    Config(R.string.batch_edit_tab_config),
    Preview(R.string.batch_edit_tab_preview)
}

/** 候选值来自哪个入口：内置字段，或某个自定义标签。 */
private sealed interface BatchEditValueTarget {
    data class Field(val field: BatchEditField) : BatchEditValueTarget
    data class CustomTag(val key: String) : BatchEditValueTarget
}

/**
 * 候选列表的加载状态：一次只展示一个来源，用请求号丢弃过期的异步结果。
 */
@Stable
private class BatchEditSelectionOptions<T>(
    private val scope: CoroutineScope,
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var visible by mutableStateOf(false)
        private set

    private var request = 0

    fun open(load: suspend () -> List<T>) {
        val token = ++request
        items = emptyList()
        loading = true
        visible = true
        scope.launch {
            val loaded = load()
            if (token == request && visible) {
                items = loaded
                loading = false
            }
        }
    }

    fun dismiss() {
        visible = false
    }
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

    var showCoverMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }
    var expandedFabMenu by remember { mutableStateOf(false) }
    var valueTarget by remember { mutableStateOf<BatchEditValueTarget?>(null) }

    val scope = rememberCoroutineScope()
    val valuePicker = remember(scope) { BatchEditSelectionOptions<BatchEditSelectableValue>(scope) }
    val coverPicker = remember(scope) { BatchEditSelectionOptions<BatchEditSelectableCover>(scope) }

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

    fun openValuePicker(target: BatchEditValueTarget, load: suspend () -> List<BatchEditSelectableValue>) {
        valueTarget = target
        valuePicker.open(load)
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

    val salt = LocalUiEngine.current == UiEngine.SALT
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    BackHandler(
        enabled = !uiState.isSaving
    ) {
        if (!uiState.isSaving) {
            if (expandedFabMenu) {
                expandedFabMenu = false
            } else {
                navigator.popBackStack()
            }
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        PageScaffold(
            title = stringResource(R.string.batch_edit_title),
            onBack = { if (!uiState.isSaving) navigator.popBackStack() },
            actions = {
                SaltIconButton(
                    LucideIcons.Check, stringResource(R.string.confirm),
                    { viewModel.saveBatchEdit() }, enabled = !uiState.isSaving
                )
            },
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
                    AppTabs(tabLabels, pagerState.currentPage, { index ->
                        scope.launch {
                            expandedFabMenu = false
                            pagerState.animateScrollToPage(index)
                        }
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
                                        .pageScroll(topAppBarScrollBehavior)
                                        .fillMaxHeight()
                                        .then(if (salt) Modifier else Modifier.imePadding()),
                                    contentPadding = PaddingValues(
                                        bottom = scaffoldBottomPadding(paddingValues, bottomExtra = 80.dp),
                                    ),
                                ) {
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
                                                                    openValuePicker(
                                                                        BatchEditValueTarget.CustomTag(key)
                                                                    ) { viewModel.getSelectedSongCustomTagValues(key) }
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
                                                                    showCoverMenu = true
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

                                                            // 歌词偏移是数值调整控件，不是标签字段。
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

                                                            else -> {
                                                                val item = requireNotNull(editField)
                                                                BatchEditFieldItem(
                                                                    field = item,
                                                                    value = item.readDraftValue(uiState),
                                                                    onValueChange = { newValue ->
                                                                        viewModel.updateField(item, newValue)
                                                                    },
                                                                    onSelectFromSongs = {
                                                                        openValuePicker(
                                                                            BatchEditValueTarget.Field(item)
                                                                        ) { viewModel.getSelectedSongFieldValues(item) }
                                                                    },
                                                                    isMultiline = item == BatchEditField.LYRICS
                                                                )
                                                            }
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

    BatchEditCoverMenuSheet(
        show = showCoverMenu,
        canRevert = uiState.coverUri != null || uiState.removeCover,
        onDismiss = { showCoverMenu = false },
        onChangeCover = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onPickFromSongs = { coverPicker.open { viewModel.getSelectedSongCovers() } },
        onSameAlbum = {
            scope.launch {
                try {
                    viewModel.getSameAlbumCovers().firstOrNull()?.second?.let(::applyCoverSource)
                } catch (e: Exception) {
                    // 读不到同专辑封面时保持当前选择。
                }
            }
        },
        onRemove = viewModel::removeCover,
        onRevert = viewModel::revertCover,
    )

    BatchEditCoverPickerSheet(
        show = coverPicker.visible,
        loading = coverPicker.loading,
        covers = coverPicker.items,
        onDismiss = coverPicker::dismiss,
        onPick = { option ->
            scope.launch {
                viewModel.getSelectedSongCover(option.sourceUri)?.let { cover ->
                    applyCoverSource(cover)
                    coverPicker.dismiss()
                }
            }
        },
    )

    BatchEditValueSheet(
        show = valuePicker.visible,
        title = when (val target = valueTarget) {
            is BatchEditValueTarget.Field -> stringResource(target.field.labelResId)
            is BatchEditValueTarget.CustomTag -> target.key
            null -> ""
        },
        loading = valuePicker.loading,
        options = valuePicker.items,
        onDismiss = valuePicker::dismiss,
        onPick = { value ->
            when (val target = valueTarget) {
                is BatchEditValueTarget.Field -> viewModel.updateField(target.field, value)
                is BatchEditValueTarget.CustomTag -> viewModel.setCustomFieldValue(target.key, value)
                null -> Unit
            }
            valuePicker.dismiss()
        },
    )

    BatchEditSaveProgressSheet(
        state = uiState,
        onDismiss = { if (!uiState.isSaving) viewModel.closeSaveBottomSheet() },
        onDismissFinished = { if (uiState.saveSuccess == true) navigator.popBackStack() },
        onAbort = viewModel::abortSave,
        onClose = viewModel::closeSaveBottomSheet,
    )

    // 批量编辑说明对话框
    if (salt) {
        SaltConfirmDialog(
            showInfoDialog,
            stringResource(R.string.batch_edit_info_summary),
            { showInfoDialog = false },
            { showInfoDialog = false },
            stringResource(R.string.cancel),
            stringResource(R.string.confirm),
            summary = stringResource(R.string.batch_edit_info_content)
        )
    } else {
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
    }

    CustomTagInputDialog(
        show = showAddCustomTagDialog,
        onDismissRequest = { showAddCustomTagDialog = false },
        onConfirm = { key, value -> viewModel.addCustomFieldAndShow(key, value) },
    )
}
