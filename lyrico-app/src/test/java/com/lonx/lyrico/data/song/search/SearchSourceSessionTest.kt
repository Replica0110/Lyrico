package com.lonx.lyrico.data.song.search

import com.lonx.lyrico.data.lyrics.LyricsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchSourceSessionTest {
    @Test
    fun holdsEverySourceUntilTheOperationCompletesAndReleasesInReverseOrder() = runBlocking {
        val events = mutableListOf<String>()
        val sources = listOf(TestSource("a", events), TestSource("b", events))

        val result = sources.withSessions { available ->
            assertEquals(listOf("a", "b"), available.map { it.id })
            assertEquals(listOf("open:a", "open:b"), events)
            events += "operation"
            "done"
        }

        assertEquals("done", result)
        assertEquals(listOf("open:a", "open:b", "operation", "close:b", "close:a"), events)
    }

    @Test
    fun skipsOnlySourcesThatFailBeforeEnteringTheirSession() = runBlocking {
        val events = mutableListOf<String>()
        val unavailable = IllegalStateException("unavailable")
        val sources = listOf(TestSource("a", events), TestSource("b", events, unavailable), TestSource("c", events))
        val failures = mutableListOf<Pair<String, Exception>>()

        val availableIds = sources.withSessions(
            onUnavailable = { source, error -> failures += source.id to error },
        ) { available -> available.map { it.id } }

        assertEquals(listOf("a", "c"), availableIds)
        assertEquals(listOf("b" to unavailable), failures)
        assertEquals(listOf("open:a", "open:b", "open:c", "close:c", "close:a"), events)
    }

    @Test
    fun operationFailureIsPropagatedAndDoesNotBecomeAnUnavailableSource() = runBlocking {
        val events = mutableListOf<String>()
        val failure = IllegalStateException("operation failed")
        var unavailableCount = 0

        val thrown = runCatching {
            listOf(TestSource("a", events), TestSource("b", events)).withSessions(
                onUnavailable = { _, _ -> unavailableCount++ },
            ) { throw failure }
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(0, unavailableCount)
        assertEquals(listOf("open:a", "open:b", "close:b", "close:a"), events)
    }

    @Test
    fun acquisitionCancellationPropagatesAndReleasesPreviouslyHeldSources() = runBlocking {
        val events = mutableListOf<String>()
        val cancellation = CancellationException("stopped")
        var unavailableCount = 0

        val thrown = runCatching {
            listOf(TestSource("a", events), TestSource("b", events, cancellation)).withSessions(
                onUnavailable = { _, _ -> unavailableCount++ },
            ) { error("Cancelled acquisition must not start the operation") }
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
        assertEquals(0, unavailableCount)
        assertEquals(listOf("open:a", "open:b", "close:a"), events)
    }

    private class TestSource(
        override val id: String,
        private val events: MutableList<String>,
        private val acquisitionFailure: Exception? = null,
    ) : SearchSource {
        override val name: String = id

        override suspend fun <T> withSession(block: suspend SearchSource.() -> T): T {
            events += "open:$id"
            acquisitionFailure?.let { throw it }
            return try {
                block()
            } finally {
                events += "close:$id"
            }
        }

        override suspend fun searchSongs(keyword: String, page: Int, separator: String, pageSize: Int): List<SongSearchResult> =
            emptyList()

        override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = null

        override suspend fun searchCovers(keyword: String, page: Int, pageSize: Int): List<SongSearchResult> = emptyList()
    }
}
