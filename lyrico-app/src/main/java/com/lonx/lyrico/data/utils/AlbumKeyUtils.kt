package com.lonx.lyrico.data.utils

import com.lonx.lyrico.data.model.artist.normalizedArtistKey

fun normalizedAlbumKey(
    album: String,
    albumArtist: String?
): String {
    val albumKey = album.normalizedArtistKey()
    val artistKey = albumArtist?.normalizedArtistKey().orEmpty()
    return "$albumKey\u0000$artistKey"
}

