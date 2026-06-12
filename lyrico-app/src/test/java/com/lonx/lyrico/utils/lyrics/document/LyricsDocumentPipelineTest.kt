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
        assertTrue(output.contains("[00:01.000]翻译"))
        assertFalse(output.contains("ttm:agent"))
    }

    @Test
    fun ttmlCanDowngradeToEnhancedLrcWithFinalWordEndTime() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtml(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.ENHANCED_LRC
        ).orEmpty()

        assertTrue(output.contains("[00:01.000]<00:01.000>A<00:02.000>"))
    }

    @Test
    fun wordLevelTtmlMetadataTranslationSurvivesFormatConversion() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtmlWithMetadataTranslation(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.ENHANCED_LRC
        ).orEmpty()

        assertTrue(output.contains("[00:01.000]<00:01.000>I<00:01.200> <00:01.300>had<00:02.000>"))
        assertTrue(output.contains("[00:01.000]我曾拥有"))
    }

    @Test
    fun ttmlParserPreservesTimedSpaceSpan() {
        val document = TtmlParser.parse(sampleWordLevelTtmlWithTimedSpace())
        val line = document.tracks.first { it.type == LyricsTrackType.Original }.lines.first()

        assertEquals("I had", line.visibleText())
        assertEquals(listOf("I", " ", "had"), line.words.map { it.text })
    }

    @Test
    fun ttmlParserKeepsAppleBackgroundVocalsSeparateFromMainLine() {
        val document = TtmlParser.parse(sampleWordLevelTtmlWithBackground())
        val original = document.tracks.first { it.type == LyricsTrackType.Original }.lines.first()
        val background = document.tracks.first { it.type == LyricsTrackType.Background }.lines.first()

        assertEquals("it's me", original.visibleText())
        assertEquals("(I'm the problem)", background.visibleText())
        assertEquals(listOf("(I'm", " ", "the", " ", "problem)"), background.words.map { it.text })
    }

    @Test
    fun ttmlWriterPreservesAppleBackgroundRoleAfterProcessing() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtmlWithBackground(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML,
            removeEmptyLines = true
        ).orEmpty()

        assertTrue(output.contains("""ttm:role="x-bg""""))
        assertTrue(output.contains("(I'm"))
        assertTrue(output.contains("problem)"))
        assertTrue(output.contains(""">(I'm</span> <span begin=""""))
        assertTrue(output.contains(""">the</span> <span begin=""""))
    }

    @Test
    fun lineLevelTtmlDowngradesToPlainLinesWhenTargetIsEnhancedLrc() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleTtml(text = "在亿万人海相遇 有同样默契", secondText = "是多幺不容易"),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.ENHANCED_LRC
        ).orEmpty()

        assertTrue(output.contains("[00:01.000]在亿万人海相遇 有同样默契"))
        assertTrue(output.contains("[00:03.000]是多幺不容易"))
        assertFalse(output.contains("<00:01.000>在亿万人海相遇 有同样默契"))
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

    private fun sampleWordLevelTtml(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1">
                    <span begin="1.000" end="2.000">A</span>
                    <span begin="2.000" end="3.000">B</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
    }

    private fun sampleWordLevelTtmlWithTimedSpace(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1">
                    <span begin="1.000" end="1.200">I</span><span begin="1.200" end="1.300"> </span><span begin="1.300" end="2.000">had</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
    }

    private fun sampleWordLevelTtmlWithBackground(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="1.000" end="3.000" itunes:key="L1">
                    <span begin="1.000" end="1.300">it's</span><span begin="1.300" end="1.450"> </span><span begin="1.450" end="1.800">me</span>
                    <span ttm:role="x-bg"><span begin="1.600" end="1.900">(I'm</span> <span begin="2.000" end="2.200">the</span> <span begin="2.300" end="2.900">problem)</span></span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
    }

    private fun sampleWordLevelTtmlWithMetadataTranslation(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <translations>
                      <translation type="subtitle" xml:lang="zh-Hans">
                        <text for="L1">我曾拥有</text>
                      </translation>
                    </translations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div>
                  <p begin="1.000" end="2.000" itunes:key="L1" ttm:agent="v1">
                    <span begin="1.000" end="1.200">I</span><span begin="1.200" end="1.300"> </span><span begin="1.300" end="2.000">had</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
    }
}
