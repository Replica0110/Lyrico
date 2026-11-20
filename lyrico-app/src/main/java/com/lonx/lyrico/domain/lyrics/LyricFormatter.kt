package com.lonx.lyrico.domain.lyrics

import android.annotation.SuppressLint

/** 歌词格式化：时间戳格式化与 XML 转义。 */
object LyricFormatter {
    
    // 匹配 LRC/Enhanced LRC 格式: [01:23.456] 或 <01:23.45>
    val LRC_TIME_PATTERN = Regex("([<\\[])(\\d{2,}):(\\d{2})\\.(\\d{2,3})([>\\]])")
    
    // 匹配 TTML 格式: begin="00:01:23.456" 或 end="00:01:23.456"
    val TTML_TIME_PATTERN = Regex("(begin=\"|end=\")(\\d{2,}):(\\d{2}):(\\d{2})\\.(\\d{2,3})(\")")

    /**
     * LRC 格式时间戳 (格式: mm:ss.SSS)
     */
    @SuppressLint("DefaultLocale")
    fun formatTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }

    /**
     * TTML 专属时间戳 (格式: HH:mm:ss.SSS)
     */
    @SuppressLint("DefaultLocale")
    fun formatTtmlTimestamp(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    /**
     * XML 字符转义（防止歌词中的特殊字符破坏 TTML 结构）
     */
    fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * XML 字符反转义
     */
    fun unescapeXml(text: String): String {
        return text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    /**
     * 计算应用偏移量，保证结果大于等于 0
     */
    fun applyOffset(time: Long, offset: Long): Long {
        return (time + offset).coerceAtLeast(0L)
    }
}
