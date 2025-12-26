package com.lonx.lyrico.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LyricsSearchResult(
    val title: String?,
    val artist: String?,
    val album: String?,
    val lyrics: String?
) : Parcelable
