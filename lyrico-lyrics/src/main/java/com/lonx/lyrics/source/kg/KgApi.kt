package com.lonx.lyrics.source.kg

import android.os.Parcelable
import com.lonx.lyrics.model.Singer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.QueryMap

@Serializable
data class KgBaseResponse<T>(
    @SerialName("status") val status: Int = 0,
    @SerialName("error_code") val errorCode: Int = 0,
    @SerialName("data") val data: T? = null
)

@Serializable
data class KgLyricSearchResponse(
    @SerialName("status") val status: Int = 0,
    @SerialName("error_code") val errorCode: Int = 0,
    @SerialName("candidates") val candidates: List<KgCandidate>? = emptyList()
)

@Serializable
@Parcelize
data class RegisterDevData(val dfid: String) : Parcelable

@Serializable
@Parcelize
data class KgSearchWrapper(val lists: List<KgSongItem>?, val total: Int) : Parcelable

@Serializable
@Parcelize
data class KgSongItem(
    @SerialName("ID") val id: String? = null,
    @SerialName("FileHash") val fileHash: String,
    @SerialName("SongName") val songName: String,
    @SerialName("Singers") val singers: List<Singer>,
    @SerialName("AlbumName") val albumName: String? = null,
    @SerialName("Duration") val duration: Int,
    @SerialName("PublishDate") val publishDate: String? = null,
) : Parcelable



@Serializable
@Parcelize
data class KgCandidate(
    val id: String,
    val accesskey: String,
    val duration: Int
) : Parcelable

@Serializable
@Parcelize
data class KgLyricContent(
    val content: String,
    val fmt: String,
    val contenttype: Int
) : Parcelable

@Parcelize
@Serializable
data class KrcLanguageItem(
    val type: Int, // 0: 罗马音(逐字), 1: 翻译(逐行)
    val lyricContent: List<List<String>> // 二维数组
): Parcelable

interface KgApi {
    @POST("https://userservice.kugou.com/risk/v1/r_register_dev")
    suspend fun registerDev(
        @QueryMap params: Map<String, String>,
        @Body body: RequestBody
    ): KgBaseResponse<RegisterDevData>

    @GET("http://complexsearch.kugou.com/v2/search/song")
    @Headers("x-router: complexsearch.kugou.com")
    suspend fun searchSong(
        @QueryMap params: Map<String, String>
    ): KgBaseResponse<KgSearchWrapper>

    @GET("https://lyrics.kugou.com/v1/search")
    suspend fun searchLyrics(
        @QueryMap params: Map<String, String>
    ): KgLyricSearchResponse

    @GET("http://lyrics.kugou.com/download")
    suspend fun downloadLyrics(
        @QueryMap params: Map<String, String>
    ): KgLyricContent
}