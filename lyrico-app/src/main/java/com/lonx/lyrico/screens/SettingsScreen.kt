package com.lonx.lyrico.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.utils.formatSize
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemCheck
import com.moriafly.salt.ui.ItemDropdown
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemText
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.SaltTheme
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDestination
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
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val folderViewModel: FolderManagerViewModel = koinViewModel()
    val folderUiState by folderViewModel.uiState.collectAsState()


    val lyricFormat = settingsUiState.lyricFormat
    val artistSeparator = settingsUiState.separator
    val romaEnabled = settingsUiState.romaEnabled
    val translationEnabled = settingsUiState.translationEnabled
    val ignoreShortAudio = settingsUiState.ignoreShortAudio
    val scrollState = rememberScrollState()
    val folders = folderUiState.folders
    val totalFolders = folders.size
    val ignoredFolders = folders.count { it.isIgnored }
    val searchSourceOrder = settingsUiState.searchSourceOrder
    val searchPageSize = settingsUiState.searchPageSize

    val showClearCacheDialog = remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        settingsViewModel.refreshCache(context)
    }
    val cacheContent = remember(settingsUiState.categorizedCacheSize) {
        if (settingsUiState.categorizedCacheSize.isEmpty()) {
            "正在计算缓存大小..."
        } else {
            val details = settingsUiState.categorizedCacheSize.map { (name, size) ->
                "$name: ${size.formatSize()}"
            }.joinToString(separator = "\n")

            "确定要清理所有缓存吗？\n\n$details\n\n总计: ${settingsUiState.totalCacheSize.formatSize()}"
        }
    }
    BasicScreenBox(
        title = "设置",
        onBack = { navigator.popBackStack() }
    ) {
        if (showClearCacheDialog.value) {
            YesNoDialog(
                title = "清理缓存",
                onDismissRequest = {
                    showClearCacheDialog.value = false
                },
                onConfirm = {
                    showClearCacheDialog.value = false
                    settingsViewModel.clearCache(context)
                },
                content = cacheContent,
                cancelText = "取消",
                confirmText = "确认",
            )
         }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ItemOuterTitle("外观")
            RoundedColumn {
                ItemDropdown(
                    text = "主题模式",
                    value = settingsUiState.themeMode.displayName,
                    content = {
                        ThemeMode.entries.forEach { mode ->
                            ItemCheck(
                                text = mode.displayName,
                                state = settingsUiState.themeMode == mode,
                                onChange = {
                                    settingsViewModel.setThemeMode(mode)
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
                ItemSwitcher(
                    text = "不扫描 60 秒以下音频",
                    state = ignoreShortAudio,
                    onChange = {
                        settingsViewModel.setIgnoreShortAudio(!ignoreShortAudio)
                    }
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
                        settingsViewModel.setSearchPageSize(tempPageSize.intValue)
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
                    value = lyricFormat.displayName,
                    content = {
                        LyricFormat.entries.forEach { format ->
                            ItemCheck(
                                text = format.displayName,
                                state = lyricFormat == format,
                                onChange = {
                                    settingsViewModel.setLyricFormat(format)
                                    state.dismiss()
                                }
                            )
                        }
                    },
                )
                ItemSwitcher(
                    state = romaEnabled,
                    onChange = {
                        settingsViewModel.setRomaEnabled(!romaEnabled)
                    },
                    text = "罗马音",
                    sub = "搜索歌词中包含罗马音"
                )
                ItemSwitcher(
                    state = translationEnabled,
                    onChange = {
                        settingsViewModel.setTranslationEnabled(!translationEnabled)
                    },
                    text = "翻译",
                    sub = "搜索歌词中包含翻译"
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
                                    settingsViewModel.setSeparator(separator)
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
                    text = "批量匹配记录",
                    sub = "查看批量匹配历史日志",
                    onClick = {
                        navigator.navigate(BatchMatchHistoryDestination())
                    }
                )
                Item(
                    text = "清理缓存",
                    sub = "缓存大小: ${settingsUiState.totalCacheSize.formatSize()}",
                    onClick = {
                        showClearCacheDialog.value = true
                    }
                )
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