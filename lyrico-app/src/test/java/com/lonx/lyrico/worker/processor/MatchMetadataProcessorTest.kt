package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.lyrics.SongSearchResult
import com.lonx.lyrico.data.model.metadata.MetadataApplyPolicy
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.model.metadata.MetadataWriteMode
import com.lonx.lyrico.data.model.metadata.SearchResultApplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MatchMetadataProcessorTest {
    @Test
    fun normalizedFieldsKeepsOnlyHostStandardFields() {
        val result = SongSearchResult(
            id = "song-1",
            pluginId = "com.example.source",
            pluginName = "Example",
            title = "Display Title",
            artist = "Display Artist",
            duration = 180000,
            fields = mapOf(
                "title" to "Field Title",
                "lyrics" to "lyric body",
                "song_mid" to "private-id"
            ),
            internal = mapOf("song_mid" to "private-id")
        )

        val normalized = result.normalizedFields()

        assertEquals("Field Title", normalized["title"])
        assertEquals("lyric body", normalized["lyrics"])
        assertEquals("Display Artist", normalized["artist"])
        assertFalse(normalized.containsKey("song_mid"))
        assertFalse(normalized.containsKey("duration"))
    }

    @Test
    fun applierSupplementsCommentWhenCurrentCommentIsBlank() {
        val patch = SearchResultApplier.buildPatch(
            current = AudioTagData(comment = ""),
            fields = mapOf("comment" to "candidate comment"),
            policy = MetadataApplyPolicy(
                fieldModes = mapOf(MetadataFieldTarget.COMMENT to MetadataWriteMode.SUPPLEMENT)
            )
        )

        assertEquals("candidate comment", patch.comment)
    }

    @Test
    fun applierDoesNotSupplementCommentWhenCurrentCommentExists() {
        val patch = SearchResultApplier.buildPatch(
            current = AudioTagData(comment = "existing comment"),
            fields = mapOf("comment" to "candidate comment"),
            policy = MetadataApplyPolicy(
                fieldModes = mapOf(MetadataFieldTarget.COMMENT to MetadataWriteMode.SUPPLEMENT)
            )
        )

        assertEquals(null, patch.comment)
    }
}
