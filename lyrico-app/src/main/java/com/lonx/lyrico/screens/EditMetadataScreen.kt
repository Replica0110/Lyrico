package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet


@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
@Destination<RootGraph>(route = "edit_metadata")
fun EditMetadataScreen(
    navigator: DestinationsNavigator,
    songFileUri: String,
    onLyricsResult: ResultRecipient<SearchResultsDestination, LyricsSearchResult>
) {
    val viewModel: EditMetadataViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData


    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity
    // BottomSheet 状态
    var showOffsetSheet by remember { mutableStateOf(false) }
    var showCoverOptionsSheet by remember { mutableStateOf(false) }
    val currentShiftOffset by viewModel.currentShiftOffset.collectAsState()

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
                if (!navigator.popBackStack()){
                    activity.finish()
                }
            }
        }
    }

    LaunchedEffect(uiState.exportLyricsResult) {
        uiState.exportLyricsResult?.let { success ->
            val msg = if (success) R.string.msg_export_lyrics_success else R.string.msg_export_lyrics_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearExportLyricsStatus()
        }
    }

    LaunchedEffect(uiState.importLyricsResult) {
        uiState.importLyricsResult?.let { success ->
            val msg = if (success) R.string.msg_import_lyrics_success else R.string.msg_import_lyrics_failed
            scope.launch { snackbarHostState.showSnackbar(context.getString(msg)) }
            viewModel.clearImportLyricsStatus()
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
        floatingActionButton = {
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!editingTagData?.lyrics.isNullOrBlank()) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.prepareLyricsOffset()
                            showOffsetSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Tune,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { viewModel.play(context) }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Play,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }

        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .overScrollVertical()
                .scrollEndHaptic()
                .imePadding(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 80.dp, // 留出空间给 FAB
            )
        ) {
            // 封面编辑区
            item(key = "cover") {
                CoverSection(
                    coverUri = uiState.coverUri,
                    title = editingTagData?.title ?: uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: "",
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
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            MetadataInputField(
                                label = stringResource(R.string.label_title),
                                value = editingTagData?.title ?: "",
                                onValueChange = { viewModel.updateTag { copy(title = it) } },
                                isModified = editingTagData?.title != originalTagData?.title,
                                onRevert = { viewModel.updateTag { copy(title = originalTagData?.title ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_artists),
                                value = editingTagData?.artist ?: "",
                                onValueChange = { viewModel.updateTag { copy(artist = it) } },
                                isModified = editingTagData?.artist != originalTagData?.artist,
                                onRevert = { viewModel.updateTag { copy(artist = originalTagData?.artist ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_album_artist),
                                value = editingTagData?.albumArtist ?: "",
                                onValueChange = { viewModel.updateTag { copy(albumArtist = it) } },
                                isModified = editingTagData?.albumArtist != originalTagData?.albumArtist,
                                onRevert = { viewModel.updateTag { copy(albumArtist = originalTagData?.albumArtist ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_album),
                                value = editingTagData?.album ?: "",
                                onValueChange = { viewModel.updateTag { copy(album = it) } },
                                isModified = editingTagData?.album != originalTagData?.album,
                                onRevert = { viewModel.updateTag { copy(album = originalTagData?.album ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_date),
                                value = editingTagData?.date ?: "",
                                onValueChange = { viewModel.updateTag { copy(date = it) } },
                                isModified = editingTagData?.date != originalTagData?.date,
                                onRevert = { viewModel.updateTag { copy(date = originalTagData?.date ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_genre),
                                value = editingTagData?.genre ?: "",
                                onValueChange = { viewModel.updateTag { copy(genre = it) } },
                                isModified = editingTagData?.genre != originalTagData?.genre,
                                onRevert = { viewModel.updateTag { copy(genre = originalTagData?.genre ?: "") } }
                            )
                        }
                    }
                }
            }

            item(key = "track_details") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_track_details))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            MetadataInputField(
                                label = stringResource(R.string.label_track_number),
                                value = editingTagData?.trackNumber ?: "",
                                onValueChange = { viewModel.updateTag { copy(trackNumber = it) } },
                                isModified = editingTagData?.trackNumber != originalTagData?.trackNumber,
                                onRevert = { viewModel.updateTag { copy(trackNumber = originalTagData?.trackNumber ?: "") } }
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
                }
            }

            item(key = "credits_other") {
                Column {
                    SmallTitle(text = stringResource(R.string.group_credits_other))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            MetadataInputField(
                                label = stringResource(R.string.label_composer),
                                value = editingTagData?.composer ?: "",
                                onValueChange = { viewModel.updateTag { copy(composer = it) } },
                                isModified = editingTagData?.composer != originalTagData?.composer,
                                onRevert = { viewModel.updateTag { copy(composer = originalTagData?.composer ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_lyricist),
                                value = editingTagData?.lyricist ?: "",
                                onValueChange = { viewModel.updateTag { copy(lyricist = it) } },
                                isModified = editingTagData?.lyricist != originalTagData?.lyricist,
                                onRevert = { viewModel.updateTag { copy(lyricist = originalTagData?.lyricist ?: "") } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_copyright),
                                value = editingTagData?.copyright ?: "",
                                onValueChange = { viewModel.updateTag { copy(copyright = it) } },
                                isModified = editingTagData?.copyright != originalTagData?.copyright,
                                onRevert = { viewModel.updateTag { copy(copyright = originalTagData?.copyright) } }
                            )
                            MetadataInputField(
                                label = stringResource(R.string.label_comment),
                                value = editingTagData?.comment ?: "",
                                onValueChange = { viewModel.updateTag { copy(comment = it) } },
                                isModified = editingTagData?.comment != originalTagData?.comment,
                                onRevert = { viewModel.updateTag { copy(comment = originalTagData?.comment ?: "") } }
                            )
                        }
                    }
                }
            }

            item(key = "custom_fields") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = stringResource(R.string.group_custom_tags))
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary)
                                .clickable {
                                    viewModel.updateTag {
                                        copy(customFields = customFields + CustomTagField())
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.action_add_custom_tag),
                                color = MiuixTheme.colorScheme.onPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            if (editingTagData?.customFields.isNullOrEmpty()) {
                                Text(
                                    text = stringResource(R.string.custom_tags_empty),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.body2
                                )
                            } else {
                                editingTagData?.customFields?.forEachIndexed { index, field ->
                                    CustomMetadataFieldEditor(
                                        field = field,
                                        isModified = field != originalTagData?.customFields?.getOrNull(index),
                                        onKeyChange = { newKey ->
                                            viewModel.updateTag {
                                                copy(
                                                    customFields = customFields.toMutableList().apply {
                                                        this[index] = this[index].copy(key = newKey)
                                                    }
                                                )
                                            }
                                        },
                                        onValueChange = { newValue ->
                                            viewModel.updateTag {
                                                copy(
                                                    customFields = customFields.toMutableList().apply {
                                                        this[index] = this[index].copy(value = newValue)
                                                    }
                                                )
                                            }
                                        },
                                        onRemove = {
                                            viewModel.updateTag {
                                                copy(
                                                    customFields = customFields.toMutableList().apply {
                                                        removeAt(index)
                                                    }
                                                )
                                            }
                                        },
                                        onRevert = {
                                            viewModel.updateTag {
                                                val originalField = originalTagData?.customFields?.getOrNull(index)
                                                copy(
                                                    customFields = customFields.toMutableList().apply {
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
                }
            }

            item(key = "lyrics") {
                Column {
                    SmallTitle(text = stringResource(R.string.label_lyrics))
                    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        MetadataInputField(
                            label = stringResource(R.string.label_lyrics),
                            value = editingTagData?.lyrics ?: "",
                            onValueChange = { viewModel.updateTag { copy(lyrics = it) } },
                            isModified = editingTagData?.lyrics != originalTagData?.lyrics,
                            onRevert = { viewModel.updateTag { copy(lyrics = originalTagData?.lyrics ?: "") } },
                            actionButtons = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                                ) {
                                    // 偏移调整按钮
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.primary)
                                            .clickable {
                                                viewModel.prepareLyricsOffset()
                                                showOffsetSheet = true
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.offset_adjust_hint),
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // 导出按钮
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.tertiaryContainer)
                                            .clickable {
                                                val fileName = viewModel.getLyricsFileName()
                                                if (fileName != null){
                                                    exportLyricsLauncher.launch(fileName)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_export_lyrics),
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // 导入按钮
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MiuixTheme.colorScheme.tertiaryContainer)
                                            .clickable {
                                                lyricsFileLauncher.launch(arrayOf("*/*"))
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_import_lyrics),
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            },
                            isMultiline = true
                        )
                    }
                }
            }
        }
    }

    // 封面操作
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
            item(key = "cover_options") {
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
                    }
                }
            }
        }
    }

    // 偏移调整 BottomSheet
    WindowBottomSheet(
        show = showOffsetSheet,
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
                    top.yukonga.miuix.kmp.basic.Text(
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
                                text = "点击编辑",
                                style = MiuixTheme.textStyles.footnote1,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
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
                label = label + if (isModified) "("+stringResource(R.string.status_modified)+")" else "",
                onValueChange = onValueChange,
                borderColor = if (isModified) LyricoColors.modifiedBorder else MiuixTheme.colorScheme.primary,
                singleLine = false,
                minLines = 10,
            )
        }
    } else {
        TextField(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            value = value,
            onValueChange = onValueChange,
            label = label + if (isModified) "("+stringResource(R.string.status_modified)+")" else "",
            trailingIcon = if (isModified) { {
                IconButton(onClick = onRevert) {
                    Icon(imageVector = MiuixIcons.Undo, contentDescription = "Undo")
                }
            } } else null,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_custom_tag),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isModified) {
                    IconButton(onClick = onRevert) {
                        Icon(imageVector = MiuixIcons.Undo, contentDescription = null)
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.action_remove_custom_tag)
                    )
                }
            }

            MetadataInputField(
                label = stringResource(R.string.label_custom_tag_name),
                value = field.key,
                onValueChange = onKeyChange,
                isModified = isModified,
                onRevert = onRevert
            )
            MetadataInputField(
                label = stringResource(R.string.label_custom_tag_value),
                value = field.value,
                onValueChange = onValueChange,
                isModified = isModified,
                onRevert = onRevert
            )
        }
    }
}
