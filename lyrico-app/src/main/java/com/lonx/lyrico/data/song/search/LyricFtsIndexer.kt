package com.lonx.lyrico.data.song.search

import com.lonx.lyrico.data.db.dao.SongDao
import com.lonx.lyrico.data.db.dao.SongLyricsForFts
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.domain.lyrics.LyricsSearchTextExtractor

object LyricFtsIndexer {
    fun buildQuery(raw: String): String? {
        val tokens = raw.toFtsIndexText().toFtsQueryTokens()
        if (tokens.isEmpty()) return null

        return if (tokens.all { it.isCjkSearchToken() }) {
            "\"${tokens.joinToString(" ")}\""
        } else {
            tokens.joinToString(" ") { token -> "${token.escapeFtsQueryToken()}*" }
        }
    }

    suspend fun replaceSong(songDao: SongDao, song: SongEntity) {
        replaceSongs(songDao, listOf(song))
    }

    suspend fun replaceSongs(songDao: SongDao, songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val uris = songs.map { it.uri }
        songDao.deleteLyricFtsByUris(uris)
        songs.forEach { song ->
            lyricLines(song).forEachIndexed { index, line ->
                songDao.insertLyricFtsLine(
                    songUri = song.uri,
                    lineIndex = index,
                    lineText = line,
                    indexedText = line.toFtsIndexText()
                )
            }
        }
    }

    suspend fun replaceSongLyrics(songDao: SongDao, rows: List<SongLyricsForFts>) {
        if (rows.isEmpty()) return
        songDao.deleteLyricFtsByUris(rows.map { it.uri })
        rows.forEach { row ->
            lyricLines(row).forEachIndexed { index, line ->
                songDao.insertLyricFtsLine(
                    songUri = row.uri,
                    lineIndex = index,
                    lineText = line,
                    indexedText = line.toFtsIndexText()
                )
            }
        }
    }

    private fun lyricLines(song: SongEntity): List<String> {
        return lyricLines(
            lyrics = song.lyrics,
            lyricSearchText = song.lyricSearchText
        )
    }

    private fun lyricLines(row: SongLyricsForFts): List<String> {
        return lyricLines(
            lyrics = row.lyrics,
            lyricSearchText = row.lyricSearchText
        )
    }

    private fun lyricLines(lyrics: String?, lyricSearchText: String?): List<String> {
        // An empty index marks lyrics that contained no searchable lines after processing.
        if (lyricSearchText == "") return emptyList()
        val searchTextLines = lyricSearchText
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        return (searchTextLines + LyricsSearchTextExtractor.extractLines(lyrics))
            .distinct()
    }
}

private fun String.toFtsIndexText(): String {
    return buildString {
        for (ch in this@toFtsIndexText) {
            if (ch.isCjkSearchChar()) {
                append(' ')
                append(ch)
                append(' ')
            } else {
                append(ch)
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
}

private fun String.toFtsQueryTokens(): List<String> {
    return split(Regex("\\s+"))
        .flatMap { token ->
            token.split(Regex("[^\\p{L}\\p{Nd}]+"))
        }
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun String.escapeFtsQueryToken(): String {
    return replace("\"", "\"\"")
}

private fun String.isCjkSearchToken(): Boolean {
    return length == 1 && first().isCjkSearchChar()
}

private fun Char.isCjkSearchChar(): Boolean {
    val code = code
    return code in 0x3400..0x4DBF ||
        code in 0x4E00..0x9FFF ||
        code in 0xF900..0xFAFF ||
        code in 0x3040..0x30FF ||
        code in 0xAC00..0xD7AF
}
