package com.lonx.lyrico.data.network

import android.util.Log
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.repository.AppLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class PluginRequestLogContext(val pluginId: String, val operationId: String?)

class NetworkLoggingInterceptor(
    private val appLogRepository: AppLogRepository,
    private val appScope: CoroutineScope
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pluginContext = request.tag(PluginRequestLogContext::class.java)
        val pluginId = pluginContext?.pluginId
        val startNs = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            val recordingStart = appLogRepository.recordingState.value.takeIf { it.active }?.startedAt ?: 0L
            val responseAt = System.currentTimeMillis()
            if (!response.isSuccessful || appLogRepository.recordingState.value.active) {
                val durationMs = elapsedMillis(startNs)
                appScope.launch(Dispatchers.IO) {
                    runCatching {
                        appLogRepository.log(
                            level = when {
                                response.code >= 500 -> AppLogLevel.ERROR
                                !response.isSuccessful -> AppLogLevel.WARNING
                                else -> AppLogLevel.DEBUG
                            },
                            type = AppLogType.NETWORK,
                            tag = TAG,
                            message = "HTTP ${response.code} ${request.method} ${request.url.host}",
                            detail = buildString {
                                appendLine("url=${request.url}")
                                pluginId?.let { appendLine("plugin=$it") }
                                pluginContext?.operationId?.let { appendLine("operationId=$it") }
                                appendLine("method=${request.method}")
                                appendLine("code=${response.code}")
                                appendLine("protocol=${response.protocol}")
                                appendLine("reasonPhrase=${response.message.ifBlank { "(empty)" }}")
                                appendLine("durationMs=$durationMs")
                                appendLine("contentLength=${response.body.contentLength()}")
                                appendLine("contentType=${response.header("Content-Type")}")
                            },
                            relatedId = pluginId,
                            recordingStartedAt = recordingStart,
                            occurredAt = responseAt
                        )
                    }.onFailure {
                        Log.w(TAG, "Failed to write network response log", it)
                    }
                }
            }
            response
        } catch (e: IOException) {
            val durationMs = elapsedMillis(startNs)
            val recordingStart = appLogRepository.recordingState.value.takeIf { it.active }?.startedAt ?: 0L
            val failedAt = System.currentTimeMillis()
            appScope.launch(Dispatchers.IO) {
                runCatching {
                    appLogRepository.log(
                        level = AppLogLevel.ERROR,
                        type = AppLogType.NETWORK,
                        tag = TAG,
                        message = "Network request failed: ${request.method} ${request.url.host} (${durationMs}ms)",
                        detail = "url=${request.url}\nplugin=$pluginId\noperationId=${pluginContext?.operationId}\ndurationMs=$durationMs\n${e.stackTraceToString()}",
                        relatedId = pluginId,
                        recordingStartedAt = recordingStart,
                        occurredAt = failedAt
                    )
                }.onFailure {
                    Log.w(TAG, "Failed to write network exception log", it)
                }
            }
            throw e
        }
    }

    private fun elapsedMillis(startNs: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)

    private companion object {
        const val TAG = "NetworkLoggingInterceptor"
    }
}
