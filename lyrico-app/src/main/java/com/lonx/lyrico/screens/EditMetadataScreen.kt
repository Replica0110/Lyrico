package com.lonx.lyrico.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonx.lyrico.ui.theme.*
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import coil3.compose.AsyncImage
import coil3.toUri
import com.lonx.lyrico.data.model.LyricsSearchResult
import com.lonx.lyrico.ui.components.bar.TopBar
import com.lonx.lyrico.utils.coil.CoverRequest
import com.moriafly.salt.ui.SaltTheme
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.SearchResultsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.result.ResultRecipient
import com.ramcosta.composedestinations.result.onResult
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
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
    val hazeState = rememberHazeState()


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
            Column(
                modifier = Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(containerColor = SaltTheme.colors.background)
                )
            ) {
                val titleText = if (uiState.songInfo?.tagData?.title != null) {
                    "${uiState.songInfo!!.tagData!!.title}"
                } else {
                    uiState.songInfo?.tagData?.fileName ?: "编辑元数据"
                }
                TopBar(
                    backgroundColor = Color.Transparent,
                    text = titleText,
                    navigationIcon = {
                        IconButton(onClick = {
                            navigator.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(
                            onClick = { viewModel.saveMetadata() },
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "保存")
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        bottom = paddingValues.calculateBottomPadding()
                    )
                    .hazeSource(hazeState)
                    .imePadding()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        },
                        icon = Icons.Default.Title
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
                        },
                        icon = Icons.Default.Person
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
                        },
                        icon = Icons.Default.Album
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
                        },
                        icon = Icons.Default.CalendarToday
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
                        },
                        icon = Icons.Default.Category
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
                        },
                        icon = Icons.AutoMirrored.Filled.QueueMusic
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
                        isMultiline = true,
                        icon = Icons.AutoMirrored.Filled.List
                    )
                    Spacer(modifier = Modifier.height(200.dp))
                }
            }
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
                color = if (isModified) Amber300 else Gray200,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isModified) Amber50.copy(alpha = 0.3f) else Color.White,
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
            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote)
        )

        if (isModified) {
            // 已修改角标
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(
                        color = Amber100.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .shadow(1.dp, RoundedCornerShape(4.dp))
            ) {
                Text(
                    text = "已修改",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber600
                )
            }

            // 撤销按钮
            IconButton(
                onClick = onRevertClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp) // 和角标错开一些
                    .background(
                        color = Gray50.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
                    .size(28.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "撤销封面修改",
                    tint = Gray700
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
                    tint = Gray400,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label.uppercase(),
                color = Gray500,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            if (isModified) {
                Box(
                    modifier = Modifier
                        .background(Amber100, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "已修改",
                        color = Amber600,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onRevert, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销修改",
                        tint = Gray400
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
                focusedBorderColor = Blue600,
                unfocusedBorderColor = if (isModified) Amber300 else Gray200,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = if (isModified) Amber50.copy(alpha = 0.3f) else Color.White,
            ),
            singleLine = !isMultiline,
            minLines = if (isMultiline) 20 else 1,
        )
    }
}