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
import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricLineTrack
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.visibleLyricLineTracks
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
    onLineOrderChange: (List<LyricLineTrack>) -> Unit,
    onTranslationEnabledChange: (Boolean) -> Unit,
    onOnlyTranslationIfAvailableChange: (Boolean) -> Unit,
    onRemoveEmptyLinesChange: (Boolean) -> Unit,
    onConversionModeChange: (ConversionMode) -> Unit
) {
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
                        title = stringResource(R.string.lyric_mode),
                        items = lyricFormatItems,
                        selectedIndex = selectedLyricFormatIndex,
                        onSelectedIndexChange = { index ->
                            onLyricFormatChange(LyricFormat.entries[index])
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.roma),
                        summary = stringResource(R.string.roma_hint),
                        checked = lyricConfig.showRomanization,
                        onCheckedChange = onRomaEnabledChange
                    )
                    SwitchPreference(
                        title = stringResource(R.string.translation),
                        summary = stringResource(R.string.translation_hint),
                        checked = lyricConfig.showTranslation,
                        onCheckedChange = onTranslationEnabledChange
                    )
                    AnimatedVisibility(visible = lyricConfig.showTranslation) {
                        SwitchPreference(
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
                        title = stringResource(R.string.remove_empty_lines),
                        summary = stringResource(R.string.remove_empty_lines_hint),
                        checked = lyricConfig.removeEmptyLines,
                        onCheckedChange = onRemoveEmptyLinesChange
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.conversion_mode),
                        items = conversionModeItems,
                        selectedIndex = selectedConversionModeIndex,
                        onSelectedIndexChange = { index ->
                            onConversionModeChange(ConversionMode.entries[index])
                        }
                    )
                }
            }

            config?.let { lyricConfig ->
                LyricLineOrderBottomSheetContent(
                    lineOrder = lyricConfig.normalizedLineOrder,
                    visibleTracks = visibleLyricLineTracks(
                        showRomanization = lyricConfig.showRomanization,
                        showTranslation = lyricConfig.showTranslation,
                        onlyTranslationIfAvailable = lyricConfig.onlyTranslationIfAvailable
                    ),
                    onLineOrderChange = onLineOrderChange
                )
            }
        }
    }
}
