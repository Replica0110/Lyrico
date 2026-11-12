package com.lonx.lyrico.screens.settings.appearance

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.AppLanguage
import com.lonx.lyrico.data.model.FloatingBarEffect
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.screens.settings.PaletteOption
import com.lonx.lyrico.screens.settings.SettingsPage
import com.lonx.lyrico.screens.settings.settingsViewModelWithEvents
import com.lonx.lyrico.ui.components.getSystemWallpaperColor
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.theme.KeyColors
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
@Destination<RootGraph>(route = "settings_appearance")
fun AppearanceSettingsScreen(navigator: DestinationsNavigator) {
    val settingsViewModel = settingsViewModelWithEvents()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val uiEngine = LocalUiEngine.current
    val settingsRepository: SettingsRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode = settingsUiState.themeMode
    val themeModeItems = ThemeMode.entries.map { stringResource(it.labelRes) }
    val selectedThemeModeIndex = ThemeMode.entries.indexOf(themeMode).coerceAtLeast(0)
    val monetEnable = settingsUiState.monetEnable
    val currentKeyColor = settingsUiState.keyColor
    val barBlurEnabled = settingsUiState.barBlurEnabled
    val floatingBottomBarEnabled = settingsUiState.floatingBottomBarEnabled
    val floatingBarEffect = settingsUiState.floatingBarEffect
    val floatingBarEffectItems = FloatingBarEffect.entries.map { stringResource(it.labelRes) }
    val monetVisibilityState = remember(settingsUiState.isInitialized) { MutableTransitionState(monetEnable) }
    LaunchedEffect(monetEnable) { monetVisibilityState.targetState = monetEnable }

    SettingsPage(
        engine = LocalUiEngine.current,
        initialized = settingsUiState.isInitialized,
        title = stringResource(R.string.settings_category_appearance),
        onBack = { navigator.popBackStack() },
    ) {
        item(key = "general") {
            SectionTitle(text = stringResource(R.string.settings_section_general))
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
            }
        }
        item(key = "theme") {
            SectionTitle(text = stringResource(R.string.settings_section_theme))
            Group {
                Choice(
                    icon = LucideIcons.Moon, tone = PreferenceIconTone.Library,
                    title = stringResource(R.string.theme_mode),
                    items = themeModeItems,
                    selectedIndex = selectedThemeModeIndex,
                    onSelectedIndexChange = { index ->
                        settingsViewModel.setThemeMode(ThemeMode.entries[index])
                    }
                )

                Toggle(
                    icon = LucideIcons.Palette, tone = PreferenceIconTone.Orange,
                    title = stringResource(R.string.monet),
                    checked = monetEnable,
                    onCheckedChange = settingsViewModel::setMonetEnable
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
        if (uiEngine == UiEngine.MIUIX) {
            item(key = "interface") {
                SectionTitle(text = stringResource(R.string.settings_section_interface))
                Group {
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
            }
        }
    }
}
