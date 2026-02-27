package com.lonx.lyrico.data.repository

import android.util.Log
import com.lonx.lyrico.data.dto.ContributorInfo
import com.lonx.lyrico.data.dto.GitHubContributorDTO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException

class GhContributorRepositoryImpl(
    private val json: Json,
    private val okHttpClient: OkHttpClient
) : GhContributorRepository {

    private val TAG = "GhContributorRepo"

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun getContributors(
        owner: String,
        repo: String
    ): Result<List<ContributorInfo>> = withContext(Dispatchers.IO) {

        val url = "https://api.github.com/repos/$owner/$repo/contributors?per_page=600"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Lyrico-App")
                .build()

            okHttpClient.newCall(request).execute().use { response ->

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }

                val stream = response.body.byteStream()

                val dtoList = json.decodeFromStream<List<GitHubContributorDTO>>(stream)

                val result = dtoList
                    .asSequence()
                    .filter { it.type != "Bot" }
                    .map {
                        ContributorInfo(
                            id = it.id,
                            login = it.login,
                            avatar_url = it.avatar_url,
                            html_url = it.html_url,
                            contributions = it.contributions
                        )
                    }
                    .sortedByDescending { it.contributions }
                    .toList()

                Result.success(result)
            }

        } catch (e: Exception) {
            Log.e(TAG, "fetch contributors error", e)
            when (e) {
                is SocketTimeoutException -> Result.failure(SocketTimeoutException("连接超时"))
                is IOException -> Result.failure(IOException("网络错误"))
                else -> Result.failure(e)
            }
        }
    }

}