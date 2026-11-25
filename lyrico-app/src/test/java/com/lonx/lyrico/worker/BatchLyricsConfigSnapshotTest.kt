package com.lonx.lyrico.worker

import com.lonx.lyrico.data.batch.BatchMatchConfig
import com.lonx.lyrico.data.batch.BatchTaskRepository
import com.lonx.lyrico.data.batch.BatchTaskStatus
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.lyrics.LyricsFormat
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.worker.processor.MatchMetadataTaskConfig
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchLyricsConfigSnapshotTest {
    @Test
    fun `legacy missing or null config resolves once persists existing wire slot and survives settings changes`() = runBlocking {
        for (explicitNull in listOf(false, true)) {
            val old = MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", listOf("plugin"),
                sourceSettings = mapOf("plugin" to mapOf("token" to "keep")))
            val objectJson = Json.parseToJsonElement(Json.encodeToString(old)).jsonObject
            val raw = if (explicitNull) JsonObject(objectJson + ("lyricRenderConfig" to JsonNull)).toString() else objectJson.toString()
            val fixture = Fixture(task(raw))
            val prepared = BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository)
            val decoded = Json.decodeFromString<MatchMetadataTaskConfig>(requireNotNull(prepared.configJson))

            assertEquals(fixture.currentConfig, decoded.lyricRenderConfig)
            assertEquals(old.sourceSettings, decoded.sourceSettings)
            assertEquals(old.enabledSourceOrderIds, decoded.enabledSourceOrderIds)
            assertEquals(1, fixture.reads)
            assertEquals(1, fixture.writes)
            fixture.currentConfig = fixture.currentConfig.copy(format = LyricsFormat.TTML)
            val recovered = BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository)
            assertEquals(prepared.configJson, recovered.configJson)
            assertEquals(1, fixture.reads)
            assertEquals(1, fixture.writes)
        }
    }

    @Test
    fun `a competing stored snapshot wins atomically instead of mixing per item settings`() = runBlocking {
        val fixture = Fixture(task(Json.encodeToString(MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", emptyList()))))
        val winner = fixture.currentConfig.copy(format = LyricsFormat.VERBATIM_LRC)
        fixture.beforeCompare = {
            fixture.stored = fixture.stored.copy(configJson = Json.encodeToString(
                MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", emptyList(), lyricRenderConfig = winner)))
        }
        val prepared = BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository)

        assertEquals(winner, Json.decodeFromString<MatchMetadataTaskConfig>(prepared.configJson!!).lyricRenderConfig)
        assertEquals(1, fixture.reads)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun `cancellation while reading settings does not persist a partial task config`() = runBlocking {
        val fixture = Fixture(task(Json.encodeToString(MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", emptyList()))))
        fixture.readFailure = CancellationException("cancelled")
        val original = fixture.stored.configJson
        val failure = runCatching { BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(original, fixture.stored.configJson)
        assertEquals(0, fixture.writes)
        fixture.readFailure = null
        BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository)
        assertEquals(1, fixture.writes)
    }

    @Test
    fun `cancelled task racing the compare cannot be revived by snapshot preparation`() = runBlocking {
        val fixture = Fixture(task(Json.encodeToString(MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", emptyList()))))
        fixture.beforeCompare = { fixture.stored = fixture.stored.copy(status = BatchTaskStatus.CANCELLED) }
        val failure = runCatching { BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository) }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(BatchTaskStatus.CANCELLED, fixture.stored.status)
        assertEquals(0, fixture.writes)
    }

    @Test
    fun `new task snapshot is used without reading live settings or modifying unrelated task kinds`() = runBlocking {
        val saved = LyricsRenderConfig(LyricsFormat.TTML, true)
        val fixture = Fixture(task(Json.encodeToString(MatchMetadataTaskConfig(BatchMatchConfig(emptyMap()), "/", emptyList(),
            lyricRenderConfig = saved))))
        assertEquals(fixture.stored, BatchLyricsConfigSnapshot.prepare(fixture.stored, fixture.settings, fixture.repository))
        val unrelated = fixture.stored.copy(type = BatchTaskType.EXPORT_LYRICS)
        assertEquals(unrelated, BatchLyricsConfigSnapshot.prepare(unrelated, fixture.settings, fixture.repository))
        assertEquals(0, fixture.reads)
        assertEquals(0, fixture.writes)
    }

    private class Fixture(var stored: BatchTaskEntity) {
        var currentConfig = LyricsRenderConfig(LyricsFormat.PLAIN_LRC, true, removeEmptyLines = false)
        var reads = 0
        var writes = 0
        var readFailure: Throwable? = null
        var beforeCompare: (() -> Unit)? = null
        val settings = proxy<SettingsRepository> { method, _ ->
            check(method == "getLyricRenderConfig")
            reads++
            readFailure?.let { throw it }
            currentConfig
        }
        val repository = proxy<BatchTaskRepository> { method, args ->
            when (method) {
                "getTask" -> stored
                "updateConfigIfUnchanged" -> {
                    beforeCompare?.also { beforeCompare = null }?.invoke()
                    if (args!![1] == stored.configJson && stored.status in listOf(BatchTaskStatus.QUEUED, BatchTaskStatus.RUNNING)) {
                        stored = stored.copy(configJson = args[2] as String)
                        writes++
                        true
                    } else false
                }
                else -> error("Unexpected repository operation: $method")
            }
        }
    }

    private companion object {
        inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args)
            } as T

        fun task(configJson: String) = BatchTaskEntity("task", BatchTaskType.MATCH_LYRICS, BatchTaskStatus.QUEUED,
            3, 0, 0, 0, 0, null, configJson, null, null, null, 0, 0, null)
    }
}
