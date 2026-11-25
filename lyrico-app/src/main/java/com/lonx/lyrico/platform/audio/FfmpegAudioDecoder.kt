package com.lonx.lyrico.platform.audio

import android.content.Context
import androidx.core.net.toUri
import java.io.IOException
import java.nio.ByteBuffer

/** ALAC/APE fallback. PCM buffers are valid until the next read or close. */
internal class FfmpegAudioDecoder(context: Context, uriString: String) : AutoCloseable {
    companion object {
        init { System.loadLibrary("ffmpeg-decoder") }
    }

    private var pointer: Long = context.contentResolver.openAssetFileDescriptor(uriString.toUri(), "r")?.use {
        openNative(it.parcelFileDescriptor.fd, it.startOffset, it.length)
    } ?: throw IOException("Cannot open audio file")

    val sampleRate: Int
    val channels: Int
    val durationUs: Long
    val mimeType: String

    init {
        try {
            check(pointer != 0L) { "Cannot initialize audio decoder" }
            mimeType = mimeTypeNative(pointer)
            val format = formatNative(pointer)
            sampleRate = format[0]
            channels = format[1]
            durationUs = durationNative(pointer)
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun read(): ByteBuffer? {
        check(pointer != 0L) { "Decoder is closed" }
        return readNative(pointer)
    }

    override fun close() {
        if (pointer != 0L) {
            closeNative(pointer)
            pointer = 0
        }
    }

    private external fun openNative(fd: Int, offset: Long, length: Long): Long
    private external fun formatNative(pointer: Long): IntArray
    private external fun mimeTypeNative(pointer: Long): String
    private external fun durationNative(pointer: Long): Long
    private external fun readNative(pointer: Long): ByteBuffer?
    private external fun closeNative(pointer: Long)
}
