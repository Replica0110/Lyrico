package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.ui.theme.LyricoColors
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
@Destination<RootGraph>(route = "edit_metadata")
fun EditMetadataScreen(
    navigator: DestinationsNavigator,
    songFilePath: String,
    onLyricsResult: ResultRecipient<SearchResultsDestination, LyricsSearchResult>
) {
    val viewModel: EditMetadataViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    onLyricsResult.onResult { result ->
        viewModel.updateMetadataFromSearchResult(result)
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.updateCover(it)
        }
    }
    LaunchedEffect(songFilePath) {
        viewModel.readMetadata(songFilePath)
    }



    LaunchedEffect(uiState.saveSuccess) {
        when (uiState.saveSuccess) {
            true -> {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_save_success))
//                    onSaveSuccess()
                }
            }

            false -> {
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_save_failed))
                }
            }

            null -> {
                // Do nothing
            }
        }
        // Consume the event
        if (uiState.saveSuccess != null) {
            viewModel.clearSaveStatus()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.play(context)
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = stringResource(R.string.cd_play)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        topBar = {
            val titleText = if (uiState.songInfo?.tagData?.title != null) {
                "${uiState.songInfo!!.tagData!!.title}"
            } else {
                uiState.songInfo?.tagData?.fileName ?: stringResource(R.string.edit_metadata_default_title)
            }
            SmallTopAppBar(
                title = titleText,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = { navigator.navigateUp() }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val keyword = if (editingTagData?.title?.isNotEmpty() == true) {
                                if (editingTagData.artist.isNullOrEmpty()) {
                                    editingTagData.title!!
                                } else {
                                    "${editingTagData.title} ${editingTagData.artist}"
                                }
                            } else {
                                uiState.songInfo?.tagData?.fileName?.substringBeforeLast(".") ?: ""
                            }
                            navigator.navigate(SearchResultsDestination(keyword))
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Search,
                            contentDescription = stringResource(R.string.action_search)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.saveMetadata()
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_save_24dp),
                            contentDescription = stringResource(R.string.action_save)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverEditor(
                coverUri = uiState.coverUri,
                isModified = uiState.isCoverModified,
                onCoverClick = {
                    launcher.launch("image/*")
                },
                onRevertClick = { viewModel.revertCover() }, // 撤销
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

// 基本信息卡片：标题、艺术家、专辑艺术家、专辑
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.category_basic_info))

                    MetadataInputGroup(
                        label = stringResource(R.string.label_title),
                        value = editingTagData?.title ?: "",
                        onValueChange = {
                            viewModel.updateTag {
                                editingTagData!!.copy(title = it)
                            }
                        },
                        isModified = editingTagData?.title != originalTagData?.title,
                        onRevert = {
                            viewModel.updateTag {
                                copy(title = originalTagData?.title ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_artists),
                        value = editingTagData?.artist ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(artist = it) }
                        },
                        isModified = editingTagData?.artist != originalTagData?.artist,
                        onRevert = {
                            viewModel.updateTag {
                                copy(artist = originalTagData?.artist ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_album_artist),
                        value = editingTagData?.albumArtist ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(albumArtist = it) }
                        },
                        isModified = editingTagData?.albumArtist != originalTagData?.albumArtist,
                        onRevert = {
                            viewModel.updateTag {
                                copy(albumArtist = originalTagData?.albumArtist ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_album),
                        value = editingTagData?.album ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(album = it) }
                        },
                        isModified = editingTagData?.album != originalTagData?.album,
                        onRevert = {
                            viewModel.updateTag {
                                copy(album = originalTagData?.album ?: "")
                            }
                        }
                    )
                }
            }

// 时间/流派/编号信息卡片
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.category_details))

                    MetadataInputGroup(
                        label = stringResource(R.string.label_date),
                        value = editingTagData?.date ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(date = it) }
                        },
                        isModified = editingTagData?.date != originalTagData?.date,
                        onRevert = {
                            viewModel.updateTag {
                                copy(date = originalTagData?.date ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_genre),
                        value = editingTagData?.genre ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(genre = it) }
                        },
                        isModified = editingTagData?.genre != originalTagData?.genre,
                        onRevert = {
                            viewModel.updateTag {
                                copy(genre = originalTagData?.genre ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_track_number),
                        value = editingTagData?.trackerNumber ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(trackerNumber = it) }
                        },
                        isModified = editingTagData?.trackerNumber != originalTagData?.trackerNumber,
                        onRevert = {
                            viewModel.updateTag {
                                copy(trackerNumber = originalTagData?.trackerNumber ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_disc_number),
                        value = editingTagData?.discNumber?.toString() ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(discNumber = it.toIntOrNull()) }
                        },
                        isModified = editingTagData?.discNumber != originalTagData?.discNumber,
                        onRevert = {
                            viewModel.updateTag {
                                copy(discNumber = originalTagData?.discNumber)
                            }
                        }
                    )
                }
            }

// 作曲/作词/备注卡片
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.category_creators))

                    MetadataInputGroup(
                        label = stringResource(R.string.label_composer),
                        value = editingTagData?.composer ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(composer = it) }
                        },
                        isModified = editingTagData?.composer != originalTagData?.composer,
                        onRevert = {
                            viewModel.updateTag {
                                copy(composer = originalTagData?.composer ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_lyricist),
                        value = editingTagData?.lyricist ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(lyricist = it) }
                        },
                        isModified = editingTagData?.lyricist != originalTagData?.lyricist,
                        onRevert = {
                            viewModel.updateTag {
                                copy(lyricist = originalTagData?.lyricist ?: "")
                            }
                        }
                    )

                    MetadataInputGroup(
                        label = stringResource(R.string.label_comment),
                        value = editingTagData?.comment ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(comment = it) }
                        },
                        isModified = editingTagData?.comment != originalTagData?.comment,
                        onRevert = {
                            viewModel.updateTag {
                                copy(comment = originalTagData?.comment ?: "")
                            }
                        }
                    )
                }
            }


            SmallTitle(text = stringResource(R.string.category_lyrics))
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {

                    MetadataInputGroup(
                        label = stringResource(R.string.label_lyrics),
                        value = editingTagData?.lyrics ?: "",
                        onValueChange = {
                            viewModel.updateTag { copy(lyrics = it) }
                        },
                        isModified = editingTagData?.lyrics != originalTagData?.lyrics,
                        onRevert = {
                            viewModel.updateTag {
                                copy(lyrics = originalTagData?.lyrics ?: "")
                            }
                        },
                        isMultiline = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(200.dp))
        }
    }

}

@Composable
fun CoverEditor(
    modifier: Modifier = Modifier,
    coverUri: Any?,                   // 当前编辑封面
    isModified: Boolean = false,              // 原始封面，用于撤销
    onCoverClick: () -> Unit,         // 点击更换封面
    onRevertClick: () -> Unit              // 点击撤销
) {
    // 判断是否修改
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(
                width = 1.5.dp,
                color = if (isModified) LyricoColors.modifiedBorder else LyricoColors.inputBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isModified) LyricoColors.modifiedBackground.copy(alpha = 0.5f) else MiuixTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCoverClick() }
    ) {
        AsyncImage(
            model = coverUri,
            contentDescription = stringResource(R.string.cd_cover),
            contentScale = ContentScale.Crop,
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

        if (isModified) {
            // 顶部对齐容器：已修改角标和撤销按钮
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 已修改角标（左侧）
                Box(
                    modifier = Modifier
                        .background(
                            color = LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .shadow(1.dp, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = stringResource(R.string.status_modified),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = LyricoColors.modifiedText
                    )
                }

                // 撤销按钮（右侧）
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onRevertClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MiuixIcons.Undo,
                        contentDescription = stringResource(R.string.action_undo_changes),
                    )
                }
            }
        }

    }

}

@Composable
private fun MetadataInputGroup(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isModified: Boolean = false,
    onRevert: () -> Unit,
    isMultiline: Boolean = false,
    icon: ImageVector? = null,
    actionButtons: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label.uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            if (isModified) {
                Box(
                    modifier = Modifier
                        .background(LyricoColors.modifiedBadgeBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.status_modified),
                        color = LyricoColors.modifiedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onRevert, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo_24dp),
                        contentDescription = stringResource(R.string.action_undo_changes)
                    )
                }
            }
            // 添加操作按钮
            actionButtons()
        }
        Spacer(modifier = Modifier.height(6.dp))
        TextField(
            value = value,
            textStyle = MiuixTheme.textStyles.body2,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
//            colors = OutlinedTextFieldDefaults.colors(
//                focusedBorderColor = LyricoColors.inputFocusedBorder,
//                unfocusedBorderColor = if (isModified) LyricoColors.modifiedBorder else LyricoColors.inputBorder,
//                focusedContainerColor = SaltTheme.colors.subBackground,
//                unfocusedContainerColor = if (isModified) LyricoColors.modifiedBackground.copy(alpha = 0.3f) else SaltTheme.colors.subBackground,
//                focusedTextColor = SaltTheme.colors.text,
//                unfocusedTextColor = SaltTheme.colors.text,
//                cursorColor = SaltTheme.colors.highlight,
//                focusedPlaceholderColor = SaltTheme.colors.subText,
//                unfocusedPlaceholderColor = SaltTheme.colors.subText,
//                focusedLabelColor = SaltTheme.colors.text,
//                unfocusedLabelColor = SaltTheme.colors.subText
//            ),
            singleLine = !isMultiline,
            minLines = if (isMultiline) 20 else 1,
        )
    }
}