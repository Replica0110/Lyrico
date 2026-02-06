package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.FolderManagerDestination
import com.ramcosta.composedestinations.generated.destinations.SearchSourcePriorityDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(UnstableSaltUiApi::class)
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
    val folders = uiState.folders
    val totalFolders = folders.size
    val ignoredFolders = folders.count { it.isIgnored }
    val searchSourceOrder = uiState.searchSourceOrder
    val searchPageSize = uiState.searchPageSize

    BasicScreenBox(
        title = "设置",
        onBack = { navigator.popBackStack() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ItemOuterTitle("外观")
            RoundedColumn {
                ItemDropdown(
                    text = "主题模式",
                    value = uiState.themeMode.displayName,
                    content = {
                        ThemeMode.entries.forEach { mode ->
                            ItemCheck(
                                text = mode.displayName,
                                state = uiState.themeMode == mode,
                                onChange = {
                                    viewModel.setThemeMode(mode)
                                    state.dismiss()
                                }
                            )
                        }
                    }
                )
            }

            ItemOuterTitle("扫描设置")
            RoundedColumn {
                Item(
                    onClick = { navigator.navigate(FolderManagerDestination()) },
                    text = "文件夹管理",
                    sub = if (totalFolders > 0) {
                        "已发现 $totalFolders 个文件夹" + if (ignoredFolders > 0) "，已忽略 $ignoredFolders 个" else ""
                    } else "管理扫描路径"
                )
            }

            ItemOuterTitle("搜索设置")
            RoundedColumn {
                Item(
                    onClick = { navigator.navigate(SearchSourcePriorityDestination()) },
                    text = "搜索源优先级",
                    sub = searchSourceOrder.joinToString(" > ") { it.sourceName }
                )
                val tempPageSize = remember(searchPageSize) {
                    mutableIntStateOf(searchPageSize)
                }
                ItemSlider(
                    value = tempPageSize.intValue.toFloat(),
                    valueRange = 1f..20f,
                    steps = 18,
                    onValueChange = {
                        tempPageSize.intValue = it.roundToInt()
                    },
                    onValueChangeFinished = {
                        viewModel.setSearchPageSize(tempPageSize.intValue)
                    },
                    sub = "${tempPageSize.intValue}",
                    text = "搜索限制数"
                )
                ItemTip(
                    text = "限制每个源的搜索结果数量，设置更大的值会消耗更多流量，同时产生更多图片缓存"
                )
            }

            ItemOuterTitle("歌词")
            RoundedColumn {
                ItemDropdown(
                    text = "歌词模式",
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
                    value = artistSeparator.toText(),
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
                                text = separator.toText(),
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

            ItemOuterTitle("其他")
            RoundedColumn {
                Item(
                    text = "关于",
                    onClick = {
                        navigator.navigate(AboutDestination())
                    }
                )
            }
            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}