package com.lonx.lyrico.domain.lyrics

import android.annotation.SuppressLint

/** 歌词时间戳的识别、格式化与非负偏移。 */
object LyricsTimestamp {

    // 匹配 LRC/Enhanced LRC 格式: [01:23.456] 或 <01:23.45>
    val lrcTimePattern = Regex("([<\\[])(\\d{2,}):(\\d{2})\\.(\\d{2,3})([>\\]])")

    /**
     * LRC 格式时间戳 (格式: mm:ss.SSS)
     */
    @SuppressLint("DefaultLocale")
    fun formatLrc(millis: Long): String {
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
    fun formatTtml(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = safeMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val ms = safeMillis % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    /**
     * 计算应用偏移量，保证结果大于等于 0
     */
    fun shift(time: Long, offset: Long): Long {
        return try { Math.addExact(time, offset).coerceAtLeast(0L) }
        catch (_: ArithmeticException) { if (offset > 0) Long.MAX_VALUE else 0L }
    }
}
