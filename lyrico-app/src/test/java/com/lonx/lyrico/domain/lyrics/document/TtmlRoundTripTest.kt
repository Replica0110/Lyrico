package com.lonx.lyrico.domain.lyrics.document

import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.document.LyricsDocument
import com.lonx.lyrico.data.lyrics.document.LyricsTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class TtmlRoundTripTest {
    @Test
    fun `mixed text and begin only words survive on original translation and romanization`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                <translations><translation><text for="a" begin="1s" end="2.5s">Hello <span begin="2s">world</span>!</text></translation></translations>
                <transliterations><transliteration><text for="a">Hello <span begin="2s">world</span>!</text></transliteration></transliterations>
              </iTunesMetadata></metadata></head>
              <body><div><p begin="1s" end="3s" itunes:key="a">Hello <span begin="2s" end="3s">world</span>!</p></div></body>
            </tt>
        """.trimIndent()
        val reread = TtmlParser.parse(TtmlWriter.write(TtmlParser.parse(raw)))
        for (track in listOf(LyricsTrackType.Original, LyricsTrackType.Translation, LyricsTrackType.Romanization)) {
            val line = reread.tracks.single { it.type == track }.lines.single()
            assertEquals("Hello world!", line.visibleText())
            assertEquals(2000L, line.words.single { it.startMs != null }.startMs)
        }
        val translation = reread.tracks.single { it.type == LyricsTrackType.Translation }.lines.single()
        assertEquals(2500L, translation.endMs)
        assertNull(translation.words.single { it.startMs != null }.endMs)
    }

    @Test
    fun `word timing and escaped text survive writing and parsing`() {
        val document = roundTrip()
        val line = document.lines(LyricsTrackType.Original).single()

        assertEquals(1000L, line.startMs)
        assertEquals(3000L, line.endMs)
        assertEquals(listOf("你&", "好"), line.words.map { it.text })
        assertEquals(listOf(1000L, 2000L), line.words.map { it.startMs })
        assertEquals(listOf(2000L, 3000L), line.words.map { it.endMs })
        assertEquals("Word", document.metadata.timing)
    }

    @Test
    fun `sidecar tracks retain language timing and links to normalized keys`() {
        val document = roundTrip()
        val original = document.lines(LyricsTrackType.Original).single()
        val translation = document.tracks.single { it.type == LyricsTrackType.Translation }
        val romanization = document.tracks.single { it.type == LyricsTrackType.Romanization }

        assertEquals("L1", original.linkKey)
        assertEquals("en", translation.language)
        assertEquals("Hello", translation.lines.single().text)
        assertEquals(original.linkKey, translation.lines.single().linkKey)
        assertEquals("zh-Latn", romanization.language)
        assertEquals(original.linkKey, romanization.lines.single().linkKey)
        assertEquals(listOf(1000L, 2000L), romanization.lines.single().words.mapNotNull { it.startMs })
    }

    @Test
    fun `agents song parts and paragraph timing survive a round trip`() {
        val document = roundTrip()
        val line = document.lines(LyricsTrackType.Original).single()

        assertEquals("singer", line.agentId)
        assertEquals("Singer", document.agents.single().name)
        assertEquals("person", document.agents.single().rawType)
        assertEquals("Chorus", line.extensions.attributes.entries.single { it.key.localName == "song-part" }.value)
        assertEquals("500", line.extensions.attributes.entries.single { it.key.localName == "divBegin" }.value)
        assertEquals("4000", line.extensions.attributes.entries.single { it.key.localName == "divEnd" }.value)
    }

    @Test
    fun `custom root attributes and nested metadata preserve namespaces`() {
        val document = roundTrip()

        assertEquals("value&more", document.extensions.attributes.entries.single { it.key.localName == "flag" }.value)
        val metadata = document.headMetadataElements.single { it.name.localName == "note" }
        assertEquals("urn:lyrico:test", metadata.name.namespaceUri)
        assertEquals("detail", metadata.children.single().text)
        assertEquals("urn:lyrico:test", metadata.children.single().name.namespaceUri)
    }

    @Test
    fun `ruby annotations retain base text and explicit syllable times`() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:tts="http://www.w3.org/ns/ttml#styling">
              <body><div><p begin="1s" end="3s"><span tts:ruby="container"><span tts:ruby="base">你好</span><span tts:ruby="textContainer"><span tts:ruby="text" begin="1s" end="2s">ni</span><span tts:ruby="text" begin="2s" end="3s">hao</span></span></span></p></div></body>
            </tt>
        """.trimIndent()
        val word = roundTrip(raw).lines(LyricsTrackType.Original).single().words.single()

        assertEquals("你好", word.text)
        assertEquals(listOf("ni", "hao"), word.ruby.map { it.text })
        assertEquals(listOf(1000L, 2000L), word.ruby.map { it.startMs })
        assertEquals(listOf(2000L, 3000L), word.ruby.map { it.endMs })
    }

    @Test
    fun `tag filtering removes linked translation and romanization lines`() {
        assertEquals("和声", TtmlParser.parse(SOURCE).lines(LyricsTrackType.Background).single().visibleText())
        val output = LyricsDocumentPipeline.process(
            raw = SOURCE,
            sourceFormat = LyricsFormat.TTML,
            targetFormat = LyricsFormat.TTML,
            removeTagLineKeywords = listOf("你&好")
        )
        val filtered = RemoveTagLinesPostProcessor(listOf("你&好")).process(TtmlParser.parse(SOURCE))

        assertTrue(filtered.lines(LyricsTrackType.Original).isEmpty())
        assertTrue(filtered.lines(LyricsTrackType.Translation).isEmpty())
        assertTrue(filtered.lines(LyricsTrackType.Romanization).isEmpty())
        assertTrue(filtered.lines(LyricsTrackType.Background).isEmpty())
        assertNull(output)
        assertEquals("", TtmlWriter.write(filtered))
    }

    @Test
    fun `structured mapping retains agents paragraph extensions and word times`() {
        val original = TtmlParser.parse(SOURCE)
        val mapped = original.toLyricsResult().toLyricsDocument()
        val line = mapped.lines(LyricsTrackType.Original).single()

        assertEquals("singer", line.agentId)
        assertEquals(listOf(1000L, 2000L), line.words.map { it.startMs })
        assertEquals("Chorus", line.extensions.attributes.entries.single { it.key.localName == "song-part" }.value)
        assertEquals("500", line.extensions.attributes.entries.single { it.key.localName == "divBegin" }.value)
        assertEquals("singer", mapped.agents.single().id)
        assertEquals("Hello", mapped.lines(LyricsTrackType.Translation).single().visibleText())
    }

    private fun roundTrip(raw: String = SOURCE): LyricsDocument =
        TtmlParser.parse(TtmlWriter.write(TtmlParser.parse(raw)))

    private fun LyricsDocument.lines(type: LyricsTrackType) =
        tracks.filter { it.type == type }.flatMap { it.lines }

    private companion object {
        val SOURCE = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:test="urn:lyrico:test" itunes:timing="Word" test:flag="value&amp;more">
              <head><metadata>
                <ttm:agent xml:id="singer" type="person"><ttm:name type="full">Singer</ttm:name></ttm:agent>
                <test:note><test:detail>detail</test:detail></test:note>
                <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                  <translations><translation type="subtitle" xml:lang="en"><text for="source-key">Hello</text></translation></translations>
                  <transliterations><transliteration xml:lang="zh-Latn"><text for="source-key"><span xmlns="http://www.w3.org/ns/ttml" begin="1s" end="2s">ni</span><span xmlns="http://www.w3.org/ns/ttml" begin="2s" end="3s">hao</span></text></transliteration></transliterations>
                </iTunesMetadata>
              </metadata></head>
              <body><div itunes:song-part="Chorus" begin="0.5s" end="4s"><p begin="1s" end="3s" itunes:key="source-key" ttm:agent="singer"><span begin="1s" end="2s">你&amp;</span><span begin="2s" end="3s">好</span><span ttm:role="x-bg" begin="1s" end="2s">和声</span></p></div></body>
            </tt>
        """.trimIndent()
    }
}
