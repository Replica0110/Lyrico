package com.lonx.lyrico.plugin.runtime

import com.lonx.lyrico.data.model.log.AppLog
import com.lonx.lyrico.data.model.log.AppLogType
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

internal class PluginCache(
    private val pluginId: String,
    private val cacheRootDir: File?,
    private val json: Json
) {
    private companion object {
        const val CACHE_LOG_TAG = "PlatformPluginCache"
    }

    fun get(key: String): String {
        val normalizedKey = key.trim()
        val file = cacheFile(normalizedKey) ?: run {
            logCache("get ignored blank key")
            return ""
        }
        if (!file.isFile) {
            logCache("miss plugin=$pluginId key=$normalizedKey")
            return ""
        }

        val entry = runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
        }.getOrNull() ?: run {
            file.delete()
            logCache("corrupt entry removed plugin=$pluginId key=$normalizedKey")
            return ""
        }

        val expiresAt = entry.longOrNull("expiresAt") ?: 0L
        if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) {
            file.delete()
            logCache("expired entry removed plugin=$pluginId key=$normalizedKey")
            return ""
        }

        logCache(
            "hit plugin=$pluginId key=$normalizedKey expiresAt=$expiresAt valueLength=${entry.string("value").length}"
        )
        return entry.string("value")
    }

    fun set(key: String, value: String, ttlMs: Long?) {
        val normalizedKey = key.trim()
        val file = cacheFile(normalizedKey) ?: run {
            logCache("set ignored blank key")
            return
        }
        val normalizedTtlMs = ttlMs ?: 0L
        val expiresAt = if (normalizedTtlMs > 0L) {
            System.currentTimeMillis() + normalizedTtlMs
        } else {
            0L
        }

        file.parentFile?.mkdirs()
        file.writeText(
            json.encodeToString(
                JsonObject.serializer(),
                buildJsonObject {
                    put("value", value)
                    put("expiresAt", expiresAt)
                }
            )
        )
        logCache(
            "set plugin=$pluginId key=$normalizedKey ttlMs=$normalizedTtlMs expiresAt=$expiresAt valueLength=${value.length}"
        )
    }

    fun remove(key: String) {
        val normalizedKey = key.trim()
        val removed = cacheFile(normalizedKey)?.delete() == true
        logCache("remove plugin=$pluginId key=$normalizedKey removed=$removed")
    }

    fun clear() {
        val cleared = pluginCacheDir()?.deleteRecursively() == true
        logCache("clear plugin=$pluginId cleared=$cleared")
    }

    private fun cacheFile(key: String): File? {
        val normalizedKey = key.trim()
        if (normalizedKey.isEmpty()) return null
        return File(pluginCacheDir() ?: return null, "${md5(normalizedKey)}.json")
    }

    private fun pluginCacheDir(): File? {
        val root = cacheRootDir ?: return null
        return File(root, md5(pluginId.ifBlank { "default" }))
    }

    private fun logCache(message: String) {
        AppLog.record(AppLogType.PLUGIN, CACHE_LOG_TAG, "Plugin cache", message, pluginId)
    }
}
