package com.lonx.lyrics.source.soda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class SodaSearchResponse(
    @SerialName("result_groups")
    val resultGroups: List<SodaResultGroup> = emptyList()
)

@Serializable
data class SodaResultGroup(
    val data: List<SodaSearchItem> = emptyList()
)

@Serializable
data class SodaSearchItem(
    val entity: SodaSearchEntity
)

@Serializable
data class SodaSearchEntity(
    val track: SodaTrack
)
@Serializable
data class SodaTrack(
    val id: String,
    val name: String,
    val duration: Int,
    val artists: List<SodaArtist> = emptyList(),
    val album: SodaAlbum,

    @SerialName("song_maker_team")
    val makerTeam: SodaMakerTeam? = null,
    val tags: List<SodaTagItem> = emptyList(),
    @SerialName("bit_rates")
    val bitRates: List<SodaBitrate> = emptyList()
)
@Serializable
data class SodaTagItem(
    val category: SodaTagCategory,
    @SerialName("first_level_tag")
    val first: SodaTagValue? = null,
    @SerialName("second_level_tag")
    val second: SodaTagValue? = null
)

@Serializable
data class SodaTagCategory(
    @SerialName("tag_id") val id: Long,
    @SerialName("tag_name") val name: String
)

@Serializable
data class SodaTagValue(
    @SerialName("tag_id") val id: Long,
    @SerialName("tag_name") val name: String
)

@Serializable
data class SodaMakerTeam(
    val composers: List<SodaArtist> = emptyList(),
    val lyricists: List<SodaArtist> = emptyList()
)

@Serializable
data class SodaArtist(
    val name: String
)

@Serializable
data class SodaAlbum(
    val name: String,
    @SerialName("url_cover")
    val cover: SodaCover
)

@Serializable
data class SodaCover(
    val uri: String = "",
    val urls: List<String> = emptyList()
)

@Serializable
data class SodaBitrate(
    val size: Long = 0,
    val quality: String = ""
)
@Serializable
data class SodaTrackV2Response(
    val lyric: SodaLyric? = null
)

@Serializable
data class SodaLyric(
    val content: String = ""
)
interface SodaApi {

    @GET("luna/pc/search/track")
    suspend fun searchSong(
        @Query("q") keyword: String,
        @Query("cursor") cursor: Int = 0,
        @Query("search_method") method: String = "input",
        @Query("aid") aid: String = "386088",
        @Query("device_platform") platform: String = "web",
        @Query("channel") channel: String = "pc_web"
    ): SodaSearchResponse


    @GET("luna/pc/track_v2")
    suspend fun getLyrics(
        @Query("track_id") trackId: String,
        @Query("media_type") mediaType: String = "track",
        @Query("aid") aid: String = "386088",
        @Query("device_platform") platform: String = "web",
        @Query("channel") channel: String = "pc_web"
    ): SodaTrackV2Response
}