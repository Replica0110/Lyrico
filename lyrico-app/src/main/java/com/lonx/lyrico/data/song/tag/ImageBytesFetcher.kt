package com.lonx.lyrico.data.song.tag

import com.lonx.lyrico.data.song.file.AudioFileAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface ImageBytesFetcher {
    suspend fun fetch(source: PictureSource): ByteArray?
}

class DefaultImageBytesFetcher(
    private val fileAccess: AudioFileAccess,
    private val okHttpClient: OkHttpClient
) : ImageBytesFetcher {
    override suspend fun fetch(source: PictureSource): ByteArray? = withContext(Dispatchers.IO) {
        when (source) {
            is PictureSource.Bytes -> source.bytes
            is PictureSource.UriSource -> fileAccess.openInputBytes(source.uri)
            is PictureSource.UrlSource -> fetchUrl(source.url)
        }
    }

    private fun fetchUrl(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body.bytes() else null
        }
    }
}
