package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.batch.BatchTaskStatus
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.db.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.log.AppLogRepository
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsFormatConfig
import com.lonx.lyrico.data.lyrics.LyricsColumnEdit
import com.lonx.lyrico.data.lyrics.LyricsColumnMapping
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagWriteResult
import com.lonx.lyrico.data.song.tag.AudioTagFieldKey
import com.lonx.lyrico.data.song.tag.FieldMutation
import com.lonx.lyrico.domain.lyrics.LyricsColumnSorter
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFormatProcessorTest {
    @Test
    fun `Empty conversion skips the actual processor before any tag write`() = runBlocking {
        assertSkippedWithoutWrite("[00:01.00]作词：某人", config().copy(
            removeTagLines = true, tagLineKeywords = listOf("作词：")
        ), "Converted lyrics are empty")
    }

    @Test
    fun `Unchanged conversion skips the actual processor before any tag write`() = runBlocking {
        assertSkippedWithoutWrite("[00:01.000]歌词", config().copy(removeEmptyLines = true),
            "Converted lyrics are unchanged")
    }

    @Test
    fun `missing word timing skips the actual processor before any tag write`() = runBlocking {
        assertSkippedWithoutWrite("[00:01.00]只有整行时间", config().copy(targetFormat = LyricsFormat.ENHANCED_LRC),
            "WORD_TIMING_REQUIRED")
    }

    @Test
    fun `metadata only TTML skips the actual processor without writing an XML skeleton`() = runBlocking {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata><note>metadata only</note></metadata></head><body/></tt>"""
        assertSkippedWithoutWrite(raw, config().copy(targetFormat = LyricsFormat.TTML, removeEmptyLines = true),
            "Converted lyrics are empty")
    }

    @Test
    fun `plain text cleanup actually patches tags and synchronizes the saved library snapshot`() = runBlocking {
        assertSaved("第一句\n\n第二句\n", config().copy(targetFormat = null, removeEmptyLines = true),
            "第一句\n第二句\n")
    }

    @Test
    fun `same format tag cleanup uses saved trimmed keywords and writes its nonempty result`() = runBlocking {
        assertSaved("[00:01.00]作词：某人\n[00:02.00]歌词\n", config().copy(
            removeTagLines = true, tagLineKeywords = listOf(" 作词： ", " ")),
            "[00:02.000]歌词")
    }

    @Test
    fun `plain text cleanup that removes everything skips without clearing an audio tag`() = runBlocking {
        assertSkippedWithoutWrite("作词：某人\n", config().copy(targetFormat = null,
            removeTagLines = true, tagLineKeywords = listOf("作词：")), "Converted lyrics are empty")
    }

    @Test
    fun `legacy saved column edit is applied ahead of newer task flags`() = runBlocking {
        val raw = "[00:01.00]作词：某人\n[00:01.00]歌词\n"
        val edit = LyricsColumnEdit(LyricsColumnMapping(2, listOf(1, 0)), LyricsColumnSorter.fingerprint(raw))
        assertSaved(raw, config().copy(targetFormat = LyricsFormat.TTML, formatLineOrder = true,
            removeTagLines = true, tagLineKeywords = listOf("作词："), legacyColumnEdits = mapOf(URI to edit)),
            "[00:01.00]歌词\n[00:01.00]作词：某人\n")
    }

    @Test
    fun `legacy missing mapping or changed fingerprint skips without any tag write`() = runBlocking {
        val raw = "[00:01.00]上\n[00:01.00]下\n"
        val edit = LyricsColumnEdit(LyricsColumnMapping(2, listOf(1, 0)), "old fingerprint")
        assertSkippedWithoutWrite(raw, config().copy(legacyColumnEdits = mapOf("another song" to edit)),
            "No column mapping for this song")
        assertSkippedWithoutWrite(raw, config().copy(legacyColumnEdits = mapOf(URI to edit)),
            "Lyrics changed since the task was created")
    }

    @Test
    fun `read cancellation and recognized parse errors propagate before writing`() = runBlocking {
        for (readFailure in listOf(CancellationException("cancelled"), null)) {
            val tags = ReadOnlyTags("""<tt xmlns="http://www.w3.org/ns/ttml"><body>unclosed""", readFailure)
            val song = SongEntity(folderId = 1, mediaId = 1, filePath = "song.flac", fileName = "song.flac", uri = URI)
            val library = strictProxy<SongLibraryRepository> { method, _ ->
                check(method == "getSongByUri")
                song
            }
            val log = strictProxy<AppLogRepository> { method, _ -> error("Unexpected log: $method") }
            val processor = LyricsFormatProcessor(library, SaveAudioTagsUseCase(library, tags, log), tags)
            val failure = runCatching { processor.process(task(config()), item()) }.exceptionOrNull()

            assertTrue(failure != null && failure !is BatchTaskSkippedException)
            if (readFailure != null) assertTrue(failure is CancellationException)
            assertEquals(0, tags.writes)
        }
    }

    private suspend fun assertSaved(raw: String, config: LyricsFormatConfig, expected: String) {
        val song = SongEntity(folderId = 1, mediaId = 1, filePath = "song.flac", fileName = "song.flac", uri = URI)
        val writes = mutableListOf<AudioTagMutation>()
        val synchronized = mutableListOf<AudioTagData>()
        val tags = object : AudioTagRepository {
            override suspend fun read(uri: String, options: AudioTagReadOptions) = AudioTagData(lyrics = raw)
            override suspend fun overwrite(uri: String, mutation: AudioTagMutation): AudioTagWriteResult =
                error("Unexpected overwrite")
            override suspend fun patch(uri: String, mutation: AudioTagMutation): AudioTagWriteResult {
                assertEquals(URI, uri)
                writes += mutation
                val written = (mutation.fields[AudioTagFieldKey.Lyrics] as FieldMutation.Set).value
                return AudioTagWriteResult.Success(AudioTagData(lyrics = written))
            }
        }
        val library = strictProxy<SongLibraryRepository> { method, args ->
            when (method) {
                "getSongByUri" -> song
                "synchronizeSavedTags" -> {
                    assertEquals(URI, args!![0])
                    synchronized += args[1] as AudioTagData
                    song.copy(lyrics = (args[1] as AudioTagData).lyrics)
                }
                else -> error("Unexpected library operation: $method")
            }
        }
        val log = strictProxy<AppLogRepository> { method, _ ->
            check(method == "log") { "Unexpected log operation: $method" }
            Unit
        }
        val processor = LyricsFormatProcessor(library, SaveAudioTagsUseCase(library, tags, log), tags)
        processor.process(task(config), item())

        assertEquals(1, writes.size)
        assertEquals(mapOf(AudioTagFieldKey.Lyrics to FieldMutation.Set(expected)), writes.single().fields)
        assertEquals(expected, synchronized.single().lyrics)
    }

    private suspend fun assertSkippedWithoutWrite(raw: String, config: LyricsFormatConfig, reason: String) {
        val song = SongEntity(folderId = 1, mediaId = 1, filePath = "song.flac", fileName = "song.flac", uri = URI)
        val library = strictProxy<SongLibraryRepository> { method, args ->
            check(method == "getSongByUri") { "Unexpected library operation: $method" }
            check(args!![0] == URI)
            song
        }
        val log = strictProxy<AppLogRepository> { method, _ -> error("Unexpected save log: $method") }
        val tags = ReadOnlyTags(raw)
        val save = SaveAudioTagsUseCase(library, tags, log)
        val processor = LyricsFormatProcessor(library, save, tags)
        val failure = runCatching { processor.process(task(config), item()) }.exceptionOrNull()

        assertTrue("Expected skip, got $failure", failure is BatchTaskSkippedException)
        assertTrue("Expected '$reason', got $failure", failure?.message.orEmpty().contains(reason))
        assertEquals(1, tags.reads)
        assertEquals(0, tags.writes)
    }

    private fun config() = LyricsFormatConfig(targetFormat = LyricsFormat.PLAIN_LRC,
        concurrency = 1, formatLineOrder = false)

    private fun task(config: LyricsFormatConfig) = BatchTaskEntity(
        taskId = "task", type = BatchTaskType.CONVERT_LYRICS_FORMAT, status = BatchTaskStatus.RUNNING,
        total = 1, current = 0, successCount = 0, failureCount = 0, skippedCount = 0,
        currentFile = null, configJson = Json.encodeToString(config), workId = null,
        startedAt = null, finishedAt = null, createdAt = 0, updatedAt = 0, errorMessage = null
    )

    private fun item() = BatchTaskItemEntity(
        itemId = "item", taskId = "task", mediaId = 1, songUri = URI, filePath = "song.flac",
        fileName = "song.flac", status = BatchTaskStatus.RUNNING, progress = null,
        resultJson = null, errorMessage = null, createdAt = 0, updatedAt = 0
    )

    private class ReadOnlyTags(private val lyrics: String, private val readFailure: Throwable? = null) : AudioTagRepository {
        var reads = 0
        var writes = 0

        override suspend fun read(uri: String, options: AudioTagReadOptions): AudioTagData {
            reads++
            readFailure?.let { throw it }
            return AudioTagData(lyrics = lyrics)
        }

        override suspend fun overwrite(uri: String, mutation: AudioTagMutation): AudioTagWriteResult {
            writes++
            error("Unexpected overwrite")
        }

        override suspend fun patch(uri: String, mutation: AudioTagMutation): AudioTagWriteResult {
            writes++
            error("Unexpected patch")
        }
    }

    private inline fun <reified T> strictProxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args)
        } as T

    private companion object {
        const val URI = "content://song/1"
    }
}
