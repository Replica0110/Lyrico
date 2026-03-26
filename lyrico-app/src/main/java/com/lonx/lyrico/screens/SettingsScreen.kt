package com.lonx.lyrico.screens

import android.annotation.SuppressLint
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.LyricFormat
import com.lonx.lyrico.data.model.ThemeMode
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    val showClearCacheDialog = remember { mutableStateOf(false) }

    val themeModeItems = ThemeMode.entries.map { stringResource(it.labelRes) }
    val selectedThemeModeIndex = ThemeMode.entries.indexOf(settingsUiState.themeMode).coerceAtLeast(0)

    val lyricFormatItems = LyricFormat.entries.map { stringResource(it.labelRes) }
    val selectedLyricFormatIndex = LyricFormat.entries.indexOf(lyricFormat).coerceAtLeast(0)

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

    val tempPageSize = remember(searchPageSize) {
        mutableIntStateOf(searchPageSize)
    }

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

    BasicScreenBox(
        title = stringResource(R.string.settings_title),
        onBack = { navigator.popBackStack() }
    ) {
        SuperDialog(
            title = stringResource(R.string.clear_cache),
            show = showClearCacheDialog,
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.cancel),
                        onClick = { showClearCacheDialog.value = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.confirm),
                        onClick = {
                            showClearCacheDialog.value = false
                            settingsViewModel.clearCache(context)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
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
            }

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
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_limit),
                        color = MiuixTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tempPageSize.intValue.toString(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Slider(
                        value = tempPageSize.intValue.toFloat(),
                        valueRange = 1f..20f,
                        steps = 18,
                        onValueChange = {
                            tempPageSize.intValue = it.roundToInt()
                        },
                        onValueChangeFinished = {
                            settingsViewModel.setSearchPageSize(tempPageSize.intValue)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.search_limit_tip),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize
                    )
                }
            }

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
                ItemDropdown(
                    text = stringResource(R.string.conversion_mode),
                    value = stringResource(conversionMode.labelRes)
                ) {
                    val modes = ConversionMode.entries.toList()
                    modes.forEach { mode ->
                        ItemCheck(
                            text = stringResource(mode.labelRes),
                            state = conversionMode == mode,
                            onChange = {
                                settingsViewModel.setConversionMode(mode)
                                state.dismiss()
                            }
                        )
                    }
                }
            }

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
                                    Toast.makeText(context, "已清空数据库", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "清空数据库失败", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
