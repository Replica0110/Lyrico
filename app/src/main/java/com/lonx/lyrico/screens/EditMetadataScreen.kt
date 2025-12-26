package com.lonx.lyrico.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.lonx.lyrico.data.SongDataHolder
import com.lonx.lyrico.ui.theme.*
import com.lonx.lyrico.viewmodel.EditMetadataViewModel
import com.lonx.lyrics.model.SongSearchResult
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    searchResult: SongSearchResult?,
    selectedLyrics: String?,
    onBackClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    onSearchResult: () -> Unit,
    onLyricsResult: () -> Unit,
    onSaveSuccess: () -> Unit,
    viewModel: EditMetadataViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val originalTagData = uiState.originalTagData
    val editingTagData = uiState.editingTagData
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        SongDataHolder.selectedSongInfo?.let {
            viewModel.loadSongInfo(it)
        }
    }

    LaunchedEffect(searchResult, selectedLyrics) {
        if (searchResult != null && selectedLyrics != null) {
            // Both are available, use the new method to prevent overwriting lyrics
            viewModel.onSelectSearchResultWithLyrics(searchResult, selectedLyrics)
            onSearchResult()
            onLyricsResult()
        } else if (searchResult != null) {
            // Fallback for when only searchResult is available
            viewModel.onSelectSearchResult(searchResult)
            onSearchResult()
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        when (uiState.saveSuccess) {
            true -> {
                scope.launch {
                    snackbarHostState.showSnackbar("保存成功")
                    onSaveSuccess()
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("元数据编辑") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val keyword = if (editingTagData?.title?.isNotEmpty() == true) {
                            if (editingTagData.artist.isNullOrEmpty()) {
                                editingTagData.title!!
                            }
                            else {
                                "${editingTagData.title} ${editingTagData.artist}"
                            }
                        } else {
                            uiState.songInfo?.fileName ?: ""
                        }
                        onSearchClick(keyword)
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
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Gray50)
            )
        },
        containerColor = Gray50
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(scrollState)
        ) {
            AsyncImage(
                model = uiState.coverUri,
                contentDescription = uiState.songInfo?.tagData?.title ?: "歌曲封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                error = rememberVectorPainter(Icons.Default.MusicNote)
            )

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetadataInputGroup(
                    label = "标题",
                    value = editingTagData?.title ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(title = it)) },
                    isModified = editingTagData?.title != originalTagData?.title,
                    icon = Icons.Default.Title
                )

                MetadataInputGroup(
                    label = "艺术家",
                    value = editingTagData?.artist ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(artist = it)) },
                    isModified = editingTagData?.artist != originalTagData?.artist,
                    icon = Icons.Default.Person
                )

                MetadataInputGroup(
                    label = "专辑",
                    value = editingTagData?.album ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(album = it)) },
                    isModified = editingTagData?.album != originalTagData?.album,
                    icon = Icons.Default.Album
                )

                MetadataInputGroup(
                    label = "年份",
                    value = editingTagData?.date ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(date = it)) },
                    isModified = editingTagData?.date != originalTagData?.date,
                    icon = Icons.Default.CalendarToday
                )

                MetadataInputGroup(
                    label = "流派",
                    value = editingTagData?.genre ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(genre = it)) },
                    isModified = editingTagData?.genre != originalTagData?.genre,
                    icon = Icons.Default.Category
                )

                MetadataInputGroup(
                    label = "音轨",
                    value = editingTagData?.channels.toString(),
                    onValueChange = {
                        viewModel.onUpdateEditingTagData(
                            editingTagData!!.copy(channels = it.toIntOrNull() ?: 0)
                        )
                    },
                    isModified = editingTagData?.channels != originalTagData?.channels,
                    icon = Icons.AutoMirrored.Filled.QueueMusic
                )

                MetadataInputGroup(
                    label = "歌词",
                    value = editingTagData?.lyrics ?: "",
                    onValueChange = { viewModel.onUpdateEditingTagData(editingTagData!!.copy(lyrics = it)) },
                    isModified = editingTagData?.lyrics != originalTagData?.lyrics,
                    isMultiline = true,
                    icon = Icons.AutoMirrored.Filled.List
                )
                Spacer(modifier = Modifier.height(200.dp))
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
                Icon(icon, contentDescription = null, tint = Gray400, modifier = Modifier.size(18.dp))
            }
            Text(
                text = label.uppercase(),
                color = Gray500,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )
            if (isModified) {
                Text(
                    text = "已修改",
                    color = Amber600,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Amber100, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
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