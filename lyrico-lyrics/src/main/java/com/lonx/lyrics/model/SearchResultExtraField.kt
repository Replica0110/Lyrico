package com.lonx.lyrics.model

data class SearchResultExtraField(
    val key: String,
    val title: String,
    val summary: String = "",
    val writeable: Boolean = true,
    val defaultTarget: SearchResultExtraTarget = SearchResultExtraTarget.COMMENT,
    val defaultMode: SearchResultExtraWriteMode = SearchResultExtraWriteMode.DISABLED
)

enum class SearchResultExtraTarget {
    COMMENT,
    SUBTITLE,
    COMPOSER,
    LYRICIST,
    GENRE,
    DISC_NUMBER,
    REPLAY_GAIN_TRACK_GAIN,
    REPLAY_GAIN_TRACK_PEAK,
    REPLAY_GAIN_REFERENCE_LOUDNESS
}

enum class SearchResultExtraWriteMode {
    DISABLED,
    SUPPLEMENT,
    OVERWRITE
}
