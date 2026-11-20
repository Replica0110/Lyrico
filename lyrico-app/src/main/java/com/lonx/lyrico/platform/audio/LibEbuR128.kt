package com.lonx.lyrico.platform.audio

import com.lonx.lyrico.data.model.replaygain.ReplayGainPeakMode
import java.nio.ByteBuffer

class LibEbuR128(
    val channels: Int,
    sampleRate: Int,
    private val peakMode: ReplayGainPeakMode
) : AutoCloseable {

    companion object {
        init {
            System.loadLibrary("ebur128")
        }
        const val FORMAT_SHORT = 1
        const val FORMAT_FLOAT = 2

        fun loudnessMultiple(states: List<LibEbuR128>): Double {
            if (states.isEmpty()) return -70.0
            return states.first().getMultipleLoudnessNative(states.map { it.nativePtr }.toLongArray())
        }
    }

    private var nativePtr: Long = 0
    var sampleCount: Long = 0
        private set

    init {
        nativePtr = initNative(channels, sampleRate, peakMode == ReplayGainPeakMode.TRUE_PEAK)
        if (nativePtr == 0L) {
            throw IllegalStateException("Failed to initialize libebur128")
        }
    }

    fun processDirect(buffer: ByteBuffer, isFloat: Boolean, frameCount: Int) {
        if (nativePtr == 0L || frameCount <= 0) return
        val format = if (isFloat) FORMAT_FLOAT else FORMAT_SHORT
        processDirectNative(nativePtr, buffer, format, frameCount)
        sampleCount += frameCount
    }

    val loudness: Double
        get() = if (nativePtr == 0L) -70.0 else getLoudnessNative(nativePtr)

    val peak: Double
        get() = if (nativePtr == 0L) {
            0.0
        } else {
            getPeakNative(nativePtr, channels, peakMode == ReplayGainPeakMode.TRUE_PEAK)
        }

    override fun close() {
        if (nativePtr != 0L) {
            destroyNative(nativePtr)
            nativePtr = 0L
        }
    }

    private external fun initNative(channels: Int, sampleRate: Int, useTruePeak: Boolean): Long
    private external fun destroyNative(statePtr: Long)
    private external fun processDirectNative(statePtr: Long, buffer: ByteBuffer, format: Int, frames: Int)
    private external fun getLoudnessNative(statePtr: Long): Double
    private external fun getPeakNative(statePtr: Long, channels: Int, useTruePeak: Boolean): Double
    private external fun getMultipleLoudnessNative(statePtrs: LongArray): Double
}
