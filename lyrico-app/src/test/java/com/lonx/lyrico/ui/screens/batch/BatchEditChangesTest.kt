package com.lonx.lyrico.ui.screens.batch

import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.worker.processor.EditTagsCustomField
import com.lonx.lyrico.worker.processor.EditTagsTaskConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchEditChangesTest {
    private val song = SongEntity(
        folderId = 1,
        mediaId = 1,
        filePath = "/music/song.flac",
        fileName = "song.flac",
        title = "Original title",
        artist = "Original artist",
        rating = 80,
        lyrics = "[00:01.00]Line",
    )

    @Test
    fun hiddenEditsAreAbsentFromPreviewAndSerializedTask() {
        val edits = BatchEditChanges(
            BatchEditUiState(
                title = "Hidden title",
                artist = "Hidden artist",
                rating = 0,
                ratingModified = true,
                coverUri = "content://cover",
                removeCover = true,
                lyricsOffset = 1000,
                replayGainTrackGain = "",
                customFields = listOf(CustomTagField("MOOD", "Happy")),
            ),
            emptySet(),
        )

        assertTrue(edits.previewChanges(song, emptyMap()).isEmpty())
        val config = edits.toTaskConfig()
        assertEquals(EditTagsTaskConfig(), config)
        val json = Json.encodeToString(EditTagsTaskConfig.serializer(), config)
        assertEquals(EditTagsTaskConfig(), Json.decodeFromString<EditTagsTaskConfig>(json))
    }

    @Test
    fun visibleTextFieldsPreserveTheirTaskMappingAndPreviewOrder() {
        val edits = BatchEditChanges(
            BatchEditUiState(
                title = "title", artist = "artist", albumArtist = "album artist", album = "album",
                date = "2026", language = "eng", genre = "genre", trackNumber = "2/10", discNumber = "3",
                composer = "composer", lyricist = "lyricist", copyright = "copyright", comment = "comment",
                lyrics = "lyrics", replayGainTrackGain = "-5 dB", replayGainTrackPeak = "0.8",
                replayGainAlbumGain = "-6 dB", replayGainAlbumPeak = "0.9", replayGainReferenceLoudness = "89 dB",
            ),
            EditFieldRegistry.knownCodes,
        )
        val expected = EditTagsTaskConfig(
            title = "title", artist = "artist", albumArtist = "album artist", album = "album",
            date = "2026", language = "eng", genre = "genre", trackNumber = "2/10", discNumber = "3",
            composer = "composer", lyricist = "lyricist", copyright = "copyright", comment = "comment",
            lyrics = "lyrics", replayGainTrackGain = "-5 dB", replayGainTrackPeak = "0.8",
            replayGainAlbumGain = "-6 dB", replayGainAlbumPeak = "0.9", replayGainReferenceLoudness = "89 dB",
        )

        assertEquals(expected, edits.toTaskConfig())
        assertEquals(
            listOf("title", "artist", "album artist", "album", "2026", "eng", "genre", "2/10", "3",
                "composer", "lyricist", "copyright", "comment", "lyrics", "-5 dB", "0.8", "-6 dB", "0.9", "89 dB"),
            edits.previewChanges(song, emptyMap()).map { it.newValue },
        )
    }

    @Test
    fun clearingVisibleTextAndRatingRemainsAnEditWhileKeepIsSkipped() {
        val edits = BatchEditChanges(
            BatchEditUiState(title = "", rating = 0, ratingModified = true),
            setOf("title", "artist", "rating"),
        )

        val preview = edits.previewChanges(song, emptyMap())
        assertEquals(listOf(BatchEditField.TITLE.labelResId, BatchEditField.RATING.labelResId), preview.map { it.labelResId })
        assertEquals(listOf("", "0"), preview.map { it.newValue })
        val config = edits.toTaskConfig()
        assertEquals("", config.title)
        assertEquals(EditTagsTaskConfig.KEEP_VALUE, config.artist)
        assertEquals(0, config.rating)
        assertTrue(config.ratingModified)
    }

    @Test
    fun coverSelectionAndRemovalKeepTheirExistingPriority() {
        val draft = BatchEditUiState(coverUri = "content://cover", removeCover = true)
        val edits = BatchEditChanges(draft, setOf("picture"))

        assertEquals("<remove_cover>", edits.previewChanges(song, emptyMap()).single().newValue)
        assertEquals("content://cover", edits.toTaskConfig().coverUri)
        assertTrue(edits.toTaskConfig().removeCover)

        val hidden = BatchEditChanges(draft, emptySet()).toTaskConfig()
        assertNull(hidden.coverUri)
        assertFalse(hidden.removeCover)
        val selection = BatchEditChanges(draft.copy(removeCover = false), setOf("picture"))
        assertEquals("content://cover", selection.previewChanges(song, emptyMap()).single().newValue)
    }

    @Test
    fun offsetKeepsTaskStringContractAndOnlyPreviewsSongsWithLyrics() {
        val edits = BatchEditChanges(BatchEditUiState(lyricsOffset = 1000), setOf("lyrics_offset"))

        assertEquals("1000", edits.toTaskConfig().lyricsOffset)
        val preview = edits.previewChanges(song, emptyMap()).single()
        assertEquals(R.string.label_lyrics_offset, preview.labelResId)
        assertEquals("[00:02.000]Line", preview.newValue)
        assertTrue(edits.previewChanges(song.copy(lyrics = null), emptyMap()).isEmpty())
        assertEquals("", BatchEditChanges(BatchEditUiState(), setOf("lyrics_offset")).toTaskConfig().lyricsOffset)
        assertEquals("", BatchEditChanges(BatchEditUiState(lyricsOffset = -1000), emptySet()).toTaskConfig().lyricsOffset)
    }

    @Test
    fun customFieldsKeepVisibilityRawKeyDeduplicationAndClearSemantics() {
        val edits = BatchEditChanges(
            BatchEditUiState(
                customFields = listOf(
                    CustomTagField("MOOD", ""),
                    CustomTagField("MOOD", "Discard duplicate"),
                    CustomTagField("mood", "Distinct raw key"),
                    CustomTagField("HIDDEN", "Hidden"),
                    CustomTagField("KEEP", EditTagsTaskConfig.KEEP_VALUE),
                    CustomTagField("", "Blank key"),
                ),
            ),
            setOf("MOOD", "KEEP", "").map(EditFieldRegistry::customTagCode).toSet(),
        )

        val expected = listOf(EditTagsCustomField("MOOD", ""), EditTagsCustomField("mood", "Distinct raw key"))
        assertEquals(expected, edits.toTaskConfig().customFields)
        val preview = edits.previewChanges(song, mapOf("MOOD" to "Calm"))
        assertEquals(expected.map { it.key }, preview.map { it.customLabel })
        assertEquals(expected.map { it.value }, preview.map { it.newValue })
        assertEquals(listOf("Calm", "Calm"), preview.map { it.oldValue })
    }

    @Test
    fun changingVisibilityDoesNotEraseTheOriginalDraft() {
        val draft = BatchEditUiState(title = "New title", replayGainTrackGain = "")
        val hidden = BatchEditChanges(draft, emptySet())
        val visible = BatchEditChanges(draft, setOf("title", "track_gain"))

        assertEquals(EditTagsTaskConfig.KEEP_VALUE, hidden.toTaskConfig().title)
        assertEquals("New title", visible.toTaskConfig().title)
        assertEquals("", visible.toTaskConfig().replayGainTrackGain)
        assertEquals("New title", draft.title)
        assertEquals("", draft.replayGainTrackGain)
    }
}
