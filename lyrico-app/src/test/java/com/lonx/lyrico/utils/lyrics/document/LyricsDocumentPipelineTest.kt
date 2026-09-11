package com.lonx.lyrico.utils.lyrics.document

import com.lonx.lyrico.data.model.ConversionMode
import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsPayloadType
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsRuby
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import com.lonx.lyrico.data.model.lyrics.document.LyricsTrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsDocumentPipelineTest {
    @Test(expected = IllegalArgumentException::class)
    fun ttmlParserRejectsDoctypeWithoutDependingOnParserFeatures() {
        TtmlParser.parse(
            """
                <?xml version="1.0"?>
                <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1" end="2">&xxe;</p></div></body></tt>
            """.trimIndent()
        )
    }

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
    fun documentToStructuredResultPreservesTtmlLanguagesTimingAndDivWindow() {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xml:lang="zh-Hant"
                itunes:timing="Word">
              <head>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <translations>
                      <translation xml:lang="zh-Hans"><text for="L1">翻译</text></translation>
                    </translations>
                    <transliterations>
                      <transliteration xml:lang="zh-Latn"><text for="L1">roman</text></transliteration>
                    </transliterations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div begin="1.000" end="5.000">
                  <p begin="1.000" end="2.000" itunes:key="L1">原文</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = TtmlParser.parse(raw)
        val result = with(LyricsDocumentPipeline) { document.toLyricsResult() }

        assertEquals("Word", result.timing)
        assertEquals("zh-Hant", result.language)
        assertEquals("zh-Hans", result.translatedLang)
        assertEquals("zh-Latn", result.romanizationLang)
        assertEquals("1000", result.original.single().extensions["divBegin"])
        assertEquals("5000", result.original.single().extensions["divEnd"])
    }

    @Test
    fun ttmlSongPartDivsSurviveRoundTrip() {
        // 管线路径保真：div 的 itunes:songPart 解析进行扩展，写回时按值分组重建 div
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="1.000" end="2.000">intro</p>
                </div>
                <div itunes:songPart="Verse">
                  <p begin="3.000" end="4.000">A</p>
                  <p begin="5.000" end="6.000">B</p>
                </div>
                <div itunes:songPart="Chorus">
                  <p begin="7.000" end="8.000">C</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = TtmlParser.parse(raw)
        val originalLines = document.tracks.first { it.type == LyricsTrackType.Original }.lines
        // 解析侧：每行继承最近祖先 div 的 songPart
        assertNull(originalLines[0].songPartValueForTest())
        assertEquals("Verse", originalLines[1].songPartValueForTest())
        assertEquals("Verse", originalLines[2].songPartValueForTest())
        assertEquals("Chorus", originalLines[3].songPartValueForTest())

        // 写回侧：按 songPart 分组重建 div
        val output = LyricsDocumentPipeline.process(
            raw = raw,
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML,
            conversionMode = ConversionMode.NONE
        ).orEmpty()

        val defaultDiv = output.indexOf("    <div>\n")
        val verseDiv = output.indexOf("""    <div itunes:song-part="Verse">""")
        val chorusDiv = output.indexOf("""    <div itunes:song-part="Chorus">""")
        assertTrue(defaultDiv in 0 until verseDiv)
        assertTrue(verseDiv in defaultDiv until chorusDiv)
        assertTrue(chorusDiv > verseDiv)
        // songPart 不落在 <p> 上
        val pTags = Regex("""<p [^>]*>""").findAll(output).map { it.value }.toList()
        assertTrue(pTags.none { it.contains("songPart") || it.contains("song-part") })
    }

    /** 测试辅助：读取行扩展中的 songPart 值 */
    private fun com.lonx.lyrico.data.model.lyrics.document.LyricsDocumentLine.songPartValueForTest(): String? {
        return extensions.attributes.entries
            .firstOrNull { it.key.localName == "song-part" || it.key.localName == "songPart" }
            ?.value?.takeIf { it.isNotBlank() }
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
    fun amllCanonicalRolesMetadataAndAgentsSurviveRewrite() {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <head>
                <metadata>
                  <ttm:agent xml:id="v1" type="other"><ttm:name type="full">Guest</ttm:name></ttm:agent>
                </metadata>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <translations>
                      <translation type="replacement" xml:lang="zh-Hans"><text for="old-key">翻译</text></translation>
                    </translations>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body>
                <div itunes:song-part="Verse">
                  <p begin="1.000" end="2.000" itunes:key="old-key" ttm:agent="v1">原文<span ttm:role="x-roman">yuan wen</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = TtmlParser.parse(raw)
        assertEquals("yuan wen", document.tracks.first { it.type == LyricsTrackType.Romanization }.lines.single().visibleText())
        assertEquals("Verse", document.tracks.first { it.type == LyricsTrackType.Original }.lines.single().songPartValueForTest())

        val output = TtmlWriter.write(document, emptyList())
        assertTrue(output.contains("""itunes:key="L1"""))
        assertTrue(output.contains("""<div itunes:song-part="Verse">"""))
        assertTrue(output.contains("""<translation type="replacement" xml:lang="zh-Hans">"""))
        assertTrue(output.contains("""<ttm:agent xml:id="v1" type="other">"""))
        assertTrue(output.contains("""<ttm:name type="full">Guest</ttm:name>"""))
        assertTrue(output.contains("""<text for="L1">yuan wen</text>"""))
    }

    @Test
    fun ttmlRewritePreservesBodyDurationHeadExtensionsAndRuby() {
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:amll="http://www.example.com/ns/amll"
                xmlns:tts="http://www.w3.org/ns/ttml#styling"
                itunes:timing="Word">
              <head>
                <metadata>
                  <ttm:title>題名</ttm:title>
                  <amll:meta key="musicName" value="題名"/>
                </metadata>
                <metadata>
                  <iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
                    <songwriters><songwriter>作者</songwriter></songwriters>
                  </iTunesMetadata>
                </metadata>
              </head>
              <body dur="00:10.000">
                <div>
                  <p begin="1.000" end="2.000" itunes:key="L9">
                    <span tts:ruby="container"><span tts:ruby="base">所</span><span tts:ruby="textContainer"><span tts:ruby="text" begin="1.000" end="2.000">しょ</span></span></span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = TtmlParser.parse(raw)
        val word = document.tracks.first { it.type == LyricsTrackType.Original }.lines.single().words.single()
        assertEquals("所", word.text)
        // 单音节注音解析到 rubySyllables（时间随 rt 携带），旧的整串 rubyText 不再由 TTML 解析填充
        assertEquals(1, word.rubySyllables.size)
        assertEquals("しょ", word.rubySyllables.single().text)
        assertEquals(1000L, word.rubySyllables.single().startMs)
        assertEquals(2000L, word.rubySyllables.single().endMs)

        val output = TtmlWriter.write(document, emptyList())
        assertTrue(output.contains("""<body dur="00:10.000">"""))
        assertTrue(output.contains("""<ttm:title>題名</ttm:title>"""))
        assertTrue(output.contains("""<amll:meta key="musicName" value="題名"/>"""))
        assertTrue(output.contains("""<songwriter>作者</songwriter>"""))
        assertTrue(output.contains("""tts:ruby="base">所</span>"""))
        assertTrue(output.contains("""tts:ruby="text" begin="00:00:01.000" end="00:00:02.000">しょ</span>"""))
    }

    @Test
    fun ttmlMultiSyllableRubySurvivesRoundTrip() {
        // AMLL 规范多音节 Ruby：一个基文本「詮」对应两个 rt（せ / ん），各自独立时间戳
        val raw = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:tts="http://www.w3.org/ns/ttml#styling"
                itunes:timing="Word">
              <body>
                <div>
                  <p begin="27.690" end="28.000" itunes:key="L1">
                    <span tts:ruby="container"><span tts:ruby="base">詮</span><span tts:ruby="textContainer"><span tts:ruby="text" begin="27.820" end="27.880">せ</span><span tts:ruby="text" begin="27.880" end="27.950">ん</span></span></span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val document = TtmlParser.parse(raw)
        val word = document.tracks.first { it.type == LyricsTrackType.Original }.lines.single().words.single()
        assertEquals("詮", word.text)
        // 词时间 = 注音音节时间的 min/max（与 AMLL 官方解析器一致）
        assertEquals(27820L, word.startMs)
        assertEquals(27950L, word.endMs)
        assertEquals(listOf("せ", "ん"), word.rubySyllables.map { it.text })
        assertEquals(listOf(27820L, 27880L), word.rubySyllables.map { it.startMs })
        assertEquals(listOf(27880L, 27950L), word.rubySyllables.map { it.endMs })

        val output = TtmlWriter.write(document, emptyList())
        // 写回四层结构：container / base 均不写时间，两个 rt 各带自己的 begin/end
        assertTrue(output.contains("""tts:ruby="base">詮</span>"""))
        assertTrue(output.contains("""tts:ruby="text" begin="00:00:27.820" end="00:00:27.880">せ</span>"""))
        assertTrue(output.contains("""tts:ruby="text" begin="00:00:27.880" end="00:00:27.950">ん</span>"""))
        assertTrue(output.contains("""xmlns:tts="http://www.w3.org/ns/ttml#styling""""))

        // 二次解析保持不丢音节（往返幂等）
        val reparsed = TtmlParser.parse(output)
        val reparsedWord = reparsed.tracks.first { it.type == LyricsTrackType.Original }.lines.single().words.single()
        assertEquals(listOf("せ", "ん"), reparsedWord.rubySyllables.map { it.text })

        // document → structured：注音音节原样带出（供 structured 路径消费）
        val structured = with(LyricsDocumentPipeline) { reparsed.toLyricsResult() }
        val structuredWord = structured.original.single().words.single()
        assertEquals(listOf("せ", "ん"), structuredWord.ruby?.map { it.text })
    }

    @Test
    fun structuredWordRubyWritesMultiSyllableTtml() {
        // structured 协议词第 4 元素 → TTML 多音节 ruby（插件直供 structured 的路径）
        val result = LyricsResult(
            tags = mapOf("ti" to "題名", "ar" to "艺人A", "al" to "专辑A"),
            original = listOf(
                LyricsLine(
                    start = 27000L,
                    end = 28000L,
                    words = listOf(
                        LyricsWord(
                            start = 27820L,
                            end = 27950L,
                            text = "詮",
                            ruby = listOf(
                                LyricsRuby(start = 27820L, end = 27880L, text = "せ"),
                                LyricsRuby(start = 27880L, end = 27950L, text = "ん")
                            )
                        )
                    )
                )
            ),
            translated = null,
            romanization = null
        )
        val output = LyricsDocumentPipeline.processStructuredResult(
            result,
            LyricRenderConfig(format = LyricFormat.TTML)
        ).orEmpty()
        assertTrue(output.contains("""tts:ruby="base">詮</span>"""))
        assertTrue(output.contains("""tts:ruby="text" begin="00:00:27.820" end="00:00:27.880">せ</span>"""))
        assertTrue(output.contains("""tts:ruby="text" begin="00:00:27.880" end="00:00:27.950">ん</span>"""))
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
    fun lrcCanUpgradeToTtmlWithLinkedTranslation() {
        val output = LyricsDocumentPipeline.process(
            raw = """
                [00:01.000]Original line
                [00:01.000]翻译行
            """.trimIndent(),
            sourceFormat = LyricFormat.PLAIN_LRC,
            targetFormat = LyricFormat.TTML
        ).orEmpty()

        assertTrue(output.contains("""itunes:key="L1""""))
        assertTrue(output.contains("""<text for="L1">翻译行</text>"""))
    }

    @Test
    fun lrcCanUpgradeToTtmlWithRomanizationAndTranslation() {
        val output = LyricsDocumentPipeline.process(
            raw = """
                [00:01.000]Original line
                [00:01.000]Romanized line
                [00:01.000]翻译行
            """.trimIndent(),
            sourceFormat = LyricFormat.PLAIN_LRC,
            targetFormat = LyricFormat.TTML
        ).orEmpty()

        assertTrue(output.contains("<transliterations>"))
        assertTrue(output.contains(""">Romanized line</span>"""))
        assertTrue(output.contains("""<text for="L1">翻译行</text>"""))
        assertFalse(output.contains("""ttm:role="x-romanization""""))
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
    fun ttmlParserPreservesTextNodeSpacesBetweenTimedSpans() {
        val document = TtmlParser.parse(sampleWordLevelTtmlWithTextNodeSpaces())
        val line = document.tracks.first { it.type == LyricsTrackType.Original }.lines.first()

        assertEquals("Wait and pretend", line.visibleText())
        assertEquals(listOf("Wait", " ", "and", " ", "pretend"), line.words.map { it.text })
        assertEquals(listOf(124818L, null, 129158L, null, 129517L), line.words.map { it.startMs })
    }

    @Test
    fun ttmlWriterKeepsTextNodeSpacesOutsideTimedSpans() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtmlWithTextNodeSpaces(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.TTML
        ).orEmpty()

        assertTrue(output.contains(""">Wait</span> <span begin=""""))
        assertTrue(output.contains(""">and</span> <span begin=""""))
        assertFalse(output.contains(">Wait </span>"))
        assertFalse(output.contains(">and </span>"))
    }

    @Test
    fun ttmlTextNodeSpacesDoNotGetSyntheticTimestampsInEnhancedLrc() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtmlWithTextNodeSpaces(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.ENHANCED_LRC
        ).orEmpty()

        assertTrue(output.contains("[02:04.818]<02:04.818>Wait <02:09.158>and <02:09.517>pretend<02:11.436>"))
        assertFalse(output.contains("<02:04.818> "))
    }

    @Test
    fun ttmlTextNodeSpacesDoNotGetSyntheticTimestampsInVerbatimLrc() {
        val output = LyricsDocumentPipeline.process(
            raw = sampleWordLevelTtmlWithTextNodeSpaces(),
            sourceFormat = LyricFormat.TTML,
            targetFormat = LyricFormat.VERBATIM_LRC
        ).orEmpty()

        assertTrue(output.contains("[02:04.818]Wait [02:09.158]and [02:09.517]pretend[02:11.436]"))
        assertFalse(output.contains("[02:04.818] "))
    }

    @Test
    fun ttmlTextNodeSpacesDoNotGetSyntheticTimestampsWhenConvertedToLyricsResult() {
        val document = TtmlParser.parse(sampleWordLevelTtmlWithTextNodeSpaces())
        val result = with(LyricsDocumentPipeline) { document.toLyricsResult() }
        val line = result.original.first()

        assertEquals(listOf("Wait ", "and ", "pretend"), line.words.map { it.text })
        assertEquals(listOf(124818L, 129158L, 129517L), line.words.map { it.start })
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

    @Test
    fun structuredResultWritesBodyDurVerbatim() {
        // structured 载荷带 bodyDur → 经 toLyricsDocument 填入 bodyExtensions → <body dur="..."> 原文输出
        val result = structuredResult(bodyDur = "04:24.660")
        val output = LyricsDocumentPipeline.processStructuredResult(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        ).orEmpty()

        assertTrue(output.contains("""<body dur="04:24.660">"""))
    }

    @Test
    fun structuredResultWithoutBodyDurOmitsDurAttribute() {
        // 旧插件不传 bodyDur（默认空串）→ <body> 不带 dur，行为与改动前一致
        val result = structuredResult(bodyDur = "")
        val output = LyricsDocumentPipeline.processStructuredResult(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        ).orEmpty()

        assertFalse(Regex("""<body[^>]*\bdur=""" ).containsMatchIn(output))
    }

    /** 构造最小 structured 歌词结果（单行逐词），可选携带 bodyDur */
    private fun structuredResult(bodyDur: String): LyricsResult {
        return LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(1000L, 2000L, "原"))
                )
            ),
            translated = null,
            romanization = null,
            payloadType = LyricsPayloadType.STRUCTURED,
            bodyDur = bodyDur
        )
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

    private fun sampleWordLevelTtmlWithTextNodeSpaces(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="2:04.818" end="2:13.827" itunes:key="L11" ttm:agent="v1"><span begin="2:04.818" end="2:05.784">Wait</span> <span begin="2:09.158" end="2:09.517">and</span> <span begin="2:09.517" end="2:11.436">pretend</span></p>
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
