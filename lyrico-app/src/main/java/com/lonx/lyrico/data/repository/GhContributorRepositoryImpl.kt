package com.lonx.lyrico.data.repository

import android.util.Log
import com.lonx.lyrico.data.model.GitHubContributor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.SocketTimeoutException

class GhContributorRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : GhContributorRepository {

    private val TAG = "GhContributorRepo"

    override suspend fun getContributors(
        owner: String,
        repo: String
    ): Result<List<GitHubContributor>> = withContext(Dispatchers.IO) {

        val url = "https://api.github.com/repos/$owner/$repo/contributors?per_page=100"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lyrico-App")
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }

            val body = response.body.string()
            val jsonArray = JSONArray(body)

            val list = mutableListOf<GitHubContributor>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // 过滤 bot
                if (obj.optString("type") == "Bot") continue

                list.add(
                    GitHubContributor(
                        id = obj.getInt("id"),
                        login = obj.getString("login"),
                        avatar_url = obj.getString("avatar_url"),
                        html_url = obj.getString("html_url"),
                        contributions = obj.getInt("contributions")
                    )
                )
            }

            Result.success(
                list.sortedByDescending { it.contributions }
            )

        } catch (e: Exception) {
            Log.e(TAG, "fetch contributors error", e)
            when (e) {
                is SocketTimeoutException -> {
                    return@withContext Result.failure(SocketTimeoutException("连接超时"))
                }

                is IOException -> {
                    return@withContext Result.failure(IOException("网络错误"))
                }

                else -> {
                    return@withContext Result.failure(e)
                }
            }
        }
    }
}