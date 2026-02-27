package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.CacheCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

object CacheManager {

    private const val TAG = "CacheManager"

    /**
     * 获取按目录分类的缓存大小
     * @return Map<分类, 字节大小>
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun getCategorizedCacheSize(
        context: Context
    ): Map<CacheCategory, Long> = withContext(Dispatchers.IO) {

        val cacheMap = mutableMapOf<CacheCategory, Long>()

        // Coil 图片缓存
        val coilCacheFile = context.imageLoader.diskCache?.directory?.toFile()
        val coilSize = coilCacheFile?.getFolderSize() ?: 0L
        cacheMap[CacheCategory.IMAGE] = coilSize

        val internalCacheDir = context.cacheDir
        var networkSize = 0L
        var otherInternalSize = 0L

        internalCacheDir.listFiles()?.forEach { file ->
            when {
                coilCacheFile != null &&
                        file.absolutePath == coilCacheFile.absolutePath -> {
                    // skip coil
                }

                file.name.contains("http", true) ||
                        file.name.contains("network", true) -> {
                    networkSize += file.getFolderSize()
                }

                else -> {
                    otherInternalSize += file.getFolderSize()
                }
            }
        }

        cacheMap[CacheCategory.NETWORK] = networkSize
        cacheMap[CacheCategory.OTHER] = otherInternalSize

        // 外部缓存
        val externalSize = context.externalCacheDir?.getFolderSize() ?: 0L
        cacheMap[CacheCategory.EXTERNAL] = externalSize

        cacheMap
    }

    /**
     * 总大小
     */
    fun getTotalSize(map: Map<CacheCategory, Long>): Long =
        map.values.sum()

    /**
     * 清理指定类别
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearCacheByCategory(
        context: Context,
        category: CacheCategory
    ) = withContext(Dispatchers.IO) {

        when (category) {

            CacheCategory.IMAGE -> {
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
            }

            CacheCategory.NETWORK -> {
                context.cacheDir.listFiles()
                    ?.filter {
                        it.name.contains("http", true) ||
                                it.name.contains("network", true)
                    }
                    ?.forEach { it.deleteRecursively() }
            }

            CacheCategory.EXTERNAL -> {
                context.externalCacheDir?.deleteRecursively()
            }

            CacheCategory.OTHER -> {
                val coilPath =
                    context.imageLoader.diskCache?.directory?.toString()

                context.cacheDir.listFiles()?.forEach { file ->
                    val isImage =
                        coilPath != null && file.absolutePath == coilPath

                    val isNetwork =
                        file.name.contains("http", true) ||
                                file.name.contains("network", true)

                    if (!isImage && !isNetwork) {
                        file.deleteRecursively()
                    }
                }
            }
        }
    }

    /**
     * 清理全部
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllCache(context: Context) =
        withContext(Dispatchers.IO) {
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
            context.cacheDir.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        }
}

/**
 * 递归获取文件夹大小（扩展函数）
 */
fun File.getFolderSize(): Long {
    if (!this.exists()) return 0L
    if (this.isFile) return this.length()

    return this.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
}

/**
 * 格式化字节大小
 */
@SuppressLint("DefaultLocale")
fun Long.formatSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (log10(this.toDouble()) / log10(1024.0)).toInt()
    return String.format(
        "%.1f %s",
        this / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}