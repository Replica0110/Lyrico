package com.lonx.lyrico.data.artwork

data class ArtistPosterFile(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val artistId: Long? = null,
    val artistName: String? = null
)
