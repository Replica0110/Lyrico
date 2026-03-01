package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.components.RoundedRectanglePainter
import com.lonx.lyrico.ui.components.getSystemWallpaperColor
import com.lonx.lyrico.ui.theme.KeyColors
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
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
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
    val minSearchSize = 1
    val maxSearchSize = 20

    val lyricFormat = settingsUiState.lyricFormat
    val artistSeparator = settingsUiState.separator
    val romaEnabled = settingsUiState.romaEnabled
    val translationEnabled = settingsUiState.translationEnabled
    val onlyTranslationIfAvailable = settingsUiState.onlyTranslationIfAvailable
    val removeEmptyLines = settingsUiState.removeEmptyLines
    val ignoreShortAudio = settingsUiState.ignoreShortAudio
    val folders = folderUiState.folders
    val totalFolders = folders.size
    val ignoredFolders = folders.count { it.isIgnored }
    val searchSourceOrder = settingsUiState.searchSourceOrder
    val searchPageSize = settingsUiState.searchPageSize

    val themeMode = settingsUiState.themeMode
    val monetEnable = settingsUiState.monetEnable
    val currentKeyColor = settingsUiState.keyColor
    val showClearCacheDialog = remember { mutableStateOf(false) }
    val showSearchLimitConfigDialog = remember { mutableStateOf(false) }

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


    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.navigateUp() }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                title = stringResource(R.string.settings_title),
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        val tempSearchPageSize = remember(searchPageSize) {
            mutableIntStateOf(searchPageSize)
        }
        SuperDialog(
            show = showClearCacheDialog,
            title = stringResource(R.string.clear_cache),
            onDismissRequest = {
                showClearCacheDialog.value = false
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = cacheContent)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            showClearCacheDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            settingsViewModel.clearCache(context)
                            showClearCacheDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        SuperDialog(
            show = showSearchLimitConfigDialog,
            title = stringResource(R.string.search_limit),
            onDismissRequest = {
                showSearchLimitConfigDialog.value = false
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.search_limit_tip),
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions
                )
                Spacer(modifier = Modifier.height(12.dp))
                var text by remember { mutableStateOf(searchPageSize.toString()) }
                TextField(
                    value = text,
                    maxLines = 1,
                    onValueChange = { newValue ->
                        val digits = newValue.filter { it.isDigit() }
                        if (digits.isEmpty()) {
                            text = ""
                        } else {
                            val limited = digits.take(3)
                            val num = limited.toIntOrNull()
                            val clamped = num?.coerceIn(minSearchSize, maxSearchSize)
                            text = clamped.toString()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = {
                            showSearchLimitConfigDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            tempSearchPageSize.intValue = text.toIntOrNull() ?: 1
                            settingsViewModel.setSearchPageSize(tempSearchPageSize.intValue)
                            showSearchLimitConfigDialog.value = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .fillMaxHeight(),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 12.dp,
            ),
            overscrollEffect = null,
        ) {
            item(
                key = "appearance"
            ) {
                SmallTitle(stringResource(R.string.section_appearance))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    val themeModes = ThemeMode.entries

                    val themeModeNames = themeModes.map { stringResource(it.labelRes) }

                    val currentSelectedIndex = themeModes.indexOf(themeMode).let {
                        if (it == -1) 0 else it // 防止异常情况导致 -1，给个默认值 0
                    }
                    SuperDropdown(
                        title = stringResource(R.string.theme_mode),
                        items = themeModeNames,
                        selectedIndex = currentSelectedIndex,
                        onSelectedIndexChange = { index ->
                            val selectedMode = themeModes[index]
                            settingsViewModel.setThemeMode(selectedMode)
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.monet),
                        checked = monetEnable,
                        onCheckedChange = {
                            settingsViewModel.setMonetEnable(!monetEnable)
                        }
                    )
                    AnimatedVisibility(visible = (monetEnable)) {
                        val currentSelectedIndex = KeyColors.indexOf(currentKeyColor).let {
                            if (it == -1) 0 else it
                        }
                        val options = KeyColors.map { keyColor ->
                            SpinnerEntry(
                                title = stringResource(keyColor.nameResId),
                                icon = {
                                    val tintColor = keyColor.color ?: getSystemWallpaperColor(context)

                                    Icon(
                                        painter = RoundedRectanglePainter(),
                                        contentDescription = stringResource(keyColor.nameResId),
                                        modifier = Modifier.padding(end = 12.dp),
                                        tint = tintColor
                                    )
                                }
                            )
                        }
                        SuperSpinner(
                            items = options,
                            selectedIndex = currentSelectedIndex,
                            title = stringResource(R.string.key_color),
                            onSelectedIndexChange = {
                                val selectedKeyColor = KeyColors[it]
                                settingsViewModel.setKeyColor(selectedKeyColor)
                            }
                        )
                    }
                }
            }
            item(
                key = "scan"
            ) {
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
                SmallTitle(stringResource(R.string.section_scan))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    SuperArrow(
                        title = stringResource(R.string.folder_manager),
                        onClick = {
                            navigator.navigate(FolderManagerDestination())
                        },
                        summary = sub
                    )
                    SuperSwitch(
                        title = stringResource(R.string.ignore_short_audio),
                        checked = ignoreShortAudio,
                        onCheckedChange = {
                            settingsViewModel.setIgnoreShortAudio(!ignoreShortAudio)
                        }
                    )
                }
            }
            item(
                key = "search"
            ) {
                SmallTitle(stringResource(R.string.section_search))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    val subText =
                        searchSourceOrder.joinToString(" > ") { context.getString(it.labelRes) }
                    SuperArrow(
                        title = stringResource(R.string.search_source_priority),
                        onClick = {
                            navigator.navigate(SearchSourcePriorityDestination())
                        },
                        summary = subText
                    )
                    SuperArrow(
                        title = stringResource(R.string.search_limit),
                        endActions = {
                            Text(
                                text = "${tempSearchPageSize.intValue}",
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        },
                        onClick = {
                            showSearchLimitConfigDialog.value = true
                        },
                        bottomAction = {
                            Slider(
                                showKeyPoints = true,
                                valueRange = minSearchSize.toFloat()..maxSearchSize.toFloat(),
                                steps = maxSearchSize - minSearchSize - 1,
                                value = tempSearchPageSize.intValue.toFloat(),
                                onValueChange = {
                                    tempSearchPageSize.intValue = it.roundToInt()
                                },
                                onValueChangeFinished = {
                                    settingsViewModel.setSearchPageSize(tempSearchPageSize.intValue)
                                }
                            )
                            Spacer(modifier = Modifier.height(BasicComponentDefaults.InsideMargin.calculateBottomPadding()))
                            Text(
                                text = stringResource(R.string.search_limit_tip),
                                fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions
                            )
                        }
                    )
                }
            }
            item(
                key = "lyric"
            ) {
                SmallTitle(stringResource(R.string.section_lyrics))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    val lyricoFormats = LyricFormat.entries

                    val lyricoFormatNames = lyricoFormats.map { stringResource(it.labelRes) }

                    val currentSelectedIndex = lyricoFormats.indexOf(lyricFormat).let {
                        if (it == -1) 0 else it
                    }
                    SuperDropdown(
                        title = stringResource(R.string.lyric_mode),
                        items = lyricoFormatNames,
                        selectedIndex = currentSelectedIndex,
                        onSelectedIndexChange = { index ->
                            val selectedMode = lyricoFormats[index]
                            settingsViewModel.setLyricFormat(selectedMode)
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = romaEnabled,
                        onCheckedChange = {
                            settingsViewModel.setRomaEnabled(!romaEnabled)
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = translationEnabled,
                        onCheckedChange = {
                            settingsViewModel.setTranslationEnabled(!translationEnabled)
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.only_translation_if_available),
                        summary = stringResource(R.string.only_translation_if_available_hint),
                        checked = onlyTranslationIfAvailable,
                        onCheckedChange = {
                            settingsViewModel.setOnlyTranslationIfAvailable(!onlyTranslationIfAvailable)
                        },
                        enabled = translationEnabled
                    )
                }
            }
            item(
                key = "metadata"
            ) {
                SmallTitle(stringResource(R.string.section_metadata))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    val artistSeparators = ArtistSeparator.entries
                    val artistSeparatorNames = artistSeparators.map { it.toText() }
                    val currentSelectedIndex = artistSeparators.indexOf(artistSeparator).let {
                        if (it == -1) 0 else it
                    }
                    SuperDropdown(
                        title = stringResource(R.string.artist_separator),
                        summary = stringResource(R.string.artist_separator_hint),
                        items = artistSeparatorNames,
                        selectedIndex = currentSelectedIndex,
                        onSelectedIndexChange = { index ->
                            val selectedSeparator = artistSeparators[index]
                            settingsViewModel.setSeparator(selectedSeparator)
                        }
                    )
                }
            }
            item(
                key = "other"
            ) {
                SmallTitle(stringResource(R.string.section_other))
                Card(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    SuperArrow(
                        title = stringResource(R.string.batch_match_history),
                        summary = stringResource(R.string.batch_match_history_hint),
                        onClick = {
                            navigator.navigate(BatchMatchHistoryDestination())
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.clear_cache),
                        onClick = {
                            showClearCacheDialog.value = true
                        },
                        endActions = {
                            Text(
                                text = settingsUiState.totalCacheSize.formatSize(),
                                fontSize = MiuixTheme.textStyles.body2.fontSize,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            )
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.about),
                        onClick = {
                            navigator.navigate(AboutDestination())
                        }
                    )
                }
            }
        }
    }
}