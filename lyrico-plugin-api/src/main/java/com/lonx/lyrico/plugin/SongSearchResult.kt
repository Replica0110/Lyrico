package com.lonx.lyrico.plugin

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SongSearchResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val source: String,
    val picUrl: String?,
    val extras: Map<String, String>
) : Parcelable