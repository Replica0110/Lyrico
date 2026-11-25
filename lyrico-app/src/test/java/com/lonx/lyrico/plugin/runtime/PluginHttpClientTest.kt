package com.lonx.lyrico.plugin.runtime

import com.lonx.lyrico.data.network.PluginRequestLogContext
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHttpClientTest {
    @Test
    fun preservesRequestBodyHeadersUserAgentAndResponseMetadata() {
        lateinit var request: Request
        val http = client { incoming ->
            request = incoming
            response(incoming, "返回文本".toResponseBody())
        }
        val result = http.execute(
            "POST",
            payload("""{"url":"https://example.com/api","body":"ignored","bodyBytes":[65,255],"headers":{"User-Agent":"","X-Values":["first","second"]}}"""),
            binaryResponse = false
        )

        val bytes = Buffer().also { request.body!!.writeTo(it) }.readByteArray()
        assertTrue(byteArrayOf(65, -1).contentEquals(bytes))
        assertEquals("Lyrico/1.2.3", request.header("User-Agent"))
        assertEquals("first, second", request.header("X-Values"))
        assertEquals("application/json; charset=utf-8", request.header("Content-Type"))
        assertEquals("provider", request.tag(PluginRequestLogContext::class.java)!!.pluginId)
        assertEquals(207, result.code)
        assertEquals("Multi-Status", result.message)
        assertEquals(listOf("first", "second"), result.headers["X-Values"])
        assertEquals("返回文本", result.bodyText)
        assertEquals("", result.bodyBase64)
    }

    @Test
    fun preservesExplicitUserAgentAndUtf8TextBody() {
        lateinit var request: Request
        val http = client { incoming ->
            request = incoming
            response(incoming, "".toResponseBody())
        }
        http.execute(
            "POST",
            payload("""{"url":"https://example.com/api","body":"歌词","headers":{"user-agent":"custom"}}"""),
            binaryResponse = false
        )
        assertEquals("custom", request.header("User-Agent"))
        assertEquals("歌词", Buffer().also { request.body!!.writeTo(it) }.readUtf8())
    }

    @Test
    fun beginningInvocationDoesNotResetCancellation() {
        val calls = AtomicInteger()
        val http = client { request ->
            calls.incrementAndGet()
            response(request, "ok".toResponseBody())
        }
        http.cancelInvocation()
        http.beginInvocation(10_000)
        val error = assertThrows(IllegalStateException::class.java) {
            http.execute("GET", requestPayload(), binaryResponse = false)
        }
        assertEquals("Plugin invocation cancelled", error.message)
        assertEquals(0, calls.get())
        http.resetCancellation()
        assertEquals("ok", http.execute("GET", requestPayload(), binaryResponse = false).bodyText)
    }

    @Test
    fun rejectsExpiredInvocationBeforeStartingHttpCall() {
        val calls = AtomicInteger()
        val http = client { request ->
            calls.incrementAndGet()
            response(request, "ok".toResponseBody())
        }
        http.beginInvocation(1)
        Thread.sleep(20)
        val error = assertThrows(IllegalStateException::class.java) {
            http.execute("GET", requestPayload(), binaryResponse = false)
        }
        assertEquals("Plugin invocation timed out", error.message)
        assertEquals(0, calls.get())
    }

    @Test
    fun cancelsTheActiveCallAndClosesTheResponseBody() {
        var closed = false
        lateinit var http: PluginHttpClient
        http = client { request ->
            http.cancelInvocation()
            response(request, object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = -1
                override fun source(): BufferedSource = Buffer().writeUtf8("body")
                override fun close() { closed = true }
            })
        }
        val error = assertThrows(Exception::class.java) {
            http.execute("GET", requestPayload(), binaryResponse = false)
        }
        assertTrue(error.message in setOf("Canceled", "Plugin invocation cancelled"))
        assertTrue(closed)
    }

    @Test
    fun enforcesSizeLimitForDeclaredAndUnknownLengths() {
        val limit = 8 * 1024 * 1024
        for (declared in listOf(true, false)) {
            var closed = false
            val http = client { request ->
                val buffer = Buffer().write(ByteArray(limit + 1))
                response(request, object : ResponseBody() {
                    override fun contentType(): MediaType? = null
                    override fun contentLength(): Long = if (declared) limit.toLong() + 1 else -1
                    override fun source(): BufferedSource = buffer
                    override fun close() { closed = true }
                })
            }
            val error = assertThrows(IllegalArgumentException::class.java) {
                http.execute("GET", requestPayload(), binaryResponse = false)
            }
            assertEquals("Plugin response is too large", error.message)
            assertTrue(closed)
        }
    }

    private fun client(respond: (Request) -> Response): PluginHttpClient = PluginHttpClient(
        HostAppInfo(versionName = "1.2.3"),
        OkHttpClient.Builder().addInterceptor { chain -> respond(chain.request()) }.build(),
        "provider"
    )

    private fun response(request: Request, body: ResponseBody): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(207)
        .message("Multi-Status")
        .addHeader("X-Values", "first")
        .addHeader("X-Values", "second")
        .body(body)
        .build()

    private fun requestPayload(): JsonObject = payload("""{"url":"https://example.com/api"}""")

    private fun payload(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
