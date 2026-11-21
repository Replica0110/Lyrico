package com.lonx.lyrico.data.lyrics

import kotlinx.serialization.Serializable

/** Output positions contain source indices; omitted indices are removed. */
@Serializable
data class LyricsColumnMapping(val sourceCount: Int, val order: List<Int>) {
    val isValid: Boolean get() = sourceCount > 0 && order.isNotEmpty() &&
        order.distinct().size == order.size && order.all { it in 0 until sourceCount }
    val isChanged: Boolean get() = order != (0 until sourceCount).toList()

    fun place(source: Int, destination: Int?): LyricsColumnMapping {
        require(isValid && source in 0 until sourceCount)
        val remaining = order.filter { it != source }.toMutableList()
        if (destination != null) remaining.add(destination.coerceIn(0, remaining.size), source)
        return if (remaining.isEmpty()) this else copy(order = remaining)
    }

    companion object {
        fun identity(count: Int) = LyricsColumnMapping(count, (0 until count).toList())
    }
}

@Serializable
data class LyricsColumnEdit(val mapping: LyricsColumnMapping, val sourceHash: String)
