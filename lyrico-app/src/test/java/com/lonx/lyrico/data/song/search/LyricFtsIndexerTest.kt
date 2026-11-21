package com.lonx.lyrico.data.song.search

import com.lonx.lyrico.data.db.dao.SongDao
import com.lonx.lyrico.data.db.dao.SongLyricsForFts
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricFtsIndexerTest {
    @Test
    fun `explicit empty search text clears prior index without resurrecting raw lyrics`() = runBlocking {
        val writes = IndexWrites()
        LyricFtsIndexer.replaceSongLyrics(writes.dao, listOf(SongLyricsForFts(1, "song", "原文", "")))

        assertEquals(listOf(listOf("song")), writes.deletedUris)
        assertTrue(writes.lines.isEmpty())
    }

    @Test
    fun `null search text falls back to lyrics and indexes CJK characters separately`() = runBlocking {
        val writes = IndexWrites()
        LyricFtsIndexer.replaceSongLyrics(writes.dao, listOf(SongLyricsForFts(1, "song", "[00:01.00]原文", null)))

        assertEquals(listOf(IndexedLine("song", 0, "原文", "原 文")), writes.lines)
    }

    @Test
    fun `precomputed and extracted lines are combined without duplicate rows`() = runBlocking {
        val writes = IndexWrites()
        LyricFtsIndexer.replaceSongLyrics(writes.dao,
            listOf(SongLyricsForFts(1, "song", "[00:01.00]原文\n[00:02.00]另一句", "原文\n预计算")))

        assertEquals(listOf("原文", "预计算", "另一句"), writes.lines.map { it.text })
        assertEquals(listOf(0, 1, 2), writes.lines.map { it.index })
    }

    // This records only the DAO write boundary. It does not simulate Room or FTS query execution.
    private class IndexWrites {
        val deletedUris = mutableListOf<List<String>>()
        val lines = mutableListOf<IndexedLine>()
        val dao = Proxy.newProxyInstance(SongDao::class.java.classLoader, arrayOf(SongDao::class.java)) { _, method, args ->
            when (method.name) {
                "deleteLyricFtsByUris" -> {
                    deletedUris += (args!![0] as List<*>).map { it as String }
                    Unit
                }
                "insertLyricFtsLine" -> {
                    lines += IndexedLine(args!![0] as String, args[1] as Int, args[2] as String, args[3] as String)
                    Unit
                }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as SongDao
    }

    private data class IndexedLine(val uri: String, val index: Int, val text: String, val indexedText: String)
}
