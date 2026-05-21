package com.lonx.lyrico.data.model.lyrics

data class SearchResultExtraField(
    val key: String,
    val title: String,
    val summary: String = "",
    val writeable: Boolean = true,
    val defaultTarget: SearchResultExtraTarget = SearchResultExtraTarget.COMMENT,
    val defaultMode: SearchResultExtraWriteMode = SearchResultExtraWriteMode.DISABLED
)

enum class SearchResultExtraTarget {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    DATE,
    TRACK_NUMBER,
    COMMENT,
    SUBTITLE,
    COMPOSER,
    LYRICIST,
    GENRE,
    DISC_NUMBER,
    LYRICS,
    COVER,
    REPLAY_GAIN_TRACK_GAIN,
    REPLAY_GAIN_TRACK_PEAK,
    REPLAY_GAIN_REFERENCE_LOUDNESS,
    CUSTOM
}

enum class SearchResultExtraWriteMode {
    DISABLED,
    SUPPLEMENT,
    OVERWRITE
}
