package com.lonx.lyrico.data.model.lyrics

import android.os.Parcelable
import com.lonx.lyrico.data.model.metadata.StandardPluginField
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class SongSearchResult(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    // UI 快速展示字段
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val date: String = "",
    val trackNumber: String = "",
    val picUrl: String = "",

    // 标准元数据字段
    val fields: Map<String, String> = emptyMap(),

    // 插件私有上下文
    val internal: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
            putAll(fields.sanitizeStandardFields())

            if (title.isNotBlank()) putIfAbsent("title", title)
            if (artist.isNotBlank()) putIfAbsent("artist", artist)
            if (album.isNotBlank()) putIfAbsent("album", album)
            if (date.isNotBlank()) putIfAbsent("date", date)
            if (trackNumber.isNotBlank()) putIfAbsent("track_number", trackNumber)
            if (picUrl.isNotBlank()) putIfAbsent("cover_url", picUrl)
        }
    }
}

fun Map<String, String>.sanitizeStandardFields(): Map<String, String> {
    return asSequence()
        .filter { (key, value) ->
            StandardPluginField.fromKey(key) != null && value.isNotBlank()
        }
        .associate { (key, value) -> key to value }
}

fun Map<String, String>.sanitizePluginInternal(): Map<String, String> {
    return asSequence()
        .filter { (key, value) ->
            key.isNotBlank() && key.length <= 64 && value.length <= 4096
        }
        .take(64)
        .associate { (key, value) -> key to value }
}
