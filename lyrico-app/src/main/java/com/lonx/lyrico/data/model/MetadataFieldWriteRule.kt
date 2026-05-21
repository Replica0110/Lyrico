package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.lyrics.Source
import kotlinx.serialization.Serializable

@Serializable
data class MetadataFieldWriteRule(
    val sourceId: String,
    val fieldKey: String,
    val target: MetadataFieldTarget = MetadataFieldTarget.COMMENT,
    val mode: MetadataWriteMode = MetadataWriteMode.DISABLED,
    val customTagKey: String? = null
) {
    val normalizedKey: String
        get() = ExtraMetadataKey.normalize(fieldKey)
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
    SUBTITLE(R.string.label_subtitle),
    LYRICS(R.string.label_lyrics),
    COVER(R.string.label_cover),
    REPLAY_GAIN_TRACK_GAIN(R.string.label_replaygain_track_gain),
    REPLAY_GAIN_TRACK_PEAK(R.string.label_replaygain_track_peak),
    REPLAY_GAIN_REFERENCE_LOUDNESS(R.string.label_replaygain_reference_loudness),
    CUSTOM(R.string.label_custom)
}

fun ExtraMetadataWriteRule.toMetadataFieldWriteRule(): MetadataFieldWriteRule {
    return MetadataFieldWriteRule(
        sourceId = source.id,
        fieldKey = normalizedKey,
        target = target.toMetadataFieldTarget(),
        mode = mode.toMetadataWriteMode()
    )
}

fun MetadataFieldWriteRule.toExtraMetadataWriteRuleOrNull(): ExtraMetadataWriteRule? {
    val source = Source.fromIdOrNameOrNull(sourceId) ?: return null
    return ExtraMetadataWriteRule(
        key = normalizedKey,
        source = source,
        target = target.toExtraMetadataTargetOrNull() ?: return null,
        mode = mode.toExtraWriteMode()
    )
}

fun ExtraMetadataTarget.toMetadataFieldTarget(): MetadataFieldTarget {
    return when (this) {
        ExtraMetadataTarget.TITLE -> MetadataFieldTarget.TITLE
        ExtraMetadataTarget.ARTIST -> MetadataFieldTarget.ARTIST
        ExtraMetadataTarget.ALBUM -> MetadataFieldTarget.ALBUM
        ExtraMetadataTarget.ALBUM_ARTIST -> MetadataFieldTarget.ALBUM_ARTIST
        ExtraMetadataTarget.GENRE -> MetadataFieldTarget.GENRE
        ExtraMetadataTarget.DATE -> MetadataFieldTarget.DATE
        ExtraMetadataTarget.TRACK_NUMBER -> MetadataFieldTarget.TRACK_NUMBER
        ExtraMetadataTarget.DISC_NUMBER -> MetadataFieldTarget.DISC_NUMBER
        ExtraMetadataTarget.COMPOSER -> MetadataFieldTarget.COMPOSER
        ExtraMetadataTarget.LYRICIST -> MetadataFieldTarget.LYRICIST
        ExtraMetadataTarget.SUBTITLE -> MetadataFieldTarget.SUBTITLE
        ExtraMetadataTarget.COMMENT -> MetadataFieldTarget.COMMENT
        ExtraMetadataTarget.REPLAY_GAIN_TRACK_GAIN -> MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN
        ExtraMetadataTarget.REPLAY_GAIN_TRACK_PEAK -> MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK
        ExtraMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
    }
}

fun MetadataFieldTarget.toExtraMetadataTargetOrNull(): ExtraMetadataTarget? {
    return when (this) {
        MetadataFieldTarget.TITLE -> ExtraMetadataTarget.TITLE
        MetadataFieldTarget.ARTIST -> ExtraMetadataTarget.ARTIST
        MetadataFieldTarget.ALBUM -> ExtraMetadataTarget.ALBUM
        MetadataFieldTarget.ALBUM_ARTIST -> ExtraMetadataTarget.ALBUM_ARTIST
        MetadataFieldTarget.GENRE -> ExtraMetadataTarget.GENRE
        MetadataFieldTarget.DATE -> ExtraMetadataTarget.DATE
        MetadataFieldTarget.TRACK_NUMBER -> ExtraMetadataTarget.TRACK_NUMBER
        MetadataFieldTarget.DISC_NUMBER -> ExtraMetadataTarget.DISC_NUMBER
        MetadataFieldTarget.COMPOSER -> ExtraMetadataTarget.COMPOSER
        MetadataFieldTarget.LYRICIST -> ExtraMetadataTarget.LYRICIST
        MetadataFieldTarget.SUBTITLE -> ExtraMetadataTarget.SUBTITLE
        MetadataFieldTarget.COMMENT -> ExtraMetadataTarget.COMMENT
        MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> ExtraMetadataTarget.REPLAY_GAIN_TRACK_GAIN
        MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> ExtraMetadataTarget.REPLAY_GAIN_TRACK_PEAK
        MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> ExtraMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS
        MetadataFieldTarget.LYRICS,
        MetadataFieldTarget.COVER,
        MetadataFieldTarget.CUSTOM -> null
    }
}

fun ExtraWriteMode.toMetadataWriteMode(): MetadataWriteMode {
    return when (this) {
        ExtraWriteMode.DISABLED -> MetadataWriteMode.DISABLED
        ExtraWriteMode.SUPPLEMENT -> MetadataWriteMode.SUPPLEMENT
        ExtraWriteMode.OVERWRITE -> MetadataWriteMode.OVERWRITE
    }
}

fun MetadataWriteMode.toExtraWriteMode(): ExtraWriteMode {
    return when (this) {
        MetadataWriteMode.DISABLED -> ExtraWriteMode.DISABLED
        MetadataWriteMode.SUPPLEMENT -> ExtraWriteMode.SUPPLEMENT
        MetadataWriteMode.OVERWRITE -> ExtraWriteMode.OVERWRITE
    }
}
