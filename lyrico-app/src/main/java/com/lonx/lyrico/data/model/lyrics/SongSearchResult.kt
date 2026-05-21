package com.lonx.lyrico.data.model.lyrics

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrico.R
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlin.collections.get

@Serializable
enum class Source(
    val id: String,
    @field:StringRes val labelRes: Int
) {
    KG("kg", R.string.kg_source_name),
    QM("qm", R.string.qm_source_name),
    NE("ne", R.string.ne_source_name),
    SODA("soda", R.string.soda_source_name),
    AM("am", R.string.am_source_name);

    companion object {
        val DEFAULT_ORDER = entries.toList()
        private val NAME_MAP = entries.associateBy { it.name }

        fun fromNameOrNull(name: String?): Source? = NAME_MAP[name?.trim()]
        fun fromIdOrNameOrNull(value: String?): Source? {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.id == normalized || it.name == normalized }
        }
    }
}

private fun Iterable<String>.parseSourceList(): List<Source> {
    val parsed = mapNotNull { Source.fromIdOrNameOrNull(it) }
    if (parsed.isEmpty()) return Source.DEFAULT_ORDER

    val result = parsed.distinct().toMutableList()
    Source.entries.forEach {
        if (it !in result) result.add(it)
    }
    return result
}

fun String?.toSourceList(): List<Source> {
    if (this.isNullOrBlank()) return Source.DEFAULT_ORDER
    return split(",").parseSourceList()
}

fun List<String>.toSourceList(): List<Source> = parseSourceList()

fun List<Source>.toSourceCsv(): String = joinToString(",") { it.name }

@Parcelize
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val source: Source? = null,
    val date: String = "",
    val trackerNumber: String = "",
    val picUrl: String = "",
    val extras: Map<String, String> = emptyMap(),
    val pluginId: String = source?.id.orEmpty(),
    val pluginName: String = source?.name.orEmpty(),
    val fields: Map<String, String> = emptyMap()
) : Parcelable {
    fun normalizedFields(): Map<String, String> {
        return buildMap {
            putAll(extras)
            putAll(fields)

            if (title.isNotBlank()) putIfAbsent("title", title)
            if (artist.isNotBlank()) putIfAbsent("artist", artist)
            if (album.isNotBlank()) putIfAbsent("album", album)
            if (duration > 0) putIfAbsent("duration", duration.toString())
            if (date.isNotBlank()) putIfAbsent("date", date)
            if (trackerNumber.isNotBlank()) putIfAbsent("track_number", trackerNumber)
            if (picUrl.isNotBlank()) putIfAbsent("cover_url", picUrl)
        }
    }
}
