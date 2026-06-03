package com.lonx.lyrico.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.ui.components.SaltDropdownItem
import com.lonx.lyrico.ui.theme.UiEngine
import com.lonx.lyrico.viewmodel.FolderManagerUiState
import com.lonx.lyrico.viewmodel.SettingsUiState
import com.lonx.lyrico.viewmodel.SettingsViewModel
import com.moriafly.salt.ui.Item
import com.moriafly.salt.ui.ItemOuterTitle
import com.moriafly.salt.ui.ItemSlider
import com.moriafly.salt.ui.ItemSwitcher
import com.moriafly.salt.ui.ItemTip
import com.moriafly.salt.ui.RoundedColumn
import com.moriafly.salt.ui.TitleBar
import com.moriafly.salt.ui.UnstableSaltUiApi
import com.moriafly.salt.ui.dialog.YesNoDialog
import com.moriafly.salt.ui.ext.safeMainCompat
import com.moriafly.salt.ui.popup.PopupMenuItem
import com.moriafly.salt.ui.rememberScrollState
import com.moriafly.salt.ui.verticalScroll
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.AppLogsDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistSplitSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.BatchTaskListDestination
import com.ramcosta.composedestinations.generated.destinations.CustomTagManagementDestination
import com.ramcosta.composedestinations.generated.destinations.EditFieldVisibilityDestination
import com.ramcosta.composedestinations.generated.destinations.FolderManagerDestination
import com.ramcosta.composedestinations.generated.destinations.PluginManagerDestination
import com.ramcosta.composedestinations.generated.destinations.QuickjsTestDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableSaltUiApi::class)
@Composable
internal fun SaltSettingsScreen(
    navigator: DestinationsNavigator,
    settingsViewModel: SettingsViewModel,
    settingsUiState: SettingsUiState,
    folderUiState: FolderManagerUiState,
    scope: CoroutineScope,
    onExportSettings: () -> Unit,
    onImportSettings: () -> Unit
) {
    val context = LocalContext.current
    var showClearCacheDialog by remember { mutableStateOf(false) }
    val searchPageSize = remember(settingsUiState.searchPageSize) {
        mutableIntStateOf(settingsUiState.searchPageSize)
    }

    if (showClearCacheDialog) {
        val cacheContent = if (settingsUiState.categorizedCacheSize.isEmpty()) {
            stringResource(R.string.calculating_cache)
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
                append(stringResource(R.string.clear_cache_confirm))
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
        YesNoDialog(
            onDismissRequest = { showClearCacheDialog = false },
            onConfirm = {
                settingsViewModel.clearCache(context)
                showClearCacheDialog = false
            },
            title = stringResource(R.string.clear_cache),
            content = cacheContent,
            cancelText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.confirm)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeMainCompat.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                )
            )
    ) {
        TitleBar(
            onBack = { navigator.popBackStack() },
            text = stringResource(R.string.settings_title)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SaltSettingsSection(title = stringResource(R.string.section_appearance)) {
                DropdownItem(
                    text = stringResource(R.string.theme_mode),
                    selected = settingsUiState.themeMode,
                    options = ThemeMode.entries,
                    label = { stringResource(it.labelRes) },
                    onSelected = settingsViewModel::setThemeMode
                )
                DropdownItem(
                    text = stringResource(R.string.ui_engine),
                    selected = settingsUiState.uiEngine,
                    options = UiEngine.entries,
                    label = { stringResource(it.labelRes) },
                    onSelected = settingsViewModel::setUiEngine
                )
            }

            val folders = folderUiState.folders
            val totalFolders = folders.count { it.addedBySaf }
            val ignoredFolders = folders.count { it.isIgnored && it.addedBySaf }
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
            SaltSettingsSection(title = stringResource(R.string.section_scan)) {
                Item(
                    onClick = { navigator.navigate(FolderManagerDestination()) },
                    text = stringResource(R.string.folder_manager),
                    sub = folderSummary
                )
                ItemSwitcher(
                    state = settingsUiState.ignoreShortAudio,
                    onChange = settingsViewModel::setIgnoreShortAudio,
                    text = stringResource(R.string.ignore_short_audio)
                )
            }

            SaltSettingsSection(title = stringResource(R.string.section_search)) {
                Item(
                    onClick = { navigator.navigate(PluginManagerDestination()) },
                    text = stringResource(R.string.plugin_manager_title)
                )
                ItemSlider(
                    value = searchPageSize.intValue.toFloat(),
                    onValueChange = { searchPageSize.intValue = it.roundToInt() },
                    text = stringResource(R.string.search_limit),
                    sub = searchPageSize.intValue.toString(),
                    valueRange = 1f..20f,
                    steps = 18,
                    onValueChangeFinished = {
                        settingsViewModel.setSearchPageSize(searchPageSize.intValue)
                    }
                )
                ItemTip(text = stringResource(R.string.search_limit_tip))
            }

            SaltSettingsSection(title = stringResource(R.string.section_lyrics)) {
                DropdownItem(
                    text = stringResource(R.string.lyric_mode),
                    selected = settingsUiState.lyricFormat,
                    options = LyricFormat.entries,
                    label = { stringResource(it.labelRes) },
                    onSelected = settingsViewModel::setLyricFormat
                )
                ItemSwitcher(
                    state = settingsUiState.romaEnabled,
                    onChange = settingsViewModel::setRomaEnabled,
                    text = stringResource(R.string.roma),
                    sub = stringResource(R.string.roma_hint)
                )
                ItemSwitcher(
                    state = settingsUiState.translationEnabled,
                    onChange = settingsViewModel::setTranslationEnabled,
                    text = stringResource(R.string.translation),
                    sub = stringResource(R.string.translation_hint)
                )
                if (settingsUiState.translationEnabled) {
                    ItemSwitcher(
                        state = settingsUiState.onlyTranslationIfAvailable,
                        onChange = settingsViewModel::setOnlyTranslationIfAvailable,
                        text = stringResource(R.string.only_translation_if_available),
                        sub = stringResource(R.string.only_translation_if_available_hint)
                    )
                }
            }

            val artistSeparators = remember {
                listOf(
                    ArtistSeparator.ENUMERATION_COMMA,
                    ArtistSeparator.SLASH,
                    ArtistSeparator.COMMA,
                    ArtistSeparator.SEMICOLON
                )
            }
            SaltSettingsSection(title = stringResource(R.string.section_text_processing)) {
                DropdownItem(
                    text = stringResource(R.string.conversion_mode),
                    sub = stringResource(R.string.conversion_mode_hint),
                    selected = settingsUiState.conversionMode,
                    options = ConversionMode.entries,
                    label = { stringResource(it.labelRes) },
                    onSelected = settingsViewModel::setConversionMode
                )
                ItemSwitcher(
                    state = settingsUiState.removeEmptyLines,
                    onChange = settingsViewModel::setRemoveEmptyLines,
                    text = stringResource(R.string.remove_empty_lines),
                    sub = stringResource(R.string.remove_empty_lines_hint)
                )
                DropdownItem(
                    text = stringResource(R.string.artist_separator),
                    sub = stringResource(R.string.artist_separator_hint),
                    selected = settingsUiState.separator,
                    options = artistSeparators,
                    label = { it.toText() },
                    onSelected = settingsViewModel::setSeparator
                )
                Item(
                    onClick = { navigator.navigate(ArtistSplitSettingsDestination()) },
                    text = stringResource(R.string.artist_split_settings_title),
                    sub = stringResource(R.string.artist_split_settings_summary)
                )
                Item(
                    onClick = { navigator.navigate(EditFieldVisibilityDestination()) },
                    text = stringResource(R.string.edit_field_visibility_settings)
                )
                Item(
                    onClick = { navigator.navigate(CustomTagManagementDestination()) },
                    text = stringResource(R.string.custom_tag_management_title)
                )
            }

            SaltSettingsSection(title = stringResource(R.string.section_backup)) {
                Item(
                    onClick = onExportSettings,
                    text = stringResource(R.string.export_config),
                    sub = stringResource(R.string.export_config_hint)
                )
                Item(
                    onClick = onImportSettings,
                    text = stringResource(R.string.import_config),
                    sub = stringResource(R.string.import_config_hint)
                )
            }

            SaltSettingsSection(title = stringResource(R.string.section_other)) {
                Item(
                    onClick = { navigator.navigate(BatchTaskListDestination()) },
                    text = stringResource(R.string.batch_task_list_title)
                )
                Item(
                    onClick = { navigator.navigate(AppLogsDestination()) },
                    text = stringResource(R.string.app_log_title),
                    sub = stringResource(R.string.app_log_summary)
                )
                if (BuildConfig.DEBUG) {
                    Item(
                        onClick = { navigator.navigate(QuickjsTestDestination()) },
                        text = stringResource(R.string.quickjs_test_title),
                        sub = stringResource(R.string.quickjs_test_description_summary)
                    )
                }
                Item(
                    onClick = { showClearCacheDialog = true },
                    text = stringResource(R.string.clear_cache),
                    tag = stringResource(
                        R.string.cache_size_label,
                        Formatter.formatFileSize(context, settingsUiState.totalCacheSize)
                    )
                )
                if (BuildConfig.DEBUG) {
                    Item(
                        onClick = {
                            scope.launch {
                                val message = if (settingsViewModel.clearSongs()) {
                                    "已清空数据库"
                                } else {
                                    "清空数据库失败"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        text = stringResource(R.string.clear_songs)
                    )
                }
                Item(
                    onClick = { navigator.navigate(AboutDestination()) },
                    text = stringResource(R.string.about_title)
                )
            }

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeMainCompat))
        }
    }
}

@Composable
private fun SaltSettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    ItemOuterTitle(text = title)
    RoundedColumn {
        content()
    }
}

@OptIn(UnstableSaltUiApi::class)
@Composable
private fun <T> DropdownItem(
    text: String,
    selected: T,
    options: List<T>,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    sub: String? = null
) {
    SaltDropdownItem(
        text = text,
        value = label(selected),
        sub = sub
    ) { popupState ->
        options.forEach { option ->
            PopupMenuItem(
                onClick = {
                    onSelected(option)
                    popupState.dismiss()
                },
                selected = option == selected,
                text = label(option)
            )
        }
    }
}
