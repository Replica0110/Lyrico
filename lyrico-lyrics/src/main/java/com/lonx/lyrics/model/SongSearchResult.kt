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
    NE(R.string.ne_source_name)
}

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

