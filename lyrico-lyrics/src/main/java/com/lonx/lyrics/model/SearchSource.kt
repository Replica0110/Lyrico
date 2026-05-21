package com.lonx.lyrics.model

interface SearchSource {
    val sourceType: Source
    val extraFields: List<SearchResultExtraField>
        get() = emptyList()

    val supportedExtras: Set<String>
        get() = extraFields
            .filter { it.writeable }
            .mapTo(mutableSetOf()) { it.key }

    fun getConfigFields(): List<SourceConfigField> = emptyList()

    fun applyConfig(config: SourceRuntimeConfig) = Unit

    suspend fun search(keyword: String, page: Int = 1, separator: String = "/", pageSize: Int = 20): List<SongSearchResult>
    suspend fun getLyrics(song: SongSearchResult): LyricsResult?
    suspend fun searchCover(keyword: String, pageSize: Int = 5): List<SongSearchResult>
}

object SearchResultExtraKeys {
    const val NETEASE_163_KEY = "netease_163_key"
    const val REPLAY_GAIN_TRACK_GAIN = "replaygain_track_gain"
    const val REPLAY_GAIN_TRACK_PEAK = "replaygain_track_peak"
    const val REPLAY_GAIN_REFERENCE_LOUDNESS = "replaygain_reference_loudness"
    const val REPLAY_GAIN_LOUDNESS_RANGE = "replaygain_loudness_range"
    const val SUBTITLE = "subtitle"
    const val COMPOSER = "composer"
    const val LYRICIST = "lyricist"
    const val GENRE = "genre"
    const val DISC_NUMBER = "disc_number"
    const val APPLE_ID = "apple_id"
    const val KG_HASH = "hash"
    const val SODA_TRACK_ID = "track_id"
}
