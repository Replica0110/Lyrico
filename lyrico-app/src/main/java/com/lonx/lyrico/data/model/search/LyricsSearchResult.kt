package com.lonx.lyrico.data.model.search

import android.os.Parcelable
import com.lonx.lyrico.data.model.lyrics.sanitizeStandardFields
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
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
    val applyTargets: Set<MetadataFieldTarget> = emptySet(),
    val fields: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
            putAll(fields.sanitizeStandardFields())

            title?.takeIf { it.isNotBlank() }?.let { putIfAbsent("title", it) }
            artist?.takeIf { it.isNotBlank() }?.let { putIfAbsent("artist", it) }
            album?.takeIf { it.isNotBlank() }?.let { putIfAbsent("album", it) }
            date?.takeIf { it.isNotBlank() }?.let { putIfAbsent("date", it) }
            trackerNumber?.takeIf { it.isNotBlank() }?.let { putIfAbsent("track_number", it) }
            picUrl?.takeIf { it.isNotBlank() }?.let { putIfAbsent("cover_url", it) }
        }
    }
}
