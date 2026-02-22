package com.lonx.lyrico.utils

import android.annotation.SuppressLint
import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

object CacheManager {

    private const val TAG = "CacheManager"

    // 定义缓存分类常量
    const val CATEGORY_IMAGE = "图片缓存"
    const val CATEGORY_NETWORK = "网络缓存"
    const val CATEGORY_EXTERNAL = "外部缓存"
    const val CATEGORY_OTHER = "其他缓存"

    /**
     * 获取按目录分类的缓存大小
     * @return Map<分类名称, 字节大小>
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun getCategorizedCacheSize(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
        val cacheMap = mutableMapOf<String, Long>()

        // 获取 Coil 图片缓存路径和大小
        val coilCacheFile = context.imageLoader.diskCache?.directory?.toFile()
        val coilSize = coilCacheFile?.getFolderSize() ?: 0L
        cacheMap[CATEGORY_IMAGE] = coilSize

        // 统计内部缓存目录 (context.cacheDir)
        val internalCacheDir = context.cacheDir
        var networkSize = 0L
        var otherInternalSize = 0L

        internalCacheDir.listFiles()?.forEach { file ->
            when {
                // 如果是 Coil 的路径，已经计算过了
                coilCacheFile != null && file.absolutePath == coilCacheFile.absolutePath -> {
                    /* skip */
                }
                file.name.contains("http", ignoreCase = true) || file.name.contains("network", ignoreCase = true) -> {
                    networkSize += file.getFolderSize()
                }
                else -> {
                    otherInternalSize += file.getFolderSize()
                }
            }
        }
        cacheMap[CATEGORY_NETWORK] = networkSize
        cacheMap[CATEGORY_OTHER] = otherInternalSize

        // 统计外部缓存目录 (context.externalCacheDir)
        val externalCacheSize = context.externalCacheDir?.getFolderSize() ?: 0L
        cacheMap[CATEGORY_EXTERNAL] = externalCacheSize

        cacheMap
    }

    /**
     * 计算总大小（工具方法）
     */
    fun getTotalSize(map: Map<String, Long>): Long = map.values.sum()

    /**
     * 清理指定类别的缓存
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearCacheByCategory(context: Context, category: String) = withContext(Dispatchers.IO) {
        when (category) {
            CATEGORY_IMAGE -> {
                context.imageLoader.diskCache?.clear()
                context.imageLoader.memoryCache?.clear()
            }
            CATEGORY_NETWORK -> {
                context.cacheDir.listFiles()?.filter {
                    it.name.contains("http", ignoreCase = true) || it.name.contains("network", ignoreCase = true)
                }?.forEach { it.deleteRecursively() }
            }
            CATEGORY_EXTERNAL -> {
                context.externalCacheDir?.deleteRecursively()
            }
            CATEGORY_OTHER -> {
                // 删除除了图片和网络缓存之外的所有内部缓存文件
                val coilPath = context.imageLoader.diskCache?.directory?.toString()
                context.cacheDir.listFiles()?.forEach { file ->
                    val isImage = coilPath != null && file.absolutePath == coilPath
                    val isNetwork = file.name.contains("http", ignoreCase = true) || file.name.contains("network", ignoreCase = true)
                    if (!isImage && !isNetwork) {
                        file.deleteRecursively()
                    }
                }
            }
        }
    }

    /**
     * 清理所有缓存
     */
    @OptIn(ExperimentalCoilApi::class)
    suspend fun clearAllCache(context: Context) = withContext(Dispatchers.IO) {
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