package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.serialization.Serializable

@Serializable
data class MetadataFieldWriteRule(
    val pluginId: String,
    val fieldKey: String,
    val target: MetadataFieldTarget = MetadataFieldTarget.COMMENT,
    val mode: MetadataWriteMode = MetadataWriteMode.DISABLED,
    val customTagKey: String? = null
) {
    val normalizedKey: String
        get() = MetadataFieldKeyAlias.normalize(fieldKey)
}

object MetadataFieldKeyAlias {
    private val aliases = mapOf(
        "NETEASE_163_KEY" to "netease_163_key",
        "REPLAY_GAIN_TRACK_GAIN" to "replaygain_track_gain",
        "REPLAY_GAIN_TRACK_PEAK" to "replaygain_track_peak",
        "REPLAY_GAIN_REFERENCE_LOUDNESS" to "replaygain_reference_loudness"
    )

    fun normalize(key: String): String {
        return aliases[key] ?: key
    }
}

@Serializable
enum class MetadataWriteMode(
    @field:StringRes val labelRes: Int
) {
    DISABLED(R.string.extra_write_mode_disabled),
    SUPPLEMENT(R.string.extra_write_mode_supplement),
    OVERWRITE(R.string.extra_write_mode_overwrite)
}

@Serializable
enum class MetadataFieldTarget(
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
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover),
    LANGUAGE(R.string.label_language),
    COPYRIGHT(R.string.label_copyright),
    RATING(R.string.label_rating),
    REPLAY_GAIN_TRACK_GAIN(R.string.label_replaygain_track_gain),
    REPLAY_GAIN_TRACK_PEAK(R.string.label_replaygain_track_peak),
    REPLAY_GAIN_ALBUM_GAIN(R.string.label_replaygain_album_gain),
    REPLAY_GAIN_ALBUM_PEAK(R.string.label_replaygain_album_peak),
    REPLAY_GAIN_REFERENCE_LOUDNESS(R.string.label_replaygain_reference_loudness),
    CUSTOM(R.string.label_custom)
}
