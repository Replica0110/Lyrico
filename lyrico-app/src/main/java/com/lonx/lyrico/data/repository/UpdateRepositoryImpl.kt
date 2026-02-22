package com.lonx.lyrico.data.repository

import android.util.Log
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.data.dto.UpdateDTO
import com.lonx.lyrico.data.model.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException

class UpdateRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : UpdateRepository {

    private val TAG = "UpdateRepositoryImpl"

    override suspend fun checkForUpdate(
        owner: String,
        repo: String
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始检查更新")

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }

            val body = response.body.string()

            val json = JSONObject(body)

            val latestVersionName = json.getString("tag_name")
            val releaseNotes = json.optString("body", "")
            val url = json.getString("html_url")

            Log.d(TAG, "最新版本: $latestVersionName 当前版本: ${BuildConfig.VERSION_NAME}")

            val hasUpdate = isNewerVersion(
                latestVersionName,
                BuildConfig.VERSION_NAME
            )

            return@withContext if (hasUpdate) {
                UpdateCheckResult.NewVersion(
                    UpdateDTO(
                        versionName = latestVersionName,
                        releaseNotes = releaseNotes,
                        url = url
                    )
                )
            } else {
                UpdateCheckResult.NoUpdateAvailable
            }
        } catch (e: Exception) {
            when (e) {
                is SocketTimeoutException -> {
                    Log.e(TAG, "连接超时", e)
                    return@withContext UpdateCheckResult.TimeoutError
                }

                is IOException -> {
                    Log.e(TAG, "更新检查时网络错误", e)
                    return@withContext UpdateCheckResult.NetworkError(e)
                }

                else -> {
                    Log.e(TAG, "更新检查时发生意外错误", e)
                    throw e
                }
            }
        }
    }

    /**
     * 版本比较
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        fun normalize(version: String): List<Int> {
            return version
                .trim()
                .removePrefix("v")
                .substringBefore("-")
                .substringBefore("+")
                .split(".")
                .map { it.toIntOrNull() ?: 0 }
        }

        val lParts = normalize(latest)
        val cParts = normalize(current)

        Log.d(TAG, "比较版本: $lParts vs $cParts")

        val max = maxOf(lParts.size, cParts.size)

        for (i in 0 until max) {
            val lv = lParts.getOrElse(i) { 0 }
            val cv = cParts.getOrElse(i) { 0 }

            when {
                lv > cv -> return true
                lv < cv -> return false
            }
        }
        return false
    }
}