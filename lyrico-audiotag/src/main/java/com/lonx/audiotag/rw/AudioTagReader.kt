package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.TagLib
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTagReader {
    private const val TAG = "AudioTagReader"

    suspend fun read(pfd: ParcelFileDescriptor, readPictures: Boolean = true): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {
                val nativeFd = FdUtils.getNativeFd(pfd)

                // 读取音频属性
                val audioProps = TagLib.getAudioProperties(nativeFd)

                // 读取 Metadata
                val metaFd = FdUtils.getNativeFd(pfd)
                val metadata =
                    TagLib.getMetadata(metaFd, readPictures) ?: return@withContext AudioTagData()

                // 处理图片
                val picList = ArrayList<AudioPicture>()
                if (readPictures) {
                    for (pic in metadata.pictures) {
                        picList.add(AudioPicture(
                            data = pic.data,
                            mimeType = pic.mimeType,
                            description = pic.description,
                            pictureType = pic.pictureType
                        ))
                    }
                }

                // 处理属性 Map
                val props = metadata.propertyMap

                // 辅助函数：安全获取 Map 中的第一个值
                fun getValue(key: String): String? {
                    if (props.containsKey(key)) {
                        val arr = props[key]
                        if (arr != null && arr.isNotEmpty()) {
                            return arr[0]
                        }
                    }
                    return null
                }

                // 尝试读取 "LYRICS", 如果没有则尝试 "UNSYNCED LYRICS" (常见于某些 ID3v2 解析)
                var lyricsStr = getValue("LYRICS")
                if (lyricsStr == null || lyricsStr.isEmpty()) {
                    lyricsStr = getValue("UNSYNCED LYRICS")
                }
                if (lyricsStr == null || lyricsStr.isEmpty()) {
                    // 极少数情况下的 Key
                    lyricsStr = getValue("USLT")
                }
                return@withContext AudioTagData(
                    title = getValue("TITLE"),
                    artist = getValue("ARTIST"),
                    album = getValue("ALBUM"),
                    genre = getValue("GENRE"),
                    date = getValue("DATE"),
                    lyrics = lyricsStr, // 赋值歌词

                    durationMilliseconds = audioProps?.length ?: 0,
                    bitrate = audioProps?.bitrate ?: 0,
                    sampleRate = audioProps?.sampleRate ?: 0,
                    channels = audioProps?.channels ?: 0,
                    rawProperties = props,
                    pictures = picList
                )

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                return@withContext AudioTagData()
            }
        }
    }
}