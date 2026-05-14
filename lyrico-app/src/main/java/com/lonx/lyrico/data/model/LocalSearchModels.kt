package com.lonx.lyrico.data.model

import com.lonx.lyrico.data.model.dao.AlbumSearchRow
import com.lonx.lyrico.data.model.dao.ArtistSearchRow
import com.lonx.lyrico.data.model.entity.SongEntity

data class LocalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val songs: List<SongEntity> = emptyList(),
    val albums: List<AlbumSearchResult> = emptyList(),
    val artists: List<ArtistSearchResult> = emptyList()
)

data class AlbumSearchResult(
    val album: String,
    val albumArtist: String?,
    val songCount: Int,
    val coverSongUri: String?,
    val coverSongLastModified: Long
)

data class ArtistSearchResult(
    val artist: String,
    val songCount: Int,
    val albumCount: Int,
    val coverSongUri: String?,
    val coverSongLastModified: Long
)

fun AlbumSearchRow.toAlbumSearchResult(): AlbumSearchResult {
    return AlbumSearchResult(
        album = album,
        albumArtist = albumArtist,
        songCount = songCount,
        coverSongUri = coverSongUri,
        coverSongLastModified = coverSongLastModified
    )
}

fun ArtistSearchRow.toArtistSearchResult(): ArtistSearchResult {
    return ArtistSearchResult(
        artist = artist,
        songCount = songCount,
        albumCount = albumCount,
        coverSongUri = coverSongUri,
        coverSongLastModified = coverSongLastModified
    )
}
