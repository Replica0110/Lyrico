package com.lonx.lyrico.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.jetbrains.annotations.TestOnly

@Parcelize
data class LyricsSearchResult(
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String?,
    val date: String?,
    val trackerNumber: String?,
    val picUrl: String?
) : Parcelable
