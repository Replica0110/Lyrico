package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.ImageCropper
import com.lonx.lyrico.ui.components.getBitmap
import com.lonx.lyrico.ui.components.rememberImageCropperState
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.utils.LyricDecoder
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrico.viewmodel.isEqualIgnoringBlank
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchCoverDestination
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

private const val LIMITED_LYRICS_INPUT_MAX_LINES = 30

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
    onLyricsResult: ResultRecipient<SearchResultsDestination, LyricsSearchResult>
) {
    val viewModel: EditMetadataViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val limitLyricsInputLines by viewModel.limitLyricsInputLines.collectAsState()
    val replayGainCalculateProgress = uiState.replayGainCalculateProgress
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity
    // BottomSheet 状态
    var showOffsetSheet by remember { mutableStateOf(false) }
    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    var showLyricsActionBottomSheet by remember { mutableStateOf(false) }
    var showPlainLyricsSheet by remember { mutableStateOf(false) }
    var showCropSheet by remember { mutableStateOf(false) }
    var showAddCustomTagDialog by remember { mutableStateOf(false) }
    var showLyricsFormatBottomSheet by remember { mutableStateOf(false) }
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }

    val currentShiftOffset by viewModel.currentShiftOffset.collectAsState()

    val clipboardManager = LocalClipboardManager.current

    val imeVisible = WindowInsets.isImeVisible
    val isFloatingToolbarVisible = !imeVisible
    
    // 各种 Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.updateCover(it) } }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.saveMetadata()
        else scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.permission_denied_cannot_save)) }
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

    // 事件监听
    onLyricsResult.onResult { result -> viewModel.updateMetadataFromSearchResult(result) }

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
            val msg = if (success) R.string.msg_save_success else R.string.msg_save_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearSaveStatus()
            if (success) {
                if (!navigator.popBackStack()) {
                    activity.finish()
                }
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

    BackHandler {
        if (!navigator.popBackStack()) {
            activity.finish()
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val titleText = uiState.songInfo?.tagData?.title
                ?: uiState.songInfo?.tagData?.fileName
                ?: stringResource(R.string.edit_metadata_default_title)

            SmallTopAppBar(
                title = titleText,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!navigator.popBackStack()) {
                                activity.finish()
                            }
                        }
                    ) { Icon(imageVector = MiuixIcons.Back, contentDescription = null) }
                },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = {
                        val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                            if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                            else "${editingTagData.title} ${editingTagData.artist}"
                        } else {
                            uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                        }
                        navigator.navigate(SearchResultsDestination(keyword))
                    }) { Icon(imageVector = MiuixIcons.Search, contentDescription = null) }

                    // 保存按钮
                    IconButton(
                        onClick = { viewModel.saveMetadata() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Icon(imageVector = MiuixIcons.Ok, contentDescription = null)
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingToolbarPosition = ToolbarPosition.CenterEnd,
        floatingToolbar = {
            AnimatedVisibility(
                visible = isFloatingToolbarVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                FloatingToolbar() {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                showAddCustomTagDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null
                            )
                        }
                        if (!editingTagData?.lyrics.isNullOrBlank()) {
                            IconButton(
                                onClick = {
                                    showLyricsActionBottomSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Notes,
                                    contentDescription = null
                                )
                            }
                        }
                        if (uiState.coverUri != null) {
                            IconButton(
                                onClick = {
                                    showCoverOptionsSheet = true
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Image,
                                    contentDescription = null
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.play(context) }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Play,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .imePadding()
                .scrollEndHaptic(),
        ) {
            // 封面编辑区
            item(key = "cover") {
                CoverSection(
                    coverUri = uiState.coverUri,
                    title = editingTagData?.title
                        ?: uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: "",
                    artist = editingTagData?.artist ?: "",
                    rating = editingTagData?.rating ?: 0,
                    isModified = uiState.coverUri != uiState.originalCover,
                    onCoverClick = { showCoverOptionsSheet = true },
                    onRevertCoverClick = { viewModel.revertCover() },
                    onRatingChange = { newRating ->
                        viewModel.updateTag { copy(rating = newRating) }
                    }
                )
            }

            // 基础信息组
            item(key = "basic_info") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_basic_info))
                    MetadataInputField(
                        label = stringResource(R.string.label_title),
                        value = editingTagData?.title ?: "",
                        onValueChange = { viewModel.updateTag { copy(title = it) } },
                        isModified = !editingTagData?.title.isEqualIgnoringBlank(
                            originalTagData?.title
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    title = originalTagData?.title ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_artists),
                        value = editingTagData?.artist ?: "",
                        onValueChange = { viewModel.updateTag { copy(artist = it) } },
                        isModified = !editingTagData?.artist.isEqualIgnoringBlank(
                            originalTagData?.artist
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    artist = originalTagData?.artist ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_album_artist),
                        value = editingTagData?.albumArtist ?: "",
                        onValueChange = { viewModel.updateTag { copy(albumArtist = it) } },
                        isModified = !editingTagData?.albumArtist.isEqualIgnoringBlank(
                            originalTagData?.albumArtist
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    albumArtist = originalTagData?.albumArtist ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_album),
                        value = editingTagData?.album ?: "",
                        onValueChange = { viewModel.updateTag { copy(album = it) } },
                        isModified = !editingTagData?.album.isEqualIgnoringBlank(
                            originalTagData?.album
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    album = originalTagData?.album ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_date),
                        value = editingTagData?.date ?: "",
                        onValueChange = { viewModel.updateTag { copy(date = it) } },
                        isModified = !editingTagData?.date.isEqualIgnoringBlank(
                            originalTagData?.date
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    date = originalTagData?.date ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_genre),
                        value = editingTagData?.genre ?: "",
                        onValueChange = { viewModel.updateTag { copy(genre = it) } },
                        isModified = !editingTagData?.genre.isEqualIgnoringBlank(
                            originalTagData?.genre
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    genre = originalTagData?.genre ?: ""
                                )
                            }
                        }
                    )
                }
            }

            item(key = "track_details") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_track_details))
                    MetadataInputField(
                        label = stringResource(R.string.label_track_number),
                        value = editingTagData?.trackNumber ?: "",
                        onValueChange = { viewModel.updateTag { copy(trackNumber = it) } },
                        isModified = !editingTagData?.trackNumber.isEqualIgnoringBlank(
                            originalTagData?.trackNumber
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    trackNumber = originalTagData?.trackNumber ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_disc_number),
                        value = editingTagData?.discNumber?.toString() ?: "",
                        onValueChange = { viewModel.updateTag { copy(discNumber = it.toIntOrNull()) } },
                        isModified = editingTagData?.discNumber != originalTagData?.discNumber,
                        onRevert = { viewModel.updateTag { copy(discNumber = originalTagData?.discNumber) } }
                    )
                }
            }

            item(key = "credits_other") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_credits_other))
                    MetadataInputField(
                        label = stringResource(R.string.label_composer),
                        value = editingTagData?.composer ?: "",
                        onValueChange = { viewModel.updateTag { copy(composer = it) } },
                        isModified = !editingTagData?.composer.isEqualIgnoringBlank(
                            originalTagData?.composer
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    composer = originalTagData?.composer ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_lyricist),
                        value = editingTagData?.lyricist ?: "",
                        onValueChange = { viewModel.updateTag { copy(lyricist = it) } },
                        isModified = !editingTagData?.lyricist.isEqualIgnoringBlank(
                            originalTagData?.lyricist
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    lyricist = originalTagData?.lyricist ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_copyright),
                        value = editingTagData?.copyright ?: "",
                        onValueChange = { viewModel.updateTag { copy(copyright = it) } },
                        isModified = !editingTagData?.copyright.isEqualIgnoringBlank(
                            originalTagData?.copyright
                        ),
                        onRevert = { viewModel.updateTag { copy(copyright = originalTagData?.copyright) } }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_comment),
                        value = editingTagData?.comment ?: "",
                        onValueChange = { viewModel.updateTag { copy(comment = it) } },
                        isModified = !editingTagData?.comment.isEqualIgnoringBlank(
                            originalTagData?.comment
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    comment = originalTagData?.comment ?: ""
                                )
                            }
                        }
                    )
                }
            }

            item(key = "replay_gain") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_replay_gain))
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
                                    // 环形进度条
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

                                    // 进度百分比文本
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
                    MetadataInputField(
                        label = stringResource(R.string.label_replaygain_track_gain),
                        value = editingTagData?.replayGainTrackGain ?: "",
                        onValueChange = { viewModel.updateTag { copy(replayGainTrackGain = it) } },
                        isModified = !editingTagData?.replayGainTrackGain.isEqualIgnoringBlank(
                            originalTagData?.replayGainTrackGain
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    replayGainTrackGain = originalTagData?.replayGainTrackGain
                                        ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_replaygain_track_peak),
                        value = editingTagData?.replayGainTrackPeak ?: "",
                        onValueChange = { viewModel.updateTag { copy(replayGainTrackPeak = it) } },
                        isModified = !editingTagData?.replayGainTrackPeak.isEqualIgnoringBlank(
                            originalTagData?.replayGainTrackPeak
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    replayGainTrackPeak = originalTagData?.replayGainTrackPeak
                                        ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_replaygain_album_gain),
                        value = editingTagData?.replayGainAlbumGain ?: "",
                        onValueChange = { viewModel.updateTag { copy(replayGainAlbumGain = it) } },
                        isModified = !editingTagData?.replayGainAlbumGain.isEqualIgnoringBlank(
                            originalTagData?.replayGainAlbumGain
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    replayGainAlbumGain = originalTagData?.replayGainAlbumGain
                                        ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_replaygain_album_peak),
                        value = editingTagData?.replayGainAlbumPeak ?: "",
                        onValueChange = { viewModel.updateTag { copy(replayGainAlbumPeak = it) } },
                        isModified = !editingTagData?.replayGainAlbumPeak.isEqualIgnoringBlank(
                            originalTagData?.replayGainAlbumPeak
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    replayGainAlbumPeak = originalTagData?.replayGainAlbumPeak
                                        ?: ""
                                )
                            }
                        }
                    )
                    MetadataInputField(
                        label = stringResource(R.string.label_replaygain_reference_loudness),
                        value = editingTagData?.replayGainReferenceLoudness ?: "",
                        onValueChange = {
                            viewModel.updateTag {
                                copy(
                                    replayGainReferenceLoudness = it
                                )
                            }
                        },
                        isModified = !editingTagData?.replayGainReferenceLoudness.isEqualIgnoringBlank(
                            originalTagData?.replayGainReferenceLoudness
                        ),
                        onRevert = {
                            viewModel.updateTag {
                                copy(
                                    replayGainReferenceLoudness = originalTagData?.replayGainReferenceLoudness
                                        ?: ""
                                )
                            }
                        }
                    )
                }
            }

            if (!editingTagData?.customFields.isNullOrEmpty()) {
                item(key = "custom_fields") {
                    Column {
                        SmallTitle(text = stringResource(R.string.group_custom_tags))
                        editingTagData.customFields.forEachIndexed { index, field ->
                            CustomMetadataFieldEditor(
                                field = field,
                                isModified = field != originalTagData?.customFields?.getOrNull(
                                    index
                                ),
                                onKeyChange = { newKey ->
                                    viewModel.updateTag {
                                        copy(
                                            customFields = customFields.toMutableList()
                                                .apply {
                                                    this[index] =
                                                        this[index].copy(key = newKey)
                                                }
                                        )
                                    }
                                },
                                onValueChange = { newValue ->
                                    viewModel.updateTag {
                                        copy(
                                            customFields = customFields.toMutableList()
                                                .apply {
                                                    this[index] =
                                                        this[index].copy(value = newValue)
                                                }
                                        )
                                    }
                                },
                                onRemove = {
                                    viewModel.updateTag {
                                        copy(
                                            customFields = customFields.toMutableList()
                                                .apply {
                                                    removeAt(index)
                                                }
                                        )
                                    }
                                },
                                onRevert = {
                                    viewModel.updateTag {
                                        val originalField =
                                            originalTagData?.customFields?.getOrNull(index)
                                        copy(
                                            customFields = customFields.toMutableList()
                                                .apply {
                                                    if (originalField != null) {
                                                        this[index] = originalField
                                                    } else {
                                                        removeAt(index)
                                                    }
                                                }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item(key = "lyrics") {
                Column {
                    SmallTitle(text = stringResource(R.string.label_lyrics))
//                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        MetadataInputField(
                            label = stringResource(R.string.label_lyrics),
                            value = editingTagData?.lyrics ?: "",
                            onValueChange = { viewModel.updateTag { copy(lyrics = it) } },
                            isModified = !editingTagData?.lyrics.isEqualIgnoringBlank(
                                originalTagData?.lyrics
                            ),
                            onRevert = {
                                viewModel.updateTag {
                                    copy(
                                        lyrics = originalTagData?.lyrics ?: ""
                                    )
                                }
                            },
                            actionButtons = {
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
                                    // 歌词操作
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
//                    }
                }
            }
        }
    }
    // 歌词操作
    WindowBottomSheet(
        show = showLyricsActionBottomSheet,
        enableNestedScroll = false,
        title = stringResource(R.string.action_lyrics_options),
        onDismissRequest = { showLyricsActionBottomSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer,
                )
            ) {
                ArrowPreference(
                    title = stringResource(R.string.action_import_lyrics),
                    onClick = {
                        showLyricsActionBottomSheet = false
                        lyricsFileLauncher.launch(arrayOf("*/*"))
                    }
                )
                editingTagData?.lyrics?.let {
                    ArrowPreference(
                        title = stringResource(R.string.action_export_lyrics),
                        onClick = {
                            showLyricsActionBottomSheet = false
                            val fileName = viewModel.getLyricsFileName()
                            if (fileName != null) {
                                exportLyricsLauncher.launch(fileName)
                            }
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.chinese_conversion_mode_simplified_to_traditional),
                        onClick = {
                            showLyricsActionBottomSheet = false
                            viewModel.convertLyrics(ConversionMode.SIMPLIFIED_TO_TRADITIONAL)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.chinese_conversion_mode_traditional_to_simplified),
                        onClick = {
                            showLyricsActionBottomSheet = false
                            viewModel.convertLyrics(ConversionMode.TRADITIONAL_TO_SIMPLIFIED)
                        }
                    )

                    ArrowPreference(
                        title = stringResource(R.string.offset_adjust_hint),
                        onClick = {
                            showLyricsActionBottomSheet = false
                            viewModel.prepareLyricsOffset()
                            showOffsetSheet = true
                        }
                    )

                    // 格式转换选项
                    val detectedFormat = LyricDecoder.detectFormat(it)
                    val formatText = when (detectedFormat) {
                        LyricFormat.PLAIN_LRC -> stringResource(R.string.lyric_format_plain)
                        LyricFormat.VERBATIM_LRC -> stringResource(R.string.lyric_format_verbatim)
                        LyricFormat.ENHANCED_LRC -> stringResource(R.string.lyric_format_enhanced)
                        LyricFormat.TTML -> stringResource(R.string.lyric_format_ttml)
                        null -> stringResource(R.string.unknown_format)
                    }

                    ArrowPreference(
                        title = stringResource(R.string.action_convert_lyrics_format),
                        summary = formatText,
                        onClick = {
                            showLyricsActionBottomSheet = false
                            showLyricsFormatBottomSheet = true
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.action_view_plain_lyrics),
                        onClick = {
                            showLyricsActionBottomSheet = false
                            showPlainLyricsSheet = true
                        }
                    )
                }
                SwitchPreference(
                    title = stringResource(R.string.limit_lyrics_input_lines),
                    summary = stringResource(R.string.limit_lyrics_input_lines_hint),
                    checked = limitLyricsInputLines,
                    onCheckedChange = { enabled ->
                        viewModel.setLimitLyricsInputLines(enabled)
                    }
                )
            }
        }
    }
    // 歌词文本预览
    var plainLyricsShowRomanization by remember { mutableStateOf(true) }
    var plainLyricsShowTranslation by remember { mutableStateOf(true) }
    val plainLyrics = viewModel.getPlainLyrics(
        showRomanization = plainLyricsShowRomanization,
        showTranslation = plainLyricsShowTranslation
    )
    WindowBottomSheet(
        show = showPlainLyricsSheet,
        enableNestedScroll = false,
        endAction = {
            IconButton(
                onClick = {
                    showPlainLyricsSheet = false
                    clipboardManager.setText(
                        AnnotatedString(plainLyrics ?: "")
                    )
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.label_plain_lyrics),
        onDismissRequest = { showPlainLyricsSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth(),
        ){
            val plainLyricsScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .heightIn(min = 30.dp, max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(plainLyricsScrollState)
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        style = MiuixTheme.textStyles.body2,
                        text = plainLyrics ?: stringResource(R.string.lyrics_empty),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlainLyricsToggleChip(
                    text = stringResource(R.string.roma),
                    selected = plainLyricsShowRomanization,
                    onClick = { plainLyricsShowRomanization = !plainLyricsShowRomanization }
                )
                PlainLyricsToggleChip(
                    text = stringResource(R.string.translation),
                    selected = plainLyricsShowTranslation,
                    onClick = { plainLyricsShowTranslation = !plainLyricsShowTranslation }
                )
            }
        }
    }
    // 封面操作
    WindowBottomSheet(
        show = showCoverOptionsSheet,
        enableNestedScroll = false,
        title = stringResource(R.string.label_cover_options),
        onDismissRequest = { showCoverOptionsSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                ArrowPreference(
                    title = stringResource(R.string.label_change_cover),
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
                    title = stringResource(R.string.label_search_cover),
                    onClick = {
                        val keyword = if (!editingTagData?.title.isNullOrEmpty()) {
                            if (editingTagData.artist.isNullOrEmpty()) editingTagData.title!!
                            else "${editingTagData.title} ${editingTagData.artist}"
                        } else {
                            uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                        }
                        showCoverOptionsSheet = false
                        navigator.navigate(SearchCoverDestination(keyword))
                    }
                )
                ArrowPreference(
                    title = "选择同专辑歌曲封面",
                    onClick = {
                        showCoverOptionsSheet = false
                        viewModel.loadSameAlbumCovers()
                    }
                )
                ArrowPreference(
                    title = stringResource(R.string.label_remove_cover),
                    onClick = {
                        showCoverOptionsSheet = false
                        viewModel.removeCover()
                    }
                )
                if (uiState.coverUri != null || uiState.originalCover != null) {
                    ArrowPreference(
                        title = stringResource(R.string.label_save_cover),
                        onClick = {
                            showCoverOptionsSheet = false
                            viewModel.exportCover(context)
                        }
                    )
                    ArrowPreference(
                        title = stringResource(R.string.label_crop_cover),
                        onClick = {
                            showCoverOptionsSheet = false
                            val sourceData = uiState.coverUri ?: uiState.originalCover

                            if (sourceData != null) {
                                scope.launch(Dispatchers.IO) {
                                    val bitmap = getBitmap(context, sourceData)
                                    withContext(Dispatchers.Main) {
                                        if (bitmap != null) {
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
                }
            }

        }
    }
    // 裁剪界面
    val cropperState = bitmapToCrop?.let { rememberImageCropperState(it) }

    WindowBottomSheet(
        show = showCropSheet,
        enableNestedScroll = false,
        title = stringResource(R.string.label_crop_cover),
        endAction = {
            if (cropperState != null) {
                IconButton(
                    onClick = {
                        val croppedBitmap = cropperState.crop()
                        viewModel.updateCover(croppedBitmap)
                        showCropSheet = false
                        // 注意：这里不清空 bitmapToCrop，等动画结束再清
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null
                    )
                }
            }
        },
        onDismissRequest = {
            showCropSheet = false
            // 同样不在这里清空
        },
        onDismissFinished = {
            // 动画完全结束后再清理，避免闪烁
            bitmapToCrop = null
        }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            if (cropperState != null) {
                ImageCropper(
                    state = cropperState,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    // 偏移调整 BottomSheet
    WindowBottomSheet(
        show = showOffsetSheet,
        enableNestedScroll = false,
        title = stringResource(R.string.offset_adjust_hint),
        onDismissRequest = { showOffsetSheet = false }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
        ) {
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = editingTagData?.lyrics ?: "",
                        style = MiuixTheme.textStyles.footnote1
                    )
                }
            }

            OffsetAdjustPanel(
                currentOffset = currentShiftOffset,
                onOffsetChange = { viewModel.applyLyricsOffset(it) },
                onReset = { viewModel.resetLyricsOffset() }
            )
        }
    }
    // 添加自定义标签 BottomSheet
    WindowDialog(
        show = showAddCustomTagDialog,
        title = stringResource(R.string.action_add_custom_tag),
        onDismissRequest = { showAddCustomTagDialog = false }
    ) {
        // 临时存储新自定义标签内容
        var newCustomTagKey by remember { mutableStateOf("") }
        var newCustomTagValue by remember { mutableStateOf("") }
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {

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
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showAddCustomTagDialog = false
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.confirm),
                    onClick = {
                        if (newCustomTagKey.isNotBlank()) {
                            viewModel.updateTag {
                                copy(
                                    customFields = customFields + CustomTagField(
                                        newCustomTagKey,
                                        newCustomTagValue
                                    )
                                )
                            }
                            showAddCustomTagDialog = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }

    // 歌词格式转换
    WindowBottomSheet(
        show = showLyricsFormatBottomSheet,
        title = stringResource(R.string.action_convert_lyrics_format_title),
        onDismissRequest = { showLyricsFormatBottomSheet = false }
    ) {
        val currentLyrics = editingTagData?.lyrics ?: ""
        val detectedFormat = LyricDecoder.detectFormat(currentLyrics)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 显示当前检测到的格式
            Text(
                text = stringResource(
                    R.string.current_detected_format,
                    when (detectedFormat) {
                        LyricFormat.PLAIN_LRC -> stringResource(R.string.lyric_format_plain)
                        LyricFormat.VERBATIM_LRC -> stringResource(R.string.lyric_format_verbatim)
                        LyricFormat.ENHANCED_LRC -> stringResource(R.string.lyric_format_enhanced)
                        LyricFormat.TTML -> stringResource(R.string.lyric_format_ttml)
                        null -> stringResource(R.string.unknown_format)
                    }
                ),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(12.dp)
            )
            Card(
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) {
                // 提供转换选项
                val availableFormats = listOfNotNull(
                    if (detectedFormat != LyricFormat.PLAIN_LRC) LyricFormat.PLAIN_LRC to stringResource(
                        R.string.lyric_format_plain
                    ) else null,
                    if (detectedFormat != LyricFormat.VERBATIM_LRC) LyricFormat.VERBATIM_LRC to stringResource(
                        R.string.lyric_format_verbatim
                    ) else null,
                    if (detectedFormat != LyricFormat.ENHANCED_LRC) LyricFormat.ENHANCED_LRC to stringResource(
                        R.string.lyric_format_enhanced
                    ) else null,
                    if (detectedFormat != LyricFormat.TTML) LyricFormat.TTML to stringResource(R.string.lyric_format_ttml) else null
                )

                availableFormats.forEach { (targetFormat, formatName) ->
                    ArrowPreference(
                        title = stringResource(R.string.convert_to_format, formatName),
                        onClick = {
                            showLyricsFormatBottomSheet = false
                            viewModel.convertLyricsFormat(targetFormat)
                        }
                    )
                }

            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = {
                        showLyricsFormatBottomSheet = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PlainLyricsToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selected) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantActions
    }

    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoverSection(
    coverUri: Any?,
    title: String,
    artist: String,
    rating: Int?,
    isModified: Boolean,
    onCoverClick: () -> Unit,
    onRevertCoverClick: () -> Unit,
    onRatingChange: (Int) -> Unit
) {
    val surfaceVariant = MiuixTheme.colorScheme.surfaceVariant
    val onSurface = MiuixTheme.colorScheme.onSurface
    val onSurfaceDim = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val context = LocalContext.current
    var imageSize by remember(coverUri) { mutableStateOf<Pair<Int, Int>?>(null) }

    // 加载图片尺寸
    LaunchedEffect(coverUri) {
        if (coverUri != null) {
            imageSize = withContext(Dispatchers.IO) {
                try {
                    when (coverUri) {
                        is ByteArray -> {
                            val options =
                                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(coverUri, 0, coverUri.size, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                options.outWidth to options.outHeight
                            } else null
                        }

                        is Uri -> {
                            context.contentResolver.openInputStream(coverUri)?.use { stream ->
                                val options =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeStream(stream, null, options)
                                if (options.outWidth > 0 && options.outHeight > 0) {
                                    options.outWidth to options.outHeight
                                } else null
                            }
                        }

                        is String -> {
                            val options =
                                BitmapFactory.Options().apply { inJustDecodeBounds = true }

                            when {
                                // 网络 URL
                                coverUri.startsWith("http://") || coverUri.startsWith("https://") -> {
                                    java.net.URL(coverUri).openStream().use { stream ->
                                        BitmapFactory.decodeStream(stream, null, options)
                                    }
                                }

                                // content:// 或 file:// 统一用 Uri 处理
                                coverUri.startsWith("content://") || coverUri.startsWith("file://") -> {
                                    val uri = coverUri.toUri()
                                    context.contentResolver.openInputStream(uri)?.use { stream ->
                                        BitmapFactory.decodeStream(stream, null, options)
                                    }
                                }

                                // 普通文件路径
                                else -> {
                                    BitmapFactory.decodeFile(coverUri, options)
                                }
                            }

                            if (options.outWidth > 0 && options.outHeight > 0) {
                                options.outWidth to options.outHeight
                            } else null
                        }

                        is Bitmap -> {
                            coverUri.width to coverUri.height
                        }

                        else -> null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {

                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.15f }
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surfaceVariant.copy(alpha = 0.6f),
                                    surfaceVariant.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.onSurfaceContainerVariant)
                            .clickable { onCoverClick() }
                    ) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                            placeholder = rememberTintedPainter(
                                painter = painterResource(id = R.drawable.ic_album_24dp),
                                tint = LyricoColors.coverPlaceholderIcon
                            ),
                            error = rememberTintedPainter(
                                painter = painterResource(id = R.drawable.ic_album_24dp),
                                tint = LyricoColors.coverPlaceholderIcon
                            )
                        )

                        // 编辑提示
                        Box(
                            modifier = Modifier
                                .matchParentSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = stringResource(R.string.edit_cover),
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // 尺寸标签
                        imageSize?.let {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .background(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${it.first}×${it.second}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = isModified,
                            enter = scaleIn() + fadeIn(),
                            exit = scaleOut() + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        LyricoColors.modifiedBadgeBackground.copy(alpha = 0.95f)
                                    )
                                    .clickable { onRevertCoverClick() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_undo_changes),
                                    fontSize = 10.sp,
                                    color = LyricoColors.modifiedText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title.ifEmpty { "未知曲目" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                fontSize = 13.sp,
                                color = onSurfaceDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 1..5) {
                                val isFilled = rating?.let { i <= it }
                                Icon(
                                    painter = painterResource(
                                        if (isFilled == true) R.drawable.ic_filled_star_24dp
                                        else R.drawable.ic_outline_star_24dp
                                    ),
                                    contentDescription = null,
                                    tint = if (isFilled == true)
                                        MiuixTheme.colorScheme.primary
                                    else
                                        onSurfaceDim.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .size(24.dp)
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
                }
            }
        }
    }
}

@Composable
private fun MetadataInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isModified: Boolean = false,
    onRevert: () -> Unit,
    isMultiline: Boolean = false,
    limitMultilineLines: Boolean = false,
    actionButtons: @Composable RowScope.() -> Unit = {}
) {
    if (isMultiline) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = isModified,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f))
                            .clickable { onRevert() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_undo_changes),
                            fontSize = 11.sp,
                            color = LyricoColors.modifiedText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                actionButtons()
            }
            TextField(
                textStyle = MiuixTheme.textStyles.body2,
                modifier = Modifier.fillMaxWidth(),
                value = value,
                label = label + if (isModified) "(" + stringResource(R.string.status_modified) + ")" else "",
                onValueChange = onValueChange,
                borderColor = if (isModified) LyricoColors.modifiedBorder else MiuixTheme.colorScheme.primary,
                singleLine = false,
                minLines = 10,
                maxLines = if (limitMultilineLines) LIMITED_LYRICS_INPUT_MAX_LINES else Int.MAX_VALUE,
            )
        }
    } else {
        TextField(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            value = value,
            onValueChange = onValueChange,
            label = label + if (isModified) "(" + stringResource(R.string.status_modified) + ")" else "",
            trailingIcon = if (isModified) {
                {
                    IconButton(onClick = onRevert) {
                        Icon(imageVector = MiuixIcons.Undo, contentDescription = "Undo")
                    }
                }
            } else null,
            borderColor = if (isModified) LyricoColors.modifiedBorder else MiuixTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CustomMetadataFieldEditor(
    field: CustomTagField,
    isModified: Boolean,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    onRevert: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isModified) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f))
                        .clickable { onRevert() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_undo_changes),
                        fontSize = 11.sp,
                        color = LyricoColors.modifiedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
                            onRemove()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        fontSize = 11.sp,
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        MetadataInputField(
            label = stringResource(R.string.label_custom_tag_name),
            value = field.key,
            onValueChange = onKeyChange,
            isModified = false,
            onRevert = onRevert
        )
        MetadataInputField(
            label = stringResource(R.string.label_custom_tag_value),
            value = field.value,
            onValueChange = onValueChange,
            isModified = false,
            onRevert = onRevert
        )
    }
}
