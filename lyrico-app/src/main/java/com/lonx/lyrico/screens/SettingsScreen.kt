@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.screens

import com.lonx.lyrico.utils.DownloadsFileWriter

import com.lonx.lyrico.ui.components.salt.SaltTextInput
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.screens.settings.*
import kotlin.math.roundToInt
import android.annotation.SuppressLint
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.data.model.AppLanguage
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.data.model.ReplayGainPeakMode
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.visibleLyricLineTracks
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.lyrics.LyricLineOrderBottomSheetContent
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.ui.components.base.YesNoBottomSheet
import com.lonx.lyrico.ui.components.getSystemWallpaperColor
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.viewmodel.FolderManagerViewModel
import com.lonx.lyrico.viewmodel.SettingsEvent
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.AppLogsDestination
import com.ramcosta.composedestinations.generated.destinations.ProblemReproductionDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistPosterFoldersDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistSplitSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.BatchTaskListDestination
import com.ramcosta.composedestinations.generated.destinations.EditFieldSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.FolderManagerDestination
import com.ramcosta.composedestinations.generated.destinations.LyricsCleanupRulesDestination
import com.ramcosta.composedestinations.generated.destinations.PluginManagerDestination
import com.ramcosta.composedestinations.generated.destinations.QuickjsTestDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
@Destination<RootGraph>(route = "settings")
fun SettingsScreen(
    navigator: DestinationsNavigator
) {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val folderViewModel: FolderManagerViewModel = koinViewModel()
    val folderUiState by folderViewModel.uiState.collectAsStateWithLifecycle()
    val settingsRepository: SettingsRepository = koinInject()
    val artistPosterFolder by settingsRepository.artistPosterFolder.collectAsStateWithLifecycle(initialValue = null)

    val uiEngine = LocalUiEngine.current
    val lyricFormat = settingsUiState.lyricFormat
    val artistSeparator = settingsUiState.separator
    val romaEnabled = settingsUiState.romaEnabled
    val themeMode = settingsUiState.themeMode
    val monetEnable = settingsUiState.monetEnable
    val floatingBottomBarEnabled = settingsUiState.floatingBottomBarEnabled
    val barBlurEnabled = settingsUiState.barBlurEnabled
    val floatingBarEffect = settingsUiState.floatingBarEffect
    val currentKeyColor = settingsUiState.keyColor
    val translationEnabled = settingsUiState.translationEnabled
    val onlyTranslationIfAvailable = settingsUiState.onlyTranslationIfAvailable
    val removeEmptyLines = settingsUiState.removeEmptyLines
    val ignoreShortAudio = settingsUiState.ignoreShortAudio
    val folders = folderUiState.folders
    val totalFolders = folders.filter { it.addedBySaf }.size
    val conversionMode = settingsUiState.conversionMode
    val searchSourceTabStyle = settingsUiState.searchSourceTabStyle
    val showAllSearchResultFields = settingsUiState.showAllSearchResultFields

    val ignoredFolders = folders.count { it.isIgnored && it.addedBySaf }
    val searchPageSize = settingsUiState.searchPageSize
    val scope = rememberCoroutineScope()

    val minSearchSize = 1
    val maxSearchSize = 20
    val tempSearchPageSize = remember(searchPageSize) { mutableIntStateOf(searchPageSize) }
    val showClearCacheDialog = remember { mutableStateOf(false) }
    val showRgTargetDialog = remember { mutableStateOf(false) }
    val showLyricLineOrderSheet = remember { mutableStateOf(false) }
    val visibleTracks = visibleLyricLineTracks(romaEnabled, translationEnabled, onlyTranslationIfAvailable)
    val monetVisibilityState = remember(settingsUiState.isInitialized) {
        MutableTransitionState(monetEnable)
    }

    LaunchedEffect(monetEnable) {
        monetVisibilityState.targetState = monetEnable
    }

    val themeModeItems = ThemeMode.entries.map { stringResource(it.labelRes) }
    val selectedThemeModeIndex =
        ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0)

    val lyricFormatItems = LyricFormat.entries.map { stringResource(it.labelRes) }
    val selectedLyricFormatIndex = LyricFormat.entries.indexOf(lyricFormat).coerceAtLeast(0)

    val conversionModeItems = ConversionMode.entries.map { stringResource(it.labelRes) }
    val selectedConversionModeIndex =
        ConversionMode.entries.indexOf(conversionMode).coerceAtLeast(0)
    val searchSourceTabStyleItems = SearchSourceTabStyle.entries.map { stringResource(it.labelRes) }
    val replayGainPeakModeItems = listOf(
        stringResource(R.string.replay_gain_peak_mode_sample),
        stringResource(R.string.replay_gain_peak_mode_true)
    )
    val selectedReplayGainPeakModeIndex = ReplayGainPeakMode.entries
        .indexOf(settingsUiState.replayGainPeakMode)
        .coerceAtLeast(0)
    val selectedSearchSourceTabStyleIndex =
        SearchSourceTabStyle.entries.indexOf(searchSourceTabStyle).coerceAtLeast(0)

    val floatingBarEffectItems = FloatingBarEffect.entries.map { stringResource(it.labelRes) }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val lineOrderSummary = settingsUiState.lyricLineOrder.filter { it in visibleTracks }
        .joinToString(" / ") { context.getString(it.labelRes) }

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
    ActionBottomSheet(
        show = showLyricLineOrderSheet.value,
        title = stringResource(R.string.lyric_line_order),
        onDismissRequest = { showLyricLineOrderSheet.value = false },
        scrollContent = false,
        content = {
            LyricLineOrderBottomSheetContent(
                lineOrder = settingsUiState.lyricLineOrder,
                visibleTracks = visibleTracks,
                onLineOrderChange = settingsViewModel::setLyricLineOrder,
            )
        },
    )
    SettingsPage(
        engine = uiEngine,
        initialized = settingsUiState.isInitialized,
        onBack = { navigator.popBackStack() },
    ) {
            item(key = "appearance"){
                SectionTitle(text = stringResource(R.string.section_appearance))
                Group {
                    Choice(
                        icon = LucideIcons.SwatchBook, tone = PreferenceIconTone.Appearance,
                        title = stringResource(R.string.ui_engine),
                        items = listOf(stringResource(R.string.ui_engine_miuix), stringResource(R.string.ui_engine_salt)),
                        selectedIndex = uiEngine.ordinal,
                        onSelectedIndexChange = { index ->
                            scope.launch { settingsRepository.saveUiEngine(UiEngine.entries[index]) }
                        },
                    )

                    val configuration = LocalConfiguration.current
                    var selectedLanguageTag by remember(configuration) {
                        mutableStateOf(
                            AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag().orEmpty()
                        )
                    }
                    Choice(
                        icon = LucideIcons.Languages, tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.app_language),
                        items = AppLanguage.entries.map { stringResource(it.labelRes) },
                        selectedIndex = AppLanguage.entries.indexOfFirst {
                            it.languageTag == selectedLanguageTag
                        }.coerceAtLeast(0),
                        onSelectedIndexChange = { index ->
                            selectedLanguageTag = AppLanguage.entries[index].languageTag
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(selectedLanguageTag)
                            )
                        }
                    )
                    Choice(
                        icon = LucideIcons.Moon, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.theme_mode),
                        items = themeModeItems,
                        selectedIndex = selectedThemeModeIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setThemeMode(ThemeMode.entries[index])
                        }
                    )
                    if (uiEngine == UiEngine.MIUIX) {
                        Toggle(
                            icon = LucideIcons.Blend, tone = PreferenceIconTone.Lyrics,
                            title = stringResource(R.string.bar_blur),
                            summary = stringResource(R.string.bar_blur_summary),
                            checked = barBlurEnabled,
                            onCheckedChange = settingsViewModel::setBarBlurEnabled,
                        )
                        Toggle(
                            icon = LucideIcons.PanelBottom, tone = PreferenceIconTone.Pink,
                            title = stringResource(R.string.floating_bottom_bar),
                            summary = stringResource(R.string.floating_bottom_bar_summary),
                            checked = floatingBottomBarEnabled,
                            onCheckedChange = { settingsViewModel.setFloatingBottomBarEnabled(it) }
                        )
                        AnimatedVisibility(visible = floatingBottomBarEnabled) {
                            Choice(
                                icon = LucideIcons.Layers, tone = PreferenceIconTone.Teal,
                                title = stringResource(R.string.floating_bar_effect),
                                items = floatingBarEffectItems,
                                selectedIndex = floatingBarEffect.ordinal,
                                onSelectedIndexChange = { index ->
                                    settingsViewModel.setFloatingBarEffect(
                                        FloatingBarEffect.entries[index]
                                    )
                                },
                            )
                        }
                    }
                    Toggle(
                        icon = LucideIcons.Palette, tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.monet),
                        checked = monetEnable,
                        onCheckedChange = {
                            settingsViewModel.setMonetEnable(!monetEnable)
                        }
                    )
                    AnimatedVisibility(visibleState = monetVisibilityState) {
                        val currentSelectedIndex = KeyColors.indexOf(currentKeyColor).let {
                            if (it == -1) 0 else it
                        }
                        val options = KeyColors.map { keyColor ->
                            PaletteOption(
                                title = stringResource(keyColor.nameResId),
                                color = keyColor.color ?: getSystemWallpaperColor(context),
                            )
                        }

                        Spinner(
                            items = options,
                            selectedIndex = currentSelectedIndex,
                            icon = LucideIcons.Palette, tone = PreferenceIconTone.Appearance,
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
                SectionTitle(text = stringResource(R.string.section_scan))
                Group {
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
                    Action(
                        icon = LucideIcons.Folder, tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.folder_manager),
                        summary = folderSummary,
                        onClick = { navigator.navigate(FolderManagerDestination()) }
                    )
                    Action(
                        icon = LucideIcons.Image, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.artist_poster_folders),
                        summary = stringResource(
                            if (artistPosterFolder == null) {
                                R.string.artist_poster_folder_not_selected
                            } else {
                                R.string.artist_poster_folder_selected
                            }
                        ),
                        onClick = { navigator.navigate(ArtistPosterFoldersDestination()) }
                    )
                    Toggle(
                        icon = LucideIcons.AudioLines, tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.ignore_short_audio),
                        checked = ignoreShortAudio,
                        onCheckedChange = { settingsViewModel.setIgnoreShortAudio(it) }
                    )
                    Toggle(
                        icon = LucideIcons.Database, tone = PreferenceIconTone.Pink,
                        title = stringResource(R.string.lyric_index_title),
                        summary = stringResource(R.string.lyric_index_summary),
                        checked = settingsUiState.lyricIndexEnabled,
                        onCheckedChange = { settingsViewModel.setLyricIndexEnabled(it) }
                    )
                    Action(
                        icon = LucideIcons.Volume2, tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.settings_replay_gain_target_loudness),
                        summary = stringResource(R.string.settings_replay_gain_target_loudness_summary),
                        value = formatLoudnessValue(settingsUiState.replayGainTargetLoudness),
                        onClick = { showRgTargetDialog.value = true },
                        holdDownState = showRgTargetDialog.value
                    )
                    Choice(
                        icon = LucideIcons.Activity, tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.settings_replay_gain_peak_mode),
                        items = replayGainPeakModeItems,
                        selectedIndex = selectedReplayGainPeakModeIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setReplayGainPeakMode(
                                ReplayGainPeakMode.entries[index]
                            )
                        }
                    )
                }
            }

            item(key = "search"){
                SectionTitle(text = stringResource(R.string.section_search))
                Group {
                    Action(
                        icon = LucideIcons.Plug, tone = PreferenceIconTone.Appearance,
                        title = stringResource(R.string.plugin_manager_title),
                        onClick = { navigator.navigate(PluginManagerDestination()) }
                    )
                    Choice(
                        icon = LucideIcons.PanelsTopLeft, tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.search_source_tab_style),
                        items = searchSourceTabStyleItems,
                        selectedIndex = selectedSearchSourceTabStyleIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setSearchSourceTabStyle(
                                SearchSourceTabStyle.entries[index]
                            )
                        }
                    )
                    Range(
                        icon = LucideIcons.ListFilter, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.search_limit),
                        valueText = tempSearchPageSize.intValue.toString(),
                        summary = stringResource(R.string.search_limit_tip),
                        valueRange = minSearchSize.toFloat()..maxSearchSize.toFloat(),
                        steps = maxSearchSize - minSearchSize - 1,
                        value = tempSearchPageSize.intValue.toFloat(),
                        onValueChange = { tempSearchPageSize.intValue = it.roundToInt() },
                        onValueChangeFinished = {
                            settingsViewModel.setSearchPageSize(tempSearchPageSize.intValue)
                        }
                    )
                    Choice(
                        icon = LucideIcons.Split, tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.artist_separator),
                        summary = stringResource(R.string.artist_separator_hint),
                        items = artistSeparatorItems,
                        selectedIndex = selectedArtistSeparatorIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setSeparator(artistSeparators[index])
                        }
                    )
                    Toggle(
                        icon = LucideIcons.TextSearch, tone = PreferenceIconTone.Pink,
                        title = stringResource(R.string.show_all_search_result_fields),
                        summary = stringResource(R.string.show_all_search_result_fields_summary),
                        checked = showAllSearchResultFields,
                        onCheckedChange = settingsViewModel::setShowAllSearchResultFields
                    )
                }
            }

            item(key = "lyrics"){
                SectionTitle(text = stringResource(R.string.section_lyrics))
                Group {
                    Choice(
                        icon = LucideIcons.FileMusic, tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.lyric_mode),
                        items = lyricFormatItems,
                        selectedIndex = selectedLyricFormatIndex,
                        onSelectedIndexChange = { index ->
                            settingsViewModel.setLyricFormat(LyricFormat.entries[index])
                        }
                    )
                    Toggle(
                        icon = LucideIcons.Type, tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = romaEnabled,
                        onCheckedChange = { settingsViewModel.setRomaEnabled(it) }
                    )
                    Toggle(
                        icon = LucideIcons.Languages, tone = PreferenceIconTone.Appearance,
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = translationEnabled,
                        onCheckedChange = { settingsViewModel.setTranslationEnabled(it) }
                    )
                    AnimatedVisibility(visible = translationEnabled) {
                        Toggle(
                            icon = LucideIcons.Captions, tone = PreferenceIconTone.Tools,
                            title = stringResource(R.string.only_translation_if_available),
                            summary = stringResource(R.string.only_translation_if_available_hint),
                            enabled = translationEnabled,
                            checked = onlyTranslationIfAvailable,
                            onCheckedChange = { settingsViewModel.setOnlyTranslationIfAvailable(it) }
                        )
                    }
                    Action(
                        icon = LucideIcons.ArrowDownUp, tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.lyric_line_order),
                        summary = lineOrderSummary,
                        onClick = { showLyricLineOrderSheet.value = true },
                        holdDownState = showLyricLineOrderSheet.value,
                    )
                }
            }

            item(key = "text_processing"){
                SectionTitle(text = stringResource(R.string.section_text_processing))
                Group {
                    Choice(
                        icon = LucideIcons.ALargeSmall, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.conversion_mode),
                        summary = stringResource(R.string.conversion_mode_hint),
                        items = conversionModeItems,
                        selectedIndex = selectedConversionModeIndex,
                        onSelectedIndexChange = {
                            settingsViewModel.setConversionMode(ConversionMode.entries[it])
                        },
                    )
                    Toggle(
                        icon = LucideIcons.RemoveLines, tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.remove_empty_lines),
                        summary = stringResource(R.string.remove_empty_lines_hint),
                        checked = removeEmptyLines,
                        onCheckedChange = { settingsViewModel.setRemoveEmptyLines(it) }
                    )
                    Action(
                        icon = LucideIcons.FilterLyrics, tone = PreferenceIconTone.Pink,
                        title = stringResource(R.string.non_lyrics_cleanup_rules_title),
                        summary = stringResource(R.string.non_lyrics_cleanup_rules_summary),
                        onClick = { navigator.navigate(LyricsCleanupRulesDestination()) }
                    )
                    Action(
                        icon = LucideIcons.Users, tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.artist_split_settings_title),
                        summary = stringResource(R.string.artist_split_settings_summary),
                        onClick = { navigator.navigate(ArtistSplitSettingsDestination()) }
                    )
                    Action(
                        icon = LucideIcons.SlidersHorizontal, tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.edit_field_settings_title),
                        summary = stringResource(R.string.edit_field_settings_summary),
                        onClick = { navigator.navigate(EditFieldSettingsDestination()) }
                    )
                }
            }

            item(key = "backup"){
                SectionTitle(text = stringResource(R.string.section_backup))
                Group {
                    Action(
                        icon = LucideIcons.FileUp, tone = PreferenceIconTone.Appearance,
                        title = stringResource(R.string.export_config),
                        summary = stringResource(R.string.export_config_hint),
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (DownloadsFileWriter.canWrite(context)) {
                                settingsViewModel.exportSettings(context)
                            } else {
                                exportLauncher.launch("lyrico_settings_backup_${currentTime}.json")
                            }
                        }
                    )
                    Action(
                        icon = LucideIcons.FileDown, tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.import_config),
                        summary = stringResource(R.string.import_config_hint),
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            item(key = "other"){
                SectionTitle(text = stringResource(R.string.section_other))
                Group {
                    Action(
                        icon = LucideIcons.ClipboardList, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.batch_task_list_title),
                        onClick = { navigator.navigate(BatchTaskListDestination()) }
                    )
                    Action(
                        icon = LucideIcons.Logs, tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.app_log_title),
                        summary = stringResource(R.string.app_log_summary),
                        onClick = { navigator.navigate(AppLogsDestination()) }
                    )
                    Action(
                        icon = LucideIcons.Activity, tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.reproduction_title),
                        summary = stringResource(R.string.log_recording_summary),
                        onClick = { navigator.navigate(ProblemReproductionDestination()) },
                    )
                    if (BuildConfig.DEBUG) {
                        Action(
                            icon = LucideIcons.Terminal, tone = PreferenceIconTone.Pink,
                            title = stringResource(R.string.quickjs_test_title),
                            summary = stringResource(R.string.quickjs_test_description_summary),
                            onClick = { navigator.navigate(QuickjsTestDestination()) }
                        )
                    }

                    val cacheSummary = stringResource(
                        R.string.cache_size_label,
                        Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                    )
                    Action(
                        icon = LucideIcons.Eraser, tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.clear_cache),
                        summary = cacheSummary,
                        onClick = { showClearCacheDialog.value = true },
                        holdDownState = showClearCacheDialog.value
                    )
                    if (BuildConfig.DEBUG) {
                        Action(
                            icon = LucideIcons.Database, tone = PreferenceIconTone.Danger,
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
                    Action(
                        icon = LucideIcons.NotebookPen, tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.user_guide),
                        onClick = { uriHandler.openUri("https://replica0110.github.io/Lyrico/guide/") }
                    )
                    Action(
                        icon = LucideIcons.Info, tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.about_title),
                        onClick = { navigator.navigate(AboutDestination()) }
                    )
                }
            }
    }
    SettingsCacheDialog(
        show = showClearCacheDialog.value,
        text = cacheContent,
        onDismiss = { showClearCacheDialog.value = false },
        onConfirm = {
            settingsViewModel.clearCache(context)
            showClearCacheDialog.value = false
        },
    )
    ReplayGainTargetLoudnessSheet(
        show = showRgTargetDialog.value,
        currentLoudness = settingsUiState.replayGainTargetLoudness,
        onDismiss = { showRgTargetDialog.value = false },
        onSave = settingsViewModel::setReplayGainTargetLoudness,
    )
}

private fun formatLoudnessNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        "%.0f".format(java.util.Locale.US, value)
    } else {
        "%.2f".format(java.util.Locale.US, value)
    }
}

private fun formatLoudnessValue(value: Double): String = "${formatLoudnessNumber(value)} LUFS"

@Composable
private fun ReplayGainTargetLoudnessSheet(
    show: Boolean,
    currentLoudness: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    val presets = remember { listOf(-23.0, -18.0, -14.0) }
    val presetLabels = listOf(
        R.string.replay_gain_target_preset_ebu,
        R.string.replay_gain_target_preset_default,
        R.string.replay_gain_target_preset_streaming
    )
    var selectedPreset by remember(show, currentLoudness) {
        mutableDoubleStateOf(if (currentLoudness in presets) currentLoudness else presets.first())
    }
    var customSelected by remember(show, currentLoudness) {
        mutableStateOf(currentLoudness !in presets)
    }
    val customState = remember(show, currentLoudness) {
        TextFieldState(if (currentLoudness in presets) "" else formatLoudnessNumber(currentLoudness))
    }
    val customText = customState.text.toString()
    var invalid by remember(show) { mutableStateOf(false) }
    val clearInputError = InputTransformation { invalid = false }

    if (LocalUiEngine.current == UiEngine.SALT) {
        com.lonx.lyrico.ui.components.salt.SaltConfirmDialog(
            show = show, dismissOnConfirm = false,
            title = stringResource(R.string.settings_replay_gain_target_loudness),
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm),
            onDismissRequest = onDismiss,
            onConfirm = {
                val value = if (customSelected) customText.trim().toDoubleOrNull() else selectedPreset
                if (value != null && value in -60.0..0.0) {
                    onSave(value)
                    onDismiss()
                } else invalid = true
            },
            content = {
                com.moriafly.salt.ui.RoundedColumn(paddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    presets.forEachIndexed { index, preset ->
                        com.moriafly.salt.ui.ItemCheck(
                            text = stringResource(presetLabels[index]),
                            state = !customSelected && selectedPreset == preset,
                            onChange = { selectedPreset = preset; customSelected = false; invalid = false },
                        )
                    }
                    com.moriafly.salt.ui.ItemCheck(
                        text = stringResource(R.string.replay_gain_target_preset_custom),
                        state = customSelected,
                        onChange = { customSelected = true; invalid = false },
                    )
                    if (customSelected) SaltTextInput(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        backgroundColor = com.moriafly.salt.ui.SaltTheme.colors.background,
                        state = customState,
                        inputTransformation = clearInputError,
                        hint = stringResource(R.string.replay_gain_target_custom_hint),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    if (invalid) com.moriafly.salt.ui.ItemTip(stringResource(R.string.replay_gain_target_invalid))
                }
            },
        )
        return
    }

    YesNoBottomSheet(
        show = show,
        title = stringResource(R.string.settings_replay_gain_target_loudness),
        onDismissRequest = onDismiss,
        onConfirm = {
            if (customSelected) {
                val customValue = customText.trim().toDoubleOrNull()
                if (customValue != null && customValue in -60.0..0.0) {
                    onSave(customValue)
                    onDismiss()
                } else {
                    invalid = true
                }
            } else {
                onSave(selectedPreset)
                onDismiss()
            }
        },
        content = {
            Column {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.secondaryContainer
                    )
                ) {
                    presets.forEachIndexed { index, preset ->
                        RadioButtonPreference(
                            title = stringResource(presetLabels[index]),
                            selected = !customSelected && selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                customSelected = false
                                invalid = false
                            }
                        )
                    }
                    RadioButtonPreference(
                        title = stringResource(R.string.replay_gain_target_preset_custom),
                        selected = customSelected,
                        onClick = {
                            customSelected = true
                            invalid = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                AnimatedVisibility(visible = customSelected) {
                    Card(
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        TextField(
                            state = customState,
                            inputTransformation = clearInputError,
                            label = stringResource(R.string.replay_gain_target_custom_hint),
                            modifier = Modifier.fillMaxWidth(),
                            lineLimits = TextFieldLineLimits.SingleLine,
                        )
                        if (invalid) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.replay_gain_target_invalid),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}
