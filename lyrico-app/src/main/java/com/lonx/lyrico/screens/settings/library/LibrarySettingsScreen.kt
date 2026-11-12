package com.lonx.lyrico.screens.settings.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.screens.settings.settingsViewModelWithEvents
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ArtistPosterFoldersDestination
import com.ramcosta.composedestinations.generated.destinations.ArtistSplitSettingsDestination
import com.ramcosta.composedestinations.generated.destinations.FolderManagerDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
@Destination<RootGraph>(route = "settings_library")
fun LibrarySettingsScreen(navigator: DestinationsNavigator) {
    val settingsViewModel = settingsViewModelWithEvents()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val ignoreShortAudio = settingsUiState.ignoreShortAudio
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        settingsViewModel.refreshDefaultPlayerInfo()
    }

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = settingsUiState.isInitialized,
        title = stringResource(R.string.settings_category_library),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "folders") {
            SectionTitle(text = stringResource(R.string.section_scan))
            Group {
                Action(
                    icon = LucideIcons.Folder, tone = PreferenceIconTone.Tools,
                    title = stringResource(R.string.folder_manager),
                    summary = stringResource(R.string.folder_manage_hint),
                    onClick = { navigator.navigate(FolderManagerDestination()) }
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
            }
        }
        item(key = "artists") {
            SectionTitle(text = stringResource(R.string.settings_section_artists))
            Group {
                Action(
                    icon = LucideIcons.Users, tone = PreferenceIconTone.Teal,
                    title = stringResource(R.string.artist_split_settings_title),
                    summary = stringResource(R.string.artist_split_settings_summary),
                    onClick = { navigator.navigate(ArtistSplitSettingsDestination()) }
                )
                Action(
                    icon = LucideIcons.Image, tone = PreferenceIconTone.Library,
                    title = stringResource(R.string.artist_poster_folders),
                    summary = stringResource(R.string.artist_poster_folders_settings_summary),
                    onClick = { navigator.navigate(ArtistPosterFoldersDestination()) }
                )
            }
        }
        item(key = "playback") {
            SectionTitle(text = stringResource(R.string.settings_section_playback))
            Group {
                DefaultPlayerSetting(
                    selected = settingsUiState.defaultPlayerComponent,
                    selectedLabel = settingsUiState.defaultPlayerLabel,
                    onSelect = settingsViewModel::setDefaultPlayerComponent,
                )
            }
        }
    }
}
