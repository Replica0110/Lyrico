package com.lonx.lyrico.data.repository

import android.util.Log
import com.lonx.lyrico.BuildConfig
import com.lonx.lyrico.data.dto.GitHubReleaseDTO
import com.lonx.lyrico.data.dto.ReleaseInfo
import com.lonx.lyrico.data.model.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException

class UpdateRepositoryImpl(
    private val json: Json,
    private val okHttpClient: OkHttpClient
) : UpdateRepository {

    private val TAG = "UpdateRepositoryImpl"

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun checkForUpdate(
        owner: String,
        repo: String
    ): UpdateCheckResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "开始检查更新")

        val requestUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

        try {
            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lyrico-App")
                .build()

            okHttpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val release = json.decodeFromStream<GitHubReleaseDTO>(
                    response.body.byteStream()
                )

                val latestVersionName = release.tag_name
                val releaseNotes = release.body.orEmpty()
                val releaseUrl = release.html_url

                Log.d(
                    TAG,
                    "最新版本: $latestVersionName 当前版本: ${BuildConfig.VERSION_NAME}"
                )

                val hasUpdate = isNewerVersion(
                    latestVersionName,
                    BuildConfig.VERSION_NAME
                )

                if (hasUpdate) {
                    UpdateCheckResult.NewVersion(
                        ReleaseInfo(
                            versionName = latestVersionName,
                            releaseNotes = releaseNotes,
                            url = releaseUrl
                        )
                    )
                } else {
                    UpdateCheckResult.NoUpdateAvailable
                }
            }

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "连接超时", e)
            UpdateCheckResult.TimeoutError

        } catch (e: IOException) {
            Log.e(TAG, "更新检查网络错误", e)
            UpdateCheckResult.NetworkError(e)

        } catch (e: SerializationException) {
            Log.e(TAG, "JSON 解析错误", e)
            UpdateCheckResult.NetworkError(IOException("数据解析失败"))
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