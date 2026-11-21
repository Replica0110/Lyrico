package com.lonx.lyrico.platform.audio

import com.lonx.lyrico.data.settings.ReplayGainPeakMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibEbuR128IntegrationTest {
    @Test
    fun concurrentStatesMeasureFloatAndShortPcmWithBothPeakModes() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = executor.invokeAll(ReplayGainPeakMode.entries.flatMap { mode ->
                listOf(true, false).map { isFloat -> Callable { measure(mode, isFloat) } }
            }).map { it.get() }
            for ((loudness, peak) in results) {
                // A mono 1 kHz sine at amplitude 0.5 is approximately -9 LUFS.
                assertEquals(-9.0, loudness, 0.2)
                assertEquals(0.5, peak, 0.01)
            }
            assertEquals(results.first().first, results.last().first, 0.01)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun albumCombinationAndSilenceKeepExistingContract() {
        LibEbuR128(1, RATE, ReplayGainPeakMode.SAMPLE_PEAK).use { first ->
            LibEbuR128(1, RATE, ReplayGainPeakMode.SAMPLE_PEAK).use { second ->
                val pcm = tone(true)
                first.processDirect(pcm, true, RATE)
                second.processDirect(pcm, true, RATE)
                assertEquals(first.loudness, LibEbuR128.loudnessMultiple(listOf(first, second)), 0.001)
                assertEquals(RATE.toLong(), first.sampleCount)
            }
        }
        LibEbuR128(1, RATE, ReplayGainPeakMode.TRUE_PEAK).use { silent ->
            silent.processDirect(ByteBuffer.allocateDirect(RATE * 4), true, RATE)
            assertTrue(silent.loudness == Double.NEGATIVE_INFINITY)
            assertEquals(0.0, silent.peak, 0.0)
        }
    }

    private fun measure(mode: ReplayGainPeakMode, isFloat: Boolean): Pair<Double, Double> =
        LibEbuR128(1, RATE, mode).use { state ->
            val pcm = tone(isFloat)
            repeat(3) { state.processDirect(pcm, isFloat, RATE) }
            state.loudness to state.peak
        }

    private fun tone(isFloat: Boolean): ByteBuffer =
        ByteBuffer.allocateDirect(RATE * if (isFloat) 4 else 2).order(ByteOrder.nativeOrder()).apply {
            repeat(RATE) { frame ->
                val sample = 0.5 * sin(2 * PI * 1000 * frame / RATE)
                if (isFloat) putFloat(sample.toFloat()) else putShort((sample * 32768).toInt().toShort())
            }
            flip()
        }

    private companion object {
        const val RATE = 48000
    }
}
