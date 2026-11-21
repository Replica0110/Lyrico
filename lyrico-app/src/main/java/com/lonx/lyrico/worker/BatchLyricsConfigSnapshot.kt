package com.lonx.lyrico.worker

import com.lonx.lyrico.data.batch.BatchTaskRepository
import com.lonx.lyrico.data.batch.BatchTaskStatus
import com.lonx.lyrico.data.batch.BatchTaskType
import com.lonx.lyrico.data.db.entity.BatchTaskEntity
import com.lonx.lyrico.data.lyrics.LyricsRenderConfig
import com.lonx.lyrico.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Resolve old nullable configs once before item fan-out and persist them for retries and recovery. */
internal object BatchLyricsConfigSnapshot {
    suspend fun prepare(
        task: BatchTaskEntity,
        settings: SettingsRepository,
        repository: BatchTaskRepository
    ): BatchTaskEntity {
        if (task.type != BatchTaskType.MATCH_METADATA && task.type != BatchTaskType.MATCH_LYRICS) return task
        var current = task
        var fallback: LyricsRenderConfig? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            if (current.status != BatchTaskStatus.QUEUED && current.status != BatchTaskStatus.RUNNING) {
                throw CancellationException("Batch task is no longer active")
            }
            val config = Json.parseToJsonElement(requireNotNull(current.configJson) { "No config" }).jsonObject
            val saved = config["lyricRenderConfig"]
            if (saved != null && saved != JsonNull) return current
            val snapshot = fallback ?: settings.getLyricRenderConfig().also { fallback = it }
            val updated = JsonObject(config + ("lyricRenderConfig" to Json.encodeToJsonElement(LyricsRenderConfig.serializer(), snapshot)))
                .toString()
            currentCoroutineContext().ensureActive()
            if (repository.updateConfigIfUnchanged(current.taskId, current.configJson, updated)) {
                return current.copy(configJson = updated)
            }
            current = repository.getTask(current.taskId) ?: throw CancellationException("Batch task was removed")
        }
    }
}
