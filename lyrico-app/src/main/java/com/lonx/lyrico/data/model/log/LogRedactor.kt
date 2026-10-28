package com.lonx.lyrico.data.model.log

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Redact before storage/recording, and again when exporting older log entries. */
internal class LogRedactor(secretValues: Collection<String> = emptyList()) {
    private val secrets = secretValues.filter { it.isNotBlank() }.flatMap {
        val encoded = URLEncoder.encode(it, "UTF-8")
        listOf(it, encoded, encoded.replace("+", "%20"),
            encoded.replace("+", "%20").replace("%21", "!").replace("%27", "'")
                .replace("%28", "(").replace("%29", ")").replace("%7E", "~"),
            JsonPrimitive(it).toString().removeSurrounding("\""))
    }.distinct().sortedByDescending(String::length)

    fun redact(text: String): String {
        val parsed = if (text.trimStart().firstOrNull() in listOf('{', '[')) {
            runCatching { Json.parseToJsonElement(text) }.getOrNull()
        } else null
        var result = parsed?.let { clean(it).toString() } ?: text
        secrets.forEach { result = result.replace(it, "[hidden]") }
        if (parsed == null) result = headers.replace(result) { it.groupValues[1] + "[hidden]" }
        result = quotedAssignments.replace(result) { it.groupValues[1] + "\"[hidden]\"" }
        result = assignments.replace(result) { it.groupValues[1] + "[hidden]" }
        result = credentials.replace(result) { it.groupValues[1] + "[hidden]@" }
        return httpQueries.replace(result) { it.groupValues[1] + "?[hidden]" }
    }

    private fun clean(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) ->
            if (isSensitiveKey(key) || key in setOf("config", "headers", "internal")) JsonPrimitive("[hidden]") else clean(value)
        })
        is JsonArray -> JsonArray(element.map(::clean))
        else -> element
    }

    companion object {
        private val sensitive = Regex("(?i)cookie|auth|password|passwd|secret|token|credential|api.?key|signature|session|^sign$")
        private const val SENSITIVE_TEXT_KEY = "(?:[\\w-]*(?:cookie|auth|token|secret|password|passwd|credential|api[_-]?key|signature|session)[\\w-]*|sign)"
        private val headers = Regex("(?im)((?:authorization|proxy-authorization|cookies?|set-cookie)[\"']?\\s*[:=]\\s*)[^\\r\\n]+")
        private val quotedAssignments = Regex("(?i)($SENSITIVE_TEXT_KEY[\"']?\\s*[:=]\\s*)(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')")
        private val assignments = Regex("(?i)($SENSITIVE_TEXT_KEY[\"']?\\s*[:=]\\s*[\"']?)([^\"'\\s&,;}\\\\]+)")
        private val credentials = Regex("(?i)(https?://)[^/\\s@]+@")
        private val httpQueries = Regex("""(?i)(https?://[^\s?"'<>]+)\?[^\s"'<>]+""")
        fun isSensitiveKey(key: String): Boolean = sensitive.containsMatchIn(key)
    }
}
