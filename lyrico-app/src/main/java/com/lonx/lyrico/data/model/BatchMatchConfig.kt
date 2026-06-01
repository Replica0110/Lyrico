package com.lonx.lyrico.data.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class BatchMatchConfig(
    val targetModes: Map<MetadataFieldTarget, MetadataWriteMode>,
    val concurrency: Int = 3,
    val preferFileName: Boolean = false
) : Parcelable

data class BatchMatchTargetGroup(
    @field:StringRes val titleRes: Int,
    val targets: List<MetadataFieldTarget>
)

object BatchMatchConfigDefaults {
    val BATCH_MATCH_TARGETS = listOf(
        MetadataFieldTarget.TITLE,
        MetadataFieldTarget.ARTIST,
        MetadataFieldTarget.ALBUM,
        MetadataFieldTarget.ALBUM_ARTIST,
        MetadataFieldTarget.GENRE,
        MetadataFieldTarget.DATE,
        MetadataFieldTarget.TRACK_NUMBER,
        MetadataFieldTarget.DISC_NUMBER,
        MetadataFieldTarget.COMPOSER,
        MetadataFieldTarget.LYRICIST,
        MetadataFieldTarget.COMMENT,
        MetadataFieldTarget.LYRICS,
        MetadataFieldTarget.COVER,
        MetadataFieldTarget.LANGUAGE,
        MetadataFieldTarget.COPYRIGHT,
        MetadataFieldTarget.RATING,
        MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN,
        MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK,
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN,
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK,
        MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    )

    val DEFAULT_ENABLED_TARGETS = setOf(
        MetadataFieldTarget.TITLE,
        MetadataFieldTarget.ARTIST,
        MetadataFieldTarget.ALBUM,
        MetadataFieldTarget.GENRE,
        MetadataFieldTarget.DATE,
        MetadataFieldTarget.TRACK_NUMBER,
        MetadataFieldTarget.LYRICS,
        MetadataFieldTarget.COVER
    )

    val DEFAULT_CONFIG = BatchMatchConfig(
        targetModes = BATCH_MATCH_TARGETS.associateWith { target ->
            if (target in DEFAULT_ENABLED_TARGETS) {
                MetadataWriteMode.SUPPLEMENT
            } else {
                MetadataWriteMode.DISABLED
            }
        },
        concurrency = 3,
        preferFileName = false
    )

    val TARGET_GROUPS = listOf(
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_basic_info,
            targets = listOf(
                MetadataFieldTarget.TITLE,
                MetadataFieldTarget.ARTIST,
                MetadataFieldTarget.ALBUM,
                MetadataFieldTarget.ALBUM_ARTIST,
                MetadataFieldTarget.GENRE,
                MetadataFieldTarget.DATE,
                MetadataFieldTarget.TRACK_NUMBER,
                MetadataFieldTarget.DISC_NUMBER
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_credits,
            targets = listOf(
                MetadataFieldTarget.COMPOSER,
                MetadataFieldTarget.LYRICIST
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_lyrics_cover,
            targets = listOf(
                MetadataFieldTarget.LYRICS,
                MetadataFieldTarget.COVER
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_extra_info,
            targets = listOf(
                MetadataFieldTarget.COMMENT,
                MetadataFieldTarget.LANGUAGE,
                MetadataFieldTarget.COPYRIGHT,
                MetadataFieldTarget.RATING
            )
        ),
        BatchMatchTargetGroup(
            titleRes = R.string.field_group_replay_gain,
            targets = listOf(
                MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN,
                MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK,
                MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN,
                MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK,
                MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
            )
        )
    )
}