package com.lonx.lyrico.screens.settings.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.SearchSourceTabStyle
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.screens.settings.settingsViewModelWithEvents
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PluginManagerDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.math.roundToInt

@Composable
@Destination<RootGraph>(route = "settings_search")
fun SearchSettingsScreen(navigator: DestinationsNavigator) {
    val settingsViewModel = settingsViewModelWithEvents()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val searchSourceTabStyle = settingsUiState.searchSourceTabStyle
    val searchSourceTabStyleItems = SearchSourceTabStyle.entries.map { stringResource(it.labelRes) }
    val selectedSearchSourceTabStyleIndex = SearchSourceTabStyle.entries.indexOf(searchSourceTabStyle).coerceAtLeast(0)
    val showAllSearchResultFields = settingsUiState.showAllSearchResultFields
    val searchPageSize = settingsUiState.searchPageSize
    val minSearchSize = 1
    val maxSearchSize = 20
    val tempSearchPageSize = remember(searchPageSize) { mutableIntStateOf(searchPageSize) }

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = settingsUiState.isInitialized,
        title = stringResource(R.string.settings_category_search),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "sources") {
            SectionTitle(text = stringResource(R.string.settings_section_sources))
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
            }
        }
        item(key = "results") {
            SectionTitle(text = stringResource(R.string.section_search))
            Group {
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
                Toggle(
                    icon = LucideIcons.TextSearch, tone = PreferenceIconTone.Pink,
                    title = stringResource(R.string.show_all_search_result_fields),
                    summary = stringResource(R.string.show_all_search_result_fields_summary),
                    checked = showAllSearchResultFields,
                    onCheckedChange = settingsViewModel::setShowAllSearchResultFields
                )
            }
        }
    }
}
