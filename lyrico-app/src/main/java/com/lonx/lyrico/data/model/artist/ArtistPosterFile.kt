package com.lonx.lyrico.data.model.artist

data class ArtistPosterFile(
    val uri: String,
    val name: String,
    val lastModified: Long,
    val artistId: Long? = null,
    val artistName: String? = null
)
