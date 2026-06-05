package com.lonx.lyrico.data.song.mapper

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongFile
import com.lonx.lyrico.data.model.entity.SongEntity

class SongMetadataMapper(
    private val sortKeyUpdater: SortKeyUpdater
) {
    fun applyAudioTagData(
        old: SongEntity,
        tag: AudioTagData,
        fileLastModified: Long = System.currentTimeMillis()
    ): SongEntity {
        return old.copy(
            title = tag.title ?: old.title,
            artist = tag.artist ?: old.artist,
            albumArtist = tag.albumArtist ?: old.albumArtist,
            album = tag.album ?: old.album,
            genre = tag.genre ?: old.genre,
            date = tag.date ?: old.date,
            trackerNumber = tag.trackNumber ?: old.trackerNumber,
            discNumber = tag.discNumber ?: old.discNumber,
            composer = tag.composer ?: old.composer,
            lyricist = tag.lyricist ?: old.lyricist,
            comment = tag.comment ?: old.comment,
            lyrics = tag.lyrics ?: old.lyrics,
            language = tag.language ?: old.language,
            copyright = tag.copyright ?: old.copyright,
            rating = tag.rating ?: old.rating,
            replayGainTrackGain = tag.replayGainTrackGain ?: old.replayGainTrackGain,
            replayGainTrackPeak = tag.replayGainTrackPeak ?: old.replayGainTrackPeak,
            replayGainAlbumGain = tag.replayGainAlbumGain ?: old.replayGainAlbumGain,
            replayGainAlbumPeak = tag.replayGainAlbumPeak ?: old.replayGainAlbumPeak,
            replayGainReferenceLoudness = tag.replayGainReferenceLoudness
                ?: old.replayGainReferenceLoudness,
            fileLastModified = fileLastModified
        ).let(sortKeyUpdater::update)
    }

    fun fromScannedFile(
        file: SongFile,
        tag: AudioTagData,
        folderId: Long,
        existingId: Long = 0L,
        source: String = "MEDIA_STORE"
    ): SongEntity {
        return SongEntity(
            id = existingId,
            mediaId = file.mediaId,
            source = source,
            uri = file.uri.toString(),
            filePath = file.filePath,
            fileName = file.fileName,
            title = tag.title,
            fileSize = file.fileSize,
            fileExtension = file.fileName.substringAfterLast(".").uppercase(),
            artist = tag.artist,
            albumArtist = tag.albumArtist,
            album = tag.album,
            genre = tag.genre,
            trackerNumber = tag.trackNumber,
            date = tag.date,
            language = tag.language,
            lyrics = tag.lyrics,
            composer = tag.composer,
            lyricist = tag.lyricist,
            comment = tag.comment,
            discNumber = tag.discNumber,
            copyright = tag.copyright,
            rating = tag.rating,
            replayGainTrackGain = tag.replayGainTrackGain,
            replayGainTrackPeak = tag.replayGainTrackPeak,
            replayGainAlbumGain = tag.replayGainAlbumGain,
            replayGainAlbumPeak = tag.replayGainAlbumPeak,
            replayGainReferenceLoudness = tag.replayGainReferenceLoudness,
            durationMilliseconds = tag.durationMilliseconds,
            bitrate = tag.bitrate,
            sampleRate = tag.sampleRate,
            channels = tag.channels,
            fileLastModified = file.lastModified,
            fileAdded = file.dateAdded,
            folderId = folderId
        ).let(sortKeyUpdater::update)
    }
}
