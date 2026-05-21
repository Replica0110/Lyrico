package com.lonx.lyrico.data.model

import android.os.Parcelable
import com.lonx.lyrico.data.model.lyrics.Source
import kotlinx.parcelize.Parcelize

@Parcelize
data class LyricsSearchResult(
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String?,
    val date: String?,
    val trackerNumber: String?,
    val picUrl: String?,
    val source: Source? = null,
    val pluginId: String = source?.id.orEmpty(),
    val pluginName: String = source?.name.orEmpty(),
    val lyricsOnly: Boolean = false,
    val extras: Map<String, String> = emptyMap(),
    val fields: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
            putAll(extras)
            putAll(fields)

            title?.takeIf { it.isNotBlank() }?.let { putIfAbsent("title", it) }
            artist?.takeIf { it.isNotBlank() }?.let { putIfAbsent("artist", it) }
            album?.takeIf { it.isNotBlank() }?.let { putIfAbsent("album", it) }
            date?.takeIf { it.isNotBlank() }?.let { putIfAbsent("date", it) }
            trackerNumber?.takeIf { it.isNotBlank() }?.let { putIfAbsent("track_number", it) }
            picUrl?.takeIf { it.isNotBlank() }?.let { putIfAbsent("cover_url", it) }
        }
    }
}
