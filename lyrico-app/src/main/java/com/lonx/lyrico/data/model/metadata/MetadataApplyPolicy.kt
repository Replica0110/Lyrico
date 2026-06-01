package com.lonx.lyrico.data.model.metadata

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
enum class MetadataWriteMode(
    @field:StringRes val labelRes: Int
) {
    DISABLED(R.string.extra_write_mode_disabled),
    SUPPLEMENT(R.string.extra_write_mode_supplement),
    OVERWRITE(R.string.extra_write_mode_overwrite)
}

@Parcelize
@Serializable
enum class MetadataFieldTarget(
    @field:StringRes val labelRes: Int
) : Parcelable {
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

data class MetadataApplyPolicy(
    val fieldModes: Map<MetadataFieldTarget, MetadataWriteMode>
) {
    fun modeOf(target: MetadataFieldTarget): MetadataWriteMode {
        return fieldModes[target] ?: MetadataWriteMode.DISABLED
    }

    companion object {
        fun overwriteAvailableFields(fields: Map<String, String>): MetadataApplyPolicy {
            return MetadataApplyPolicy(
                fields.keys
                    .mapNotNull { key -> StandardPluginField.fromKey(key)?.target }
                    .distinct()
                    .associateWith { MetadataWriteMode.OVERWRITE }
            )
        }
    }
}

enum class StandardPluginField(
    val key: String,
    val target: MetadataFieldTarget
) {
    TITLE("title", MetadataFieldTarget.TITLE),
    ARTIST("artist", MetadataFieldTarget.ARTIST),
    ALBUM("album", MetadataFieldTarget.ALBUM),
    ALBUM_ARTIST("album_artist", MetadataFieldTarget.ALBUM_ARTIST),
    GENRE("genre", MetadataFieldTarget.GENRE),
    DATE("date", MetadataFieldTarget.DATE),
    TRACK_NUMBER("track_number", MetadataFieldTarget.TRACK_NUMBER),
    DISC_NUMBER("disc_number", MetadataFieldTarget.DISC_NUMBER),
    COMPOSER("composer", MetadataFieldTarget.COMPOSER),
    LYRICIST("lyricist", MetadataFieldTarget.LYRICIST),
    COMMENT("comment", MetadataFieldTarget.COMMENT),
    LYRICS("lyrics", MetadataFieldTarget.LYRICS),
    COVER_URL("cover_url", MetadataFieldTarget.COVER),
    LANGUAGE("language", MetadataFieldTarget.LANGUAGE),
    COPYRIGHT("copyright", MetadataFieldTarget.COPYRIGHT),
    RATING("rating", MetadataFieldTarget.RATING),
    REPLAY_GAIN_TRACK_GAIN("replaygain_track_gain", MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN),
    REPLAY_GAIN_TRACK_PEAK("replaygain_track_peak", MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK),
    REPLAY_GAIN_ALBUM_GAIN("replaygain_album_gain", MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN),
    REPLAY_GAIN_ALBUM_PEAK("replaygain_album_peak", MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK),
    REPLAY_GAIN_REFERENCE_LOUDNESS(
        "replaygain_reference_loudness",
        MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    );

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): StandardPluginField? {
            return byKey[key]
        }
    }
}
