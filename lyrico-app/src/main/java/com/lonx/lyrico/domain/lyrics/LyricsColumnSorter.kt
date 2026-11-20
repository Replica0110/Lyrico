package com.lonx.lyrico.domain.lyrics

import java.security.MessageDigest
import com.lonx.lyrico.data.lyrics.LyricsColumnMapping

object LyricsColumnSorter {
    private val timestamp = Regex("""\[(\d{1,9}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<\d+:\d{2}(?:[.:]\d{1,3})?>""")
    private val xmlRoot = Regex("""<(?:[\w-]+:)?tt(?:\s|>)""", RegexOption.IGNORE_CASE)
    private val token = Regex("""([^\r\n]*)(\r\n|\n|\r|$)""")
    data class Line(val text: String, val ending: String)
    data class Group(val indices: List<Int>, val text: List<String>)
    data class Analysis(val lines: List<Line>, val groups: List<Group>, val sourceCount: Int) {
        val samples get() = groups.filter { it.indices.size == sourceCount }
        val incomplete get() = groups.filter { it.indices.size != sourceCount }
    }

    fun fingerprint(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun analyze(raw: String): Analysis {
        val lines = token.findAll(raw.removePrefix("\uFEFF")).filter { it.value.isNotEmpty() }
            .map { Line(it.groupValues[1], it.groupValues[2]) }.toList()
        if (xmlRoot.containsMatchIn(raw)) return Analysis(lines, emptyList(), 0)
        val groups = linkedMapOf<List<Long>, MutableList<Int>>()
        lines.forEachIndexed { index, line ->
            val content = line.text.trimStart(' ', '\t', '\uFEFF')
            val matches = timestamp.findAll(content).toList()
            if (matches.firstOrNull()?.range?.first != 0) return@forEachIndexed
            // Only leading timestamps identify a line. Inline word timing is never a role signal.
            var end = 0
            val keys = matches.takeWhile { match ->
                (match.range.first == end).also { if (it) end = match.range.last + 1 }
            }.map { match ->
                match.groupValues[1].toLong() * 60000 + match.groupValues[2].toLong() * 1000 +
                    match.groupValues[3].padEnd(3, '0').toLong()
            }
            groups.getOrPut(keys) { mutableListOf() }.add(index)
        }
        val result = groups.values.map { indices -> Group(indices, indices.map { visible(lines[it].text) }) }
        return Analysis(lines, result, result.maxOfOrNull { it.indices.size } ?: 0)
    }

    fun visible(raw: String): String = raw.replace(timestamp, "").replace(wordTimestamp, "").trim()

    fun apply(raw: String, twoColumnMapping: LyricsColumnMapping, threeColumnMapping: LyricsColumnMapping): String {
        require(twoColumnMapping.isValid && twoColumnMapping.sourceCount == 2)
        require(threeColumnMapping.isValid && threeColumnMapping.sourceCount == 3)
        val analysis = analyze(raw)
        if (!twoColumnMapping.isChanged && !threeColumnMapping.isChanged) return raw
        val replacements = mutableMapOf<Int, String?>()
        analysis.groups.forEach { group ->
            val mapping = when (group.indices.size) {
                2 -> twoColumnMapping
                3 -> threeColumnMapping
                else -> return@forEach
            }
            group.indices.forEachIndexed { position, index ->
                replacements[index] = mapping.order.getOrNull(position)?.let { source ->
                    analysis.lines[group.indices[source]].text
                }
            }
        }
        return buildString {
            if (raw.startsWith("\uFEFF")) append("\uFEFF")
            analysis.lines.forEachIndexed { index, line ->
                if (index !in replacements) append(line.text).append(line.ending)
                else replacements[index]?.let { append(it).append(line.ending) }
            }
        }
    }

    /** Kept for decoding and applying older in-flight single-song edits. */
    fun apply(raw: String, mapping: LyricsColumnMapping): String {
        require(mapping.isValid)
        val analysis = analyze(raw)
        require(analysis.sourceCount == mapping.sourceCount)
        if (!mapping.isChanged) return raw
        val replacements = mutableMapOf<Int, String?>()
        analysis.samples.forEach { group ->
            group.indices.forEachIndexed { position, index ->
                replacements[index] = mapping.order.getOrNull(position)?.let { source ->
                    analysis.lines[group.indices[source]].text
                }
            }
        }
        return buildString {
            if (raw.startsWith("\uFEFF")) append("\uFEFF")
            analysis.lines.forEachIndexed { index, line ->
                if (index !in replacements) append(line.text).append(line.ending)
                else replacements[index]?.let { append(it).append(line.ending) }
            }
        }
    }
}
