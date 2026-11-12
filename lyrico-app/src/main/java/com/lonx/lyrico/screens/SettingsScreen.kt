package com.lonx.lyrico.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutDestination
import com.ramcosta.composedestinations.generated.destinations.BatchTaskListDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsAppearanceDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsEditingDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsLibraryDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsSearchDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDataDestination
import com.ramcosta.composedestinations.generated.destinations.SettingsDiagnosticsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Composable
@Destination<RootGraph>(route = "settings")
fun SettingsScreen(navigator: DestinationsNavigator) {
    val uriHandler = LocalUriHandler.current
    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = true,
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "preferences") {
            SectionTitle(text = stringResource(R.string.settings_section_preferences))
            Group {
                Action(
                    title = stringResource(R.string.settings_category_appearance),
                    icon = LucideIcons.SwatchBook, tone = PreferenceIconTone.Appearance,
                    onClick = { navigator.navigate(SettingsAppearanceDestination()) },
                )
                Action(
                    title = stringResource(R.string.settings_category_library),
                    icon = LucideIcons.LibraryBig, tone = PreferenceIconTone.Library,
                    onClick = { navigator.navigate(SettingsLibraryDestination()) },
                )
                Action(
                    title = stringResource(R.string.settings_category_search),
                    icon = LucideIcons.Search, tone = PreferenceIconTone.Tools,
                    onClick = { navigator.navigate(SettingsSearchDestination()) },
                )
                Action(
                    title = stringResource(R.string.settings_category_editing),
                    icon = LucideIcons.Tags, tone = PreferenceIconTone.Lyrics,
                    onClick = { navigator.navigate(SettingsEditingDestination()) },
                )
                Action(
                    title = stringResource(R.string.settings_category_data),
                    icon = LucideIcons.Database, tone = PreferenceIconTone.Teal,
                    onClick = { navigator.navigate(SettingsDataDestination()) },
                )
            }
        }
        item(key = "tools") {
            SectionTitle(text = stringResource(R.string.settings_section_tools))
            Group {
                Action(
                    icon = LucideIcons.ClipboardList, tone = PreferenceIconTone.Library,
                    title = stringResource(R.string.batch_task_list_title),
                    onClick = { navigator.navigate(BatchTaskListDestination()) }
                )
                Action(
                    title = stringResource(R.string.settings_category_diagnostics),
                    icon = LucideIcons.LifeBuoy, tone = PreferenceIconTone.Orange,
                    onClick = { navigator.navigate(SettingsDiagnosticsDestination()) },
                )
            }
        }
        item(key = "help") {
            SectionTitle(text = stringResource(R.string.settings_section_help))
            Group {
                Action(
                    title = stringResource(R.string.user_guide),
                    icon = LucideIcons.NotebookPen, tone = PreferenceIconTone.Library,
                    onClick = { uriHandler.openUri("https://replica0110.github.io/Lyrico/guide/") },
                )
                Action(
                    title = stringResource(R.string.about_title),
                    icon = LucideIcons.Info, tone = PreferenceIconTone.Orange,
                    onClick = { navigator.navigate(AboutDestination()) },
                )
            }
        }
    }
}
