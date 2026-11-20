package com.lonx.lyrico.data.song.mapper

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.song.scan.SongFile
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.domain.lyrics.LyricsSearchTextExtractor

class SongMetadataMapper(
    private val sortKeyUpdater: SortKeyUpdater
) {
    /** Synchronize a complete, successfully read file snapshot, not a partial edit. */
    fun applyAudioTagData(
        old: SongEntity,
        tag: AudioTagData,
        fileLastModified: Long = System.currentTimeMillis()
    ): SongEntity {
        val lyrics = tag.lyrics
        return old.copy(
            title = tag.title,
            artist = tag.artist,
            albumArtist = tag.albumArtist,
            album = tag.album,
            genre = tag.genre,
            date = tag.date,
            trackerNumber = tag.trackNumber,
            discNumber = tag.discNumber,
            composer = tag.composer,
            lyricist = tag.lyricist,
            comment = tag.comment,
            lyrics = lyrics,
            lyricSearchText = LyricsSearchTextExtractor.toSearchText(lyrics),
            language = tag.language,
            copyright = tag.copyright,
            rating = tag.rating,
            // These are read back from the saved file; null means the tag was removed.
            replayGainTrackGain = tag.replayGainTrackGain,
            replayGainTrackPeak = tag.replayGainTrackPeak,
            replayGainAlbumGain = tag.replayGainAlbumGain,
            replayGainAlbumPeak = tag.replayGainAlbumPeak,
            replayGainReferenceLoudness = tag.replayGainReferenceLoudness,
            fileLastModified = fileLastModified
        ).let(sortKeyUpdater::update)
    }

    fun fromScannedFile(
        file: SongFile,
        tag: AudioTagData,
        folderId: Long,
        existingId: Long = 0L,
        source: String = "MEDIA_STORE",
        indexLyrics: Boolean = false
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
            lyricSearchText = if (indexLyrics) {
                LyricsSearchTextExtractor.toSearchText(tag.lyrics).orEmpty()
            } else {
                null
            },
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
