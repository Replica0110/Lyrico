package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricFormat
import com.lonx.lyrico.data.lyrics.LyricsColumnMapping
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProcessingTest {

    @Test
    fun `空白歌词不产生改动`() {
        assertNull(LyricsProcessing.apply("  \n ", LyricsProcessingOptions(removeEmptyLines = true)))
    }

    @Test
    fun `同格式且无文本操作时不重复写回`() {
        val raw = "[00:01.00]第一句"
        assertNull(
            LyricsProcessing.apply(raw, LyricsProcessingOptions(targetFormat = LyricFormat.PLAIN_LRC))
        )
    }

    @Test
    fun `整理列序按映射交换同时间戳的两行`() {
        val raw = "[00:01.00]上行\n[00:01.00]下行\n"
        val options = LyricsProcessingOptions(
            formatLineOrder = true,
            twoColumnMapping = LyricsColumnMapping(sourceCount = 2, order = listOf(1, 0)),
        )

        assertEquals("[00:01.00]下行\n[00:01.00]上行\n", LyricsProcessing.apply(raw, options))
    }

    @Test
    fun `列序未变化时返回 null`() {
        val raw = "[00:01.00]上行\n[00:01.00]下行\n"

        assertNull(LyricsProcessing.apply(raw, LyricsProcessingOptions(formatLineOrder = true)))
    }

    @Test
    fun `去除空行与只有时间戳的占位行`() {
        val raw = "[00:01.00]第一句\n[00:02.00]\n[00:03.00]第二句\n"

        val result = LyricsProcessing.apply(raw, LyricsProcessingOptions(removeEmptyLines = true))

        assertEquals("[00:01.00]第一句\n[00:03.00]第二句\n", result)
    }

    @Test
    fun `去除标签行按调用方给出的关键词`() {
        val raw = "作词：某人\n[00:01.00]第一句\n"

        val result = LyricsProcessing.apply(
            raw = raw,
            options = LyricsProcessingOptions(removeTagLines = true),
            tagLineKeywords = listOf("作词："),
        )

        assertEquals("[00:01.00]第一句\n", result)
    }

    @Test
    fun `无时间戳的纯文本也能去除空行`() {
        val raw = "第一句\n\n第二句\n"

        assertEquals(
            "第一句\n第二句\n",
            LyricsProcessing.apply(raw, LyricsProcessingOptions(removeEmptyLines = true))
        )
    }

    @Test
    fun `无时间戳的纯文本也能去除标签行`() {
        val raw = "作词：某人\n第一句\n"

        assertEquals(
            "第一句\n",
            LyricsProcessing.apply(
                raw,
                LyricsProcessingOptions(removeTagLines = true),
                tagLineKeywords = listOf("作词：")
            )
        )
    }

    @Test
    fun `转换为 TTML 保留时间轴结构`() {
        val raw = "[00:01.00]第一句\n[00:02.50]第二句\n"

        val converted = requireNotNull(LyricsFormatConverter.convert(raw, LyricFormat.TTML)) {
            "应输出 TTML"
        }

        assertTrue("应输出 TTML 根元素", converted.contains("<tt"))
        assertTrue("应保留首句时间", converted.contains("00:00:01"))
    }
}
