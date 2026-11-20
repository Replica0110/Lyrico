package com.lonx.lyrico.ui.screens.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.lyrics.LyricsOperation
import com.lonx.lyrico.ui.components.base.ItemMenuAction
import com.lonx.lyrico.ui.components.editor.EditorSheet
import com.lonx.lyrico.ui.components.icons.LucideIcons
import com.lonx.lyrico.ui.components.icons.PreferenceIconTone
import com.lonx.lyrico.ui.components.lyrics.LyricsOrganizationMenu
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.ui.theme.LocalUiEngine
import com.moriafly.salt.ui.ItemSwitcher
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun LyricsActionSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    hasLyricsSearchSources: Boolean,
    onSearchLyrics: () -> Unit,
    onImportLyrics: () -> Unit,
    hasLyrics: Boolean,
    onExportLyrics: () -> Unit,
    onConvertSimplifiedToTraditional: () -> Unit,
    onConvertTraditionalToSimplified: () -> Unit,
    onAdjustOffset: () -> Unit,
    onConvertFormat: () -> Unit,
    onOrganizeLyrics: (LyricsOperation) -> Unit,
    onViewPlainLyrics: () -> Unit,
    limitLyricsInputLines: Boolean,
    onLimitLyricsInputLinesChange: (Boolean) -> Unit,
) {
    val salt = LocalUiEngine.current == UiEngine.SALT

    EditorSheet(
        show = show,
        enableNestedScroll = false,
        title = stringResource(R.string.action_lyrics_options),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            EditorMenuGroup {
                if (hasLyricsSearchSources) {
                    ItemMenuAction(
                        icon = LucideIcons.Search,
                        tone = PreferenceIconTone.Appearance,
                        title = stringResource(R.string.action_search_lyrics),
                        onClick = onSearchLyrics
                    )
                }
                ItemMenuAction(
                    icon = LucideIcons.FileDown,
                    tone = PreferenceIconTone.Tools,
                    title = stringResource(R.string.action_import_lyrics),
                    onClick = onImportLyrics
                )
                if (hasLyrics) {
                    ItemMenuAction(
                        icon = LucideIcons.FileUp,
                        tone = PreferenceIconTone.Library,
                        title = stringResource(R.string.action_export_lyrics),
                        onClick = onExportLyrics
                    )
                    ItemMenuAction(
                        icon = LucideIcons.Languages,
                        tone = PreferenceIconTone.Lyrics,
                        title = stringResource(R.string.chinese_conversion_mode_simplified_to_traditional),
                        onClick = onConvertSimplifiedToTraditional
                    )
                    ItemMenuAction(
                        icon = LucideIcons.Languages,
                        tone = PreferenceIconTone.Pink,
                        title = stringResource(R.string.chinese_conversion_mode_traditional_to_simplified),
                        onClick = onConvertTraditionalToSimplified
                    )
                    ItemMenuAction(
                        icon = LucideIcons.Timing,
                        tone = PreferenceIconTone.Teal,
                        title = stringResource(R.string.offset_adjust_hint),
                        onClick = onAdjustOffset
                    )
                    ItemMenuAction(
                        icon = LucideIcons.Convert,
                        tone = PreferenceIconTone.Orange,
                        title = stringResource(R.string.lyrics_convert),
                        onClick = onConvertFormat
                    )
                    LyricsOrganizationMenu(onSelect = onOrganizeLyrics) { open ->
                        ItemMenuAction(
                            icon = LucideIcons.Sort,
                            tone = PreferenceIconTone.Appearance,
                            title = stringResource(R.string.lyrics_organize),
                            onClick = open
                        )
                    }
                    ItemMenuAction(
                        icon = LucideIcons.Captions,
                        tone = PreferenceIconTone.Tools,
                        title = stringResource(R.string.action_view_plain_lyrics),
                        onClick = onViewPlainLyrics
                    )
                }
                if (salt) {
                    ItemSwitcher(
                        iconPainter = rememberVectorPainter(LucideIcons.Captions),
                        iconColor = PreferenceIconTone.Appearance.color(),
                        text = stringResource(R.string.limit_lyrics_input_lines),
                        sub = stringResource(R.string.limit_lyrics_input_lines_hint),
                        state = limitLyricsInputLines,
                        onChange = onLimitLyricsInputLinesChange
                    )
                } else {
                    SwitchPreference(
                        startAction = {
                            com.lonx.lyrico.ui.components.icons.PreferenceIcon(
                                LucideIcons.Captions,
                                PreferenceIconTone.Appearance
                            )
                        },
                        title = stringResource(R.string.limit_lyrics_input_lines),
                        summary = stringResource(R.string.limit_lyrics_input_lines_hint),
                        checked = limitLyricsInputLines,
                        onCheckedChange = onLimitLyricsInputLinesChange
                    )
                }
            }
        }
    }
}
