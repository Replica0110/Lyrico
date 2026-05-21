package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.lyrics.Source
import kotlinx.serialization.Serializable

@Serializable
enum class ExtraMetadataKey(
    val rawKey: String,
    @field:StringRes val labelRes: Int,
    @field:StringRes val summaryRes: Int,
    val defaultTarget: ExtraMetadataTarget
) {
    NETEASE_163_KEY(
        rawKey = "netease_163_key",
        labelRes = R.string.label_netease_163_key,
        summaryRes = R.string.label_netease_163_key_summary,
        defaultTarget = ExtraMetadataTarget.COMMENT
    ),
    REPLAY_GAIN_TRACK_GAIN(
        rawKey = "replaygain_track_gain",
        labelRes = R.string.label_replaygain_track_gain,
        summaryRes = R.string.label_replaygain_track_gain_summary,
        defaultTarget = ExtraMetadataTarget.REPLAY_GAIN_TRACK_GAIN
    ),
    REPLAY_GAIN_TRACK_PEAK(
        rawKey = "replaygain_track_peak",
        labelRes = R.string.label_replaygain_track_peak,
        summaryRes = R.string.label_replaygain_track_peak_summary,
        defaultTarget = ExtraMetadataTarget.REPLAY_GAIN_TRACK_PEAK
    ),
    REPLAY_GAIN_REFERENCE_LOUDNESS(
        rawKey = "replaygain_reference_loudness",
        labelRes = R.string.label_replaygain_reference_loudness,
        summaryRes = R.string.label_replaygain_reference_loudness_summary,
        defaultTarget = ExtraMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    );

    companion object {
        fun fromPersistedKey(key: String): ExtraMetadataKey? {
            return entries.firstOrNull { it.rawKey == key || it.name == key }
        }

        fun normalize(key: String): String {
            return fromPersistedKey(key)?.rawKey ?: key
        }
    }
}

@Serializable
enum class ExtraMetadataTarget(
    @field:StringRes val labelRes: Int
) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM(R.string.label_album),
    ALBUM_ARTIST(R.string.label_album_artist),
    GENRE(R.string.label_genre),
    DATE(R.string.label_date),
    TRACK_NUMBER(R.string.label_track_number),
    DISC_NUMBER(R.string.label_disc_number),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    SUBTITLE(R.string.label_subtitle),
    COMMENT(R.string.label_comment),
    REPLAY_GAIN_TRACK_GAIN(R.string.label_replaygain_track_gain),
    REPLAY_GAIN_TRACK_PEAK(R.string.label_replaygain_track_peak),
    REPLAY_GAIN_REFERENCE_LOUDNESS(R.string.label_replaygain_reference_loudness)
}

@Serializable
enum class ExtraWriteMode(
    @field:StringRes val labelRes: Int
) {
    DISABLED(R.string.extra_write_mode_disabled),
    SUPPLEMENT(R.string.extra_write_mode_supplement),
    OVERWRITE(R.string.extra_write_mode_overwrite)
}

@Serializable
data class ExtraMetadataWriteRule(
    val key: String,
    val source: Source,
    val target: ExtraMetadataTarget = ExtraMetadataKey.fromPersistedKey(key)?.defaultTarget
        ?: ExtraMetadataTarget.COMMENT,
    val mode: ExtraWriteMode = ExtraWriteMode.DISABLED
) {
    val normalizedKey: String
        get() = ExtraMetadataKey.normalize(key)
}

