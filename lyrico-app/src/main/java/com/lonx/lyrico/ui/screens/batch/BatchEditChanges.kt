package com.lonx.lyrico.ui.screens.batch

import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.domain.lyrics.LyricsTextTransform
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
            BatchEditField.entries
                .filter { it.isText }
                .forEach { field ->
                    addTextChange(this, field.labelResId, field.readSongValue(song), field.readDraftValue(state))
                }

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
                        newValue = LyricsTextTransform.shiftOffset(song.lyrics, state.lyricsOffset)
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

        // 封面、评级、歌词偏移由专用控件维护，按各自的可见开关回落。
        var state = copy(
            rating = if (visible(BatchEditField.RATING.code)) rating else 0,
            ratingModified = visible(BatchEditField.RATING.code) && ratingModified,
            coverUri = if (visible(BatchEditField.COVER.code)) coverUri else null,
            removeCover = visible(BatchEditField.COVER.code) && removeCover,
            lyricsOffset = if (visible(LYRICS_OFFSET_CODE)) lyricsOffset else 0L,
            customFields = customFields
                .filter {
                    visible(EditFieldRegistry.customTagCode(it.key.uppercase(Locale.ROOT))) &&
                        it.value != keep
                }
                .distinctBy { it.key },
        )

        BatchEditField.entries
            .filter { it.isText }
            .forEach { field ->
                if (!visible(field.code)) state = state.withDraftValue(field, keep)
            }

        return state
    }

    private companion object {
        const val LYRICS_OFFSET_CODE = "lyrics_offset"
    }
}
