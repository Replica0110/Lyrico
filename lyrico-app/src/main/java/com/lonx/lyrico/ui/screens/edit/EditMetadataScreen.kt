@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.screens.edit

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.audiotag.model.artistPictureTypes
import com.lonx.audiotag.model.type
import com.lonx.lyrico.ExternalAudioEditHost
import com.lonx.lyrico.R
import com.lonx.lyrico.data.artwork.ArtistPosterReader
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldKind
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.editfield.toEditFieldBlocks
import com.lonx.lyrico.data.model.appearance.UiEngine
import com.lonx.lyrico.data.model.artwork.CoverRequest
import com.lonx.lyrico.data.model.export.ExportDestination
import com.lonx.lyrico.data.model.lyrics.LyricsOperation
import com.lonx.lyrico.data.model.plugin.PluginSourceType
import com.lonx.lyrico.data.model.search.LyricsSearchResult
import com.lonx.lyrico.data.model.text.ConversionMode
import com.lonx.lyrico.domain.artist.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterGrouping
import com.lonx.lyrico.domain.poster.ArtistPosterPages
import com.lonx.lyrico.domain.poster.ResolvedArtistPoster
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.ui.UiMessage
import com.lonx.lyrico.ui.components.base.ExportDestinationBottomSheet
import com.lonx.lyrico.ui.components.base.PillButton
import com.lonx.lyrico.ui.components.base.PillButtonDefaults
import com.lonx.lyrico.ui.components.base.PillButtonSize
import com.lonx.lyrico.ui.components.blur.BlurredTopBar
import com.lonx.lyrico.ui.components.blur.blurSource
import com.lonx.lyrico.ui.components.blur.rememberBarBlurBackdrop
import com.lonx.lyrico.ui.components.button.salt.SaltIconButton
import com.lonx.lyrico.ui.components.cover.rememberArtistPosterSource
import com.lonx.lyrico.ui.components.editor.CustomTagInputDialog
import com.lonx.lyrico.ui.components.editor.rememberEditorTextFieldState
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenu
import com.lonx.lyrico.ui.components.fab.ExpandableFabMenuStyle
import com.lonx.lyrico.ui.components.fab.FabMenuItem
import com.lonx.lyrico.ui.components.getBitmap
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.lyrics.LyricsProcessingSheet
import com.lonx.lyrico.ui.components.poster.ArtistPosterMenu
import com.lonx.lyrico.ui.components.poster.rememberArtistPosterMenuState
import com.lonx.lyrico.ui.components.scaffold.PageScaffold
import com.lonx.lyrico.ui.components.scaffold.pageScroll
import com.lonx.lyrico.ui.components.scaffold.scaffoldContentPadding
import com.lonx.lyrico.ui.components.scaffold.scaffoldTopHorizontalPadding
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditFieldSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.SearchCoverDestination
import com.ramcosta.composedestinations.generated.destinations.SearchLyricsDestination
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class
)
@Composable
@Destination<RootGraph>(route = "edit_metadata")
fun EditMetadataScreen(
    navigator: DestinationsNavigator,
    songFileUri: String,
    onCoverSearchResult: ResultRecipient<SearchCoverDestination, String>,
    onLyricsResult: ResultRecipient<SearchResultsDestination, LyricsSearchResult>,
    onLyricsSearchResult: ResultRecipient<SearchLyricsDestination, LyricsSearchResult>,
    externalEditRequestId: Long? = null,
) {
    val viewModel: EditMetadataViewModel = koinViewModel()
    val searchSourceProvider: SearchSourceProvider = koinInject()
    val mainSearchSources by remember(searchSourceProvider) {
        searchSourceProvider.observeSources(PluginSourceType.METADATA)
    }.collectAsState(initial = emptyList())
    val lyricsSearchSources by remember(searchSourceProvider) {
        searchSourceProvider.observeSources(PluginSourceType.LYRICS)
    }.collectAsState(initial = emptyList())
    val coverSearchSources by remember(searchSourceProvider) {
        searchSourceProvider.observeSources(PluginSourceType.COVER)
    }.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()
    // 字段顺序与显隐来自「编辑字段」配置；字段块按该配置的顺序渲染。
    val visibleFields by viewModel.visibleFields.collectAsState()
    val limitLyricsInputLines by viewModel.limitLyricsInputLines.collectAsState()
    val replayGainCalculateProgress = uiState.replayGainCalculateProgress
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData
    // 内嵌海报之外，还可以按「文件名 = 艺术家名」去外置艺术家海报文件夹里找
    val artistPosterSource = rememberArtistPosterSource()
    /**
     * 某位艺术家的**外置**海报请求：只按文件名去海报文件夹里找。
     *
     * 必须一位艺术家一个请求——外置海报是按文件名认人的，整段艺术家字段（`A/B/C`）匹配不到 `A.jpg`。
     * 也必须是 folder-only：内嵌海报的归属由 [ArtistPosterPages] / `ArtistPosterGrouping` 决定，
     * 若这里再让取图链路按自己的规则找一次内嵌图，两边对「描述为空」的旧图会给出不同答案，
     * 多艺术家时就会出现几位艺术家显示同一张图、各自的海报文件永远查不到。
     */
    fun artistPosterFileRequest(artistName: String) = CoverRequest(
        uri = songFileUri.toUri(),
        lastUpdate = 0L,
        pictureType = AudioPictureType.Artist,
        fallbackPictureTypes = listOf(AudioPictureType.LeadArtist, AudioPictureType.Band),
        // 外置海报只是内嵌艺术家海报缺失时的兜底，不要退化成普通封面
        fallbackToAny = false,
        skipEmbeddedPictures = true,
        artistName = artistName.takeIf { it.isNotBlank() },
        artistPosterFolders = listOfNotNull(artistPosterSource.folder),
        artistPosterRevision = artistPosterSource.revision
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity
    fun closeEditor(saved: Boolean) {
        if (externalEditRequestId != null) {
            (activity as? ExternalAudioEditHost)?.finishExternalAudioEdit(
                requestId = externalEditRequestId,
                saved = saved,
            )
        } else if (!navigator.popBackStack()) {
            activity.finish()
        }
    }
    // 艺术家字段按「艺术家拆分规则」拆成一个个艺术家；艺术家海报用图片描述记录归属
    val artistSplitConfig by viewModel.artistSplitConfig.collectAsState()
    val artistNames = remember(editingTagData?.artist, artistSplitConfig) {
        ArtistNameSplitter.splitArtists(editingTagData?.artist, artistSplitConfig)
    }
    // 标签里的所有艺术家图片，以及每张图片归属的艺术家
    val artistPictureEntries = ArtistPosterGrouping.entries(
        pictures = editingTagData?.pictures.orEmpty(),
        artistNames = artistNames
    )
    val originalArtistPictures = originalTagData?.pictures.orEmpty()
        .filter { it.type in artistPictureTypes }
    // 一位艺术家一个海报位：内嵌海报优先，没有的交给外置海报文件夹
    val artistPages = ArtistPosterPages.build(
        pictures = editingTagData?.pictures.orEmpty(),
        original = originalTagData?.pictures.orEmpty(),
        artistNames = artistNames
    )
    // BottomSheet 状态
    var showOffsetSheet by remember { mutableStateOf(false) }
    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showLyricsActionBottomSheet by remember { mutableStateOf(false) }
    var showLyricsExportDestinationSheet by remember { mutableStateOf(false) }
    var showCoverExportDestinationSheet by remember { mutableStateOf(false) }
    var showPlainLyricsSheet by remember { mutableStateOf(false) }
    var showCropSheet by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }
    var lyricsOperation by remember { mutableStateOf(LyricsOperation.CONVERT) }
    var showLyricsProcessingSheet by remember { mutableStateOf(false) }
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }
    var cropRequest by remember { mutableStateOf<CropRequest>(CropRequest.Cover) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    var isSearchFabMenuExpanded by remember { mutableStateOf(false) }
    // 艺术家海报的菜单与选择面板由组件自己管理
    val artistPosterMenu = rememberArtistPosterMenuState()
    var addPosterArtistName by remember { mutableStateOf<String?>(null) }
    val currentShiftOffset by viewModel.currentShiftOffset.collectAsState()

    val clipboardManager = LocalClipboard.current

    /** 弹一条带「撤销」的提示；用户点撤销时执行 [restoreChange]。 */
    fun showUndoSnackbar(message: String, restoreChange: () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.action_cancel_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                restoreChange()
            }
        }
    }

    fun showCancelUndoSnackbar(fieldLabel: String, restoreChange: () -> Unit) {
        showUndoSnackbar(context.getString(R.string.msg_field_reverted, fieldLabel), restoreChange)
    }

    fun <T> revertField(
        fieldLabel: String,
        currentValue: T,
        originalValue: T,
        applyValue: AudioTagData.(T) -> AudioTagData
    ) {
        viewModel.updateTag { applyValue(originalValue) }
        showCancelUndoSnackbar(fieldLabel) {
            viewModel.updateTag { applyValue(currentValue) }
        }
    }

    fun revertSimpleTextField(
        definition: EditFieldDefinition,
        field: SimpleTextField,
        original: AudioTagData?,
        edited: AudioTagData?,
    ) {
        revertField(
            fieldLabel = context.getString(definition.titleRes),
            currentValue = edited?.let(field.valueOf),
            originalValue = original?.let(field.valueOf),
        ) { value -> field.write(this, value) }
    }

    val salt = LocalUiEngine.current == UiEngine.SALT
    val imeVisible = WindowInsets.isImeVisible
    val isFloatingToolbarVisible = !imeVisible

    val picturePages = buildList {
        add(
            PicturePagerItem(
                label = stringResource(R.string.label_cover),
                editLabel = stringResource(R.string.edit_cover),
                source = uiState.coverUri,
                isModified = uiState.coverUri != uiState.originalCover,
                onClick = { showCoverOptionsSheet = true },
                onRevertClick = {
                    val previousCoverUri = uiState.coverUri
                    val previousPicture = uiState.picture
                    val previousPictures = editingTagData?.pictures.orEmpty()
                    val previousPicUrl = editingTagData?.picUrl
                    viewModel.revertCover()
                    showCancelUndoSnackbar(
                        context.getString(R.string.label_cover)
                    ) {
                        viewModel.restoreCoverSnapshot(
                            coverUri = previousCoverUri,
                            picture = previousPicture,
                            pictures = previousPictures,
                            picUrl = previousPicUrl
                        )
                    }
                }
            )
        )

        if (originalTagData?.supportsTypedPictures == true) {
            // 一位艺术家一个海报位：先放标签里的内嵌海报，没有的再去外置海报文件夹按文件名找
            artistPages.forEach { page ->
                val entry = page.entry
                add(
                    PicturePagerItem(
                        label = entry?.displayName(stringResource(R.string.artist_image_untagged))
                            ?: page.artistName.ifBlank { stringResource(R.string.label_artist) },
                        editLabel = stringResource(R.string.edit_artist_image),
                        source = when {
                            entry != null -> entry.picture.data
                            page.showExternalPoster -> artistPosterFileRequest(page.artistName)
                            else -> null
                        },
                        isModified = entry?.let {
                            isArtistPictureModified(it.picture, originalArtistPictures)
                        } ?: !page.showExternalPoster,
                        isUnmatched = entry?.isUnmatched == true,
                        sourceLabel = if (entry != null) {
                            stringResource(R.string.label_image_source_embedded)
                        } else if (page.showExternalPoster) {
                            stringResource(R.string.label_image_source_external)
                        } else {
                            null
                        },
                        onClick = { artistPosterMenu.open(entry, page.artistName) },
                        onRevertClick = {
                            val previousPictures = editingTagData?.pictures.orEmpty()
                            viewModel.revertArtistImages()
                            showCancelUndoSnackbar(
                                context.getString(R.string.label_artist_image)
                            ) {
                                viewModel.restoreArtistImageSnapshot(previousPictures)
                            }
                        }
                    )
                )
            }
        }
    }
    // 封面区轮播：第 0 页是封面，其后每页对应一位艺术家的海报位
    val coverPagerState = rememberPagerState(pageCount = { picturePages.size })
    val currentArtistPage = artistPages.getOrNull(coverPagerState.currentPage - 1)
    val externalPosterRequest = currentArtistPage?.takeIf { it.showExternalPoster }
        ?.let { artistPosterFileRequest(it.artistName) }
    val posterReader: ArtistPosterReader = org.koin.compose.koinInject()
    var externalPoster by remember(externalPosterRequest) { mutableStateOf<ResolvedArtistPoster.File?>(null) }
    LaunchedEffect(externalPosterRequest) {
        externalPoster = try {
            externalPosterRequest?.let { posterReader.resolve(it, strict = true) as? ResolvedArtistPoster.File }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("EditMetadata", "Cannot resolve external artist poster", e)
            null
        }
    }
    val posterFileEditor: com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterEditViewModel = koinViewModel()
    val posterFileEditState by posterFileEditor.state.collectAsState()
    var pendingPosterFileDelete by remember { mutableStateOf<ResolvedArtistPoster.File?>(null) }

    // 各种 Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.updateCover(context, it) }
    }

    fun showPosterMessage(message: UiMessage) {
        val text = message.asString(context) ?: return
        if (salt) android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
        else scope.launch { snackbarHostState.showSnackbar(text) }
    }

    com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterPickerHost(
        show = addPosterArtistName != null,
        artistName = addPosterArtistName.orEmpty(),
        posterFolder = artistPosterSource.folder,
        onDismissRequest = { addPosterArtistName = null },
        embedSummary = stringResource(R.string.artist_poster_embed_current_song_summary),
        enabled = !uiState.isSaving && !uiState.isSavingArtistPoster,
        onSetPosterFolder = { uri, onAdded ->
            viewModel.setArtistPosterFolder(uri, onAdded, ::showPosterMessage)
        },
        onImagePicked = { artistName, target, uri ->
            when (target) {
                com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterTarget.EmbedIntoSongs ->
                    viewModel.setArtistImage(context, uri, artistName)
                com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterTarget.SaveToFolder ->
                    viewModel.saveArtistImageToFolder(context, uri, artistName, ::showPosterMessage)
            }
        },
    )

    fun cropArtistImage(bytes: ByteArray, request: CropRequest) {
        scope.launch(Dispatchers.IO) {
            val bitmap = getBitmap(context, bytes)
            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    cropRequest = request
                    bitmapToCrop = bitmap
                    showCropSheet = true
                } else {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.msg_read_artist_image_failed)
                    )
                }
            }
        }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onWritePermissionResult(result.resultCode == Activity.RESULT_OK)
        if (result.resultCode != Activity.RESULT_OK) scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.permission_denied_cannot_save)) }
    }

    // 导入歌词文件选择器
    val lyricsFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importLyrics(context, it) }
    }

    // 导出歌词文件选择器
    val exportLyricsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> uri?.let { viewModel.exportLyrics(context, it) } }

    // 导出封面文件选择器
    val exportCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri -> uri?.let { viewModel.exportCover(context, it) } }

    ExportDestinationBottomSheet(
        show = showLyricsExportDestinationSheet,
        onDismissRequest = { showLyricsExportDestinationSheet = false },
        onConfirm = { destination ->
            showLyricsExportDestinationSheet = false
            when (destination) {
                ExportDestination.SELECTED_DIRECTORY -> {
                    viewModel.getLyricsFileName()?.let(exportLyricsLauncher::launch)
                }

                ExportDestination.AUDIO_DIRECTORY -> {
                    viewModel.exportLyricsToAudioDirectory(context)
                }
            }
        }
    )

    ExportDestinationBottomSheet(
        show = showCoverExportDestinationSheet,
        onDismissRequest = { showCoverExportDestinationSheet = false },
        onConfirm = { destination ->
            showCoverExportDestinationSheet = false
            when (destination) {
                ExportDestination.SELECTED_DIRECTORY -> {
                    exportCoverLauncher.launch(viewModel.getCoverFileName())
                }

                ExportDestination.AUDIO_DIRECTORY -> {
                    viewModel.exportCoverToAudioDirectory(context)
                }
            }
        }
    )

    // 事件监听
    onLyricsResult.onResult { result -> viewModel.updateMetadataFromSearchResult(result) }
    onLyricsSearchResult.onResult { result -> viewModel.updateMetadataFromSearchResult(result) }

    onCoverSearchResult.onResult { result -> viewModel.updateCover(result) }
    LaunchedEffect(uiState.permissionIntentSender) {
        uiState.permissionIntentSender?.let { intentSender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            viewModel.consumePermissionRequest()
        }
    }

    LaunchedEffect(songFileUri) { viewModel.readMetadata(songFileUri) }

    LaunchedEffect(uiState.saveSuccess) {
        uiState.saveSuccess?.let { success ->
            val message = if (success) {
                context.getString(R.string.msg_save_success)
            } else {
                context.getString(
                    R.string.msg_save_failed_with_reason,
                    uiState.saveFailureMessage?.asString(context) ?: context.getString(R.string.unknown_error_simple)
                )
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = !success,
                    duration = if (success) SnackbarDuration.Short else SnackbarDuration.Indefinite
                )
            }
            viewModel.clearSaveStatus()
            if (success) {
                closeEditor(saved = true)
            }
        }
    }

    LaunchedEffect(uiState.exportLyricsResult) {
        uiState.exportLyricsResult?.let { success ->
            val msg =
                if (success) R.string.msg_export_lyrics_success else R.string.msg_export_lyrics_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearExportLyricsStatus()
        }
    }

    LaunchedEffect(uiState.exportCoverResult) {
        uiState.exportCoverResult?.let { success ->
            val message = context.getString(
                if (success) R.string.msg_picture_saved else R.string.msg_picture_save_failed,
                context.getString(R.string.label_cover)
            )
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.clearExportCoverStatus()
        }
    }

    LaunchedEffect(uiState.exportArtistImageResult) {
        uiState.exportArtistImageResult?.let { success ->
            val message = context.getString(
                if (success) R.string.msg_picture_saved else R.string.msg_picture_save_failed,
                context.getString(R.string.label_artist_image)
            )
            scope.launch { snackbarHostState.showSnackbar(message) }
            viewModel.clearExportArtistImageStatus()
        }
    }

    LaunchedEffect(uiState.importLyricsResult) {
        uiState.importLyricsResult?.let { success ->
            val msg =
                if (success) R.string.msg_import_lyrics_success else R.string.msg_import_lyrics_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearImportLyricsStatus()
        }
    }

    LaunchedEffect(uiState.replayGainScanMessage) {
        uiState.replayGainScanMessage?.let { message ->
            scope.launch {
                message.asString(context)?.let { it1 -> snackbarHostState.showSnackbar(it1) }
            }
            viewModel.clearReplayGainScanMessage()
        }
    }

    LaunchedEffect(uiState.sameAlbumCoverMessage) {
        uiState.sameAlbumCoverMessage?.let { message ->
            scope.launch {
                message.asString(context)?.let { it1 -> snackbarHostState.showSnackbar(it1) }
            }
            viewModel.clearSameAlbumCoverMessage()
        }
    }

    BackHandler(enabled = externalEditRequestId != null) {
        if (!uiState.isSaving) closeEditor(saved = uiState.saveSuccess == true)
    }

    BackHandler(enabled = isFabMenuExpanded || isSearchFabMenuExpanded) {
        if (isSearchFabMenuExpanded) {
            isSearchFabMenuExpanded = false
        } else {
            isFabMenuExpanded = false
        }
    }

    if (uiState.editingTagData == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (salt) androidx.compose.material3.CircularProgressIndicator(color = SaltTheme.colors.highlight)
            else CircularProgressIndicator()
        }
        return
    }

    val titleState = rememberEditorTextFieldState(
        value = editingTagData?.title.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(title = it) } }
    )

    val artistState = rememberEditorTextFieldState(
        value = editingTagData?.artist.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(artist = it) } }
    )

    val albumArtistState = rememberEditorTextFieldState(
        value = editingTagData?.albumArtist.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(albumArtist = it) } }
    )

    val albumState = rememberEditorTextFieldState(
        value = editingTagData?.album.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(album = it) } }
    )

    val dateState = rememberEditorTextFieldState(
        value = editingTagData?.date.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(date = it) } }
    )

    val languageState = rememberEditorTextFieldState(
        value = editingTagData?.language.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(language = it) } }
    )

    val genreState = rememberEditorTextFieldState(
        value = editingTagData?.genre.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(genre = it) } }
    )

    val trackNumberState = rememberEditorTextFieldState(
        value = editingTagData?.trackNumber.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(trackNumber = it) } }
    )

    val discNumberState = rememberEditorTextFieldState(
        value = editingTagData?.discNumber?.toString().orEmpty(),
        onValueChange = { viewModel.updateTag { copy(discNumber = it.toIntOrNull()) } }
    )

    val composerState = rememberEditorTextFieldState(
        value = editingTagData?.composer.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(composer = it) } }
    )

    val lyricistState = rememberEditorTextFieldState(
        value = editingTagData?.lyricist.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(lyricist = it) } }
    )

    val copyrightState = rememberEditorTextFieldState(
        value = editingTagData?.copyright.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(copyright = it) } }
    )

    val commentState = rememberEditorTextFieldState(
        value = editingTagData?.comment.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(comment = it) } }
    )

    val replayGainTrackGainState = rememberEditorTextFieldState(
        value = editingTagData?.replayGainTrackGain.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(replayGainTrackGain = it) } }
    )

    val replayGainTrackPeakState = rememberEditorTextFieldState(
        value = editingTagData?.replayGainTrackPeak.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(replayGainTrackPeak = it) } }
    )

    val replayGainAlbumGainState = rememberEditorTextFieldState(
        value = editingTagData?.replayGainAlbumGain.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(replayGainAlbumGain = it) } }
    )

    val replayGainAlbumPeakState = rememberEditorTextFieldState(
        value = editingTagData?.replayGainAlbumPeak.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(replayGainAlbumPeak = it) } }
    )

    val replayGainReferenceLoudnessState = rememberEditorTextFieldState(
        value = editingTagData?.replayGainReferenceLoudness.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(replayGainReferenceLoudness = it) } }
    )

    val lyricsState = rememberEditorTextFieldState(
        value = editingTagData?.lyrics.orEmpty(),
        onValueChange = { viewModel.updateTag { copy(lyrics = it) } }
    )

    // 文本输入的状态与读写映射；显示顺序只由 visibleFields 决定。
    val simpleTextFields = remember(
        titleState, artistState, albumArtistState, albumState, dateState, languageState,
        genreState, composerState, lyricistState, copyrightState, commentState,
        replayGainTrackGainState, replayGainTrackPeakState, replayGainAlbumGainState,
        replayGainAlbumPeakState, replayGainReferenceLoudnessState,
    ) {
        mapOf(
            "title" to SimpleTextField(
                state = titleState,
                valueOf = { it.title },
                write = { value -> copy(title = value) },
            ),
            "artist" to SimpleTextField(
                state = artistState,
                valueOf = { it.artist },
                write = { value -> copy(artist = value) },
            ),
            "album_artist" to SimpleTextField(
                state = albumArtistState,
                valueOf = { it.albumArtist },
                write = { value -> copy(albumArtist = value) },
            ),
            "album" to SimpleTextField(
                state = albumState,
                valueOf = { it.album },
                write = { value -> copy(album = value) },
            ),
            "date" to SimpleTextField(
                state = dateState,
                valueOf = { it.date },
                write = { value -> copy(date = value) },
            ),
            "language" to SimpleTextField(
                state = languageState,
                valueOf = { it.language },
                write = { value -> copy(language = value) },
            ),
            "genre" to SimpleTextField(
                state = genreState,
                valueOf = { it.genre },
                write = { value -> copy(genre = value) },
            ),
            "composer" to SimpleTextField(
                state = composerState,
                valueOf = { it.composer },
                write = { value -> copy(composer = value) },
            ),
            "lyricist" to SimpleTextField(
                state = lyricistState,
                valueOf = { it.lyricist },
                write = { value -> copy(lyricist = value) },
            ),
            "copyright" to SimpleTextField(
                state = copyrightState,
                valueOf = { it.copyright },
                write = { value -> copy(copyright = value) },
            ),
            "comment" to SimpleTextField(
                state = commentState,
                valueOf = { it.comment },
                write = { value -> copy(comment = value) },
            ),
            "track_gain" to SimpleTextField(
                state = replayGainTrackGainState,
                valueOf = { it.replayGainTrackGain },
                write = { value -> copy(replayGainTrackGain = value) },
            ),
            "track_peak" to SimpleTextField(
                state = replayGainTrackPeakState,
                valueOf = { it.replayGainTrackPeak },
                write = { value -> copy(replayGainTrackPeak = value) },
            ),
            "album_gain" to SimpleTextField(
                state = replayGainAlbumGainState,
                valueOf = { it.replayGainAlbumGain },
                write = { value -> copy(replayGainAlbumGain = value) },
            ),
            "album_peak" to SimpleTextField(
                state = replayGainAlbumPeakState,
                valueOf = { it.replayGainAlbumPeak },
                write = { value -> copy(replayGainAlbumPeak = value) },
            ),
            "reference_loudness" to SimpleTextField(
                state = replayGainReferenceLoudnessState,
                valueOf = { it.replayGainReferenceLoudness },
                write = { value -> copy(replayGainReferenceLoudness = value) },
            ),
        )
    }

    val fieldBlocks = remember(visibleFields) { visibleFields.toEditFieldBlocks() }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val topBarBackdrop = rememberBarBlurBackdrop()
Box(modifier = Modifier.fillMaxSize()) {
    PageScaffold(
        title = uiState.songInfo?.tagData?.title ?: uiState.songInfo?.tagData?.fileName
            ?: stringResource(R.string.edit_metadata_default_title),
        onBack = { if (externalEditRequestId == null || !uiState.isSaving) closeEditor(saved = uiState.saveSuccess == true) },
        actions = {
            if (uiState.isSaving) androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp), color = SaltTheme.colors.highlight)
            else SaltIconButton(LucideIcons.Check, stringResource(R.string.confirm), { viewModel.saveMetadata() })
        },
        miuixTopBar = {
            val titleText = uiState.songInfo?.tagData?.title
                ?: uiState.songInfo?.tagData?.fileName
                ?: stringResource(R.string.edit_metadata_default_title)

            BlurredTopBar(backdrop = topBarBackdrop) {
                SmallTopAppBar(
                    title = titleText,
                    color = Color.Transparent,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (externalEditRequestId == null || !uiState.isSaving) {
                                    closeEditor(saved = uiState.saveSuccess == true)
                                }
                            }
                        ) { Icon(imageVector = LucideIcons.ArrowLeft, contentDescription = null) }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.saveMetadata() },
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) CircularProgressIndicator(
                                modifier = Modifier.size(
                                    24.dp
                                )
                            )
                            else Icon(imageVector = LucideIcons.Check, contentDescription = null)
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .then(if (salt) Modifier else Modifier.blurSource(topBarBackdrop).imePadding())
                .pageScroll(topAppBarScrollBehavior),
            // 用 contentPadding 而不是 padding，表单才会从毛玻璃顶栏下面滚过
            contentPadding = if (salt) scaffoldContentPadding(paddingValues, bottomExtra = 132.dp) else scaffoldTopHorizontalPadding(paddingValues),
        ) {
            fieldBlocks.forEach { block ->
                val definition = block.fields.first()
                item(key = block.key) {
                    Column {
                        if (salt && block.kind == EditFieldKind.ReplayGain) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = uiState.isReplayGainCalculating,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                progress = { replayGainCalculateProgress ?: 0f },
                                                modifier = Modifier.size(20.dp),
                                                color = SaltTheme.colors.highlight,
                                                strokeWidth = 2.5.dp,
                                                trackColor = SaltTheme.colors.highlight.copy(alpha = 0.2f),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            com.moriafly.salt.ui.Text(
                                                "${((replayGainCalculateProgress ?: 0f) * 100).toInt()}%",
                                                style = SaltTheme.textStyles.sub,
                                                color = SaltTheme.colors.highlight,
                                            )
                                        }
                                    }
                                }
                                // 行内小胶囊：用应用密度映射，避免 Salt 原生 Small 的 40dp + 正文字号。
                                PillButton(
                                    text = stringResource(if (uiState.isReplayGainCalculating) R.string.replay_gain_calculate_in_progress else R.string.action_calculate_replay_gain),
                                    onClick = { if (uiState.isReplayGainCalculating) viewModel.cancelScan() else viewModel.calculateReplayGain() },
                                    colors = PillButtonDefaults.colors(
                                        containerColor = SaltTheme.colors.highlight,
                                        contentColor = SaltTheme.colors.onHighlight,
                                    ),
                                    style = PillButtonDefaults.style(PillButtonSize.Small))
                            }
                        }
                        if (!salt && block.kind == EditFieldKind.ReplayGain) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = uiState.isReplayGainCalculating,
                                        enter = fadeIn(),
                                        exit = fadeOut()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                progress = { replayGainCalculateProgress ?: 0f },
                                                modifier = Modifier.size(20.dp),
                                                color = MiuixTheme.colorScheme.primary,
                                                strokeWidth = 2.5.dp,
                                                trackColor = MiuixTheme.colorScheme.primary.copy(
                                                    alpha = 0.2f
                                                )
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = "${((replayGainCalculateProgress ?: 0f) * 100).toInt()}%",
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(MiuixTheme.colorScheme.primary)
                                        .clickable {
                                            if (!uiState.isReplayGainCalculating) {
                                                viewModel.calculateReplayGain()
                                            } else {
                                                viewModel.cancelScan()
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (uiState.isReplayGainCalculating) {
                                            stringResource(R.string.replay_gain_calculate_in_progress)
                                        } else {
                                            stringResource(R.string.action_calculate_replay_gain)
                                        },
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        when {
                            definition.custom -> {
                                val key = requireNotNull(EditFieldRegistry.customTagKeyOf(definition.code))
                                val field = editingTagData?.customFields
                                    .orEmpty()
                                    .firstOrNull { it.key.equals(key, ignoreCase = true) }
                                    ?.copy(key = key)
                                    ?: CustomTagField(
                                        key = key,
                                        value = "",
                                    )
                                val originalField = originalTagData?.customFields
                                    .orEmpty()
                                    .firstOrNull { it.key.equals(key, ignoreCase = true) }
                                    ?.copy(key = key)
                                CustomMetadataFieldEditor(
                                    field = field,
                                    isModified = originalField
                                        ?.let { field != it }
                                        ?: field.value.isNotEmpty(),
                                    onValueChange = { newValue ->
                                        viewModel.updateCustomFieldValue(key, newValue)
                                    },
                                    onRemove = {
                                        viewModel.removeCustomFieldValue(key)
                                    },
                                    onRevert = {
                                        val previousField = editingTagData?.customFields
                                            .orEmpty()
                                            .firstOrNull { it.key.equals(key, ignoreCase = true) }
                                            ?.copy(key = key)
                                        viewModel.revertCustomField(key)
                                        showCancelUndoSnackbar(key) {
                                            if (previousField != null) {
                                                viewModel.updateCustomFieldValue(
                                                    key = key,
                                                    value = previousField.value
                                                )
                                            } else {
                                                viewModel.removeCustomFieldValue(key)
                                            }
                                        }
                                    }
                                )
                            }
                            block.kind == EditFieldKind.Cover -> {
                                CoverSection(
                                    coverUri = uiState.coverUri,
                                    title = editingTagData?.title
                                        ?: uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".")
                                        ?: "",
                                    artist = editingTagData?.artist ?: "",
                                    pages = picturePages,
                                    pagerState = coverPagerState,
                                )
                            }
                            definition.code == "rating" -> {
                                RatingField(
                                    rating = editingTagData?.rating,
                                    onRatingChange = { value -> viewModel.updateTag { copy(rating = value) } },
                                )
                            }
                            definition.code == "lyrics" -> {
                                MetadataInputField(
                                    label = stringResource(R.string.label_lyrics),
                                    state = lyricsState,
                                    isModified = !editingTagData?.lyrics.isEqualIgnoringBlank(
                                        originalTagData?.lyrics
                                    ),
                                    onRevert = {
                                        revertField(
                                            fieldLabel = context.getString(R.string.label_lyrics),
                                            currentValue = editingTagData?.lyrics ?: "",
                                            originalValue = originalTagData?.lyrics ?: ""
                                        ) { copy(lyrics = it) }
                                    },
                                    actionButtons = {
                                        if (salt) SaltIconButton(LucideIcons.EllipsisVertical,
                                            stringResource(R.string.action_lyrics_options), { showLyricsActionBottomSheet = true }, size = 32.dp, iconSize = 20.dp)
                                        else
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(
                                                6.dp,
                                                Alignment.End
                                            )
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .background(MiuixTheme.colorScheme.primary)
                                                    .clickable {
                                                        showLyricsActionBottomSheet = true
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.action_lyrics_options),
                                                    fontSize = 11.sp,
                                                    color = MiuixTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    },
                                    isMultiline = true,
                                    limitMultilineLines = limitLyricsInputLines
                                )
                            }
                            definition.code == "track_number" -> {
                                MetadataInputField(
                                    label = stringResource(R.string.label_track_number),
                                    state = trackNumberState,
                                    isModified = !editingTagData?.trackNumber.isEqualIgnoringBlank(
                                        originalTagData?.trackNumber
                                    ),
                                    onRevert = {
                                        revertField(
                                            fieldLabel = context.getString(R.string.label_track_number),
                                            currentValue = editingTagData?.trackNumber ?: "",
                                            originalValue = originalTagData?.trackNumber ?: ""
                                        ) { copy(trackNumber = it) }
                                    }
                                )
                            }
                            definition.code == "disc_number" -> {
                                MetadataInputField(
                                    label = stringResource(R.string.label_disc_number),
                                    state = discNumberState,
                                    isModified = editingTagData?.discNumber != originalTagData?.discNumber,
                                    onRevert = {
                                        revertField(
                                            fieldLabel = context.getString(R.string.label_disc_number),
                                            currentValue = editingTagData?.discNumber,
                                            originalValue = originalTagData?.discNumber
                                        ) { copy(discNumber = it) }
                                    }
                                )
                            }
                            definition.simpleTextInput -> {
                                block.fields.forEach { textDefinition ->
                                    val field = simpleTextFields.getValue(textDefinition.code)
                                    MetadataInputField(
                                        label = stringResource(textDefinition.titleRes),
                                        state = field.state,
                                        isModified = !editedValue(editingTagData, field)
                                            .isEqualIgnoringBlank(editedValue(originalTagData, field)),
                                        onRevert = {
                                            revertSimpleTextField(textDefinition, field, originalTagData, editingTagData)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }
    val fabMenuItemCount = remember(editingTagData) {
        var count = 3 // 固定项：添加自定义标签、播放、字段显示设置

        if (editingTagData != null) {
            count += 3 // 歌词操作、封面操作、艺术家图片操作
        }

        count
    }
    val searchMenuItemCount = remember(mainSearchSources, lyricsSearchSources, coverSearchSources) {
        listOf(mainSearchSources, lyricsSearchSources, coverSearchSources).count { it.isNotEmpty() }
    }
    if (searchMenuItemCount > 0) {
        ExpandableFabMenu(
            modifier = Modifier.padding(bottom = 116.dp),
            visible = isFloatingToolbarVisible,
            expanded = isSearchFabMenuExpanded,
            enabled = true,
            itemCount = searchMenuItemCount,
            style = ExpandableFabMenuStyle.default().copy(mainIcon = LucideIcons.Search),
            onExpandedChange = { expanded ->
                if (expanded) isFabMenuExpanded = false
                isSearchFabMenuExpanded = expanded
            }
        ) {
            if (mainSearchSources.isNotEmpty()) {
                FabMenuItem(
                    label = stringResource(R.string.action_main_search),
                    icon = LucideIcons.Search,
                    onClick = {
                        isSearchFabMenuExpanded = false
                        val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                            if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                            else "${editingTagData.title} ${editingTagData.artist}"
                        } else {
                            uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                        }
                        navigator.navigate(SearchResultsDestination(keyword))
                    }
                )
            }
            if (lyricsSearchSources.isNotEmpty()) {
                FabMenuItem(
                    label = stringResource(R.string.action_search_lyrics),
                    icon = LucideIcons.Captions,
                    onClick = {
                        isSearchFabMenuExpanded = false
                        navigator.navigate(
                            SearchLyricsDestination(
                                title = editingTagData?.title.orEmpty(),
                                artist = editingTagData?.artist.orEmpty(),
                                album = editingTagData?.album.orEmpty(),
                                date = editingTagData?.date.orEmpty()
                            )
                        )
                    }
                )
            }
            if (coverSearchSources.isNotEmpty()) {
                FabMenuItem(
                    label = stringResource(R.string.action_search_cover),
                    icon = LucideIcons.ImageSearch,
                    onClick = {
                        isSearchFabMenuExpanded = false
                        val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                            if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                            else "${editingTagData.title} ${editingTagData.artist}"
                        } else {
                            uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                        }
                        navigator.navigate(SearchCoverDestination(keyword))
                    }
                )
            }
        }
    }
    ExpandableFabMenu(
        modifier = Modifier
            .padding(bottom = 48.dp),
        visible = isFloatingToolbarVisible,
        expanded = isFabMenuExpanded,
        enabled = true,
        itemCount = fabMenuItemCount,
        onExpandedChange = { expanded ->
            if (expanded) isSearchFabMenuExpanded = false
            isFabMenuExpanded = expanded
        }
    ) {
        FabMenuItem(
            label = stringResource(R.string.action_add_custom_tag),
            icon = LucideIcons.Plus,
            onClick = {
                isFabMenuExpanded = false
                showAddCustomTagDialog = true
            }
        )

        if (editingTagData != null) {
            FabMenuItem(
                label = stringResource(R.string.action_lyrics_options),
                icon = LucideIcons.Captions,
                onClick = {
                    isFabMenuExpanded = false
                    showLyricsActionBottomSheet = true
                }
            )
        }

        if (editingTagData != null) {
            FabMenuItem(
                label = stringResource(R.string.label_cover_options),
                icon = LucideIcons.Image,
                onClick = {
                    isFabMenuExpanded = false
                    showCoverOptionsSheet = true
                }
            )
            FabMenuItem(
                label = stringResource(R.string.label_artist_image_options),
                icon = LucideIcons.Users,
                onClick = {
                    isFabMenuExpanded = false
                    // 通用入口沿用当前海报位；在封面页时由用户选择目标。
                    artistPosterMenu.open()
                }
            )
        }

        FabMenuItem(
            label = stringResource(R.string.menu_play_music),
            icon = LucideIcons.Play,
            onClick = {
                isFabMenuExpanded = false
                viewModel.play(context)
            }
        )

        FabMenuItem(
            label = stringResource(R.string.edit_field_settings_title),
            icon = LucideIcons.Settings,
            onClick = {
                isFabMenuExpanded = false
                navigator.navigate(EditFieldSettingsDestination())
            }
        )
    }
}
    // 歌词操作
    LyricsActionSheet(
        show = showLyricsActionBottomSheet,
        onDismissRequest = { showLyricsActionBottomSheet = false },
        hasLyricsSearchSources = lyricsSearchSources.isNotEmpty(),
        onSearchLyrics = {
            showLyricsActionBottomSheet = false
            navigator.navigate(
                SearchLyricsDestination(
                    title = editingTagData?.title.orEmpty(),
                    artist = editingTagData?.artist.orEmpty(),
                    album = editingTagData?.album.orEmpty(),
                    date = editingTagData?.date.orEmpty()
                )
            )
        },
        onImportLyrics = {
            showLyricsActionBottomSheet = false
            lyricsFileLauncher.launch(arrayOf("*/*"))
        },
        hasLyrics = editingTagData?.lyrics != null,
        onExportLyrics = {
            showLyricsActionBottomSheet = false
            showLyricsExportDestinationSheet = true
        },
        onConvertSimplifiedToTraditional = {
            showLyricsActionBottomSheet = false
            viewModel.convertLyrics(ConversionMode.SIMPLIFIED_TO_TRADITIONAL)
        },
        onConvertTraditionalToSimplified = {
            showLyricsActionBottomSheet = false
            viewModel.convertLyrics(ConversionMode.TRADITIONAL_TO_SIMPLIFIED)
        },
        onAdjustOffset = {
            showLyricsActionBottomSheet = false
            viewModel.prepareLyricsOffset()
            showOffsetSheet = true
        },
        onConvertFormat = {
            showLyricsActionBottomSheet = false
            lyricsOperation = LyricsOperation.CONVERT
            showLyricsProcessingSheet = true
        },
        onOrganizeLyrics = { operation ->
            showLyricsActionBottomSheet = false
            lyricsOperation = operation
            showLyricsProcessingSheet = true
        },
        onViewPlainLyrics = {
            showLyricsActionBottomSheet = false
            showPlainLyricsSheet = true
        },
        limitLyricsInputLines = limitLyricsInputLines,
        onLimitLyricsInputLinesChange = { viewModel.setLimitLyricsInputLines(it) }
    )
    // 歌词文本预览
    var plainLyricsShowRomanization by remember { mutableStateOf(true) }
    var plainLyricsShowTranslation by remember { mutableStateOf(true) }
    val plainLyrics = viewModel.getPlainLyrics(
        showRomanization = plainLyricsShowRomanization,
        showTranslation = plainLyricsShowTranslation
    )
    LyricsPreviewSheet(
        show = showPlainLyricsSheet,
        onDismissRequest = { showPlainLyricsSheet = false },
        plainLyrics = plainLyrics,
        showRomanization = plainLyricsShowRomanization,
        onShowRomanizationChange = { plainLyricsShowRomanization = it },
        showTranslation = plainLyricsShowTranslation,
        onShowTranslationChange = { plainLyricsShowTranslation = it },
        onCopyLyrics = {
            showPlainLyricsSheet = false
            if (!plainLyrics.isNullOrEmpty()) {
                scope.launch {
                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("copy plain lyrics", plainLyrics)))
                }
            }
        }
    )
    // 封面操作
    CoverOptionsSheet(
        show = showCoverOptionsSheet,
        onDismissRequest = { showCoverOptionsSheet = false },
        hasCoverSearchSources = coverSearchSources.isNotEmpty(),
        onSearchCover = {
            val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                else "${editingTagData.title} ${editingTagData.artist}"
            } else {
                uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
            }
            showCoverOptionsSheet = false
            navigator.navigate(SearchCoverDestination(keyword))
        },
        onChangeCover = {
            showCoverOptionsSheet = false
            photoPickerLauncher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        },
        onSelectSameAlbumCover = {
            showCoverOptionsSheet = false
            viewModel.loadSameAlbumCovers()
        },
        onRemoveCover = {
            showCoverOptionsSheet = false
            viewModel.removeFrontCover()
        },
        canExportOrCropCover = uiState.coverUri != null || uiState.originalCover != null,
        onExportCover = {
            showCoverOptionsSheet = false
            showCoverExportDestinationSheet = true
        },
        onCropCover = {
            showCoverOptionsSheet = false
            val sourceData = uiState.coverUri ?: uiState.originalCover

            if (sourceData != null) {
                scope.launch(Dispatchers.IO) {
                    val bitmap = getBitmap(context, sourceData)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            cropRequest = CropRequest.Cover
                            bitmapToCrop = bitmap
                            showCropSheet = true
                        } else {
                            snackbarHostState.showSnackbar(context.getString(R.string.msg_read_cover_failed)) // "无法读取封面图片"
                        }
                    }
                }
            }
        }
    )

    /** 重新绑定：目标艺术家已有海报时，等用户选「交换归属」还是「直接替换」。 */
    var pendingRebind by remember { mutableStateOf<PendingRebind?>(null) }

    /** 改挂归属，给一次反悔机会。 */
    fun applyRebind(target: AudioPicture, artistName: String, swap: Boolean = false) {
        val previousPictures = editingTagData?.pictures.orEmpty()
        if (swap) {
            viewModel.swapArtistImages(target, artistName)
        } else {
            viewModel.reassignArtistImages(target, artistName)
        }
        showUndoSnackbar(
            context.getString(R.string.msg_artist_image_reassigned, artistName)
        ) {
            viewModel.restoreArtistImageSnapshot(previousPictures)
        }
    }

    // 艺术家海报的操作菜单与选择面板
    ArtistPosterMenu(
        state = artistPosterMenu,
        artists = artistNames,
        entries = artistPictureEntries,
        currentEntry = currentArtistPage?.entry,
        currentPageIsArtistSlot = currentArtistPage != null,
        currentArtistName = currentArtistPage?.artistName,
        onSetPoster = { addPosterArtistName = it },
        onRemove = { viewModel.removeArtistImage(it) },
        onExport = { viewModel.exportArtistImage(context, it) },
        onCrop = { cropArtistImage(it.data, CropRequest.ArtistImage(it)) },
        externalPoster = externalPoster,
        externalActionsEnabled = !posterFileEditState.isRunning && !uiState.isSavingArtistPoster,
        onCropExternal = { cropArtistImage(it.bytes, CropRequest.ExternalArtistImage(it)) },
        onExportExternal = { viewModel.exportArtistImage(context, it.bytes) },
        onDeleteExternal = { pendingPosterFileDelete = it },
        onReassign = { target, artistName ->
            val ownerKey = ArtistPosterGrouping.ownerKeyOf(artistName)
            val occupied = artistPictureEntries.any {
                it.picture !== target && it.artist != null && it.ownerKey == ownerKey
            }
            if (occupied) {
                // 目标艺术家已有海报：让用户决定是交换归属还是直接替换掉它
                pendingRebind = PendingRebind(target, artistName)
            } else {
                applyRebind(target, artistName)
            }
        },
        onMissingArtist = {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.msg_artist_field_empty_for_poster)
                )
            }
        },
    )
    com.lonx.lyrico.ui.components.base.YesNoDialog(
        show = pendingPosterFileDelete != null,
        title = stringResource(R.string.dialog_delete_file_title),
        summary = stringResource(R.string.dialog_delete_file_content, pendingPosterFileDelete?.name.orEmpty()),
        onDismissRequest = { pendingPosterFileDelete = null },
        onConfirm = { pendingPosterFileDelete?.let(posterFileEditor::deletePosterFile) },
    )
    com.lonx.lyrico.ui.screens.artist.poster.ArtistPosterRemovalHost(
        viewModel = posterFileEditor,
        pendingIds = emptySet(),
        onDismissConfirmation = {},
    )
    // 裁剪界面
    com.lonx.lyrico.ui.components.crop.ImageCropperSheet(
        show = showCropSheet, bitmap = bitmapToCrop,
        title = stringResource(if (cropRequest is CropRequest.Cover) R.string.label_crop_cover else R.string.label_crop_artist_image),
        onCrop = { bitmap ->
            when (val request = cropRequest) {
                CropRequest.Cover -> viewModel.updateCover(bitmap)
                is CropRequest.ArtistImage -> viewModel.replaceArtistImage(bitmap, request.target)
                is CropRequest.ExternalArtistImage -> posterFileEditor.cropPoster(request.target, bitmap)
            }
        },
        onDismissRequest = { showCropSheet = false },
        onDismissFinished = { if (!showCropSheet) bitmapToCrop = null }
    )
    // 偏移调整 BottomSheet
    LyricsOffsetSheet(
        show = showOffsetSheet,
        onDismissRequest = { showOffsetSheet = false },
        lyricsText = editingTagData?.lyrics.orEmpty(),
        currentOffset = currentShiftOffset,
        onOffsetChange = { viewModel.applyLyricsOffset(it) }
    )
    // 目标艺术家已有海报：交换归属，还是把原来那张替换掉
    ArtistImageConflictDialog(
        pending = pendingRebind,
        onDismissRequest = { pendingRebind = null },
        onSwap = { target, artistName ->
            applyRebind(target, artistName, swap = true)
        },
        onReplace = { target, artistName ->
            applyRebind(target, artistName, swap = false)
        }
    )

    CustomTagInputDialog(
        show = showAddCustomTagDialog,
        onDismissRequest = { showAddCustomTagDialog = false },
        onConfirm = { key, value -> viewModel.addCustomFieldAndShow(key, value) },
    )
    LyricsProcessingSheet(
        show = showLyricsProcessingSheet,
        raw = editingTagData?.lyrics.orEmpty(),
        operation = lyricsOperation,
        onDismiss = { showLyricsProcessingSheet = false },
        onConfirm = { options ->
            viewModel.processLyrics(options)
            showLyricsProcessingSheet = false
        }
    )
}
