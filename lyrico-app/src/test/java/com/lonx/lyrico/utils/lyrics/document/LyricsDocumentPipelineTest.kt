package com.lonx.lyrico.utils.lyrics.document

import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsDocumentPipelineTest {
    @Test
    fun ttmlParserPreservesAgentKeyAndTranslationLink() {
        val document = TtmlParser.parse(sampleTtml())

        assertEquals("zh-Hant", document.metadata.language)
        assertEquals("v1", document.agents.first().id)
        assertEquals("v1", document.tracks.first { it.type == LyricsTrackType.Original }.lines.first().agentId)
        assertEquals("L1", document.tracks.first { it.type == LyricsTrackType.Original }.lines.first().linkKey)

        val translation = document.tracks.first { it.type == LyricsTrackType.Translation }
        assertEquals("zh-Hans", translation.language)
        assertEquals("L1", translation.lines.first().linkKey)
    }

    @Test
    fun ttmlWriterKeepsAgentAndKeyAfterScriptConversion() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(text = "後來"),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML,
            conversionMode = ConversionMode.TRADITIONAL_TO_SIMPLIFIED
        ).orEmpty()

        assertTrue(output.contains("""ttm:agent="v1""""))
        assertTrue(output.contains("""itunes:key="L1""""))
        assertTrue(output.contains("后来"))
    }

    @Test
    fun removeTranslationDoesNotRemoveOriginalKeyOrAgent() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML,
            showTranslation = false
        ).orEmpty()

        assertFalse(output.contains("<translations>"))
        assertTrue(output.contains("""ttm:agent="v1""""))
        assertTrue(output.contains("""itunes:key="L1""""))
    }

    @Test
    fun removeEmptyOriginalLineRemovesLinkedTranslation() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(secondText = ""),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML,
            removeEmptyLines = true
        ).orEmpty()

        assertFalse(output.contains("""itunes:key="L2""""))
        assertFalse(output.contains("""for="L2""""))
        assertTrue(output.contains("""itunes:key="L1""""))
        assertTrue(output.contains("""for="L1""""))
    }

    @Test
    fun ttmlCanDowngradeToPlainLrc() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.PLAIN_LRC
        ).orEmpty()

        assertTrue(output.contains("[00:01.000]A"))
        assertFalse(output.contains("ttm:agent"))
    }

    @Test
    fun ttmlCanDowngradeToEnhancedLrcWithFinalWordEndTime() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.ENHANCED_LRC
        ).orEmpty()

        assertTrue(output.contains("[00:01.000]<00:01.000>A<00:02.000>"))
    }

    @Test
    fun rawResultEncoderUsesDocumentPipelineForTtmlVisibility() {
        val result = LyricsResult(
            tags = emptyMap(),
            original = emptyList(),
            translated = null,
            romanization = null,
            payloadType = LyricsPayloadType.RAW_TTML,
            rawTtml = sampleTtml()
        )
        val output = LyricsDocumentPipeline.processRawResult(
            result = result,
            config = LyricRenderConfig(
                format = LyricFormat.TTML,
                showRomanization = true,
                showTranslation = false,
                removeEmptyLines = true
            )
        ).orEmpty()

        assertTrue(output.contains("""ttm:agent="v1""""))
        assertTrue(output.contains("""itunes:key="L1""""))
        assertFalse(output.contains("<translations>"))
    }

    private fun sampleTtml(
        text: String = "A",
        secondText: String = "B"
    ): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xml:lang="zh-Hant">
              <head>
                <metadata>
                  <ttm:agent type="person" xml:id="v1"/>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <translations>
                      <translation xml:lang="zh-Hans">
                        <text for="L1">翻译</text>
                        <text for="L2">空行翻译</text>
                      </translation>
                    </translations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1">$text</p>
                  <p begin="3.000" end="4.000" itunes:key="L2" ttm:agent="v1">$secondText</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
    }
}
