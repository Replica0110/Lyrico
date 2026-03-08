package com.lonx.audiotag.internal

import android.util.Log
import androidx.annotation.Keep
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.io.path.name

@Keep
internal class NativeInputStream(private val deviceFile: AudioFile, fis: FileInputStream) {
    private val channel = fis.channel

    fun name() = requireNotNull(deviceFile.path.name)

    fun readBlock(buf: ByteBuffer): Int {
        try {
            return channel.read(buf)
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error reading block", e)
            return -2
        }
    }

    fun isOpen(): Boolean {
        return channel.isOpen
    }

    fun seekFromBeginning(offset: Long): Boolean {
        try {
            channel.position(offset)
            return true
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error seeking from beginning", e)
            return false
        }
    }

    fun seekFromCurrent(offset: Long): Boolean {
        try {
            channel.position(channel.position() + offset)
            return true
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error seeking from current", e)
            return false
        }
    }

    fun seekFromEnd(offset: Long): Boolean {
        try {
            channel.position(channel.size() + offset)
            return true
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error seeking from end", e)
            return false
        }
    }

    fun tell() =
        try {
            channel.position()
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error getting position", e)
            Long.MIN_VALUE
        }

    fun length() =
        try {
            channel.size()
        } catch (e: Exception) {
            Log.d("NativeInputStream", "Error getting length", e)
            Long.MIN_VALUE
        }

    fun close() {
        channel.close()
    }
}