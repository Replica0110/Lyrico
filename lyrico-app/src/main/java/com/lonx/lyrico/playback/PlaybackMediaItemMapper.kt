package com.lonx.lyrico.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri

private const val EXTRA_ID = "lyrico.id"
private const val EXTRA_FOLDER_ID = "lyrico.folder_id"
private const val EXTRA_MEDIA_ID = "lyrico.media_id"
private const val EXTRA_FILE_PATH = "lyrico.file_path"
private const val EXTRA_FILE_NAME = "lyrico.file_name"
private const val EXTRA_FILE_SIZE = "lyrico.file_size"
private const val EXTRA_FILE_EXTENSION = "lyrico.file_extension"
private const val EXTRA_TITLE = "lyrico.title"
private const val EXTRA_ARTIST = "lyrico.artist"
private const val EXTRA_ALBUM_ARTIST = "lyrico.album_artist"
private const val EXTRA_DISC_NUMBER = "lyrico.disc_number"
private const val EXTRA_COMPOSER = "lyrico.composer"
private const val EXTRA_LYRICIST = "lyrico.lyricist"
private const val EXTRA_COMMENT = "lyrico.comment"
private const val EXTRA_ALBUM = "lyrico.album"
private const val EXTRA_GENRE = "lyrico.genre"
private const val EXTRA_TRACK_NUMBER = "lyrico.track_number"
private const val EXTRA_DATE = "lyrico.date"
private const val EXTRA_LYRICS = "lyrico.lyrics"
private const val EXTRA_COPYRIGHT = "lyrico.copyright"
private const val EXTRA_RATING = "lyrico.rating"
private const val EXTRA_REPLAY_GAIN_TRACK_GAIN = "lyrico.replay_gain_track_gain"
private const val EXTRA_REPLAY_GAIN_TRACK_PEAK = "lyrico.replay_gain_track_peak"
private const val EXTRA_REPLAY_GAIN_ALBUM_GAIN = "lyrico.replay_gain_album_gain"
private const val EXTRA_REPLAY_GAIN_ALBUM_PEAK = "lyrico.replay_gain_album_peak"
private const val EXTRA_REPLAY_GAIN_REFERENCE_LOUDNESS = "lyrico.replay_gain_reference_loudness"
private const val EXTRA_DURATION = "lyrico.duration"
private const val EXTRA_BITRATE = "lyrico.bitrate"
private const val EXTRA_SAMPLE_RATE = "lyrico.sample_rate"
private const val EXTRA_CHANNELS = "lyrico.channels"
private const val EXTRA_RAW_PROPERTIES = "lyrico.raw_properties"
private const val EXTRA_FILE_LAST_MODIFIED = "lyrico.file_last_modified"
private const val EXTRA_FILE_ADDED = "lyrico.file_added"
private const val EXTRA_DB_UPDATE_TIME = "lyrico.db_update_time"
private const val EXTRA_TITLE_GROUP_KEY = "lyrico.title_group_key"
private const val EXTRA_TITLE_SORT_KEY = "lyrico.title_sort_key"
private const val EXTRA_ARTIST_GROUP_KEY = "lyrico.artist_group_key"
private const val EXTRA_ARTIST_SORT_KEY = "lyrico.artist_sort_key"
private const val EXTRA_URI = "lyrico.uri"

fun SongEntity.toPlaybackMediaItem(): MediaItem {
    val displayTitle = title.takeUnless { it.isNullOrBlank() } ?: fileName

    val extras = Bundle().apply {
        putLong(EXTRA_ID, id)
        putLong(EXTRA_FOLDER_ID, folderId)
        putLong(EXTRA_MEDIA_ID, mediaId)
        putString(EXTRA_FILE_PATH, filePath)
        putString(EXTRA_FILE_NAME, fileName)
        putLong(EXTRA_FILE_SIZE, fileSize)
        putString(EXTRA_FILE_EXTENSION, fileExtension)
        putString(EXTRA_TITLE, title)
        putString(EXTRA_ARTIST, artist)
        putString(EXTRA_ALBUM_ARTIST, albumArtist)
        putIntOrNull(EXTRA_DISC_NUMBER, discNumber)
        putString(EXTRA_COMPOSER, composer)
        putString(EXTRA_LYRICIST, lyricist)
        putString(EXTRA_COMMENT, comment)
        putString(EXTRA_ALBUM, album)
        putString(EXTRA_GENRE, genre)
        putString(EXTRA_TRACK_NUMBER, trackerNumber)
        putString(EXTRA_DATE, date)
        putString(EXTRA_LYRICS, lyrics)
        putString(EXTRA_COPYRIGHT, copyright)
        putIntOrNull(EXTRA_RATING, rating)
        putString(EXTRA_REPLAY_GAIN_TRACK_GAIN, replayGainTrackGain)
        putString(EXTRA_REPLAY_GAIN_TRACK_PEAK, replayGainTrackPeak)
        putString(EXTRA_REPLAY_GAIN_ALBUM_GAIN, replayGainAlbumGain)
        putString(EXTRA_REPLAY_GAIN_ALBUM_PEAK, replayGainAlbumPeak)
        putString(EXTRA_REPLAY_GAIN_REFERENCE_LOUDNESS, replayGainReferenceLoudness)
        putInt(EXTRA_DURATION, durationMilliseconds)
        putInt(EXTRA_BITRATE, bitrate)
        putInt(EXTRA_SAMPLE_RATE, sampleRate)
        putInt(EXTRA_CHANNELS, channels)
        putString(EXTRA_RAW_PROPERTIES, rawProperties)
        putLong(EXTRA_FILE_LAST_MODIFIED, fileLastModified)
        putLong(EXTRA_FILE_ADDED, fileAdded)
        putLong(EXTRA_DB_UPDATE_TIME, dbUpdateTime)
        putString(EXTRA_TITLE_GROUP_KEY, titleGroupKey)
        putString(EXTRA_TITLE_SORT_KEY, titleSortKey)
        putString(EXTRA_ARTIST_GROUP_KEY, artistGroupKey)
        putString(EXTRA_ARTIST_SORT_KEY, artistSortKey)
        putString(EXTRA_URI, uri)
    }

    return MediaItem.Builder()
        .setMediaId(id.takeIf { it > 0 }?.toString() ?: getUri.toString())
        .setUri(getUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                .setExtras(extras)
                .build()
        )
        .build()
}

fun Uri.toFallbackMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setMediaId(toString())
        .setUri(this)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(lastPathSegment ?: toString())
                .build()
        )
        .build()
}

fun MediaItem.toSongEntityOrNull(): SongEntity? {
    val extras = mediaMetadata.extras ?: return null

    val mediaId = extras.getLong(EXTRA_MEDIA_ID, 0L)
    val fileName = extras.getString(EXTRA_FILE_NAME).orEmpty()
    val filePath = extras.getString(EXTRA_FILE_PATH).orEmpty()
    val uri = extras.getString(EXTRA_URI).orEmpty()

    if (fileName.isBlank() || uri.isBlank()) return null

    return SongEntity(
        id = extras.getLong(EXTRA_ID, 0L),
        folderId = extras.getLong(EXTRA_FOLDER_ID, 0L),
        mediaId = mediaId,
        filePath = filePath,
        fileName = fileName,
        fileSize = extras.getLong(EXTRA_FILE_SIZE, 0L),
        fileExtension = extras.getString(EXTRA_FILE_EXTENSION),
        title = extras.getString(EXTRA_TITLE),
        artist = extras.getString(EXTRA_ARTIST),
        albumArtist = extras.getString(EXTRA_ALBUM_ARTIST),
        discNumber = extras.getIntOrNull(EXTRA_DISC_NUMBER),
        composer = extras.getString(EXTRA_COMPOSER),
        lyricist = extras.getString(EXTRA_LYRICIST),
        comment = extras.getString(EXTRA_COMMENT),
        album = extras.getString(EXTRA_ALBUM),
        genre = extras.getString(EXTRA_GENRE),
        trackerNumber = extras.getString(EXTRA_TRACK_NUMBER),
        date = extras.getString(EXTRA_DATE),
        lyrics = extras.getString(EXTRA_LYRICS),
        copyright = extras.getString(EXTRA_COPYRIGHT),
        rating = extras.getIntOrNull(EXTRA_RATING),
        replayGainTrackGain = extras.getString(EXTRA_REPLAY_GAIN_TRACK_GAIN),
        replayGainTrackPeak = extras.getString(EXTRA_REPLAY_GAIN_TRACK_PEAK),
        replayGainAlbumGain = extras.getString(EXTRA_REPLAY_GAIN_ALBUM_GAIN),
        replayGainAlbumPeak = extras.getString(EXTRA_REPLAY_GAIN_ALBUM_PEAK),
        replayGainReferenceLoudness = extras.getString(EXTRA_REPLAY_GAIN_REFERENCE_LOUDNESS),
        durationMilliseconds = extras.getInt(EXTRA_DURATION, 0),
        bitrate = extras.getInt(EXTRA_BITRATE, 0),
        sampleRate = extras.getInt(EXTRA_SAMPLE_RATE, 0),
        channels = extras.getInt(EXTRA_CHANNELS, 0),
        rawProperties = extras.getString(EXTRA_RAW_PROPERTIES),
        fileLastModified = extras.getLong(EXTRA_FILE_LAST_MODIFIED, 0L),
        fileAdded = extras.getLong(EXTRA_FILE_ADDED, 0L),
        dbUpdateTime = extras.getLong(EXTRA_DB_UPDATE_TIME, System.currentTimeMillis()),
        titleGroupKey = extras.getString(EXTRA_TITLE_GROUP_KEY) ?: "#",
        titleSortKey = extras.getString(EXTRA_TITLE_SORT_KEY) ?: "#",
        artistGroupKey = extras.getString(EXTRA_ARTIST_GROUP_KEY) ?: "#",
        artistSortKey = extras.getString(EXTRA_ARTIST_SORT_KEY) ?: "#",
        uri = uri
    )
}

private fun Bundle.putIntOrNull(key: String, value: Int?) {
    if (value != null) putInt(key, value)
}

private fun Bundle.getIntOrNull(key: String): Int? {
    return if (containsKey(key)) getInt(key) else null
}
