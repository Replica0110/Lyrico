package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.model.AudioPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.HashMap
import kotlin.collections.iterator

object AudioTagWriter {
    private const val TAG = "AudioTagWriter"

    /**
     * 通用标签写入方法
     * 写入歌词请在 updates 中包含 "LYRICS" -> "歌词内容"
     */
    suspend fun writeTags(
        pfd: ParcelFileDescriptor,
        updates: Map<String, String>,
        preserveOldTags: Boolean = true
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fd = FdUtils.getNativeFd(pfd)
                val mapToSave = HashMap<String, Array<String>>()

                if (preserveOldTags) {
                    val oldFd = FdUtils.getNativeFd(pfd)
                    val oldMeta = TagLib.getMetadata(oldFd, false)
                    if (oldMeta != null) {
                        mapToSave.putAll(oldMeta.propertyMap)
                    }
                }

                for ((k, v) in updates) {
                    // 简单的空值处理：如果传入空字符串，有的库会写入空标签，有的会删除
                    // 这里原样写入，由 TagLib 处理
                    mapToSave[k] = arrayOf(v)
                }

                return@withContext TagLib.savePropertyMap(fd, mapToSave)
            } catch (e: Exception) {
                Log.e(TAG, "Write tags error", e)
                return@withContext false
            }
        }
    }

    /**
     * 专门用于写入歌词的快捷方法
     */
    suspend fun writeLyrics(
        pfd: ParcelFileDescriptor,
        lyrics: String
    ): Boolean {
        // 使用 "LYRICS" 作为标准 Key，TagLib 会自动适配 ID3v2/FLAC/MP4
        val map = HashMap<String, String>()
        map["LYRICS"] = lyrics
        return writeTags(pfd, map, preserveOldTags = true)
    }

    suspend fun writePictures(
        pfd: ParcelFileDescriptor,
        pictures: List<AudioPicture>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fd = FdUtils.getNativeFd(pfd)
                val libPics = ArrayList<Picture>()
                for (p in pictures) {
                    libPics.add(Picture(
                        data = p.data,
                        mimeType = p.mimeType,
                        description = p.description,
                        pictureType = p.pictureType
                    ))
                }
                val arr = libPics.toTypedArray()
                return@withContext TagLib.savePictures(fd, arr)
            } catch (e: Exception) {
                Log.e(TAG, "Write pictures error", e)
                return@withContext false
            }
        }
    }
}