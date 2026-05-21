package com.lonx.lyrics.source.soda

import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchResultExtraField
import com.lonx.lyrics.model.SearchResultExtraKeys
import com.lonx.lyrics.model.SearchResultExtraTarget
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.SodaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.Response

class SodaSource(
    private val api: SodaApi
) : SearchSource {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override val sourceType: Source
        get() = Source.SODA

    override val extraFields = listOf(
        SearchResultExtraField(
            key = SearchResultExtraKeys.SODA_TRACK_ID,
            title = "Track ID",
            summary = "汽水歌词请求所需的内部字段",
            writeable = false
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.COMPOSER,
            title = "作曲",
            summary = "汽水返回的 composer 字段",
            defaultTarget = SearchResultExtraTarget.COMPOSER
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.LYRICIST,
            title = "作词",
            summary = "汽水返回的 lyricist 字段",
            defaultTarget = SearchResultExtraTarget.LYRICIST
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.SUBTITLE,
            title = "副标题",
            summary = "汽水返回的 relationMedia 字段",
            defaultTarget = SearchResultExtraTarget.SUBTITLE
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.GENRE,
            title = "流派",
            summary = "汽水 tags 中的 Genre 字段",
            defaultTarget = SearchResultExtraTarget.GENRE
        )
    )

    override suspend fun search(
        keyword: String,
        page: Int,
        separator: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(Dispatchers.IO) {

        val resp = requestSearch(keyword)
        val list = resp.resultGroups
            .firstOrNull()
            ?.data
            ?: return@withContext emptyList()

        list.mapNotNull { item ->
            val track = item.entity.track
            if (track.id.isBlank()) return@mapNotNull null

            val subtitle = item.entity.track.relationMedia
            val artists = track.artists.joinToString(separator) { it.name }
            val composers = track.makerTeam?.composers
                ?.joinToString(separator) { it.name }
                .orEmpty()

            val lyricists = track.makerTeam?.lyricists
                ?.joinToString(separator) { it.name }
                .orEmpty()

            val cover = buildCover(track.album.cover)
            val tagMap = parseTags(track)
            SongSearchResult(
                id = track.id,
                title = track.name,
                artist = artists,
                album = track.album.name,
                duration = track.duration.toLong(),
                source = sourceType,
                picUrl = cover,
                extras = buildMap {
                    put(SearchResultExtraKeys.SODA_TRACK_ID, track.id)

                    if (composers.isNotEmpty()) {
                        put(SearchResultExtraKeys.COMPOSER, composers)
                    }
                    if (lyricists.isNotEmpty()) {
                        put(SearchResultExtraKeys.LYRICIST, lyricists)
                    }
                    if (subtitle.isNotEmpty()) {
                        put(SearchResultExtraKeys.SUBTITLE, subtitle)
                     }
                    tagMap.forEach { (k, v) ->
                        put(k, v) // e.g. genre
                    }
                }
            )
        }
    }
    override suspend fun searchCover(
        keyword: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(Dispatchers.IO) {

        try {
            val resp = requestSearch(keyword)

            val list = resp.resultGroups
                .firstOrNull()
                ?.data
                ?: return@withContext emptyList()

            list.take(pageSize).mapNotNull { item ->
                val track = item.entity.track
                if (track.id.isBlank()) return@mapNotNull null

                val artists = track.artists.joinToString("/") { it.name }
                val cover = buildCover(track.album.cover)
                
                if (cover.isBlank()) return@mapNotNull null

                SongSearchResult(
                    id = track.id,
                    title = track.name,
                    artist = artists,
                    album = track.album.name,
                    duration = track.duration.toLong(),
                    source = sourceType,
                    picUrl = cover
                )
            }

        } catch (e: Exception) {
            if (e is SodaRateLimitException) throw e
            emptyList()
        }
    }
    override suspend fun getLyrics(song: SongSearchResult): LyricsResult =
        withContext(Dispatchers.IO) {

            val trackId = song.extras[SearchResultExtraKeys.SODA_TRACK_ID] ?: song.id

            val resp = requestLyrics(trackId)
            val raw = resp.lyric?.content
            val translations = resp.lyric?.translations?.cn

            val lyricsData = LyricsData(
                original = raw,
                translated = translations,
                romanization = null,
                type = "soda"
            )

            val result = SodaParser.parse(lyricsData)

            result.copy(
                tags = result.tags + mapOf(
                    "ti" to song.title,
                    "ar" to song.artist,
                    "al" to song.album
                )
            )
        }

    private suspend fun requestSearch(keyword: String): SodaSearchResponse {
        val response = api.searchSong(keyword)
        val body = readResponseBody(response)
        return json.decodeFromString<SodaSearchResponse>(body)
    }

    private suspend fun requestLyrics(trackId: String): SodaTrackV2Response {
        val response = api.getLyrics(trackId)
        val body = readResponseBody(response)
        return json.decodeFromString<SodaTrackV2Response>(body)
    }

    private fun readResponseBody(
        response: Response<ResponseBody>
    ): String {
        val body = response.body()?.string()
        val errorBody = response.errorBody()?.string()
        val payload = body ?: errorBody.orEmpty()
        if (payload.isBlank()) throw SodaRateLimitException()

        return payload
    }

    private fun buildCover(cover: SodaCover): String {
        val domain = cover.urls.firstOrNull() ?: return ""
        val uri = cover.uri
        if (uri.isBlank()) return ""

        return if (domain.contains(uri)) {
            domain
        } else {
            "$domain$uri~c5_1400x1400.jpg"
        }
    }
    private fun parseTags(track: SodaTrack): Map<String, String> {

        val result = mutableMapOf<String, MutableSet<String>>()

        track.tags.forEach { tag ->
            val category = tag.category.name.lowercase()

            val set = result.getOrPut(category) { mutableSetOf() }

            tag.second?.name?.let { set.add(it) }
            tag.first?.name?.let { set.add(it) }
        }

        return result.mapValues { (_, v) ->
            v.joinToString(" / ")
        }
    }

}
