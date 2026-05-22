package com.lonx.lyrico.data.model

import android.os.Parcelable
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
    val pluginId: String = "",
    val pluginName: String = "",
    val lyricsOnly: Boolean = false,
    val fields: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
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
