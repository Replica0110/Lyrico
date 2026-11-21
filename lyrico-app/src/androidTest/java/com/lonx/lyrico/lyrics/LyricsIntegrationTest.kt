package com.lonx.lyrico.lyrics

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.MainActivity
import com.lonx.lyrico.data.batch.BatchTaskRepository
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.db.LyricoDatabase
import com.lonx.lyrico.data.db.entity.FolderEntity
import com.lonx.lyrico.data.db.entity.SongEntity
import com.lonx.lyrico.data.lyrics.LyricsFileNames
import com.lonx.lyrico.data.lyrics.LyricsFileTransfer
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.settings.UiEngine
import com.lonx.lyrico.data.song.search.LyricFtsIndexer
import com.lonx.lyrico.data.song.search.SongSearchResult
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.text.ConversionMode
import com.lonx.lyrico.domain.lyrics.LyricsParser
import com.lonx.lyrico.domain.lyrics.LyricsRenderer
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.plugin.runtime.QuickJsRuntime
import com.lonx.lyrico.plugin.source.PluginJsonParser
import com.lonx.lyrico.worker.BatchLyricsConfigSnapshot
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.GlobalContext

class LyricsIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun nativePluginOutputRendersWritesAndReopensWithRoomSearchIndex() = runBlocking {
        val runtime = QuickJsRuntime()
        val lyrics = try {
            assertEquals("歌词", runtime.eval("Platform.base64.decodeText(Platform.base64.encodeText('歌词'))", "host-test.js"))
            runtime.eval("""
                function getLyrics(request) {
                  return [{ type: 'structured', tags: {ti:'Fixture', ar:'Test', al:'Validation', date:'2026'},
                    original: [[1000,2000,[[1000,1500,'臺灣 '],[1500,2000,'validation']]]],
                    translated: [[1000,2000,'integration translation']], romanization: null }];
                }
            """.trimIndent(), "lyrics-fixture.js")
            val json = runtime.call("getLyrics", "{}")
            PluginJsonParser(Json).parseLyricsCandidates(json, "fixture", "Fixture",
                SongSearchResult(id = "song", pluginId = "fixture", pluginName = "Fixture"), true).single().lyrics
        } finally {
            runtime.close()
        }
        val rendered = LyricsRenderer.render(lyrics, LyricsRenderConfig(LyricsFormat.TTML,
            showRomanization = true, conversionMode = ConversionMode.TRADITIONAL_TO_SIMPLIFIED))
        assertTrue(rendered.contains("台湾"))
        assertEquals(1000L, requireNotNull(LyricsParser.parseOrNull(rendered)).original.single().start)

        val koin = GlobalContext.get()
        val database = koin.get<LyricoDatabase>()
        val directory = File(context.cacheDir, "lyrics-${UUID.randomUUID()}").apply { mkdirs() }
        val audio = File(directory, "fixture.wav").apply { writeBytes(wave()) }
        val uri = Uri.fromFile(audio).toString()
        val folderId = database.folderDao().insert(FolderEntity(path = directory.path))
        database.songDao().insert(SongEntity(uri = uri, mediaId = 0, folderId = folderId,
            filePath = audio.path, fileName = audio.name, title = "Fixture"))
        try {
            val saved = koin.get<SaveAudioTagsUseCase>().patch(uri, AudioTagData(title = "Fixture", lyrics = rendered))
            assertTrue("Tag write must succeed: $saved", saved is SaveAudioTagsResult.Success)
            val reopened = koin.get<AudioTagRepository>().read(uri, AudioTagReadOptions(strict = true))
            assertEquals(rendered, reopened.lyrics)
            assertEquals("Fixture", reopened.title)
            assertEquals(rendered, requireNotNull(database.songDao().getSongByUri(uri)).lyrics)
            database.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM song_lyric_lines_fts WHERE songUri = ? AND indexedText MATCH ?",
                arrayOf(uri, requireNotNull(LyricFtsIndexer.buildQuery("validation")))
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("Saved lyrics must be searchable", cursor.getInt(0) > 0)
            }
        } finally {
            database.songDao().deleteByUri(uri)
            database.folderDao().deleteFoldersPermanently(listOf(folderId))
            directory.deleteRecursively()
        }
    }

    @Test
    fun documentsProviderExportReusesSiblingTruncatesAndImportsUtf8() = runBlocking {
        val granted = CountDownLatch(1)
        var failure: String? = "Grant activity did not respond"
        val receiver = object : ResultReceiver(null) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                failure = if (resultCode == 0) null else resultData?.getString("error") ?: "Grant failed"
                granted.countDown()
            }
        }
        context.startActivity(Intent().setClassName(instrumentation.context.packageName,
            LyricsTestDocumentsGrantActivity::class.java.name)
            .putExtra("targetPackage", context.packageName).putExtra("result", receiver)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue("Timed out obtaining test URI grant", granted.await(10, TimeUnit.SECONDS))
        assertEquals(null, failure)
        verifyDocumentsProviderExport()
    }

    private suspend fun verifyDocumentsProviderExport() {
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root")
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, "root")
        val directory = requireNotNull(DocumentsContract.createDocument(context.contentResolver, root,
            DocumentsContract.Document.MIME_TYPE_DIR, "export-${UUID.randomUUID()}"))
        try {
            val source = requireNotNull(DocumentsContract.createDocument(context.contentResolver,
                directory, "audio/wav", "source.wav"))
            context.contentResolver.openOutputStream(source, "wt")!!.use { it.write(wave()) }
            val longText = "[00:01.000]歌词导出 validation long text\n"
            val name = requireNotNull(LyricsFileNames.defaultName("source.live", longText))
            val first = LyricsFileTransfer.writeNextToAudio(context, source, name, longText)
            assertEquals(longText, LyricsFileTransfer.read(context, first))
            val shortText = "[00:01.000]短\n"
            val second = LyricsFileTransfer.writeNextToAudio(context, source, name, shortText)
            assertEquals(first, second)
            assertEquals(shortText, LyricsFileTransfer.read(context, second))
            LyricsFileTransfer.write(context, second, longText)
            assertEquals(longText, LyricsFileTransfer.read(context, second))
        } finally {
            DocumentsContract.deleteDocument(context.contentResolver, directory)
        }
    }

    @Test
    fun legacyBatchConfigIsPersistedAndReusedAfterSettingsChange() = runBlocking {
        val koin = GlobalContext.get()
        val repository = koin.get<BatchTaskRepository>()
        val settings = koin.get<SettingsRepository>()
        val previous = settings.getLyricRenderConfig().format
        val taskId = repository.createTask(BatchTaskType.MATCH_LYRICS, emptyList(),
            """{"lyricRenderConfig":null,"futureField":"keep"}""")
        try {
            val original = requireNotNull(repository.getTask(taskId))
            val captured = BatchLyricsConfigSnapshot.prepare(original, settings, repository)
            assertEquals(captured.configJson, requireNotNull(repository.getTask(taskId)).configJson)
            assertTrue(requireNotNull(captured.configJson).contains("keep"))
            settings.saveLyricDisplayMode(if (previous == LyricsFormat.TTML) LyricsFormat.PLAIN_LRC else LyricsFormat.TTML)
            val recovered = BatchLyricsConfigSnapshot.prepare(requireNotNull(repository.getTask(taskId)), settings, repository)
            assertEquals(captured.configJson, recovered.configJson)
            assertFalse(repository.updateConfigIfUnchanged(taskId, original.configJson, "{}"))
        } finally {
            settings.saveLyricDisplayMode(previous)
            repository.deleteTask(taskId)
        }
    }

    @Test
    fun mainActivityOpensWithBothUiEngines() = runBlocking {
        val settings = GlobalContext.get().get<SettingsRepository>()
        val previous = settings.uiEngine.first()
        try {
            for (engine in UiEngine.entries) {
                settings.saveUiEngine(engine)
                val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                instrumentation.waitForIdleSync()
                assertFalse(activity.isFinishing)
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        } finally {
            settings.saveUiEngine(previous)
        }
    }

    private fun wave(): ByteArray {
        val samples = ByteArray(8820)
        return ByteBuffer.allocate(44 + samples.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(44100); putInt(88200)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(samples.size); put(samples)
        }.array()
    }

    private companion object {
        const val AUTHORITY = "com.lonx.lyrico.test.lyrics.documents"
    }
}
