package com.lonx.lyrico.plugin.source

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Host-owned requests never inherit plugin request headers or cookies. */
class PluginDistributionClient(private val client: OkHttpClient = defaultClient()) {
    companion object {
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                try { PluginCatalogParser.requireUrl(chain.request().url.toString()) }
                catch (error: IllegalArgumentException) { throw IOException(error.message, error) }
                chain.proceed(chain.request())
            }
            .build()
    }

    suspend fun fetch(url: String): PluginCatalog = request(url) { response, active ->
        val bytes = java.io.ByteArrayOutputStream()
        read(response, 1024 * 1024, active) { buffer, count -> bytes.write(buffer, 0, count) }
        PluginCatalogParser.parse(bytes.toString(Charsets.UTF_8.name()), response.request.url.toString())
    }

    suspend fun download(release: PluginRelease, target: File, progress: (Int) -> Unit) {
        try {
            request(release.downloadUrl) { response, active ->
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                try {
                    target.outputStream().use { output ->
                        read(response, release.size, active) { buffer, count ->
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            total += count
                            progress((total * 100 / release.size).toInt())
                        }
                    }
                    require(total == release.size) { "Plugin package size mismatch" }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    require(hash.equals(release.sha256, ignoreCase = true)) { "Plugin package SHA-256 mismatch" }
                } finally { if (!active()) target.delete() }
            }
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun read(
        response: Response,
        limit: Long,
        active: () -> Boolean,
        consume: (ByteArray, Int) -> Unit
    ) {
        require(response.body.contentLength() <= limit) { "Response is too large" }
        response.body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                check(active()) { "Request cancelled" }
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "Response is too large" }
                consume(buffer, count)
            }
        }
    }

    private suspend fun <T> request(url: String, consume: (Response, () -> Boolean) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(PluginCatalogParser.requireUrl(url)).build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(runCatching {
                        response.use {
                            check(it.isSuccessful) { "HTTP ${it.code}" }
                            consume(it) { continuation.isActive }
                        }
                    })
                }
            })
        }
}
