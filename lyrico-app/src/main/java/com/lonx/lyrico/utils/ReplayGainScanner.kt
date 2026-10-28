package com.lonx.lyrico.utils

import android.content.Context
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.recordOperation
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import com.lonx.lyrico.data.model.ReplayGainPeakMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.coroutines.cancellation.CancellationException

data class ReplayGainAnalysis(
    val loudnessLufs: Double,
    val sampleCount: Long,
    val peak: Double
)

data class AlbumReplayGainAnalysis(
    val loudnessLufs: Double,
    val sampleCount: Long,
    val peak: Double,
    val tracks: List<ReplayGainAnalysis>
)

// 定义具体的错误原因，方便调用方根据枚举或类名进行多语言/自定义处理
sealed interface ReplayGainError {
    data object NoAudioTrack : ReplayGainError
    data object UnknownMimeType : ReplayGainError
    data object ZeroSampleCount : ReplayGainError
    data class UnsupportedCodec(val mimeType: String?) : ReplayGainError
    data class CodecException(val message: String?, val isAlacIssue: Boolean = false) : ReplayGainError
    data class GeneralException(val message: String?) : ReplayGainError
}

sealed interface ReplayGainCalculateState {
    data class Progress(val percent: Float) : ReplayGainCalculateState
    data object Cancelled : ReplayGainCalculateState
    data class Success(
        val analysis: ReplayGainAnalysis,
        val mimeType: String
    ) : ReplayGainCalculateState

    // 将失败统一为一个状态，携带错误原因对象
    data class Failed(
        val mimeType: String?,
        val error: ReplayGainError
    ) : ReplayGainCalculateState
}

sealed interface AlbumReplayGainCalculateState {
    data class Progress(val percent: Float) : AlbumReplayGainCalculateState
    data object Cancelled : AlbumReplayGainCalculateState
    data class Success(val analysis: AlbumReplayGainAnalysis) : AlbumReplayGainCalculateState
    data class Failed(
        val uriString: String?,
        val mimeType: String?,
        val error: ReplayGainError
    ) : AlbumReplayGainCalculateState
}

class ReplayGainScanner(private val context: Context, private val appLogRepository: AppLogRepository? = null) {
    companion object {
        const val DEFAULT_TARGET_LOUDNESS_LUFS = -18.0
        private const val PROGRESS_UPDATE_THRESHOLD = 0.01
    }

    fun analyze(
        uriString: String,
        peakMode: ReplayGainPeakMode = ReplayGainPeakMode.SAMPLE_PEAK
    ): Flow<ReplayGainCalculateState> = flow {
        var decoded: ReplayGainDecodeResult? = null
        try {
            emit(ReplayGainCalculateState.Progress(0f))
            decoded = decodeToState(uriString, peakMode) { progress ->
                emit(ReplayGainCalculateState.Progress(progress))
            }
            emit(ReplayGainCalculateState.Progress(1.0f))
            emit(
                ReplayGainCalculateState.Success(
                    analysis = decoded.analysis,
                    mimeType = decoded.mimeType
                )
            )
        } catch (e: CancellationException) {
            emit(ReplayGainCalculateState.Cancelled)
            throw e
        } catch (e: ReplayGainScanException) {
            emit(ReplayGainCalculateState.Failed(e.mimeType, e.error))
        } finally {
            runCatching { decoded?.state?.close() }
        }
    }.flowOn(Dispatchers.IO)

    fun analyzeAlbum(
        uriStrings: List<String>,
        peakMode: ReplayGainPeakMode = ReplayGainPeakMode.SAMPLE_PEAK
    ): Flow<AlbumReplayGainCalculateState> = flow {
        if (uriStrings.isEmpty()) {
            emit(AlbumReplayGainCalculateState.Failed(null, null, ReplayGainError.ZeroSampleCount))
            return@flow
        }

        val decodedTracks = mutableListOf<ReplayGainDecodeResult>()
        try {
            emit(AlbumReplayGainCalculateState.Progress(0f))
            uriStrings.forEachIndexed { index, uriString ->
                val decoded = decodeToState(uriString, peakMode) { trackProgress ->
                    val albumProgress = (index + trackProgress) / uriStrings.size.toFloat()
                    emit(AlbumReplayGainCalculateState.Progress(albumProgress.coerceIn(0f, 1f)))
                }
                decodedTracks += decoded
            }

            val states = decodedTracks.map { it.state }
            val albumLoudness = LibEbuR128.loudnessMultiple(states)
            val albumPeak = decodedTracks.maxOf { it.analysis.peak }
            val sampleCount = decodedTracks.sumOf { it.analysis.sampleCount }

            emit(AlbumReplayGainCalculateState.Progress(1.0f))
            emit(
                AlbumReplayGainCalculateState.Success(
                    AlbumReplayGainAnalysis(
                        loudnessLufs = albumLoudness,
                        sampleCount = sampleCount,
                        peak = albumPeak,
                        tracks = decodedTracks.map { it.analysis }
                    )
                )
            )
        } catch (e: CancellationException) {
            emit(AlbumReplayGainCalculateState.Cancelled)
            throw e
        } catch (e: ReplayGainScanException) {
            emit(AlbumReplayGainCalculateState.Failed(e.uriString, e.mimeType, e.error))
        } finally {
            decodedTracks.forEach { decoded ->
                runCatching { decoded.state.close() }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun decodeToState(
        uriString: String,
        peakMode: ReplayGainPeakMode,
        onProgress: suspend (Float) -> Unit
    ): ReplayGainDecodeResult {
        try {
            return decodeWithPlatform(uriString, peakMode, onProgress)
        } catch (platformError: ReplayGainScanException) {
            currentCoroutineContext().ensureActive()
            appLogRepository?.recordOperation(AppLogType.METADATA, "ReplayGainScanner", "Platform decoding failed; trying FFmpeg",
                "uri=$uriString\nmime=${platformError.mimeType}\npeakMode=$peakMode\nerror=${platformError.error}", uriString)
            // The platform state is already closed. Retry from the beginning with a new state.
            var state: LibEbuR128? = null
            var completed = false
            var fallbackMimeType: String? = null
            try {
                FfmpegAudioDecoder(context, uriString).use { decoder ->
                    fallbackMimeType = decoder.mimeType
                    onProgress(0f)
                    val analysisState = LibEbuR128(decoder.channels, decoder.sampleRate, peakMode)
                    state = analysisState
                    var lastProgress = 0.0
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val pcm = decoder.read() ?: break
                        analysisState.processDirect(pcm, true, pcm.remaining() / (decoder.channels * 4))
                        if (decoder.durationUs > 0) {
                            val progress = (analysisState.sampleCount.toDouble() / decoder.sampleRate * 1_000_000 / decoder.durationUs).coerceIn(0.0, 1.0)
                            if (progress - lastProgress >= PROGRESS_UPDATE_THRESHOLD) {
                                onProgress(progress.toFloat())
                                lastProgress = progress
                            }
                        }
                    }
                    if (analysisState.sampleCount == 0L) {
                        throw ReplayGainScanException(uriString, decoder.mimeType, ReplayGainError.ZeroSampleCount)
                    }
                    val result = ReplayGainDecodeResult(
                        ReplayGainAnalysis(analysisState.loudness, analysisState.sampleCount, analysisState.peak),
                        decoder.mimeType, analysisState
                    )
                    appLogRepository?.recordOperation(AppLogType.METADATA, "ReplayGainScanner", "FFmpeg decoding succeeded",
                        "uri=$uriString\nmime=${decoder.mimeType}\nsampleRate=${decoder.sampleRate}\nchannels=${decoder.channels}\nsampleCount=${analysisState.sampleCount}", uriString)
                    completed = true
                    return result
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: UnsupportedOperationException) {
                appLogRepository?.recordOperation(AppLogType.METADATA, "ReplayGainScanner", "ReplayGain decoding failed",
                    "uri=$uriString\nplatformError=${platformError.error}\nfallback=${error.stackTraceToString()}", uriString, AppLogLevel.ERROR)
                throw platformError
            } catch (error: ReplayGainScanException) {
                appLogRepository?.recordOperation(AppLogType.METADATA, "ReplayGainScanner", "ReplayGain decoding failed",
                    "uri=$uriString\nplatformError=${platformError.error}\nfallbackError=${error.error}", uriString, AppLogLevel.ERROR)
                throw error
            } catch (error: Exception) {
                appLogRepository?.recordOperation(AppLogType.METADATA, "ReplayGainScanner", "ReplayGain decoding failed",
                    "uri=$uriString\nplatformError=${platformError.error}\nfallback=${error.stackTraceToString()}", uriString, AppLogLevel.ERROR)
                val failedMimeType = fallbackMimeType ?: platformError.mimeType?.takeIf {
                    it.equals("audio/alac", true) || it.equals("audio/ape", true) || it.equals("audio/x-ape", true)
                }
                if (failedMimeType != null) {
                    throw ReplayGainScanException(uriString, failedMimeType, ReplayGainError.GeneralException(error.message))
                }
                platformError.addSuppressed(error)
                throw platformError
            } finally {
                if (!completed) runCatching { state?.close() }
            }
        }
    }

    private suspend fun decodeWithPlatform(
        uriString: String,
        peakMode: ReplayGainPeakMode,
        onProgress: suspend (Float) -> Unit
    ): ReplayGainDecodeResult {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var mimeType: String? = null
        var ebuR128: LibEbuR128? = null
        var completed = false

        try {
            extractor.setDataSource(context, uriString.toUri(), null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }

            if (trackIndex == null) {
                throw ReplayGainScanException(uriString, null, ReplayGainError.NoAudioTrack)
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            mimeType = format.getString(MediaFormat.KEY_MIME)
            if (mimeType == null) {
                throw ReplayGainScanException(uriString, null, ReplayGainError.UnknownMimeType)
            }

            val decoderName = MediaCodecList(MediaCodecList.ALL_CODECS).findDecoderForFormat(format)
            if (decoderName == null) {
                throw ReplayGainScanException(uriString, mimeType, ReplayGainError.UnsupportedCodec(mimeType))
            }

            codec = MediaCodec.createByCodecName(decoderName).apply {
                configure(format, null, null, 0)
                start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var lastReportedProgress = 0.0

            onProgress(0f)

            while (!outputEnded) {
                // 检查协程是否被取消，如果取消会抛出 CancellationException
                currentCoroutineContext().ensureActive()

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        pcmEncoding = if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        val sr = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val ch = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        ebuR128?.close()
                        ebuR128 = LibEbuR128(ch, sr, peakMode)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        if (ebuR128 == null) {
                            val outFmt = codec.outputFormat
                            ebuR128 = LibEbuR128(
                                outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                                outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                peakMode
                            )
                        }

                        codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                            if (bufferInfo.size > 0) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val isFloat = (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT)
                                val frameCount = bufferInfo.size / (ebuR128.channels * (if (isFloat) 4 else 2))
                                ebuR128.processDirect(outputBuffer.slice(), isFloat, frameCount)

                                if (durationUs > 0) {
                                    val currentProgress = (bufferInfo.presentationTimeUs.toDouble() / durationUs).coerceIn(0.0, 1.0)
                                    if (currentProgress - lastReportedProgress >= PROGRESS_UPDATE_THRESHOLD) {
                                        onProgress(currentProgress.toFloat())
                                        lastReportedProgress = currentProgress
                                    }
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEnded = true
                    }
                }
            }

            onProgress(1.0f)

            if (ebuR128 == null || ebuR128.sampleCount == 0L) {
                throw ReplayGainScanException(uriString, mimeType, ReplayGainError.ZeroSampleCount)
            }

            completed = true
            return ReplayGainDecodeResult(
                analysis = ReplayGainAnalysis(
                    loudnessLufs = ebuR128.loudness,
                    sampleCount = ebuR128.sampleCount,
                    peak = ebuR128.peak
                ),
                mimeType = mimeType,
                state = ebuR128
            )

        } catch (e: CancellationException) {
            throw e
        } catch (e: ReplayGainScanException) {
            throw e
        } catch (e: IllegalStateException) {
            val isAlacIssue = mimeType.equals("audio/alac", true) && e.message?.contains("Executing states", true) == true
            throw ReplayGainScanException(
                uriString = uriString,
                mimeType = mimeType,
                error = ReplayGainError.CodecException(e.message, isAlacIssue)
            )
        } catch (e: Exception) {
            throw ReplayGainScanException(
                uriString = uriString,
                mimeType = mimeType,
                error = ReplayGainError.GeneralException(e.message)
            )
        } finally {
            if (!completed) {
                runCatching { ebuR128?.close() }
            }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private data class ReplayGainDecodeResult(
        val analysis: ReplayGainAnalysis,
        val mimeType: String,
        val state: LibEbuR128
    )

    private class ReplayGainScanException(
        val uriString: String?,
        val mimeType: String?,
        val error: ReplayGainError
    ) : Exception(
        when (error) {
            is ReplayGainError.NoAudioTrack -> "No audio track"
            is ReplayGainError.UnknownMimeType -> "Unknown MIME type"
            is ReplayGainError.ZeroSampleCount -> "Zero sample count"
            is ReplayGainError.UnsupportedCodec -> "Unsupported codec: ${error.mimeType.orEmpty()}"
            is ReplayGainError.CodecException -> error.message
            is ReplayGainError.GeneralException -> error.message
        }
    )

    /**
     * 将解析出的 LUFS 响度格式化为 Track/Album Gain
     */
    fun formatGain(
        loudnessLufs: Double,
        targetLoudnessLufs: Double = DEFAULT_TARGET_LOUDNESS_LUFS
    ): String {
        // Gain = 目标参考响度 - 实际测量响度
        val gainDb = targetLoudnessLufs - loudnessLufs
        return "%.2f dB".format(java.util.Locale.US, gainDb)
    }

    /**
     * 将解析出的 LUFS 响度格式化为 Track Gain
     */
    fun formatGain(
        analysis: ReplayGainAnalysis,
        targetLoudnessLufs: Double = DEFAULT_TARGET_LOUDNESS_LUFS
    ): String {
        return formatGain(analysis.loudnessLufs, targetLoudnessLufs)
    }

    /**
     * 将目标参考响度格式化为写入标签的文本，例如 "-18 LUFS"
     */
    fun formatReferenceLoudness(targetLoudnessLufs: Double): String {
        val value = if (targetLoudnessLufs % 1.0 == 0.0) {
            "%.0f".format(java.util.Locale.US, targetLoudnessLufs)
        } else {
            "%.2f".format(java.util.Locale.US, targetLoudnessLufs)
        }
        return "$value LUFS"
    }

    /** 格式化归一化峰值。 */
    fun formatPeak(peak: Double): String {
        // 真实峰值可能超过 1.0，因此这里不做上限截断。
        return "%.6f".format(java.util.Locale.US, peak.coerceAtLeast(0.0))
    }

}
