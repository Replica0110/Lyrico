package com.lonx.lyrico.utils

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.ExtraMetadataTarget
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.model.ExtraWriteMode
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrics.model.SearchResultExtraKeys

class ExtraMetadataResolver(
    private val minimumScore: Double = 0.35
) {
    fun resolve(
        currentSong: SongEntity,
        scoredResults: List<ScoredSearchResult>,
        rules: List<ExtraMetadataWriteRule>,
        currentTagData: AudioTagData? = null
    ): AudioTagData {
        var output = AudioTagData()

        rules.asSequence()
            .filter { it.mode != ExtraWriteMode.DISABLED }
            .forEach { rule ->
                val value = findBestExtraValue(rule, scoredResults) ?: return@forEach
                output = writeIfAllowed(output, currentSong, currentTagData, rule, value)
            }

        return output
    }

    fun mergeNonNull(first: AudioTagData, second: AudioTagData): AudioTagData {
        return first.copy(
            title = second.title ?: first.title,
            artist = second.artist ?: first.artist,
            album = second.album ?: first.album,
            albumArtist = second.albumArtist ?: first.albumArtist,
            genre = second.genre ?: first.genre,
            date = second.date ?: first.date,
            language = second.language ?: first.language,
            trackNumber = second.trackNumber ?: first.trackNumber,
            discNumber = second.discNumber ?: first.discNumber,
            composer = second.composer ?: first.composer,
            lyricist = second.lyricist ?: first.lyricist,
            comment = second.comment ?: first.comment,
            lyrics = second.lyrics ?: first.lyrics,
            copyright = second.copyright ?: first.copyright,
            rating = second.rating ?: first.rating,
            replayGainTrackGain = second.replayGainTrackGain ?: first.replayGainTrackGain,
            replayGainTrackPeak = second.replayGainTrackPeak ?: first.replayGainTrackPeak,
            replayGainAlbumGain = second.replayGainAlbumGain ?: first.replayGainAlbumGain,
            replayGainAlbumPeak = second.replayGainAlbumPeak ?: first.replayGainAlbumPeak,
            replayGainReferenceLoudness = second.replayGainReferenceLoudness
                ?: first.replayGainReferenceLoudness,
            picUrl = second.picUrl ?: first.picUrl,
            pictures = if (second.pictures.isNotEmpty()) second.pictures else first.pictures,
            customFields = if (second.customFields.isNotEmpty()) second.customFields else first.customFields
        )
    }

    private fun findBestExtraValue(
        rule: ExtraMetadataWriteRule,
        scoredResults: List<ScoredSearchResult>
    ): String? {
        return scoredResults
            .asSequence()
            .filter { it.result.source == rule.source }
            .filter { source ->
                source.source?.extraFields
                    ?.any { it.key == rule.normalizedKey && it.writeable } != false
            }
            .filter { it.score >= minimumScore }
            .sortedByDescending { it.score }
            .mapNotNull { it.result.normalizedFields()[rule.normalizedKey]?.takeIf(String::isNotBlank) }
            .firstOrNull()
    }

    private fun writeIfAllowed(
        currentOutput: AudioTagData,
        currentSong: SongEntity,
        currentTagData: AudioTagData?,
        rule: ExtraMetadataWriteRule,
        value: String
    ): AudioTagData {
        return when (rule.target) {
            ExtraMetadataTarget.TITLE -> if (canWriteText(rule.mode, currentTagData?.title ?: currentSong.title)) currentOutput.copy(title = value) else currentOutput
            ExtraMetadataTarget.ARTIST -> if (canWriteText(rule.mode, currentTagData?.artist ?: currentSong.artist)) currentOutput.copy(artist = value) else currentOutput
            ExtraMetadataTarget.ALBUM -> if (canWriteText(rule.mode, currentTagData?.album ?: currentSong.album)) currentOutput.copy(album = value) else currentOutput
            ExtraMetadataTarget.ALBUM_ARTIST -> if (canWriteText(rule.mode, currentTagData?.albumArtist ?: currentSong.albumArtist)) currentOutput.copy(albumArtist = value) else currentOutput
            ExtraMetadataTarget.GENRE -> if (canWriteText(rule.mode, currentTagData?.genre ?: currentSong.genre)) currentOutput.copy(genre = value) else currentOutput
            ExtraMetadataTarget.DATE -> if (canWriteText(rule.mode, currentTagData?.date ?: currentSong.date)) currentOutput.copy(date = value) else currentOutput
            ExtraMetadataTarget.TRACK_NUMBER -> if (canWriteText(rule.mode, currentTagData?.trackNumber ?: currentSong.trackerNumber)) currentOutput.copy(trackNumber = value) else currentOutput
            ExtraMetadataTarget.DISC_NUMBER -> if (canWriteText(rule.mode, (currentTagData?.discNumber ?: currentSong.discNumber)?.toString())) currentOutput.copy(discNumber = value.toIntOrNull()) else currentOutput
            ExtraMetadataTarget.COMPOSER -> if (canWriteText(rule.mode, currentTagData?.composer ?: currentSong.composer)) currentOutput.copy(composer = value) else currentOutput
            ExtraMetadataTarget.LYRICIST -> if (canWriteText(rule.mode, currentTagData?.lyricist ?: currentSong.lyricist)) currentOutput.copy(lyricist = value) else currentOutput
            ExtraMetadataTarget.SUBTITLE -> writeGenericComment(currentOutput, currentSong, rule.mode, value)
            ExtraMetadataTarget.COMMENT -> writeComment(currentOutput, currentSong, rule, value)
            ExtraMetadataTarget.REPLAY_GAIN_TRACK_GAIN -> {
                val currentValue = currentTagData?.replayGainTrackGain
                    ?: currentSong.replayGainTrackGain
                if (canWriteReplayGain(rule.mode, currentValue)) {
                    currentOutput.copy(replayGainTrackGain = value)
                } else currentOutput
            }
            ExtraMetadataTarget.REPLAY_GAIN_TRACK_PEAK -> {
                val currentValue = currentTagData?.replayGainTrackPeak
                    ?: currentSong.replayGainTrackPeak
                if (canWriteReplayGain(rule.mode, currentValue)) {
                    currentOutput.copy(replayGainTrackPeak = value)
                } else currentOutput
            }
            ExtraMetadataTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> {
                val currentValue = currentTagData?.replayGainReferenceLoudness
                    ?: currentSong.replayGainReferenceLoudness
                if (canWriteReplayGain(rule.mode, currentValue)) {
                    currentOutput.copy(replayGainReferenceLoudness = value)
                } else currentOutput
            }
        }
    }

    private fun writeComment(
        currentOutput: AudioTagData,
        currentSong: SongEntity,
        rule: ExtraMetadataWriteRule,
        value: String
    ): AudioTagData {
        if (rule.normalizedKey != SearchResultExtraKeys.NETEASE_163_KEY) return currentOutput
        val currentComment = currentSong.comment
        val canWrite = when (rule.mode) {
            ExtraWriteMode.DISABLED -> false
            ExtraWriteMode.OVERWRITE -> true
            ExtraWriteMode.SUPPLEMENT -> currentComment.isNullOrBlank() || isNetease163Key(currentComment)
        }
        return if (canWrite) currentOutput.copy(comment = value) else currentOutput
    }

    private fun writeGenericComment(
        currentOutput: AudioTagData,
        currentSong: SongEntity,
        mode: ExtraWriteMode,
        value: String
    ): AudioTagData {
        val canWrite = when (mode) {
            ExtraWriteMode.DISABLED -> false
            ExtraWriteMode.OVERWRITE -> true
            ExtraWriteMode.SUPPLEMENT -> currentSong.comment.isNullOrBlank()
        }
        return if (canWrite) currentOutput.copy(comment = value) else currentOutput
    }

    private fun canWriteReplayGain(
        mode: ExtraWriteMode,
        currentValue: String?
    ): Boolean {
        return when (mode) {
            ExtraWriteMode.DISABLED -> false
            ExtraWriteMode.SUPPLEMENT -> currentValue.isNullOrBlank()
            ExtraWriteMode.OVERWRITE -> true
        }
    }

    private fun canWriteText(
        mode: ExtraWriteMode,
        currentValue: String?
    ): Boolean {
        return when (mode) {
            ExtraWriteMode.DISABLED -> false
            ExtraWriteMode.SUPPLEMENT -> currentValue.isNullOrBlank()
            ExtraWriteMode.OVERWRITE -> true
        }
    }

    private fun isNetease163Key(value: String?): Boolean {
        return value?.startsWith("163 key(Don't modify):") == true
    }
}
