package com.lonx.lyrico.domain.lyrics

import com.lonx.lyrico.data.lyrics.LyricsColumnEdit
import com.lonx.lyrico.data.lyrics.LyricsColumnMapping
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsFormatConfig
import com.lonx.lyrico.data.lyrics.LyricsOperation
import com.lonx.lyrico.data.lyrics.LyricsProcessingOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProcessorTest {
    @Test
    fun `blank source and cleanup that removes every line are Empty`() {
        assertEquals(LyricsProcessingResult.Empty, LyricsProcessor.process(" \n ",
            LyricsProcessingOptions(removeEmptyLines = true)))
        assertEquals(LyricsProcessingResult.Empty, LyricsProcessor.process("作词：某人\n",
            LyricsProcessingOptions(removeTagLines = true, tagLineKeywords = listOf("作词："))))
    }

    @Test
    fun `same format without actual text operations preserves exact input`() {
        val options = LyricsProcessingOptions(targetFormat = LyricsFormat.PLAIN_LRC)
        assertEquals(LyricsProcessingResult.Unchanged, LyricsProcessor.process("[00:01.00]歌词", options))
        assertEquals(LyricsProcessingResult.Unchanged, LyricsProcessor.process("[00:01.00]歌词", options.copy(
            removeTagLines = true, tagLineKeywords = listOf(" ", "\n"))))
    }

    @Test
    fun `same format tag filtering uses its single normalized keyword snapshot`() {
        val options = LyricsProcessingOptions(targetFormat = LyricsFormat.PLAIN_LRC,
            removeTagLines = true, tagLineKeywords = listOf(" 作词： ", "", "作词："))
        assertEquals(LyricsProcessingResult.Changed("[00:02.000]歌词"), LyricsProcessor.process(
            "[00:01.00]作词：某人\n[00:02.00]歌词\n", options))
    }

    @Test
    fun `disabled tag removal never applies stored keywords`() {
        assertEquals(LyricsProcessingResult.Unchanged, LyricsProcessor.process("作词：某人\n歌词\n",
            LyricsProcessingOptions(tagLineKeywords = listOf("作词："))))
    }

    @Test
    fun `sorting changes column order and remains exclusive of cleanup and conversion`() {
        val raw = "[00:01.00]作词：某人\n[00:01.00]歌词\n"
        val options = LyricsProcessingOptions(formatLineOrder = true, targetFormat = LyricsFormat.TTML,
            removeTagLines = true, tagLineKeywords = listOf("作词："),
            twoColumnMapping = LyricsColumnMapping(2, listOf(1, 0)))

        assertEquals(LyricsProcessingResult.Changed("[00:01.00]歌词\n[00:01.00]作词：某人\n"),
            LyricsProcessor.process(raw, options))
    }

    @Test
    fun `identity sorting is Unchanged and inactive mappings do not create an operation`() {
        assertEquals(LyricsProcessingResult.Unchanged, LyricsProcessor.process("[00:01.00]歌词\n",
            LyricsProcessingOptions(formatLineOrder = true)))
        val inactive = LyricsProcessingOptions(twoColumnMapping = LyricsColumnMapping(2, listOf(1, 0)))
        assertFalse(inactive.hasOperation())
        assertEquals(LyricsProcessingResult.Unchanged, LyricsProcessor.process("第一句\n第二句", inactive))
    }

    @Test
    fun `empty line cleanup preserves timestamps and existing line endings`() {
        val raw = "[00:01.00]第一句\r\n[00:02.00]\r\n[00:03.00]第二句\r\n"
        assertEquals(LyricsProcessingResult.Changed("[00:01.00]第一句\r\n[00:03.00]第二句\r\n"),
            LyricsProcessor.process(raw, LyricsProcessingOptions(removeEmptyLines = true)))
    }

    @Test
    fun `unrecognized plain text can run empty and tag cleanup without format conversion`() {
        assertEquals(LyricsProcessingResult.Changed("第一句\n第二句\n"), LyricsProcessor.process(
            "第一句\n\n第二句\n", LyricsOperation.REMOVE_EMPTY.processingOptions()))
        assertEquals(LyricsProcessingResult.Changed("第一句\n"), LyricsProcessor.process("作词：某人\n第一句\n",
            LyricsOperation.REMOVE_TAGS.processingOptions().copy(tagLineKeywords = listOf("作词："))))
    }

    @Test
    fun `conversion still preserves a real timeline and rejects unknown source formats`() {
        val result = LyricsProcessor.process("[00:01.00]歌词\n", LyricsOperation.CONVERT.processingOptions(LyricsFormat.TTML))
        assertTrue((result as LyricsProcessingResult.Changed).text.contains("00:00:01"))
        assertEquals(LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.UNRECOGNIZED_FORMAT),
            LyricsProcessor.process("纯文本", LyricsOperation.CONVERT.processingOptions(LyricsFormat.TTML)))
    }

    @Test
    fun `legacy authored edits have priority and reject changed source fingerprints`() {
        val raw = "[00:01.00]上\n[00:01.00]下\n"
        val edit = LyricsColumnEdit(LyricsColumnMapping(2, listOf(1, 0)), LyricsColumnSorter.fingerprint(raw))
        assertEquals(LyricsProcessingResult.Changed("[00:01.00]下\n[00:01.00]上\n"), LyricsProcessor.process(raw,
            LyricsProcessingOptions(targetFormat = LyricsFormat.TTML, removeEmptyLines = true), columnEdit = edit))
        assertEquals(LyricsProcessingResult.Unsupported(LyricsProcessingResult.Reason.SOURCE_CHANGED),
            LyricsProcessor.process(raw + "changed", LyricsProcessingOptions(), columnEdit = edit))
    }

    @Test
    fun `operation builders and saved configs pass the same actual options to the processor`() {
        for (operation in LyricsOperation.entries) {
            val options = operation.processingOptions(LyricsFormat.TTML)
            assertEquals(operation == LyricsOperation.CONVERT, options.targetFormat != null)
            assertEquals(operation == LyricsOperation.SORT, options.formatLineOrder)
            assertEquals(operation == LyricsOperation.REMOVE_EMPTY, options.removeEmptyLines)
            assertEquals(operation == LyricsOperation.REMOVE_TAGS, options.removeTagLines)
        }
        val config = LyricsFormatConfig(concurrency = 2, formatLineOrder = false, removeTagLines = true,
            tagLineKeywords = listOf("saved keyword"))
        assertEquals(listOf("saved keyword"), config.processingOptions().tagLineKeywords)
    }
}
