package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.ui.components.RoundedRectanglePainter
import com.lonx.lyrico.ui.components.getSystemWallpaperColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.lonx.lyrico.viewmodel.SettingsEvent
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.BatchMatchHistoryDestination
import com.ramcosta.composedestinations.generated.destinations.FolderManagerDestination
import com.ramcosta.composedestinations.generated.destinations.SearchSourcePriorityDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
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
    val themeMode = settingsUiState.themeMode
    val monetEnable = settingsUiState.monetEnable
    val currentKeyColor = settingsUiState.keyColor
    val translationEnabled = settingsUiState.translationEnabled
    val onlyTranslationIfAvailable = settingsUiState.onlyTranslationIfAvailable
    val removeEmptyLines = settingsUiState.removeEmptyLines
    val ignoreShortAudio = settingsUiState.ignoreShortAudio
    val scrollState = rememberScrollState()
    val folders = folderUiState.folders
    val totalFolders = folders.size
    val conversionMode = settingsUiState.conversionMode
    val ignoredFolders = folders.count { it.isIgnored }
    val searchSourceOrder = settingsUiState.searchSourceOrder
    val searchPageSize = settingsUiState.searchPageSize
    val scope = rememberCoroutineScope()

    val minSearchSize = 1
    val maxSearchSize = 20
    val tempSearchPageSize = remember(searchPageSize) {
        mutableIntStateOf(searchPageSize)
    }
    val showSearchLimitConfigDialog = remember { mutableStateOf(false) }
    val showClearCacheDialog = remember { mutableStateOf(false) }

    val themeModeItems = ThemeMode.entries.map { stringResource(it.labelRes) }
    val selectedThemeModeIndex =
        ThemeMode.entries.indexOf(settingsUiState.themeMode).coerceAtLeast(0)

    val lyricFormatItems = LyricFormat.entries.map { stringResource(it.labelRes) }
    val selectedLyricFormatIndex = LyricFormat.entries.indexOf(lyricFormat).coerceAtLeast(0)

    val conversionModeItems = ConversionMode.entries.map { stringResource(it.labelRes) }
    val selectedConversionModeIndex =
        ConversionMode.entries.indexOf(conversionMode).coerceAtLeast(0)
    val artistSeparators = remember {
        listOf(
            ArtistSeparator.ENUMERATION_COMMA,
            ArtistSeparator.SLASH,
            ArtistSeparator.COMMA,
            ArtistSeparator.SEMICOLON
        )
    }
    val artistSeparatorItems = artistSeparators.map { it.toText() }
    val selectedArtistSeparatorIndex = artistSeparators.indexOf(artistSeparator).coerceAtLeast(0)


    val context = LocalContext.current
    LaunchedEffect(Unit) {
        settingsViewModel.refreshCache(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { settingsViewModel.exportSettings(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { settingsViewModel.importSettings(context, it) }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    val text = event.message.asString(context)
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                        Formatter.formatFileSize(context, size)
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
                        Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                    )
                )
            }
        }
    }
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 12.dp),
                        onClick = { navigator.popBackStack() }
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { paddingValues ->
        SuperDialog(
            title = stringResource(R.string.clear_cache),
            show = showClearCacheDialog.value,
            onDismissRequest = { showClearCacheDialog.value = false }
        ) {
            Column {
                Text(
                    text = cacheContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    color = MiuixTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
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
            show = showSearchLimitConfigDialog.value,
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
            item(key = "appearance"){
                SmallTitle(text = stringResource(R.string.section_appearance))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SuperDropdown(
                        title = stringResource(R.string.theme_mode),
                        items = themeModeItems,
                        selectedIndex = selectedThemeModeIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setThemeMode(ThemeMode.entries[index])
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
                                    val tintColor =
                                        keyColor.color ?: getSystemWallpaperColor(context)

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

            item(key = "scan"){
                SmallTitle(text = stringResource(R.string.section_scan))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val folderSummary = if (totalFolders > 0) {
                        buildString {
                            append(stringResource(R.string.folder_found, totalFolders))
                            if (ignoredFolders > 0) {
                                append(stringResource(R.string.folder_ignored, ignoredFolders))
                            }
                        }
                    } else {
                        stringResource(R.string.folder_manage_hint)
                    }
                    SuperArrow(
                        title = stringResource(R.string.folder_manager),
                        summary = folderSummary,
                        onClick = { navigator.navigate(FolderManagerDestination()) }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.ignore_short_audio),
                        checked = ignoreShortAudio,
                        onCheckedChange = { settingsViewModel.setIgnoreShortAudio(it) }
                    )
                }
            }

            item(key = "search"){
                SmallTitle(text = stringResource(R.string.section_search))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    val searchSourceSummary = searchSourceOrder
                        .map { stringResource(it.labelRes) }
                        .joinToString(" > ")
                    SuperArrow(
                        title = stringResource(R.string.search_source_priority),
                        summary = searchSourceSummary,
                        onClick = { navigator.navigate(SearchSourcePriorityDestination()) }
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

            item(key = "lyrics"){
                SmallTitle(text = stringResource(R.string.section_lyrics))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SuperDropdown(
                        title = stringResource(R.string.lyric_mode),
                        items = lyricFormatItems,
                        selectedIndex = selectedLyricFormatIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setLyricFormat(LyricFormat.entries[index])
                        }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = romaEnabled,
                        onCheckedChange = { settingsViewModel.setRomaEnabled(it) }
                    )
                    SuperSwitch(
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = translationEnabled,
                        onCheckedChange = { settingsViewModel.setTranslationEnabled(it) }
                    )
                    AnimatedVisibility(visible = translationEnabled) {
                        SuperSwitch(
                            title = stringResource(R.string.only_translation_if_available),
                            summary = stringResource(R.string.only_translation_if_available_hint),
                            enabled = translationEnabled,
                            checked = onlyTranslationIfAvailable,
                            onCheckedChange = { settingsViewModel.setOnlyTranslationIfAvailable(it) }
                        )
                    }
                    SuperSwitch(
                        title = stringResource(R.string.remove_empty_lines),
                        summary = stringResource(R.string.remove_empty_lines_hint),
                        checked = removeEmptyLines,
                        onCheckedChange = { settingsViewModel.setRemoveEmptyLines(it) }
                    )
                    SuperDropdown(
                        title = stringResource(R.string.conversion_mode),
                        items = conversionModeItems,
                        selectedIndex = selectedConversionModeIndex,
                        onSelectedIndexChange = {
                            settingsViewModel.setConversionMode(ConversionMode.entries[it])
                        },
                    )
                }
            }

            item(key = "metadata"){
                SmallTitle(text = stringResource(R.string.section_metadata))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SuperDropdown(
                        title = stringResource(R.string.artist_separator),
                        summary = stringResource(R.string.artist_separator_hint),
                        items = artistSeparatorItems,
                        selectedIndex = selectedArtistSeparatorIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setSeparator(artistSeparators[index])
                        }
                    )
                }
            }

            item(key = "backup"){
                SmallTitle(text = stringResource(R.string.section_backup))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.export_config),
                        summary = stringResource(R.string.export_config_hint),
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            exportLauncher.launch("lyrico_settings_backup_${currentTime}.json")
                        }
                    )
                    SuperArrow(
                        title = stringResource(R.string.import_config),
                        summary = stringResource(R.string.import_config_hint),
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            item(key = "other"){
                SmallTitle(text = stringResource(R.string.section_other))
                Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                    SuperArrow(
                        title = stringResource(R.string.batch_match_history),
                        summary = stringResource(R.string.batch_match_history_hint),
                        onClick = { navigator.navigate(BatchMatchHistoryDestination()) }
                    )
                    val cacheSummary = stringResource(
                        R.string.cache_size_label,
                        Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                    )
                    SuperArrow(
                        title = stringResource(R.string.clear_cache),
                        summary = cacheSummary,
                        onClick = { showClearCacheDialog.value = true },
                        holdDownState = showClearCacheDialog.value
                    )
                    if (BuildConfig.DEBUG) {
                        SuperArrow(
                            title = stringResource(R.string.clear_songs),
                            onClick = {
                                scope.launch {
                                    val success = settingsViewModel.clearSongs()
                                    if (success) {
                                        Toast.makeText(context, "已清空数据库", Toast.LENGTH_SHORT)
                                            .show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "清空数据库失败",
                                            Toast.LENGTH_SHORT
                                        )
                                            .show()
                                    }
                                }
                            }
                        )
                    }
                    SuperArrow(
                        title = stringResource(R.string.about),
                        onClick = { navigator.navigate(AboutDestination()) }
                    )
                }
            }
        }
    }
}
