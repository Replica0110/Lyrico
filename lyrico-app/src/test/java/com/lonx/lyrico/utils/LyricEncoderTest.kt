package com.lonx.lyrico.utils

import com.lonx.lyrico.data.model.lyrics.LyricFormat
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.LyricsAgentEntry
import com.lonx.lyrico.data.model.lyrics.LyricsLine
import com.lonx.lyrico.data.model.lyrics.LyricsMetadataElement
import com.lonx.lyrico.data.model.lyrics.LyricsResult
import com.lonx.lyrico.data.model.lyrics.LyricsWord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricEncoderTest {
    @Test
    fun structuredWordLevelRomanizationIsWrittenToTtmlHeadSidecar() {
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(
                        LyricsWord(start = 1000L, end = 1500L, text = "眼"),
                        LyricsWord(start = 1500L, end = 2000L, text = "前")
                    )
                )
            ),
            translated = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "面前"))
                )
            ),
            romanization = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(
                        LyricsWord(start = 1000L, end = 1500L, text = "ngaan"),
                        LyricsWord(start = 1500L, end = 2000L, text = "cin")
                    )
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(
                format = LyricFormat.TTML,
                showRomanization = true,
                showTranslation = true
            )
        )

        // 音译写入 head <transliterations> sidecar（词级时间保留），正文 <p> 挂 itunes:key
        assertTrue(output.contains("""itunes:key="L1""""))
        assertTrue(output.contains("<transliterations>"))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.000" end="00:00:01.500">ngaan</span>"""))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.500" end="00:00:02.000">cin</span>"""))
        // 词级音译不再被拼成整行内联 x-romanization
        assertFalse(output.contains("""ttm:role="x-romanization""""))
        // 翻译为整行内联，保持不变
        assertTrue(output.contains("""ttm:role="x-translation">面前</span>"""))
    }

    @Test
    fun structuredWholeLineRomanizationKeepsSingleSidecarSpan() {
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼"))
                )
            ),
            translated = null,
            romanization = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "ngaan"))
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(
                format = LyricFormat.TTML,
                showRomanization = true,
                showTranslation = false
            )
        )

        assertTrue(output.contains("<transliterations>"))
        assertTrue(output.contains("""<span xmlns="http://www.w3.org/ns/ttml" begin="00:00:01.000" end="00:00:02.000">ngaan</span>"""))
        assertFalse(output.contains("""ttm:role="x-romanization""""))
    }

    // ---------- structured 协议扩展：行级属性 / agents / metadata 写回 ----------

    @Test
    fun structuredLineExtensionsWrittenToPTag() {
        // 行级扩展属性（ttm:agent 等）原样输出到 <p> 标签
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼前")),
                    extensions = mapOf("ttm:agent" to "v1")
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        assertTrue(output.contains("""<p begin="00:00:01.000" end="00:00:02.000" ttm:agent="v1">"""))
    }

    @Test
    fun songPartGroupedIntoDivs() {
        // itunes:songPart 从行扩展提取，按值分组重建 <div itunes:songPart="...">，不输出到 <p>
        fun line(start: Long, songPart: String?) = LyricsLine(
            start = start,
            end = start + 1000L,
            words = listOf(LyricsWord(start = start, end = start + 1000L, text = "词")),
            extensions = if (songPart != null) mapOf("itunes:songPart" to songPart) else emptyMap()
        )

        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                line(1000L, null),      // 默认 div
                line(2000L, "Verse"),   // Verse div
                line(3000L, "Verse"),   // 同组，不重复开 div
                line(4000L, "Chorus"),  // 切换 Chorus div
                line(5000L, null)       // 回到默认 div
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        // 分组 div：默认 → Verse → Chorus → 默认（连续相同 songPart 归入同一 div）
        val defaultDivIndex = output.indexOf("    <div>\n")
        val verseDivIndex = output.indexOf("""    <div itunes:songPart="Verse">""")
        val chorusDivIndex = output.indexOf("""    <div itunes:songPart="Chorus">""")
        val lastDefaultDivIndex = output.lastIndexOf("    <div>\n")

        assertTrue(defaultDivIndex in 0 until verseDivIndex)
        assertTrue(verseDivIndex in defaultDivIndex until chorusDivIndex)
        assertTrue(chorusDivIndex in verseDivIndex until lastDefaultDivIndex)
        assertTrue(lastDefaultDivIndex > chorusDivIndex)

        // songPart 是 div 属性，<p> 上不得出现
        val pTags = Regex("""<p [^>]*>""").findAll(output).map { it.value }.toList()
        assertTrue(pTags.none { it.contains("songPart") })
    }

    @Test
    fun agentsWrittenToHeadAsTtmlAgent() {
        // agents → head <ttm:agent xml:id="..." type="..."><ttm:name type="full">...</ttm:name></ttm:agent>
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼前"))
                )
            ),
            agents = listOf(
                LyricsAgentEntry(id = "v1", type = "person", name = "演唱者A"),
                LyricsAgentEntry(id = "v1000", type = "group")
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        assertTrue(output.contains("""<ttm:agent xml:id="v1" type="person">"""))
        assertTrue(output.contains("""<ttm:name type="full">演唱者A</ttm:name>"""))
        // 无名字的 agent 自闭合
        assertTrue(output.contains("""<ttm:agent xml:id="v1000" type="group"/>"""))
        // head 在 body 之前
        assertTrue(output.indexOf("<head>") < output.indexOf("<body>"))
    }

    @Test
    fun officialSongwritersWrittenToItunesMetadata() {
        // 官方 songwriters 结构 → <iTunesMetadata> 容器内输出
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼前"))
                )
            ),
            metadata = listOf(
                LyricsMetadataElement(
                    name = "songwriters",
                    children = listOf(
                        LyricsMetadataElement(name = "songwriter", text = "BuzzY.D"),
                        LyricsMetadataElement(name = "songwriter", text = "NKidd")
                    )
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        assertTrue(output.contains("<iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">"))
        assertTrue(output.contains("<songwriters>"))
        assertTrue(output.contains("<songwriter>BuzzY.D</songwriter>"))
        assertTrue(output.contains("<songwriter>NKidd</songwriter>"))
    }

    @Test
    fun passthroughMetadataWrittenWithRootNamespace() {
        // 非官方元素透传：带前缀的元素在根节点补 xmlns 声明
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼前"))
                )
            ),
            metadata = listOf(
                LyricsMetadataElement(
                    name = "amll:meta",
                    namespace = "http://www.example.com/ns/amll",
                    attributes = mapOf("key" to "musicName", "value" to "歌曲名")
                ),
                LyricsMetadataElement(
                    name = "myExt",
                    attributes = mapOf("foo" to "bar"),
                    children = listOf(LyricsMetadataElement(name = "item", text = "a"))
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        // 根节点补 amll 命名空间声明
        assertTrue(output.contains("""xmlns:amll="http://www.example.com/ns/amll""""))
        // amll:meta 自闭合（无 text 无 children），属性原样输出
        assertTrue(output.contains("""<amll:meta key="musicName" value="歌曲名"/>"""))
        // 无前缀透传元素与嵌套 children
        assertTrue(output.contains("""<myExt foo="bar">"""))
        assertTrue(output.contains("<item>a</item>"))
        assertTrue(output.contains("</myExt>"))
    }

    @Test
    fun noHeadEmittedWhenNoExtensions() {
        // 无 agents/metadata/音译时不输出 <head>（与旧版行为一致）
        val result = LyricsResult(
            tags = emptyMap(),
            original = listOf(
                LyricsLine(
                    start = 1000L,
                    end = 2000L,
                    words = listOf(LyricsWord(start = 1000L, end = 2000L, text = "眼前"))
                )
            )
        )

        val output = LyricEncoder.encode(
            result = result,
            config = LyricRenderConfig(format = LyricFormat.TTML)
        )

        assertFalse(output.contains("<head>"))
        assertTrue(output.contains("<body>"))
        assertTrue(output.contains("    <div>\n"))
        assertTrue(output.contains("    </div>\n"))
    }
}
