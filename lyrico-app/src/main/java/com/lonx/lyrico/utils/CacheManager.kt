package com.lonx.lyrico.utils

import android.content.Context
import coil3.imageLoader
import com.lonx.lyrico.data.model.cache.CacheCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/** Operate on the application's live caches through their owning libraries. */
class CacheManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    suspend fun getCategorizedCacheSize(): Map<CacheCategory, Long> = withContext(Dispatchers.IO) {
        val imageCache = context.imageLoader.diskCache
        val networkCache = httpClient.cache
        val ownedDirectories = setOfNotNull(imageCache?.directory?.toFile(), networkCache?.directory)
        mapOf(
            CacheCategory.IMAGE to (imageCache?.size ?: 0L),
            CacheCategory.NETWORK to (networkCache?.size() ?: 0L),
            CacheCategory.OTHER to otherCacheFiles(ownedDirectories).sumOf { it.folderSize() },
            CacheCategory.EXTERNAL to (context.externalCacheDir?.folderSize() ?: 0L),
        )
    }

    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        val loader = context.imageLoader
        val imageCache = loader.diskCache
        val networkCache = httpClient.cache
        val ownedDirectories = setOfNotNull(imageCache?.directory?.toFile(), networkCache?.directory)
        imageCache?.clear()
        loader.memoryCache?.clear()
        networkCache?.evictAll()
        otherCacheFiles(ownedDirectories).forEach { it.deleteRecursively() }
        context.externalCacheDir?.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun otherCacheFiles(ownedDirectories: Set<File>): List<File> =
        context.cacheDir.listFiles().orEmpty().filter { child ->
            ownedDirectories.none { it.absoluteFile == child.absoluteFile }
        }
}

private fun File.folderSize(): Long = when {
    !exists() -> 0L
    isFile -> length()
    else -> walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
