package com.lonx.lyrico.data.artwork

import android.graphics.BitmapFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

/** Reads image bounds without allocating the bitmap; optional metadata failures return null. */
internal class ArtworkDimensionsReader(
    private val calls: Call.Factory,
    private val decodeBounds: (InputStream) -> Pair<Int, Int>? = ::decodeArtworkBounds
) {
    suspend fun read(url: String): Pair<Int, Int>? {
        currentCoroutineContext().ensureActive()
        if (url.isBlank()) return null
        return try {
            val request = Request.Builder().url(url).build()
            suspendCancellableCoroutine { continuation ->
                val call = calls.newCall(request)
                call.timeout().timeout(15, TimeUnit.SECONDS)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, error: IOException) {
                        continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use {
                                if (!continuation.isActive || !it.isSuccessful) null
                                else decodeBounds(it.body.byteStream())?.takeIf { (width, height) ->
                                    width > 0 && height > 0
                                }
                            }
                        }
                        continuation.resumeWith(result)
                    }
                })
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            currentCoroutineContext().ensureActive()
            null
        }
    }
}

private fun decodeArtworkBounds(stream: InputStream): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(stream, null, options)
    return (options.outWidth to options.outHeight).takeIf { (width, height) -> width > 0 && height > 0 }
}
