package com.lonx.lyrico.utils

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class ReplayGainAnalysis(
    val sumSquares: Double,
    val sampleCount: Long,
    val peak: Double
) {
    val rms: Double
        get() = if (sampleCount == 0L) 0.0 else sqrt(sumSquares / sampleCount)
}

sealed interface ReplayGainScanResult {
    data class Success(
        val analysis: ReplayGainAnalysis,
        val mimeType: String
    ) : ReplayGainScanResult

    data class UnsupportedCodec(
        val mimeType: String?
    ) : ReplayGainScanResult

    data class Failed(
        val mimeType: String?,
        val message: String?
    ) : ReplayGainScanResult
}

class ReplayGainScanner(
    private val context: Context
) {
    companion object {
        private const val TARGET_LOUDNESS_DBFS = -18.0
        private const val MIN_RMS = 1e-9
    }

    suspend fun analyze(uriString: String): ReplayGainScanResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var mimeType: String? = null

        return try {
            extractor.setDataSource(context, Uri.parse(uriString), null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return ReplayGainScanResult.Failed(null, "No audio track found")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: return ReplayGainScanResult.Failed(null, "Unknown audio MIME type")
            mimeType = mime
            val decoderName = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format)
                ?: return ReplayGainScanResult.UnsupportedCodec(mime)

            codec = MediaCodec.createByCodecName(decoderName).apply {
                configure(format, null, null, 0)
                start()
            }
            val codecRef = codec ?: return ReplayGainScanResult.Failed(mime, "Decoder initialization failed")

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sumSquares = 0.0
            var sampleCount = 0L
            var peak = 0.0
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codecRef.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codecRef.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codecRef.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            codecRef.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codecRef.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codecRef.outputFormat
                        if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputIndex >= 0) {
                            val outputBuffer = codecRef.getOutputBuffer(outputIndex)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        val floatBuffer = outputBuffer.slice()
                                            .order(ByteOrder.LITTLE_ENDIAN)
                                            .asFloatBuffer()
                                        while (floatBuffer.hasRemaining()) {
                                            val sample = floatBuffer.get().toDouble().coerceIn(-1.0, 1.0)
                                            val absSample = abs(sample)
                                            sumSquares += sample * sample
                                            sampleCount++
                                            if (absSample > peak) peak = absSample
                                        }
                                    }

                                    else -> {
                                        val shortBuffer = outputBuffer.slice()
                                            .order(ByteOrder.LITTLE_ENDIAN)
                                            .asShortBuffer()
                                        while (shortBuffer.hasRemaining()) {
                                            val sample = (shortBuffer.get().toInt() / 32768.0).coerceIn(-1.0, 1.0)
                                            val absSample = abs(sample)
                                            sumSquares += sample * sample
                                            sampleCount++
                                            if (absSample > peak) peak = absSample
                                        }
                                    }
                                }
                            }

                            codecRef.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEnded = true
                            }
                        }
                    }
                }
            }

            if (sampleCount == 0L) {
                ReplayGainScanResult.Failed(mime, "Decoded sample count is zero")
            } else {
                ReplayGainScanResult.Success(
                    analysis = ReplayGainAnalysis(sumSquares, sampleCount, peak),
                    mimeType = mime
                )
            }
        } catch (e: IllegalStateException) {
            ReplayGainScanResult.Failed(
                mimeType,
                mapCodecStateError(mimeType, e.message)
            )
        } catch (e: Exception) {
            ReplayGainScanResult.Failed(mimeType, e.message)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    fun formatGain(analysis: ReplayGainAnalysis): String {
        val loudnessDb = 20.0 * log10(analysis.rms.coerceAtLeast(MIN_RMS))
        val gainDb = TARGET_LOUDNESS_DBFS - loudnessDb
        return "%.2f dB".format(java.util.Locale.US, gainDb)
    }

    fun formatAlbumGain(sumSquares: Double, sampleCount: Long): String {
        val rms = if (sampleCount == 0L) MIN_RMS else sqrt(sumSquares / sampleCount).coerceAtLeast(MIN_RMS)
        val loudnessDb = 20.0 * log10(rms)
        val gainDb = TARGET_LOUDNESS_DBFS - loudnessDb
        return "%.2f dB".format(java.util.Locale.US, gainDb)
    }

    fun formatPeak(peak: Double): String {
        return "%.6f".format(java.util.Locale.US, peak.coerceIn(0.0, 1.0))
    }

    fun buildFailureMessage(result: ReplayGainScanResult): String {
        return when (result) {
            is ReplayGainScanResult.Success -> "ReplayGain 标签已生成，可继续手动调整"
            is ReplayGainScanResult.UnsupportedCodec -> {
                val formatName = describeMimeType(result.mimeType)
                "当前设备不支持解码 $formatName，暂时无法扫描 ReplayGain"
            }
            is ReplayGainScanResult.Failed -> {
                val formatName = describeMimeType(result.mimeType)
                if (result.message.isNullOrBlank()) {
                    "扫描 ReplayGain 失败: $formatName"
                } else {
                    "扫描 ReplayGain 失败: $formatName，${result.message}"
                }
            }
        }
    }

    private fun describeMimeType(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "audio/alac" -> "ALAC (m4a)"
            "audio/raw" -> "PCM/WAV"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm" -> "AAC/MP4"
            null -> "未知格式"
            else -> mimeType
        }
    }

    private fun mapCodecStateError(mimeType: String?, message: String?): String {
        if (message.isNullOrBlank()) {
            return "解码器进入异常状态"
        }

        return when {
            mimeType.equals("audio/alac", ignoreCase = true) &&
                message.contains("Executing states", ignoreCase = true) -> {
                "当前设备上的 ALAC 解码器不可用或不稳定"
            }
            else -> message
        }
    }
}
