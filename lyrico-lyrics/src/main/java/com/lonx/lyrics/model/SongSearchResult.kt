package com.lonx.lyrics.model

import android.os.Parcelable
import androidx.annotation.StringRes
import com.lonx.lyrics.R
import kotlinx.parcelize.Parcelize

enum class Source(
    @field:StringRes val labelRes: Int
) {
    KG(R.string.kg_source_name),
    QM(R.string.qm_source_name),
    NE(R.string.ne_source_name);

    companion object {
        val DEFAULT_ORDER = listOf(QM, KG, NE)
        private val NAME_MAP = entries.associateBy { it.name }

        fun fromNameOrNull(name: String?): Source? = NAME_MAP[name?.trim()]
    }
}

/** 核心解析逻辑，统一处理 CSV 或 List<String> */
private fun Iterable<String>.parseSourceList(): List<Source> {
    val parsed = mapNotNull { Source.fromNameOrNull(it) }
    if (parsed.isEmpty()) return Source.DEFAULT_ORDER

    val result = parsed.distinct().toMutableList()

    // 自动补齐缺失源
    Source.entries.forEach {
        if (it !in result) result.add(it)
    }

    return result
}

/** CSV String → List<Source> */
fun String?.toSourceList(): List<Source> {
    if (this.isNullOrBlank()) return Source.DEFAULT_ORDER
    return split(",").parseSourceList()
}

/** List<String> → List<Source> */
fun List<String>.toSourceList(): List<Source> = parseSourceList()

/** List<Source> → CSV String */
fun List<Source>.toSourceCsv(): String = joinToString(",") { it.name }

@Parcelize
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // 毫秒
    val source: Source,
    val date: String = "",
    val trackerNumber: String = "",
    val picUrl: String = "",
    val extras: Map<String, String> = emptyMap()
) : Parcelable

