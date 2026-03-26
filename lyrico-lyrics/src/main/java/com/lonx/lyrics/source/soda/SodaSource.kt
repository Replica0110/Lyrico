package com.lonx.lyrics.source.soda

import android.util.Log
import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.SodaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SodaSource(
    private val api: SodaApi
) : SearchSource {

    override val sourceType: Source
        get() = Source.SODA

    override suspend fun search(
        keyword: String,
        page: Int,
        separator: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(Dispatchers.IO) {

        val resp = api.searchSong(keyword)

        Log.d("SODA", "resp: $resp")
        val list = resp.resultGroups
            .firstOrNull()
            ?.data
            ?: return@withContext emptyList()

        list.mapNotNull { item ->
            val track = item.entity.track
            if (track.id.isBlank()) return@mapNotNull null

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
                    put("track_id", track.id)

                    if (composers.isNotEmpty()) {
                        put("composer", composers)
                    }
                    if (lyricists.isNotEmpty()) {
                        put("lyricist", lyricists)
                    }
                    tagMap.forEach { (k, v) ->
                        put(k, v) // e.g. genre
                    }
                }
            )
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult =
        withContext(Dispatchers.IO) {

            val trackId = song.extras["track_id"] ?: song.id

            val resp = api.getLyrics(trackId)
            val raw = resp.lyric?.content

            val lyricsData = LyricsData(
                original = raw,
                translated = null,
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