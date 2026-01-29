package com.lonx.lyrico.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.data.model.toChar
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Icon
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.Text
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.icons.ArrowBack
import com.moriafly.salt.ui.icons.SaltIcons
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@OptIn(
    ExperimentalMaterial3Api::class, UnstableSaltUiApi::class
)
@Composable
@Destination<RootGraph>(route = "settings")
fun SettingsScreen(
    navigator: DestinationsNavigator
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val lyricDisplayMode = uiState.lyricDisplayMode
    val artistSeparator = uiState.separator
    val romaEnabled = uiState.romaEnabled
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SaltTheme.colors.background),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarColors(
                    containerColor = SaltTheme.colors.background,
                    scrolledContainerColor = SaltTheme.colors.background,
                    navigationIconContentColor = SaltTheme.colors.text,
                    titleContentColor = SaltTheme.colors.text,
                    actionIconContentColor = SaltTheme.colors.text,
                    subtitleContentColor = SaltTheme.colors.subText
                ),
                title = {
                    Text(
                        text = "设置",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(imageVector = SaltIcons.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaltTheme.colors.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {

            ItemOuterTitle("歌词")
            RoundedColumn {
                ItemDropdown(
                    text = "歌词模式",
                    enabled = false,
                    sub = "暂未实现具体逻辑",
                    value = if (lyricDisplayMode == LyricDisplayMode.WORD_BY_WORD) {
                        "逐字歌词"
                    } else "逐行歌词",
                    content = {
                        ItemCheck(
                            text = "逐字歌词",
                            state = lyricDisplayMode == LyricDisplayMode.WORD_BY_WORD,
                            onChange = {
                                viewModel.setLyricDisplayMode(LyricDisplayMode.WORD_BY_WORD)
                                state.dismiss()
                            }
                        )
                        ItemCheck(
                            text = "逐行歌词",
                            state = lyricDisplayMode == LyricDisplayMode.LINE_BY_LINE,
                            onChange = {
                                viewModel.setLyricDisplayMode(LyricDisplayMode.LINE_BY_LINE)
                                state.dismiss()
                            }
                        )
                    },
                )
                ItemSwitcher(
                    state = romaEnabled,
                    onChange = viewModel::setRomaEnabled,
                    text = "罗马音",
                    sub = "搜索歌词中包含罗马音"
                )
            }
            ItemOuterTitle("元数据")
            RoundedColumn {
                ItemDropdown(
                    text = "艺术家分隔符",
                    value = artistSeparator.toChar(),
                    sub = "存在多个艺术家时使用该分隔符分隔",
                    content = {
                        val separators = listOf(
                            ArtistSeparator.ENUMERATION_COMMA,
                            ArtistSeparator.SLASH,
                            ArtistSeparator.COMMA,
                            ArtistSeparator.SEMICOLON
                        )
                        separators.forEach { separator ->
                            ItemCheck(
                                text = separator.toChar(),
                                state = artistSeparator == separator,
                                onChange = {
                                    viewModel.setSeparator(separator)
                                    state.dismiss()
                                }
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
        }
    }
}