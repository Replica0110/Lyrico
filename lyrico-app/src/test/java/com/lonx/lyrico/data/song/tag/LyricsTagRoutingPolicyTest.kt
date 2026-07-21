package com.lonx.lyrico.data.song.tag

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTagRoutingPolicyTest {
    @Test
    fun preferredTargetUsesTtmlLyricsOnlyForDetectedTtmlWhenEnabled() {
        assertEquals(
            MetadataFieldTarget.TTML_LYRICS,
            LyricsTagRoutingPolicy.preferredTarget(TTML, preferTtmlLyricsTag = true)
        )
        assertEquals(
            MetadataFieldTarget.LYRICS,
            LyricsTagRoutingPolicy.preferredTarget(ELRC, preferTtmlLyricsTag = true)
        )
        assertEquals(
            MetadataFieldTarget.LYRICS,
            LyricsTagRoutingPolicy.preferredTarget(TTML, preferTtmlLyricsTag = false)
        )
    }

    @Test
    fun routeMovesTtmlMutationWithoutClearingLyrics() {
        val routed = LyricsTagRoutingPolicy.route(
            mutation = AudioTagMutation(
                mode = AudioTagMutationMode.Patch,
                fields = mapOf(AudioTagFieldKey.Lyrics to FieldMutation.Set(TTML))
            ),
            preferTtmlLyricsTag = true
        )

        assertFalse(routed.fields.containsKey(AudioTagFieldKey.Lyrics))
        assertEquals(FieldMutation.Set(TTML), routed.fields[AudioTagFieldKey.TtmlLyrics])
    }

    @Test
    fun routeKeepsNonTtmlLyricsInOriginalTag() {
        val routed = LyricsTagRoutingPolicy.route(
            mutation = AudioTagMutation(
                mode = AudioTagMutationMode.Patch,
                fields = mapOf(AudioTagFieldKey.Lyrics to FieldMutation.Set(ELRC))
            ),
            preferTtmlLyricsTag = true
        )

        assertEquals(FieldMutation.Set(ELRC), routed.fields[AudioTagFieldKey.Lyrics])
        assertFalse(routed.fields.containsKey(AudioTagFieldKey.TtmlLyrics))
    }

    @Test
    fun routeRejectsNonTtmlContentInTtmlLyricsButAllowsClearingIt() {
        val rejected = LyricsTagRoutingPolicy.route(
            mutation = AudioTagMutation(
                mode = AudioTagMutationMode.Patch,
                fields = mapOf(AudioTagFieldKey.TtmlLyrics to FieldMutation.Set(ELRC))
            ),
            preferTtmlLyricsTag = false
        )
        val cleared = LyricsTagRoutingPolicy.route(
            mutation = AudioTagMutation(
                mode = AudioTagMutationMode.Patch,
                fields = mapOf(AudioTagFieldKey.TtmlLyrics to FieldMutation.Clear)
            ),
            preferTtmlLyricsTag = false
        )

        assertTrue(rejected.fields.isEmpty())
        assertEquals(FieldMutation.Clear, cleared.fields[AudioTagFieldKey.TtmlLyrics])
    }

    @Test
    fun tagMapBuilderWritesTheStandardTtmlLyricsKey() {
        val tags = TagMapBuilder().build(
            uri = "content://audio/song.flac",
            current = AudioTagData(),
            mutation = AudioTagMutation(
                mode = AudioTagMutationMode.Patch,
                fields = mapOf(AudioTagFieldKey.TtmlLyrics to FieldMutation.Set(TTML))
            )
        )

        assertEquals(TTML, tags["TTML LYRICS"])
        assertFalse(tags.containsKey("LYRICS"))
    }

    private companion object {
        const val TTML = """<tt xmlns="http://www.w3.org/ns/ttml"><body /></tt>"""
        const val ELRC = "[00:00.00]<00:00.00>Hello"
    }
}
