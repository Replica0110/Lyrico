package com.lonx.lyrico.data.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class BatchMatchConfig(
    val targetModes: Map<PluginMetadataFieldTarget, PluginMetadataWriteMode>,
    val concurrency: Int = 3,
    val preferFileName: Boolean = false
) : Parcelable

data class BatchMatchTargetGroup(
    @field:StringRes val titleRes: Int,
    val targets: List<PluginMetadataFieldTarget>
)

object BatchMatchConfigDefaults {
    val BATCH_MATCH_TARGETS = listOf(
        PluginMetadataFieldTarget.TITLE,
        PluginMetadataFieldTarget.ARTIST,
        PluginMetadataFieldTarget.ALBUM,
        PluginMetadataFieldTarget.ALBUM_ARTIST,
        PluginMetadataFieldTarget.GENRE,
        PluginMetadataFieldTarget.DATE,
        PluginMetadataFieldTarget.TRACK_NUMBER,
        PluginMetadataFieldTarget.DISC_NUMBER,
        PluginMetadataFieldTarget.COMPOSER,
        PluginMetadataFieldTarget.LYRICIST,
        PluginMetadataFieldTarget.COMMENT,
        PluginMetadataFieldTarget.LYRICS,
        PluginMetadataFieldTarget.COVER,
        PluginMetadataFieldTarget.LANGUAGE,
        PluginMetadataFieldTarget.COPYRIGHT,
        PluginMetadataFieldTarget.RATING,
        PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN,
        PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK,
        PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN,
        PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK,
        PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    )

    val DEFAULT_ENABLED_TARGETS = setOf(
        PluginMetadataFieldTarget.TITLE,
        PluginMetadataFieldTarget.ARTIST,
        PluginMetadataFieldTarget.ALBUM,
        PluginMetadataFieldTarget.GENRE,
        PluginMetadataFieldTarget.DATE,
        PluginMetadataFieldTarget.TRACK_NUMBER,
        PluginMetadataFieldTarget.LYRICS,
        PluginMetadataFieldTarget.COVER
    )

    val DEFAULT_CONFIG = BatchMatchConfig(
        targetModes = BATCH_MATCH_TARGETS.associateWith { target ->
            if (target in DEFAULT_ENABLED_TARGETS) {
                PluginMetadataWriteMode.SUPPLEMENT
            } else {
                PluginMetadataWriteMode.DISABLED
            }
        },
        concurrency = 3,
        preferFileName = false
    )

    val TARGET_GROUPS = listOf(
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_basic_info,
            targets = listOf(
                PluginMetadataFieldTarget.TITLE,
                PluginMetadataFieldTarget.ARTIST,
                PluginMetadataFieldTarget.ALBUM,
                PluginMetadataFieldTarget.ALBUM_ARTIST,
                PluginMetadataFieldTarget.GENRE,
                PluginMetadataFieldTarget.DATE,
                PluginMetadataFieldTarget.TRACK_NUMBER,
                PluginMetadataFieldTarget.DISC_NUMBER
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_credits,
            targets = listOf(
                PluginMetadataFieldTarget.COMPOSER,
                PluginMetadataFieldTarget.LYRICIST
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_lyrics_cover,
            targets = listOf(
                PluginMetadataFieldTarget.LYRICS,
                PluginMetadataFieldTarget.COVER
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_extra_info,
            targets = listOf(
                PluginMetadataFieldTarget.COMMENT,
                PluginMetadataFieldTarget.LANGUAGE,
                PluginMetadataFieldTarget.COPYRIGHT,
                PluginMetadataFieldTarget.RATING
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_replay_gain,
            targets = listOf(
                PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN,
                PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK,
                PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN,
                PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK,
                PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
            )
        )
    )
}