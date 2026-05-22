package com.lonx.lyrico.data.model.lyrics

data class SearchResultMetadataField(
    val key: String,
    val title: String,
    val summary: String = "",
    val writeable: Boolean = true,
    val defaultTarget: SearchResultMetadataTarget = SearchResultMetadataTarget.COMMENT,
    val defaultMode: SearchResultMetadataWriteMode = SearchResultMetadataWriteMode.DISABLED
)

enum class SearchResultMetadataTarget {
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

enum class SearchResultMetadataWriteMode {
    DISABLED,
    SUPPLEMENT,
    OVERWRITE
}
