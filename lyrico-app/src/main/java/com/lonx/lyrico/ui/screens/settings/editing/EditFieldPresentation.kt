package com.lonx.lyrico.ui.screens.settings.editing

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.lonx.lyrico.R
import com.lonx.lyrico.data.editfield.EditFieldBlock
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.ui.components.icons.LucideIcons

/**
 * 字段在「编辑字段」设置里的名称与图标。
 *
 * 内置字段用资源文案，自定义标签用键名；组件（目前只有回放增益）用分组名，因为成员字段各自有名称。
 */

@Composable
internal fun EditFieldBlock.displayTitle(): String = stringResource(
    if (isComposite) R.string.group_replay_gain else fields.first().titleRes
)

@Composable
internal fun EditFieldDefinition.displayTitle(): String =
    if (custom) EditFieldRegistry.customTagKeyOf(code).orEmpty() else stringResource(titleRes)

internal fun EditFieldDefinition.settingsIcon(): ImageVector = when (code) {
    "picture" -> LucideIcons.Image
    "rating" -> LucideIcons.Star
    "title" -> LucideIcons.Music
    "artist", "album_artist" -> LucideIcons.Users
    "album", "disc_number" -> LucideIcons.Disc
    "date" -> LucideIcons.Calendar
    "language" -> LucideIcons.Languages
    "genre" -> LucideIcons.Tags
    "track_number" -> LucideIcons.ListMusic
    "lyricist" -> LucideIcons.NotebookPen
    "composer" -> LucideIcons.Music
    "copyright" -> LucideIcons.Copyright
    "comment" -> LucideIcons.MessageSquare
    "track_gain", "album_gain", "reference_loudness" -> LucideIcons.Volume2
    "track_peak", "album_peak" -> LucideIcons.Activity
    "lyrics" -> LucideIcons.Captions
    "lyrics_offset" -> LucideIcons.ArrowDownUp
    else -> LucideIcons.Tags
}
