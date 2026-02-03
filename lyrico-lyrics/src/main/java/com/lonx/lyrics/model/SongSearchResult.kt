package com.lonx.lyrics.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class Source { KG, QM, NE, LRCLIB }

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

