package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class BatchTaskType(
    @field:StringRes val labelRes: Int
) {
    MATCH_METADATA(R.string.batch_task_match_tags),
    EDIT_TAGS(R.string.batch_task_edit_tags),
    RENAME_FILES(R.string.batch_task_rename_files),
    CONVERT_LYRICS_FORMAT(R.string.batch_task_convert_lyrics_format),
    SCAN_REPLAY_GAIN(R.string.batch_task_scan_replay_gain),
    EXPORT_LYRICS(R.string.batch_task_export_lyrics),
    EXPORT_COVER(R.string.batch_task_export_cover)
}
