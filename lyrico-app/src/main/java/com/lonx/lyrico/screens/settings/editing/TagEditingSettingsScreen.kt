package com.lonx.lyrico.screens.settings.editing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.ReplayGainPeakMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.visibleLyricLineTracks
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.screens.settings.settingsViewModelWithEvents
import com.lonx.lyrico.ui.components.base.ActionBottomSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.lyrics.LyricLineOrderBottomSheetContent
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.EditFieldSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.LyricsCleanupRulesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
@Destination<RootGraph>(route = "settings_editing")
fun TagEditingSettingsScreen(navigator: DestinationsNavigator) {
    val settingsViewModel = settingsViewModelWithEvents()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lyricFormat = settingsUiState.lyricFormat
    val lyricFormatItems = LyricFormat.entries.map { stringResource(it.labelRes) }
    val selectedLyricFormatIndex = LyricFormat.entries.indexOf(lyricFormat).coerceAtLeast(0)
    val romaEnabled = settingsUiState.romaEnabled
    val translationEnabled = settingsUiState.translationEnabled
    val onlyTranslationIfAvailable = settingsUiState.onlyTranslationIfAvailable
    val removeEmptyLines = settingsUiState.removeEmptyLines
    val conversionMode = settingsUiState.conversionMode
    val conversionModeItems = ConversionMode.entries.map { stringResource(it.labelRes) }
    val selectedConversionModeIndex = ConversionMode.entries.indexOf(conversionMode).coerceAtLeast(0)
    var showLyricLineOrderSheet by remember { mutableStateOf(false) }
    var showRgTargetDialog by remember { mutableStateOf(false) }
    val visibleTracks = visibleLyricLineTracks(romaEnabled, translationEnabled, onlyTranslationIfAvailable)
    val lineOrderSummary = settingsUiState.lyricLineOrder.filter { it in visibleTracks }
        .joinToString(" / ") { context.getString(it.labelRes) }
    val artistSeparator = settingsUiState.separator
    val artistSeparators = remember {
        listOf(
            ArtistSeparator.ENUMERATION_COMMA,
            ArtistSeparator.SLASH,
            ArtistSeparator.COMMA,
            ArtistSeparator.SEMICOLON,
        )
    }
    val artistSeparatorItems = artistSeparators.map { it.toText() }
    val selectedArtistSeparatorIndex = artistSeparators.indexOf(artistSeparator).coerceAtLeast(0)
    val replayGainPeakModeItems = listOf(
        stringResource(R.string.replay_gain_peak_mode_sample),
        stringResource(R.string.replay_gain_peak_mode_true),
    )
    val selectedReplayGainPeakModeIndex = ReplayGainPeakMode.entries.indexOf(settingsUiState.replayGainPeakMode).coerceAtLeast(0)

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = settingsUiState.isInitialized,
        title = stringResource(R.string.settings_category_editing),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "fields") {
            SectionTitle(text = stringResource(R.string.settings_section_fields))
            Group {
                Action(
                    icon = LucideIcons.SlidersHorizontal, tone = PreferenceIconTone.Orange,
                    title = stringResource(R.string.edit_field_settings_title),
                    summary = stringResource(R.string.edit_field_settings_summary),
                    onClick = { navigator.navigate(EditFieldSettingsDestination()) }
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
            }
        }
        item(key = "lyrics") {
            SectionTitle(text = stringResource(R.string.settings_section_lyric_content))
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
                    onClick = { showLyricLineOrderSheet = true },
                    holdDownState = showLyricLineOrderSheet,
                )
            }
        }
        item(key = "processing") {
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
                Action(
                    icon = LucideIcons.FilterLyrics, tone = PreferenceIconTone.Pink,
                    title = stringResource(R.string.non_lyrics_cleanup_rules_title),
                    summary = stringResource(R.string.non_lyrics_cleanup_rules_summary),
                    onClick = { navigator.navigate(LyricsCleanupRulesDestination()) }
                )
                Toggle(
                    icon = LucideIcons.RemoveLines, tone = PreferenceIconTone.Lyrics,
                    title = stringResource(R.string.remove_empty_lines),
                    summary = stringResource(R.string.remove_empty_lines_hint),
                    checked = removeEmptyLines,
                    onCheckedChange = { settingsViewModel.setRemoveEmptyLines(it) }
                )
            }
        }
        item(key = "replay_gain") {
            SectionTitle(text = stringResource(R.string.settings_section_loudness))
            Group {
                Action(
                    icon = LucideIcons.Volume2, tone = PreferenceIconTone.Teal,
                    title = stringResource(R.string.settings_replay_gain_target_loudness),
                    summary = stringResource(R.string.settings_replay_gain_target_loudness_summary),
                    value = formatLoudnessValue(settingsUiState.replayGainTargetLoudness),
                    onClick = { showRgTargetDialog = true },
                    holdDownState = showRgTargetDialog
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
    }
    ActionBottomSheet(
        show = showLyricLineOrderSheet,
        title = stringResource(R.string.lyric_line_order),
        onDismissRequest = { showLyricLineOrderSheet = false },
        scrollContent = false,
        content = {
            LyricLineOrderBottomSheetContent(
                lineOrder = settingsUiState.lyricLineOrder,
                visibleTracks = visibleTracks,
                onLineOrderChange = settingsViewModel::setLyricLineOrder,
            )
        },
    )
    ReplayGainTargetLoudnessSheet(
        show = showRgTargetDialog,
        currentLoudness = settingsUiState.replayGainTargetLoudness,
        onDismiss = { showRgTargetDialog = false },
        onSave = settingsViewModel::setReplayGainTargetLoudness,
    )
}
