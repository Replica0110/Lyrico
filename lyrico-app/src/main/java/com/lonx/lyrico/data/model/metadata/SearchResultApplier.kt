package com.lonx.lyrico.data.model.metadata

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.SongEntity

object SearchResultApplier {
    fun applyFields(
        current: AudioTagData,
        fields: Map<String, String>,
        policy: MetadataApplyPolicy
    ): AudioTagData {
        var output = current

        fields.forEach { (key, value) ->
            if (value.isBlank()) return@forEach
            val target = StandardPluginField.fromKey(key)?.target ?: return@forEach

            output = when (policy.modeOf(target)) {
                MetadataWriteMode.DISABLED -> output
                MetadataWriteMode.SUPPLEMENT -> {
                    if (output.isTargetBlank(target)) {
                        output.setTarget(target, value)
                    } else {
                        output
                    }
                }
                MetadataWriteMode.OVERWRITE -> output.setTarget(target, value)
            }
        }

        return output
    }

    fun buildPatch(
        current: AudioTagData,
        fields: Map<String, String>,
        policy: MetadataApplyPolicy
    ): AudioTagData {
        val applied = applyFields(current, fields, policy)
        return current.diffToPatch(applied)
    }
}

fun SongEntity.toAudioTagData(): AudioTagData {
    return AudioTagData(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        genre = genre,
        date = date,
        language = language,
        trackNumber = trackerNumber,
        discNumber = discNumber,
        composer = composer,
        lyricist = lyricist,
        comment = comment,
        lyrics = lyrics,
        copyright = copyright,
        rating = rating,
        replayGainTrackGain = replayGainTrackGain,
        replayGainTrackPeak = replayGainTrackPeak,
        replayGainAlbumGain = replayGainAlbumGain,
        replayGainAlbumPeak = replayGainAlbumPeak,
        replayGainReferenceLoudness = replayGainReferenceLoudness
    )
}

private fun AudioTagData.isTargetBlank(target: MetadataFieldTarget): Boolean {
    return when (target) {
        MetadataFieldTarget.TITLE -> title.isNullOrBlank()
        MetadataFieldTarget.ARTIST -> artist.isNullOrBlank()
        MetadataFieldTarget.ALBUM -> album.isNullOrBlank()
        MetadataFieldTarget.ALBUM_ARTIST -> albumArtist.isNullOrBlank()
        MetadataFieldTarget.GENRE -> genre.isNullOrBlank()
        MetadataFieldTarget.DATE -> date.isNullOrBlank()
        MetadataFieldTarget.TRACK_NUMBER -> trackNumber.isNullOrBlank()
        MetadataFieldTarget.DISC_NUMBER -> discNumber == null
        MetadataFieldTarget.COMPOSER -> composer.isNullOrBlank()
        MetadataFieldTarget.LYRICIST -> lyricist.isNullOrBlank()
        MetadataFieldTarget.COMMENT -> comment.isNullOrBlank()
        MetadataFieldTarget.LYRICS -> lyrics.isNullOrBlank()
        MetadataFieldTarget.TTML_LYRICS -> ttmlLyrics.isNullOrBlank()
        MetadataFieldTarget.COVER -> picUrl.isNullOrBlank() && pictures.isEmpty()
        MetadataFieldTarget.LANGUAGE -> language.isNullOrBlank()
        MetadataFieldTarget.COPYRIGHT -> copyright.isNullOrBlank()
        MetadataFieldTarget.RATING -> rating == null
        MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> replayGainTrackGain.isNullOrBlank()
        MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> replayGainTrackPeak.isNullOrBlank()
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN -> replayGainAlbumGain.isNullOrBlank()
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK -> replayGainAlbumPeak.isNullOrBlank()
        MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> replayGainReferenceLoudness.isNullOrBlank()
        MetadataFieldTarget.CUSTOM -> customFields.isEmpty()
    }
}

private fun AudioTagData.setTarget(
    target: MetadataFieldTarget,
    value: String
): AudioTagData {
    return when (target) {
        MetadataFieldTarget.TITLE -> copy(title = value)
        MetadataFieldTarget.ARTIST -> copy(artist = value)
        MetadataFieldTarget.ALBUM -> copy(album = value)
        MetadataFieldTarget.ALBUM_ARTIST -> copy(albumArtist = value)
        MetadataFieldTarget.GENRE -> copy(genre = value)
        MetadataFieldTarget.DATE -> copy(date = value)
        MetadataFieldTarget.TRACK_NUMBER -> copy(trackNumber = value)
        MetadataFieldTarget.DISC_NUMBER -> copy(discNumber = parseIntTag(value))
        MetadataFieldTarget.COMPOSER -> copy(composer = value)
        MetadataFieldTarget.LYRICIST -> copy(lyricist = value)
        MetadataFieldTarget.COMMENT -> copy(comment = value)
        MetadataFieldTarget.LYRICS -> copy(lyrics = value)
        MetadataFieldTarget.TTML_LYRICS -> copy(ttmlLyrics = value)
        MetadataFieldTarget.COVER -> copy(picUrl = value)
        MetadataFieldTarget.LANGUAGE -> copy(language = value)
        MetadataFieldTarget.COPYRIGHT -> copy(copyright = value)
        MetadataFieldTarget.RATING -> copy(rating = parseIntTag(value))
        MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> copy(replayGainTrackGain = value)
        MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> copy(replayGainTrackPeak = value)
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN -> copy(replayGainAlbumGain = value)
        MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK -> copy(replayGainAlbumPeak = value)
        MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> copy(replayGainReferenceLoudness = value)
        MetadataFieldTarget.CUSTOM -> this
    }
}

private fun AudioTagData.diffToPatch(applied: AudioTagData): AudioTagData {
    return AudioTagData(
        title = applied.title.takeIf { it != title },
        artist = applied.artist.takeIf { it != artist },
        album = applied.album.takeIf { it != album },
        albumArtist = applied.albumArtist.takeIf { it != albumArtist },
        genre = applied.genre.takeIf { it != genre },
        date = applied.date.takeIf { it != date },
        language = applied.language.takeIf { it != language },
        trackNumber = applied.trackNumber.takeIf { it != trackNumber },
        discNumber = applied.discNumber.takeIf { it != discNumber },
        composer = applied.composer.takeIf { it != composer },
        lyricist = applied.lyricist.takeIf { it != lyricist },
        comment = applied.comment.takeIf { it != comment },
        lyrics = applied.lyrics.takeIf { it != lyrics },
        ttmlLyrics = applied.ttmlLyrics.takeIf { it != ttmlLyrics },
        copyright = applied.copyright.takeIf { it != copyright },
        rating = applied.rating.takeIf { it != rating },
        replayGainTrackGain = applied.replayGainTrackGain.takeIf { it != replayGainTrackGain },
        replayGainTrackPeak = applied.replayGainTrackPeak.takeIf { it != replayGainTrackPeak },
        replayGainAlbumGain = applied.replayGainAlbumGain.takeIf { it != replayGainAlbumGain },
        replayGainAlbumPeak = applied.replayGainAlbumPeak.takeIf { it != replayGainAlbumPeak },
        replayGainReferenceLoudness = applied.replayGainReferenceLoudness
            .takeIf { it != replayGainReferenceLoudness },
        picUrl = applied.picUrl.takeIf { it != picUrl },
        customFields = applied.customFields.takeIf { it != customFields }.orEmpty()
    )
}

private fun parseIntTag(value: String): Int? {
    return value
        .substringBefore("/")
        .filter { it.isDigit() }
        .takeIf { it.isNotBlank() }
        ?.toIntOrNull()
}
