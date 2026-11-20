package com.lonx.lyrico.data.song.library

import com.lonx.lyrico.domain.artist.normalizedArtistKey

fun normalizedAlbumKey(
    album: String,
    albumArtist: String?
): String {
    val albumKey = album.normalizedArtistKey()
    val artistKey = albumArtist?.normalizedArtistKey().orEmpty()
    return "$albumKey\u0000$artistKey"
}

