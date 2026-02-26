package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
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

@SuppressLint("LocalContextGetResourceValueCall")
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
    val onlyTranslationIfAvailable = settingsUiState.onlyTranslationIfAvailable
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
    val calculatingText = stringResource(R.string.calculating_cache)
    val confirmText = stringResource(R.string.clear_cache_confirm)

    val cacheContent = remember(
        settingsUiState.categorizedCacheSize,
        settingsUiState.totalCacheSize
    ) {
        if (settingsUiState.categorizedCacheSize.isEmpty()) {
            calculatingText
        } else {
            val details = settingsUiState.categorizedCacheSize
                .map { (category, size) ->
                    context.getString(
                        R.string.cache_item,
                        context.getString(category.labelRes),
                        size.formatSize()
                    )
                }
                .joinToString(separator = "\n")

            buildString {
                append(confirmText)
                append("\n\n")
                append(details)
                append("\n\n")
                append(
                    context.getString(
                        R.string.cache_total,
                        settingsUiState.totalCacheSize.formatSize()
                    )
                )
            }
        }
    }
    BasicScreenBox(
        title = stringResource(R.string.settings_title),
        onBack = { navigator.popBackStack() }
    ) {
        if (showClearCacheDialog.value) {
            YesNoDialog(
                title = stringResource(R.string.clear_cache),
                onDismissRequest = {
                    showClearCacheDialog.value = false
                },
                onConfirm = {
                    showClearCacheDialog.value = false
                    settingsViewModel.clearCache(context)
                },
                content = cacheContent,
                cancelText = stringResource(R.string.cancel),
                confirmText = stringResource(R.string.confirm),
            )
         }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ItemOuterTitle(stringResource(R.string.section_appearance))
            RoundedColumn {
                ItemDropdown(
                    text = stringResource(R.string.theme_mode),
                    value = stringResource(settingsUiState.themeMode.labelRes),
                    content = {
                        ThemeMode.entries.forEach { mode ->
                            ItemCheck(
                                text = stringResource(mode.labelRes),
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

            ItemOuterTitle(stringResource(R.string.section_scan))
            RoundedColumn {
                val sub = if (totalFolders > 0) {
                    buildString {
                        append(stringResource(R.string.folder_found, totalFolders))
                        if (ignoredFolders > 0) {
                            append(stringResource(R.string.folder_ignored, ignoredFolders))
                        }
                    }
                } else {
                    stringResource(R.string.folder_manage_hint)
                }
                Item(
                    onClick = { navigator.navigate(FolderManagerDestination()) },
                    text = stringResource(R.string.folder_manager),
                    sub = sub
                )
                ItemSwitcher(
                    text = stringResource(R.string.ignore_short_audio),
                    state = ignoreShortAudio,
                    onChange = {
                        settingsViewModel.setIgnoreShortAudio(!ignoreShortAudio)
                    }
                )
            }

            ItemOuterTitle(stringResource(R.string.section_search))
            RoundedColumn {
                val subText = searchSourceOrder.map { stringResource(it.labelRes) }
                    .joinToString(" > ")
                Item(
                    onClick = { navigator.navigate(SearchSourcePriorityDestination()) },
                    text = stringResource(R.string.search_source_priority),
                    sub = subText
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
                    text = stringResource(R.string.search_limit)
                )
                ItemTip(
                    text = stringResource(R.string.search_limit_tip)
                )
            }

            ItemOuterTitle(stringResource(R.string.section_lyrics))
            RoundedColumn {
                ItemDropdown(
                    text = stringResource(R.string.lyric_mode),
                    value = stringResource(lyricFormat.labelRes),
                    content = {
                        LyricFormat.entries.forEach { format ->
                            ItemCheck(
                                text = stringResource(format.labelRes),
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
                    text = stringResource(R.string.roma),
                    sub = stringResource(R.string.roma_hint)
                )
                ItemSwitcher(
                    enabled = translationEnabled,
                    state = onlyTranslationIfAvailable,
                    onChange = {
                        settingsViewModel.setOnlyTranslationIfAvailable(!onlyTranslationIfAvailable)
                    },
                    text = stringResource(R.string.only_translation_if_available),
                    sub = stringResource(R.string.only_translation_if_available_hint)
                )
                ItemSwitcher(
                    state = translationEnabled,
                    onChange = {
                        settingsViewModel.setTranslationEnabled(!translationEnabled)
                    },
                    text = stringResource(R.string.translation),
                    sub = stringResource(R.string.translation_hint)
                )
            }
            ItemOuterTitle(stringResource(R.string.section_metadata))
            RoundedColumn {
                ItemDropdown(
                    text = stringResource(R.string.artist_separator),
                    value = artistSeparator.toText(),
                    sub = stringResource(R.string.artist_separator_hint),
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

            ItemOuterTitle(stringResource(R.string.section_other))
            RoundedColumn {
                Item(
                    text = stringResource(R.string.batch_match_history),
                    sub = stringResource(R.string.batch_match_history_hint),
                    onClick = {
                        navigator.navigate(BatchMatchHistoryDestination())
                    }
                )
                val sub = stringResource(
                    R.string.cache_size_label,
                    settingsUiState.totalCacheSize.formatSize()
                )
                Item(
                    text = stringResource(R.string.clear_cache),
                    sub = sub,
                    onClick = {
                        showClearCacheDialog.value = true
                    }
                )
                Item(
                    text = stringResource(R.string.about),
                    onClick = {
                        navigator.navigate(AboutDestination())
                    }
                )
            }
            Spacer(modifier = Modifier.height(SaltTheme.dimens.padding))
        }
    }
}