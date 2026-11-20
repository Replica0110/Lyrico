package com.lonx.lyrico.ui.screens.batch

import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.domain.lyrics.LyricEncoder
import com.lonx.lyrico.worker.processor.EditTagsCustomField
import com.lonx.lyrico.worker.processor.EditTagsTaskConfig
import java.util.Locale

/** 预览和提交使用同一份按字段可见性过滤的编辑快照。 */
internal class BatchEditChanges(draft: BatchEditUiState, visibleFieldCodes: Set<String>) {
    val state: BatchEditUiState = draft.filterHiddenEditFields(visibleFieldCodes)
    private val customFields = state.customFields.filter { it.key.isNotBlank() }

    fun previewChanges(
        song: SongEntity,
        customTagValues: Map<String, String>
    ): List<BatchEditPreviewChange> {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun value(text: String?): String = text.orEmpty()
        fun addTextChange(
            changes: MutableList<BatchEditPreviewChange>,
            labelResId: Int,
            oldValue: String?,
            newValue: String
        ) {
            if (newValue != keep) {
                changes += BatchEditPreviewChange(
                    labelResId = labelResId,
                    customLabel = null,
                    oldValue = value(oldValue),
                    newValue = newValue
                )
            }
        }

        return buildList {
            addTextChange(this, BatchEditField.TITLE.labelResId, song.title, state.title)
            addTextChange(this, BatchEditField.ARTIST.labelResId, song.artist, state.artist)
            addTextChange(this, BatchEditField.ALBUM_ARTIST.labelResId, song.albumArtist, state.albumArtist)
            addTextChange(this, BatchEditField.ALBUM.labelResId, song.album, state.album)
            addTextChange(this, BatchEditField.DATE.labelResId, song.date, state.date)
            addTextChange(this, BatchEditField.LANGUAGE.labelResId, song.language, state.language)
            addTextChange(this, BatchEditField.GENRE.labelResId, song.genre, state.genre)
            addTextChange(this, BatchEditField.TRACK_NUMBER.labelResId, song.trackerNumber, state.trackNumber)
            addTextChange(this, BatchEditField.DISC_NUMBER.labelResId, song.discNumber?.toString(), state.discNumber)
            addTextChange(this, BatchEditField.COMPOSER.labelResId, song.composer, state.composer)
            addTextChange(this, BatchEditField.LYRICIST.labelResId, song.lyricist, state.lyricist)
            addTextChange(this, BatchEditField.COPYRIGHT.labelResId, song.copyright, state.copyright)
            addTextChange(this, BatchEditField.COMMENT.labelResId, song.comment, state.comment)
            addTextChange(this, BatchEditField.LYRICS.labelResId, song.lyrics, state.lyrics)
            addTextChange(this, BatchEditField.REPLAY_GAIN_TRACK_GAIN.labelResId, song.replayGainTrackGain, state.replayGainTrackGain)
            addTextChange(this, BatchEditField.REPLAY_GAIN_TRACK_PEAK.labelResId, song.replayGainTrackPeak, state.replayGainTrackPeak)
            addTextChange(this, BatchEditField.REPLAY_GAIN_ALBUM_GAIN.labelResId, song.replayGainAlbumGain, state.replayGainAlbumGain)
            addTextChange(this, BatchEditField.REPLAY_GAIN_ALBUM_PEAK.labelResId, song.replayGainAlbumPeak, state.replayGainAlbumPeak)
            addTextChange(this, BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS.labelResId, song.replayGainReferenceLoudness, state.replayGainReferenceLoudness)

            if (state.ratingModified) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.RATING.labelResId,
                        customLabel = null,
                        oldValue = song.rating?.toString().orEmpty(),
                        newValue = state.rating.toString()
                    )
                )
            }

            if (state.removeCover || state.coverUri != null) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.COVER.labelResId,
                        customLabel = null,
                        oldValue = "<current_cover>",
                        newValue = if (state.removeCover) "<remove_cover>" else state.coverUri?.toString().orEmpty()
                    )
                )
            }

            if (state.lyricsOffset != 0L && song.lyrics != null) {
                add(
                    BatchEditPreviewChange(
                        labelResId = R.string.label_lyrics_offset,
                        customLabel = null,
                        oldValue = song.lyrics,
                        newValue = LyricEncoder.shiftLyricsOffset(song.lyrics, state.lyricsOffset)
                    )
                )
            }

            customFields.forEach { field ->
                add(
                    BatchEditPreviewChange(
                        labelResId = null,
                        customLabel = field.key,
                        oldValue = customTagValues[CustomTagKey.normalize(field.key)].orEmpty(),
                        newValue = field.value
                    )
                )
            }
        }
    }

    fun toTaskConfig(): EditTagsTaskConfig = with(state) {
        EditTagsTaskConfig(
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            album = album,
            date = date,
            language = language,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber,
            composer = composer,
            lyricist = lyricist,
            copyright = copyright,
            comment = comment,
            lyrics = lyrics,
            rating = rating,
            ratingModified = ratingModified,
            coverUri = coverUri?.toString(),
            removeCover = removeCover,
            lyricsOffset = lyricsOffsetForTask,
            replayGainTrackGain = replayGainTrackGain,
            replayGainTrackPeak = replayGainTrackPeak,
            replayGainAlbumGain = replayGainAlbumGain,
            replayGainAlbumPeak = replayGainAlbumPeak,
            replayGainReferenceLoudness = replayGainReferenceLoudness,
            customFields = this@BatchEditChanges.customFields
                .map { EditTagsCustomField(key = it.key, value = it.value) }
        )
    }

    private fun BatchEditUiState.filterHiddenEditFields(
        visibleFieldCodes: Set<String>,
    ): BatchEditUiState {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun visible(code: String): Boolean = code in visibleFieldCodes

        return copy(
            title = if (visible("title")) title else keep,
            artist = if (visible("artist")) artist else keep,
            albumArtist = if (visible("album_artist")) albumArtist else keep,
            album = if (visible("album")) album else keep,
            date = if (visible("date")) date else keep,
            language = if (visible("language")) language else keep,
            genre = if (visible("genre")) genre else keep,
            trackNumber = if (visible("track_number")) trackNumber else keep,
            discNumber = if (visible("disc_number")) discNumber else keep,
            composer = if (visible("composer")) composer else keep,
            lyricist = if (visible("lyricist")) lyricist else keep,
            copyright = if (visible("copyright")) copyright else keep,
            comment = if (visible("comment")) comment else keep,
            lyrics = if (visible("lyrics")) lyrics else keep,
            rating = if (visible("rating")) rating else 0,
            ratingModified = visible("rating") && ratingModified,
            coverUri = if (visible("picture")) coverUri else null,
            removeCover = visible("picture") && removeCover,
            lyricsOffset = if (visible("lyrics_offset")) lyricsOffset else 0L,
            replayGainTrackGain = if (visible("track_gain")) replayGainTrackGain else keep,
            replayGainTrackPeak = if (visible("track_peak")) replayGainTrackPeak else keep,
            replayGainAlbumGain = if (visible("album_gain")) replayGainAlbumGain else keep,
            replayGainAlbumPeak = if (visible("album_peak")) replayGainAlbumPeak else keep,
            replayGainReferenceLoudness = if (visible("reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                keep
            },
            customFields = customFields
                .filter {
                    visible(EditFieldRegistry.customTagCode(it.key.uppercase(Locale.ROOT))) &&
                        it.value != keep
                }
                .distinctBy { it.key },
        )
    }
}
