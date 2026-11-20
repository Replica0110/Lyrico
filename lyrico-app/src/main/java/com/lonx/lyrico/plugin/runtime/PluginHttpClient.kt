package com.lonx.lyrico.plugin.runtime

import android.util.Base64
import com.lonx.lyrico.data.model.log.LogOperation
import com.lonx.lyrico.data.network.PluginRequestLogContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class PluginHttpClient(
    private val appInfo: HostAppInfo,
    private val okHttpClient: OkHttpClient,
    private val pluginId: String
) {
    private val cancelled = AtomicBoolean(false)
    private val activeHttp = AtomicReference<Call?>(null)
    private var invocationDeadline = 0L

    fun resetCancellation() { cancelled.set(false) }

    fun cancelInvocation() {
        cancelled.set(true)
        activeHttp.get()?.cancel()
    }

    fun beginInvocation(timeoutMs: Long) {
        invocationDeadline = if (timeoutMs > 0) System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs) else 0L
    }

    fun execute(
        method: String,
        payload: JsonObject,
        binaryResponse: Boolean
    ): HostHttpResponse {
        val urlText = payload.string("url")
        require(urlText.isNotBlank()) { "HTTP url is blank" }

        val contentType = payload.string("contentType").ifBlank {
            if (binaryResponse) {
                "application/octet-stream"
            } else {
                "application/json; charset=utf-8"
            }
        }

        val requestBuilder = Request.Builder().url(urlText)
            .tag(PluginRequestLogContext::class.java, PluginRequestLogContext(pluginId, LogOperation.current.get()?.id))
        if (method == "POST" || method == "PUT" || method == "PATCH") {
            requestBuilder.header("Content-Type", contentType)
        }

        payload.obj("headers")?.forEach { (key, value) ->
            val headerValue = value.headerString()
            if (key.equals("User-Agent", ignoreCase = true) && headerValue.isBlank()) {
                return@forEach
            }
            requestBuilder.header(key, headerValue)
        }

        if (!hasNonBlankHeader(payload.obj("headers"), "User-Agent")) {
            requestBuilder.header("User-Agent", buildDefaultUserAgent(appInfo))
        }

        val requestBody = if (method == "POST" || method == "PUT" || method == "PATCH") {
            payload.requestBodyBytes().toRequestBody(contentType.toMediaType())
        } else {
            null
        }

        requestBuilder.method(method, requestBody)

        check(!cancelled.get()) { "Plugin invocation cancelled" }
        val remainingNanos = invocationDeadline - System.nanoTime()
        check(invocationDeadline == 0L || remainingNanos > 0) { "Plugin invocation timed out" }
        val remainingMs = if (invocationDeadline == 0L) 0L else
            TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L)
        val client = okHttpClient.newBuilder()
            .callTimeout(remainingMs, TimeUnit.MILLISECONDS)
            .followRedirects(payload.booleanOrNull("followRedirects") ?: true)
            .followSslRedirects(payload.booleanOrNull("followRedirects") ?: true)
            .connectTimeout((payload.intOrNull("connectTimeoutMs") ?: 8_000).toLong(), TimeUnit.MILLISECONDS)
            .readTimeout((payload.intOrNull("readTimeoutMs") ?: 12_000).toLong(), TimeUnit.MILLISECONDS)
            .build()

        val call = client.newCall(requestBuilder.build())
        activeHttp.set(call)
        if (cancelled.get()) call.cancel()
        try {
            return call.execute().use { response ->
                val limit = 8L * 1024 * 1024
                require(response.body.contentLength() <= limit) { "Plugin response is too large" }
                val output = ByteArrayOutputStream()
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        check(!cancelled.get()) { "Plugin invocation cancelled" }
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size().toLong() + count <= limit) { "Plugin response is too large" }
                        output.write(buffer, 0, count)
                    }
                }
                val responseBytes = output.toByteArray()
                val bodyText = if (binaryResponse) {
                    ""
                } else {
                    responseBytes.toString(Charsets.UTF_8)
                }

                val bodyBase64 = if (binaryResponse) {
                    Base64.encodeToString(responseBytes, Base64.NO_WRAP)
                } else {
                    ""
                }

                HostHttpResponse(
                    code = response.code,
                    message = response.message,
                    headers = response.headers.toMultimap(),
                    bodyText = bodyText,
                    bodyBase64 = bodyBase64
                )
            }
        } finally { activeHttp.compareAndSet(call, null) }
    }

    private fun hasNonBlankHeader(headers: JsonObject?, targetName: String): Boolean {
        if (headers == null) return false
        return headers.any { (key, value) ->
            key.equals(targetName, ignoreCase = true) && value.headerString().isNotBlank()
        }
    }

    private fun JsonObject.requestBodyBytes(): ByteArray {
        val bodyBase64 = string("bodyBase64")
        if (bodyBase64.isNotBlank()) {
            return Base64.decode(bodyBase64, Base64.DEFAULT)
        }

        val bodyBytes = this["bodyBytes"] as? JsonArray
        if (bodyBytes != null) {
            return ByteArray(bodyBytes.size) { index ->
                bodyBytes[index].jsonPrimitive.int.toByte()
            }
        }

        return string("body").toByteArray(Charsets.UTF_8)
    }
}

internal data class HostHttpResponse(
    val code: Int,
    val message: String,
    val headers: Map<String, List<String>>,
    val bodyText: String,
    val bodyBase64: String
)

private fun JsonElement.headerString(): String {
    return when (this) {
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString(", ") {
            it.jsonPrimitive.contentOrNull.orEmpty()
        }
        else -> toString()
    }
}

private fun JsonObject.booleanOrNull(key: String): Boolean? {
    return this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
}
