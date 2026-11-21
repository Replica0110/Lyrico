package com.lonx.lyrico.data.artwork

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkDimensionsReaderTest {
    @Test
    fun `reads bounds from the response stream and closes its body with a bounded call timeout`() = runBlocking {
        val call = PendingCall()
        val body = TrackingBody()
        val reader = ArtworkDimensionsReader(call) { stream ->
            assertEquals('i'.code, stream.read())
            640 to 480
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        assertEquals(URL, call.request().url.toString())
        assertEquals(TimeUnit.SECONDS.toNanos(15), call.timeout().timeoutNanos())
        call.respond(body)
        assertEquals(640 to 480, pending.await())
        assertTrue(body.closed)
        assertFalse(call.isCanceled())
    }

    @Test
    fun `failed status closes body without decoding it`() = runBlocking {
        val call = PendingCall()
        val body = TrackingBody()
        val reader = ArtworkDimensionsReader(call) { error("An HTTP error is not an image") }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        call.respond(body, 404)
        assertNull(pending.await())
        assertTrue(body.closed)
    }

    @Test
    fun `invalid image dimensions or decoder errors do not break search results and always close body`() = runBlocking {
        for (dimensions in listOf(null, 0 to 100, 100 to -1)) {
            val call = PendingCall()
            val body = TrackingBody()
            val reader = ArtworkDimensionsReader(call) { dimensions }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
            call.respond(body)
            assertNull(pending.await())
            assertTrue(body.closed)
        }
        val call = PendingCall()
        val body = TrackingBody()
        val reader = ArtworkDimensionsReader(call) { throw IOException("Unreadable image") }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        call.respond(body)
        assertNull(pending.await())
        assertTrue(body.closed)
    }

    @Test
    fun `blank and invalid URLs do not create network calls`() = runBlocking {
        val reader = ArtworkDimensionsReader(Call.Factory { error("No call should be created") }) { 1 to 1 }
        assertNull(reader.read(""))
        assertNull(reader.read("  "))
        assertNull(reader.read("not a URL"))
    }

    @Test
    fun `network failure is optional metadata failure`() = runBlocking {
        val call = PendingCall()
        val reader = ArtworkDimensionsReader(call) { error("No response to decode") }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        call.fail(IOException("Timed out"))
        assertNull(pending.await())
    }

    @Test
    fun `cancelled old request cancels HTTP and closes late response without overwriting new result`() = runBlocking {
        val oldCall = PendingCall()
        val newCall = PendingCall()
        var requests = 0
        val reader = ArtworkDimensionsReader(Call.Factory { request ->
            (if (requests++ == 0) oldCall else newCall).newCall(request)
        }) { 800 to 600 }
        val old = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        old.cancel()
        assertTrue(oldCall.isCanceled())
        val latest = async(start = CoroutineStart.UNDISPATCHED) { reader.read("https://example.com/new.jpg") }
        newCall.respond(TrackingBody())
        assertEquals(800 to 600, latest.await())
        val lateBody = TrackingBody()
        oldCall.respond(lateBody)
        assertTrue(lateBody.closed)
        assertTrue(old.isCancelled)
        assertEquals(800 to 600, latest.await())
    }

    @Test
    fun `decoder cancellation is not converted into empty metadata`() = runBlocking {
        val call = PendingCall()
        val body = TrackingBody()
        val reader = ArtworkDimensionsReader(call) { throw CancellationException("Cancelled decode") }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { reader.read(URL) }
        call.respond(body)
        val failure = runCatching { pending.await() }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(body.closed)
    }

    /** Controls callback order without a real server or Android bitmap decoder. */
    private class PendingCall : Call by OkHttpClient().newCall(Request.Builder().url(URL).build()), Call.Factory {
        private lateinit var incoming: Request
        private lateinit var callback: Callback
        private val timeout = Timeout()
        private var executed = false
        private var cancelled = false

        override fun newCall(request: Request): Call = apply { incoming = request }
        override fun request(): Request = incoming
        override fun timeout(): Timeout = timeout
        override fun execute(): Response = error("Expected async request")
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = cancelled
        override fun cancel() { cancelled = true }
        override fun clone(): Call = PendingCall().newCall(incoming)

        fun respond(body: ResponseBody, code: Int = 200) {
            callback.onResponse(this, Response.Builder().request(incoming).protocol(Protocol.HTTP_1_1)
                .code(code).message("Response").body(body).build())
        }

        fun fail(error: IOException) = callback.onFailure(this, error)
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
            private set
        private val content = Buffer().writeUtf8("image data")
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = content.size
        override fun source(): BufferedSource = content
        override fun close() { closed = true; content.close() }
    }

    private companion object {
        const val URL = "https://example.com/cover.jpg"
    }
}
