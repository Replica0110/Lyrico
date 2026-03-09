package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.internal.Metadata
import com.lonx.audiotag.internal.MetadataResult
import com.lonx.audiotag.internal.TagLibJNI
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioTagData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTagReader {

    private const val TAG = "AudioTagReader"

    suspend fun read(pfd: ParcelFileDescriptor, readPictures: Boolean = true): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {


                val fd = FdUtils.getNativeFd(pfd)

                val result = TagLibJNI.read(fd)


                val metadata = when (result) {
                    is MetadataResult.Success -> result.metadata
                    else -> null
                } ?: return@withContext AudioTagData()

                buildAudioTagData(metadata, readPictures)

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                AudioTagData()
            }
        }
    }
    suspend fun readPicture(pfd: ParcelFileDescriptor): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val fd = FdUtils.getNativeFd(pfd)
                val result = TagLibJNI.readPicture(fd)
                    return@withContext result

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
            }
            return@withContext null
        }
    }

    private fun buildAudioTagData(metadata: Metadata, readPictures: Boolean): AudioTagData {
        // 合并所有 tag map
        val props = LinkedHashMap<String, List<String>>().apply {
            putAll(metadata.id3v2)
            putAll(metadata.xiph)
            putAll(metadata.mp4)
        }

        Log.d(TAG, "Reading metadata: $props")

        // Helper 函数，取第一个非空字符串
        fun firstOf(vararg keys: String): String? {
            for (key in keys) {
                val arr = props[key]
                if (!arr.isNullOrEmpty()) {
                    val value = arr[0].trim()
                    if (value.isNotEmpty()) return value
                }
            }
            return null
        }

        // Helper 函数，取第一个整数（用于 track/disc）
        fun firstIntOf(vararg keys: String): Int? = firstOf(*keys)?.substringBefore('/')?.toIntOrNull()

        // 常用可选字段
        val lyrics = firstOf("LYRICS", "UNSYNCED LYRICS", "USLT", "LYRIC", "LYRICSENG","TXXX:USLT")
        val albumArtist = firstOf("ALBUMARTIST", "ALBUM ARTIST", "TPE2", "aART", "ALBUMARTISTSORT")
        val discNumber = firstIntOf("DISCNUMBER", "DISC", "TPOS", "DISKNUMBER")
        val composer = firstOf("COMPOSER", "TCOM", "©wrt")
        val lyricist = firstOf("LYRICIST", "TEXT", "WRITER", "LYRICS BY")
        val comment = firstOf("COMMENT", "COMM", "DESCRIPTION","TXXX:COMM")
        val style = firstOf("STYLE", "SUBGENRE", "MOOD")

        // 处理封面
        val pictures = mutableListOf<AudioPicture>()
        if (readPictures && metadata.cover != null) {
            pictures.add(
                AudioPicture(
                    data = metadata.cover,
                    mimeType = "image/*",
                    description = "",
                    pictureType = "3" // Cover front
                )
            )
        }

        val audioProps = metadata.properties

        Log.d(TAG, "Reading properties: $audioProps")
        // 返回 AudioTagData
        return AudioTagData(
            title = firstOf("TITLE", "TIT2", "©nam"),
            artist = firstOf("ARTIST", "TPE1", "©ART"),
            album = firstOf("ALBUM", "TALB", "©alb"),
            genre = firstOf("GENRE") ?: style,
            date = firstOf("DATE", "YEAR", "TDRC", "©day"),
            trackerNumber = firstIntOf("TRACKNUMBER", "TRACK", "TRCK", "©trk")?.toString(),
            albumArtist = albumArtist,
            discNumber = discNumber,
            composer = composer,
            lyricist = lyricist,
            comment = comment,
            lyrics = lyrics,
            durationMilliseconds = audioProps.durationMs.toInt(),
            bitrate = audioProps.bitrateKbps,
            sampleRate = audioProps.sampleRateHz,
            channels = audioProps.channels,
            pictures = pictures
        )
    }
}