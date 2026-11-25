package com.lonx.lyrico.domain.lyrics.document

internal object TtmlTime {
    private val milliseconds = Regex("""^(\d+(?:\.\d+)?)ms$""")
    private val seconds = Regex("""^(\d+(?:\.\d+)?)s$""")
    private val plainSeconds = Regex("""^(\d+(?:\.\d+)?)$""")
    private val clockHours = Regex("""^(\d+):(\d{2}):(\d{2})(?:\.(\d+))?$""")
    private val clockMinutes = Regex("""^(\d+):(\d{2})(?:\.(\d+))?$""")

    fun parseMsOrNull(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        milliseconds.matchEntire(text)?.let { match ->
            return match.groupValues[1].toFiniteDoubleOrNull()?.toLong()
        }
        seconds.matchEntire(text)?.let { match ->
            return match.groupValues[1].toFiniteDoubleOrNull()?.times(1000)?.toLong()
        }
        plainSeconds.matchEntire(text)?.let { match ->
            return match.groupValues[1].toFiniteDoubleOrNull()?.times(1000)?.toLong()
        }
        clockHours.matchEntire(text)?.let { match ->
            return clockMs(
                hours = match.groupValues[1],
                minutes = match.groupValues[2],
                seconds = match.groupValues[3],
                fraction = match.groupValues[4]
            )
        }
        clockMinutes.matchEntire(text)?.let { match ->
            return clockMs(
                hours = "0",
                minutes = match.groupValues[1],
                seconds = match.groupValues[2],
                fraction = match.groupValues[3]
            )
        }
        return null
    }

    fun isValid(value: String?): Boolean = parseMsOrNull(value) != null

    private fun clockMs(hours: String, minutes: String, seconds: String, fraction: String): Long? {
        return runCatching {
            val wholeSeconds = Math.addExact(
                Math.addExact(
                    Math.multiplyExact(hours.toLong(), 3600L),
                    Math.multiplyExact(minutes.toLong(), 60L)
                ),
                seconds.toLong()
            )
            val milliseconds = fraction.takeIf { it.isNotEmpty() }
                ?.padEnd(3, '0')
                ?.take(3)
                ?.toLong()
                ?: 0L
            Math.addExact(Math.multiplyExact(wholeSeconds, 1000L), milliseconds)
        }.getOrNull()
    }

    private fun String.toFiniteDoubleOrNull(): Double? {
        return toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
