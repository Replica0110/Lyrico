package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.rememberTintedPainter
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.ArrowBack
import com.moriafly.salt.ui.icons.SaltIcons
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult

@OptIn(
    ExperimentalMaterial3Api::class,
    UnstableSaltUiApi::class
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


    onLyricsResult.onResult { result ->
        viewModel.updateMetadataFromSearchResult(result)
    }

    LaunchedEffect(songFilePath) {
        viewModel.readMetadata(songFilePath)
    }



    LaunchedEffect(uiState.saveSuccess) {
        when (uiState.saveSuccess) {
            true -> {
                scope.launch {
                    snackbarHostState.showSnackbar("保存成功")
//                    onSaveSuccess()
                }
            }

            false -> {
                scope.launch {
                    snackbarHostState.showSnackbar("保存失败")
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
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {

            val titleText = if (uiState.songInfo?.tagData?.title != null) {
                "${uiState.songInfo!!.tagData!!.title}"
            } else {
                uiState.songInfo?.tagData?.fileName ?: "编辑元数据"
            }

            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = titleText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarColors(
                    containerColor = SaltTheme.colors.background,
                    scrolledContainerColor = SaltTheme.colors.background,
                    navigationIconContentColor = SaltTheme.colors.text,
                    titleContentColor = SaltTheme.colors.text,
                    actionIconContentColor = SaltTheme.colors.text,
                    subtitleContentColor = SaltTheme.colors.subText
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        navigator.popBackStack()
                    }) {
                        Icon(SaltIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val keyword = if (editingTagData?.title?.isNotEmpty() == true) {
                            if (editingTagData.artist.isNullOrEmpty()) {
                                editingTagData.title!!
                            } else {
                                "${editingTagData.title} ${editingTagData.artist}"
                            }
                        } else {
                            uiState.songInfo?.tagData?.fileName ?: ""
                        }
                        navigator.navigate(SearchResultsDestination(keyword))
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = "搜索"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.saveMetadata() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = SaltTheme.colors.highlight
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_save_24dp),
                                contentDescription = "保存"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
                .imePadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CoverEditor(
                coverUri = uiState.coverUri,
                isModified = uiState.coverUri != uiState.originalCover,
                onCoverClick = { /* 弹出选择图片 */ },
                onRevertClick = { viewModel.revertCover() }, // 撤销
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            )

            MetadataInputGroup(
                label = "标题",
                value = editingTagData?.title ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            title = it
                        )
                    )
                },
                isModified = editingTagData?.title != originalTagData?.title,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            title = originalTagData?.title ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "艺术家",
                value = editingTagData?.artist ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            artist = it
                        )
                    )
                },
                isModified = editingTagData?.artist != originalTagData?.artist,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            artist = originalTagData?.artist ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "专辑",
                value = editingTagData?.album ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            album = it
                        )
                    )
                },
                isModified = editingTagData?.album != originalTagData?.album,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            album = originalTagData?.album ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "年份",
                value = editingTagData?.date ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            date = it
                        )
                    )
                },
                isModified = editingTagData?.date != originalTagData?.date,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            date = originalTagData?.date ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "流派",
                value = editingTagData?.genre ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            genre = it
                        )
                    )
                },
                isModified = editingTagData?.genre != originalTagData?.genre,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            genre = originalTagData?.genre ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "音轨",
                value = editingTagData?.trackerNumber ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(trackerNumber = it)
                    )
                },
                isModified = editingTagData?.trackerNumber != originalTagData?.trackerNumber,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            trackerNumber = originalTagData?.trackerNumber ?: ""
                        )
                    )
                }
            )

            MetadataInputGroup(
                label = "歌词",
                value = editingTagData?.lyrics ?: "",
                onValueChange = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            lyrics = it
                        )
                    )
                },
                isModified = editingTagData?.lyrics != originalTagData?.lyrics,
                onRevert = {
                    viewModel.onUpdateEditingTagData(
                        editingTagData!!.copy(
                            lyrics = originalTagData?.lyrics ?: ""
                        )
                    )
                },
                isMultiline = true
            )
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
                color = if (isModified) LyricoColors.modifiedBackground.copy(alpha = 0.5f) else SaltTheme.colors.subBackground,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCoverClick() }
    ) {
        AsyncImage(
            model = coverUri,
            contentDescription = "封面",
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
            // 已修改角标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(
                        color = LyricoColors.modifiedBadgeBackground.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .shadow(1.dp, RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = "已修改",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = LyricoColors.modifiedText
                )
            }

            // 撤销按钮
            IconButton(
                onClick = onRevertClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp) // 和角标错开一些
                    .background(
                        color = SaltTheme.colors.background.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
                    .size(28.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_undo_24dp),
                    contentDescription = "撤销"
                )
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
                    tint = SaltTheme.colors.subText,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label.uppercase(),
                color = SaltTheme.colors.subText,
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
                        text = "已修改",
                        color = LyricoColors.modifiedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onRevert, modifier = Modifier.size(24.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo_24dp),
                        contentDescription = "撤销修改",
                        tint = SaltTheme.colors.subText
                    )
                }
            }
            // 添加操作按钮
            actionButtons()
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LyricoColors.inputFocusedBorder,
                unfocusedBorderColor = if (isModified) LyricoColors.modifiedBorder else LyricoColors.inputBorder,
                focusedContainerColor = SaltTheme.colors.subBackground,
                unfocusedContainerColor = if (isModified) LyricoColors.modifiedBackground.copy(alpha = 0.3f) else SaltTheme.colors.subBackground,
                focusedTextColor = SaltTheme.colors.text,
                unfocusedTextColor = SaltTheme.colors.text,
                cursorColor = SaltTheme.colors.highlight,
                focusedPlaceholderColor = SaltTheme.colors.subText,
                unfocusedPlaceholderColor = SaltTheme.colors.subText,
                focusedLabelColor = SaltTheme.colors.text,
                unfocusedLabelColor = SaltTheme.colors.subText
            ),
            singleLine = !isMultiline,
            minLines = if (isMultiline) 20 else 1,
        )
    }
}