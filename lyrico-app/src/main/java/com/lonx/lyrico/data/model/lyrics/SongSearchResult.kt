package com.lonx.lyrico.data.model.lyrics

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlin.collections.get

@Parcelize
@Serializable
data class SongSearchResult(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val date: String = "",
    val trackNumber: String = "",
    val picUrl: String = "",
    val fields: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
            putAll(fields)

            if (title.isNotBlank()) putIfAbsent("title", title)
            if (artist.isNotBlank()) putIfAbsent("artist", artist)
            if (album.isNotBlank()) putIfAbsent("album", album)
            if (duration > 0) putIfAbsent("duration", duration.toString())
            if (date.isNotBlank()) putIfAbsent("date", date)
            if (trackNumber.isNotBlank()) putIfAbsent("track_number", trackNumber)
            if (picUrl.isNotBlank()) putIfAbsent("cover_url", picUrl)
        }
    }
}

object MetadataFieldKeys {
    const val TITLE = "title"
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val DATE = "date"
    const val TRACK_NUMBER = "track_number"
    const val COVER_URL = "cover_url"
}
