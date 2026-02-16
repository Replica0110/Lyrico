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

                fun firstIntOf(vararg keys: String): Int? {
                    val raw = firstOf(*keys) ?: return null
                    return raw.substringBefore('/').toIntOrNull()
                }

                val lyrics = firstOf(
                    "LYRICS",
                    "UNSYNCED LYRICS",
                    "USLT",
                    "LYRIC",
                    "LYRICSENG"
                )


                val albumArtist = firstOf(
                    "ALBUMARTIST",     // FLAC/Vorbis
                    "ALBUM ARTIST",
                    "TPE2",            // ID3v2
                    "aART",            // MP4
                    "ALBUMARTISTSORT"
                )

                val discNumber = firstIntOf(
                    "DISCNUMBER",
                    "DISC",
                    "TPOS",           // ID3v2
                    "DISKNUMBER"
                )

                val composer = firstOf(
                    "COMPOSER",
                    "TCOM",           // ID3v2
                    "©wrt"            // MP4
                )

                val lyricist = firstOf(
                    "LYRICIST",
                    "TEXT",           // ID3v2 作词
                    "WRITER",
                    "LYRICS BY"
                )

                val comment = firstOf(
                    "COMMENT",
                    "COMM",           // ID3
                    "DESCRIPTION"
                )

                val style = firstOf(
                    "STYLE",
                    "SUBGENRE",
                    "MOOD"
                )

                return@withContext AudioTagData(
                    title = firstOf("TITLE"),
                    artist = firstOf("ARTIST"),
                    album = firstOf("ALBUM"),
                    genre = firstOf("GENRE") ?: style, // fallback 到 style
                    date = firstOf("DATE", "YEAR"),
                    trackerNumber = firstIntOf("TRACKNUMBER", "TRACK", "TRCK")?.toString(),

                    albumArtist = albumArtist,
                    discNumber = discNumber,
                    composer = composer,
                    lyricist = lyricist,
                    comment = comment,
                    lyrics = lyrics,

                    durationMilliseconds = audioProps?.length ?: 0,
                    bitrate = audioProps?.bitrate ?: 0,
                    sampleRate = audioProps?.sampleRate ?: 0,
                    channels = audioProps?.channels ?: 0,
                    rawProperties = props,
                    pictures = picList
                )

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                AudioTagData()
            }
        }
    }
}