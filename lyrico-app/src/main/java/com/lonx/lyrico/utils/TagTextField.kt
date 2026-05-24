package com.lonx.lyrico.utils

import androidx.annotation.StringRes
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.entity.SongEntity
import kotlinx.serialization.Serializable

@Serializable
enum class TagTextField(@field:StringRes val labelRes: Int) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM(R.string.label_album),
    ALBUM_ARTIST(R.string.label_album_artist),
    GENRE(R.string.label_genre),
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    DATE(R.string.label_year),
    LANGUAGE(R.string.label_language),
    TRACK_NUMBER(R.string.label_track_number),
    COPYRIGHT(R.string.label_copyright);

    fun valueOf(song: SongEntity): String? = when (this) {
        TITLE -> song.title
        ARTIST -> song.artist
        ALBUM -> song.album
        ALBUM_ARTIST -> song.albumArtist
        GENRE -> song.genre
        COMMENT -> song.comment
        LYRICS -> song.lyrics
        COMPOSER -> song.composer
        LYRICIST -> song.lyricist
        DATE -> song.date
        LANGUAGE -> song.language
        TRACK_NUMBER -> song.trackerNumber
        COPYRIGHT -> song.copyright
    }

    fun valueOf(tag: AudioTagData): String? = when (this) {
        TITLE -> tag.title
        ARTIST -> tag.artist
        ALBUM -> tag.album
        ALBUM_ARTIST -> tag.albumArtist
        GENRE -> tag.genre
        COMMENT -> tag.comment
        LYRICS -> tag.lyrics
        COMPOSER -> tag.composer
        LYRICIST -> tag.lyricist
        DATE -> tag.date
        LANGUAGE -> tag.language
        TRACK_NUMBER -> tag.trackNumber
        COPYRIGHT -> tag.copyright
    }

    fun copyInto(tag: AudioTagData, value: String): AudioTagData = when (this) {
        TITLE -> tag.copy(title = value)
        ARTIST -> tag.copy(artist = value)
        ALBUM -> tag.copy(album = value)
        ALBUM_ARTIST -> tag.copy(albumArtist = value)
        GENRE -> tag.copy(genre = value)
        COMMENT -> tag.copy(comment = value)
        LYRICS -> tag.copy(lyrics = value)
        COMPOSER -> tag.copy(composer = value)
        LYRICIST -> tag.copy(lyricist = value)
        DATE -> tag.copy(date = value)
        LANGUAGE -> tag.copy(language = value)
        TRACK_NUMBER -> tag.copy(trackNumber = value)
        COPYRIGHT -> tag.copy(copyright = value)
    }
}
