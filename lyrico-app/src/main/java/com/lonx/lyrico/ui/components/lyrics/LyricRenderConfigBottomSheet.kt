@file:OptIn(com.moriafly.salt.ui.UnstableSaltUiApi::class)

package com.lonx.lyrico.ui.components.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.UiEngine
import com.lonx.lyrico.ui.components.base.SaltBottomSheet
import com.lonx.lyrico.ui.components.base.SaltDropdownRow
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.icons.PreferenceIcon
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.moriafly.salt.ui.ItemSwitcher
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun LyricRenderConfigBottomSheet(
    show: Boolean,
    config: LyricRenderConfig?,
    onDismissRequest: () -> Unit,
    onLyricFormatChange: (LyricFormat) -> Unit,
    onRomaEnabledChange: (Boolean) -> Unit,
    onTranslationEnabledChange: (Boolean) -> Unit,
    onOnlyTranslationIfAvailableChange: (Boolean) -> Unit,
    onRemoveEmptyLinesChange: (Boolean) -> Unit,
    onConversionModeChange: (ConversionMode) -> Unit
) {
    val salt = LocalUiEngine.current == UiEngine.SALT
    if (salt) {
        SaltBottomSheet(
            show = show,
            onDismissRequest = onDismissRequest,
            onDismissFinished = {},
            // 配置行自身不滚动，由弹层统一滚动。
            scrollContent = true,
        ) {
            config?.let { lyricConfig ->
                val lyricFormatItems = LyricFormat.entries.map {
                    stringResource(it.labelRes)
                }
                val selectedLyricFormatIndex = LyricFormat.entries
                    .indexOf(lyricConfig.format)
                    .coerceAtLeast(0)
                val conversionModeItems = ConversionMode.entries.map {
                    stringResource(it.labelRes)
                }
                val selectedConversionModeIndex = ConversionMode.entries
                    .indexOf(lyricConfig.conversionMode)
                    .coerceAtLeast(0)

                com.moriafly.salt.ui.RoundedColumn {
                SaltDropdownRow(
                    title = stringResource(R.string.lyric_mode),
                    labels = lyricFormatItems,
                    selectedIndex = selectedLyricFormatIndex,
                ) { index ->
                    onLyricFormatChange(LyricFormat.entries[index])
                }
                ItemSwitcher(
                    text = stringResource(R.string.roma),
                    sub = stringResource(R.string.roma_hint),
                    state = lyricConfig.showRomanization,
                    onChange = onRomaEnabledChange,
                )
                ItemSwitcher(
                    text = stringResource(R.string.translation),
                    sub = stringResource(R.string.translation_hint),
                    state = lyricConfig.showTranslation,
                    onChange = onTranslationEnabledChange,
                )
                AnimatedVisibility(visible = lyricConfig.showTranslation) {
                    ItemSwitcher(
                        text = stringResource(R.string.only_translation_if_available),
                        sub = stringResource(R.string.only_translation_if_available_hint),
                        state = lyricConfig.onlyTranslationIfAvailable,
                        enabled = lyricConfig.showTranslation,
                        onChange = onOnlyTranslationIfAvailableChange,
                    )
                }
                ItemSwitcher(
                    text = stringResource(R.string.remove_empty_lines),
                    sub = stringResource(R.string.remove_empty_lines_hint),
                    state = lyricConfig.removeEmptyLines,
                    onChange = onRemoveEmptyLinesChange,
                )
                SaltDropdownRow(
                    title = stringResource(R.string.conversion_mode),
                    labels = conversionModeItems,
                    selectedIndex = selectedConversionModeIndex,
                ) { index ->
                    onConversionModeChange(ConversionMode.entries[index])
                }
                }
            }
        }
        return
    }
    WindowBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 32.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.secondaryContainer
                )
            ) {
                config?.let { lyricConfig ->
                    val lyricFormatItems = LyricFormat.entries.map {
                        stringResource(it.labelRes)
                    }
                    val selectedLyricFormatIndex = LyricFormat.entries
                        .indexOf(lyricConfig.format)
                        .coerceAtLeast(0)
                    val conversionModeItems = ConversionMode.entries.map {
                        stringResource(it.labelRes)
                    }
                    val selectedConversionModeIndex = ConversionMode.entries
                        .indexOf(lyricConfig.conversionMode)
                        .coerceAtLeast(0)

                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.FileMusic, PreferenceIconTone.Lyrics) },
                        title = stringResource(R.string.lyric_mode),
                        items = lyricFormatItems,
                        selectedIndex = selectedLyricFormatIndex,
                        onSelectedIndexChange = { index ->
                            onLyricFormatChange(LyricFormat.entries[index])
                        }
                    )
                    SwitchPreference(
                        startAction = { PreferenceIcon(LucideIcons.Type, PreferenceIconTone.Lyrics) },
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = lyricConfig.showRomanization,
                        onCheckedChange = onRomaEnabledChange
                    )
                    SwitchPreference(
                        startAction = { PreferenceIcon(LucideIcons.Languages, PreferenceIconTone.Lyrics) },
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = lyricConfig.showTranslation,
                        onCheckedChange = onTranslationEnabledChange
                    )
                    AnimatedVisibility(visible = lyricConfig.showTranslation) {
                        SwitchPreference(
                            startAction = { PreferenceIcon(LucideIcons.Captions, PreferenceIconTone.Lyrics, enabled = lyricConfig.showTranslation) },
                            title = stringResource(R.string.only_translation_if_available),
                            summary = stringResource(
                                R.string.only_translation_if_available_hint
                            ),
                            enabled = lyricConfig.showTranslation,
                            checked = lyricConfig.onlyTranslationIfAvailable,
                            onCheckedChange = onOnlyTranslationIfAvailableChange
                        )
                    }
                    SwitchPreference(
                        startAction = { PreferenceIcon(LucideIcons.RemoveLines, PreferenceIconTone.Lyrics) },
                        title = stringResource(R.string.remove_empty_lines),
                        summary = stringResource(R.string.remove_empty_lines_hint),
                        checked = lyricConfig.removeEmptyLines,
                        onCheckedChange = onRemoveEmptyLinesChange
                    )
                    WindowDropdownPreference(
                        startAction = { PreferenceIcon(LucideIcons.ALargeSmall, PreferenceIconTone.Lyrics) },
                        title = stringResource(R.string.conversion_mode),
                        items = conversionModeItems,
                        selectedIndex = selectedConversionModeIndex,
                        onSelectedIndexChange = { index ->
                            onConversionModeChange(ConversionMode.entries[index])
                        }
                    )
                }
            }


        }
    }
}
