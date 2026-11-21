package com.lonx.lyrico.ui.screens.edit

import com.lonx.lyrico.R
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.platform.audio.ReplayGainCalculateState
import com.lonx.lyrico.platform.audio.ReplayGainError
import com.lonx.lyrico.platform.audio.ReplayGainScanner
import com.lonx.lyrico.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/** 一次 ReplayGain 扫描的结果，交给 ViewModel 写进编辑态。 */
sealed interface ReplayGainScanEvent {
    data class Progress(val percent: Float) : ReplayGainScanEvent

    data class Succeeded(
        val trackGain: String,
        val trackPeak: String,
        val referenceLoudness: String,
    ) : ReplayGainScanEvent

    data object Cancelled : ReplayGainScanEvent

    /** 扫描给出明确原因；[detail] 是待落盘的诊断文本。 */
    data class Failed(val message: UiMessage, val detail: String) : ReplayGainScanEvent

    /** 扫描自身抛异常。 */
    data class Crashed(val error: Exception) : ReplayGainScanEvent

    /** 无论成功或失败都会收到，用于收尾进度与忙碌标记。 */
    data object Finished : ReplayGainScanEvent
}

/**
 * 单曲 ReplayGain 扫描的编排：取设置、跑扫描、翻译失败原因。
 *
 * 只关心扫描本身，不持有编辑态；进度与结果通过 [ReplayGainScanEvent] 回传，
 * 是否写进歌词/标签由调用方决定。
 */
class ReplayGainScanController(
    private val scanner: ReplayGainScanner,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private var scanJob: Job? = null
    private var running = false

    /** 每次启动递增；收尾时据此判断自己是否仍是当前扫描。 */
    private var scanToken = 0

    fun start(uri: String, onEvent: (ReplayGainScanEvent) -> Unit) {
        if (running) return
        running = true
        val token = ++scanToken

        // 先进入 try，保证首次调度前取消也能执行收尾。
        scanJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val settings = settingsRepository.getReplayGainSettings()
                onEvent(ReplayGainScanEvent.Progress(0f))

                scanner.analyze(uri, settings.peakMode)
                    .flowOn(Dispatchers.IO) // 解码与 DSP 计算不占用主线程
                    .collect { state ->
                        when (state) {
                            is ReplayGainCalculateState.Progress ->
                                onEvent(ReplayGainScanEvent.Progress(state.percent))

                            is ReplayGainCalculateState.Success -> {
                                running = false
                                onEvent(
                                    ReplayGainScanEvent.Succeeded(
                                        trackGain = scanner.formatGain(state.analysis, settings.targetLoudness),
                                        trackPeak = scanner.formatPeak(state.analysis.peak),
                                        referenceLoudness = scanner.formatReferenceLoudness(settings.targetLoudness),
                                    )
                                )
                            }

                            is ReplayGainCalculateState.Cancelled -> {
                                running = false
                                onEvent(ReplayGainScanEvent.Cancelled)
                            }

                            is ReplayGainCalculateState.Failed -> {
                                running = false
                                onEvent(
                                    ReplayGainScanEvent.Failed(
                                        message = mapErrorToUiMessage(state.mimeType, state.error),
                                        detail = buildFailureDetail(uri, state.mimeType, state.error),
                                    )
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // 外部 cancel 时 flow 可能来不及发出 Cancelled，这里补齐终态。
                if (token == scanToken && running) {
                    running = false
                    onEvent(ReplayGainScanEvent.Cancelled)
                }
            } catch (e: Exception) {
                if (token == scanToken) {
                    running = false
                    onEvent(ReplayGainScanEvent.Crashed(e))
                }
            } finally {
                // 取消后马上重扫时，旧协程的收尾不能覆盖新扫描的状态。
                if (token == scanToken) {
                    running = false
                    scanJob = null
                    onEvent(ReplayGainScanEvent.Finished)
                }
            }
        }
    }

    fun cancel() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun buildFailureDetail(
        uri: String,
        mimeType: String?,
        error: ReplayGainError,
    ): String = buildString {
        appendLine("Uri: $uri")
        appendLine("MimeType: ${mimeType ?: "unknown"}")
        appendLine("Error: ${error::class.java.name}")
        appendLine("Message: ${error.toLogMessage()}")
    }

    /** 失败原因 → 用户可见文案。 */
    private fun mapErrorToUiMessage(mimeType: String?, error: ReplayGainError): UiMessage {
        val format = when (mimeType?.lowercase()) {
            "audio/alac" -> "ALAC"
            "audio/ape", "audio/x-ape" -> "APE"
            "audio/flac" -> "FLAC"
            "audio/mpeg" -> "MP3"
            "audio/mp4a-latm" -> "AAC"
            null -> "unknown"
            else -> mimeType
        }

        return when (error) {
            is ReplayGainError.NoAudioTrack -> UiMessage.StringResource(R.string.replay_gain_error_no_audio_track)
            is ReplayGainError.UnknownMimeType -> UiMessage.StringResource(R.string.replay_gain_error_unknown_mime_type)
            is ReplayGainError.ZeroSampleCount -> UiMessage.StringResource(R.string.replay_gain_error_zero_sample_count)
            is ReplayGainError.UnsupportedCodec -> UiMessage.StringResource(R.string.replay_gain_error_unsupported_codec, format)
            is ReplayGainError.CodecException -> {
                if (error.isAlacIssue) UiMessage.StringResource(R.string.replay_gain_error_alac_issue)
                else UiMessage.StringResource(R.string.replay_gain_error_codec_exception, error.message ?: "")
            }
            is ReplayGainError.GeneralException -> UiMessage.StringResource(R.string.replay_gain_error_general, error.message ?: "")
        }
    }

    private fun ReplayGainError.toLogMessage(): String = when (this) {
        is ReplayGainError.NoAudioTrack -> "No audio track"
        is ReplayGainError.UnknownMimeType -> "Unknown MIME type"
        is ReplayGainError.ZeroSampleCount -> "Zero sample count"
        is ReplayGainError.UnsupportedCodec -> "Unsupported codec: ${mimeType ?: "unknown"}"
        is ReplayGainError.CodecException -> message.orEmpty()
        is ReplayGainError.GeneralException -> message.orEmpty()
    }
}
