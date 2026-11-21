package com.lonx.lyrico.domain.lyrics

import android.annotation.SuppressLint
import com.github.houbb.opencc4j.util.ZhConverterUtil
import com.lonx.lyrico.data.text.ConversionMode

object LyricsTextTransform {
    /**
     * 转换单个文本段
     */
    private fun convertText(text: String, conversionMode: ConversionMode): String {
        return when (conversionMode) {
            ConversionMode.TRADITIONAL_TO_SIMPLIFIED -> ZhConverterUtil.toSimple(text)
            ConversionMode.SIMPLIFIED_TO_TRADITIONAL -> ZhConverterUtil.toTraditional(text)
            else -> text
        }
    }

    /**
     * 对纯文本歌词做简繁转换。
     */
    fun convertScript(lyricsText: String, conversionMode: ConversionMode): String {
        if (conversionMode == ConversionMode.NONE || lyricsText.isBlank()) return lyricsText

        // 匹配所有时间戳 token（LRC 和 TTML），对非时间戳部分做转换
        val timeTokenPattern = Regex(
            """[\[<]\d{2,}:\d{2}\.\d{2,3}[>\]]""" +          // LRC: [01:23.456] 或 <01:23.456>
            """|begin="\d{2,}:\d{2}:\d{2}\.\d{2,3}""" +     // TTML begin
            """|end="\d{2,}:\d{2}:\d{2}\.\d{2,3}"""         // TTML end
        )

        val result = StringBuilder()
        var lastEnd = 0

        timeTokenPattern.findAll(lyricsText).forEach { match ->
            if (match.range.first > lastEnd) {
                val textSegment = lyricsText.substring(lastEnd, match.range.first)
                result.append(convertText(textSegment, conversionMode))
            }
            result.append(match.value)
            lastEnd = match.range.last + 1
        }

        if (lastEnd < lyricsText.length) {
            result.append(convertText(lyricsText.substring(lastEnd), conversionMode))
        }

        return result.toString()
    }

    /** Complete editing text: XML attributes and element names are never script-converted. */
    fun convertLyricsScript(raw: String, mode: ConversionMode): String {
        if (mode == ConversionMode.NONE || raw.isBlank()) return raw
        if (LyricsParser.detectFormat(raw) != com.lonx.lyrico.data.lyrics.LyricsFormat.TTML) return convertScript(raw, mode)
        val document = com.lonx.lyrico.domain.lyrics.document.TtmlXml.parse(raw)
        var changed = false
        fun visit(node: org.w3c.dom.Node) {
            if (node.nodeType == org.w3c.dom.Node.TEXT_NODE || node.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                val converted = convertText(node.nodeValue, mode)
                if (converted != node.nodeValue) { node.nodeValue = converted; changed = true }
            }
            for (index in 0 until node.childNodes.length) visit(node.childNodes.item(index))
        }
        visit(document.documentElement)
        return if (changed) com.lonx.lyrico.domain.lyrics.document.TtmlXml.write(document) else raw
    }

    /**
     * 整体时间偏移（支持 LRC、Enhanced LRC、Verbatim、TTML）；[offset] 正数延后，负数提前。
     */
    @SuppressLint("DefaultLocale")
    fun shiftOffset(lyricsText: String, offset: Long): String {
        if (offset == 0L || lyricsText.isBlank()) return lyricsText

        if (LyricsParser.detectFormat(lyricsText) == com.lonx.lyrico.data.lyrics.LyricsFormat.TTML) {
            val document = com.lonx.lyrico.domain.lyrics.document.TtmlXml.parse(lyricsText)
            com.lonx.lyrico.domain.lyrics.document.TtmlXml.shift(document, offset)
            return com.lonx.lyrico.domain.lyrics.document.TtmlXml.write(document)
        }
        // 处理 LRC 格式的时间戳 ([mm:ss.xxx] 或 <mm:ss.xxx>)
        val resultText = LyricsTimestamp.lrcTimePattern.replace(lyricsText) { match ->
            val prefix = match.groupValues[1] // '[' 或 '<'
            val min = match.groupValues[2].toLong()
            val sec = match.groupValues[3].toLong()
            val msStr = match.groupValues[4]
            val suffix = match.groupValues[5] // ']' 或 '>'

            // 将毫秒补齐到3位，例如 .12 -> 120ms, .5 -> 500ms
            val ms = msStr.padEnd(3, '0').toLong()

            val totalMs = (min * 60 + sec) * 1000 + ms
            val newTotalMs = LyricsTimestamp.shift(totalMs, offset)

            val newMin = newTotalMs / 60000
            val newSec = (newTotalMs % 60000) / 1000
            val newMs = newTotalMs % 1000

            // 保持原有的括号类型，并将时间标准化为 3位毫秒
            String.format("%s%02d:%02d.%03d%s", prefix, newMin, newSec, newMs, suffix)
        }

        return resultText
    }
}
