package com.lonx.lyrico.domain.metadata

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HiddenEditFieldsTest {
    private val original = AudioTagData(
        title = "Original title",
        artist = "Original artist",
        album = "Original album",
        albumArtist = "Original album artist",
        genre = "Rock",
        date = "2024",
        language = "eng",
        trackNumber = "2/10",
        discNumber = 1,
        composer = "Composer",
        lyricist = "Lyricist",
        comment = "Comment",
        lyrics = "[00:01.00]Lyrics",
        copyright = "Copyright",
        rating = 80,
        replayGainTrackGain = "-5 dB",
        replayGainTrackPeak = "0.8",
        replayGainAlbumGain = "-6 dB",
        replayGainAlbumPeak = "0.9",
        replayGainReferenceLoudness = "89 dB",
        pictures = listOf(AudioPicture(byteArrayOf(1, 2, 3))),
        picUrl = "https://example.com/original.jpg",
        customFields = listOf(CustomTagField("MOOD", "Calm")),
    )

    @Test
    fun hiddenFieldsRetainOriginalValuesWhenDraftIsCleared() {
        assertEquals(original, AudioTagData().preserveHiddenEditFields(original, emptySet()))
    }

    @Test
    fun visibleFieldsCanBeClearedWithoutRestoringOriginalValues() {
        val cleared = AudioTagData()
        val visibleCodes = EditFieldRegistry.knownCodes + EditFieldRegistry.customTagCode("MOOD")

        assertEquals(cleared, cleared.preserveHiddenEditFields(original, visibleCodes))
    }

    @Test
    fun visibleEditsAndHiddenValuesAreMerged() {
        val draft = original.copy(title = "New title", artist = "New artist", rating = null)

        assertEquals(
            original.copy(title = "New title", rating = null),
            draft.preserveHiddenEditFields(original, setOf("title", "rating")),
        )
    }

    @Test
    fun customTagsRespectCaseInsensitiveVisibilityAndKeepRepeatedValues() {
        val base = original.copy(customFields = listOf(
            CustomTagField("mood", "Old mood"),
            CustomTagField("PRIVATE", "Original private value"),
        ))
        val draft = base.copy(customFields = listOf(
            CustomTagField("Mood", "Happy"),
            CustomTagField("MOOD", "Energetic"),
            CustomTagField("PRIVATE", "Changed private value"),
            CustomTagField("NEW", "New visible tag"),
            CustomTagField("HIDDEN", "New hidden tag"),
        ))

        val result = draft.preserveHiddenEditFields(base, setOf("tag:MOOD", "tag:NEW"))

        assertEquals(listOf(
            CustomTagField("Mood", "Happy"),
            CustomTagField("MOOD", "Energetic"),
            CustomTagField("NEW", "New visible tag"),
            CustomTagField("PRIVATE", "Original private value"),
        ), result.customFields)
    }

    @Test
    fun pictureVisibilityControlsEmbeddedPicturesAndRemoteSourceTogether() {
        val draft = original.copy(pictures = emptyList(), picUrl = null)

        assertEquals(original, draft.preserveHiddenEditFields(original, emptySet()))
        assertEquals(draft, draft.preserveHiddenEditFields(original, setOf("picture")))
    }

    @Test
    fun filePropertiesRemainFromDraft() {
        val draft = original.copy(
            fileName = "renamed.flac",
            durationMilliseconds = 123000,
            bitrate = 900,
            sampleRate = 48000,
            channels = 2,
            supportsTypedPictures = true,
        )

        assertEquals(draft, draft.preserveHiddenEditFields(original, emptySet()))
    }

    @Test
    fun missingOriginalSnapshotLeavesDraftUntouched() {
        assertSame(original, original.preserveHiddenEditFields(null, emptySet()))
    }
}
