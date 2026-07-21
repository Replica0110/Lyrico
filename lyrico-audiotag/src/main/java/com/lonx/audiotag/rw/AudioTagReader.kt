package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.lonx.audiotag.TagLib
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.AudioTagKeys
import com.lonx.audiotag.model.CustomTagField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioTagReader {

    private const val TAG = "AudioTagReader"

    suspend fun read(
        pfd: ParcelFileDescriptor,
        readPictures: Boolean = true,
        multiValueSeparator: String = "/"
    ): AudioTagData {
        return withContext(Dispatchers.IO) {
            try {
                val nativeFd = FdUtils.getNativeFd(pfd)

                // 读取音频属性
                val audioProps = TagLib.getAudioProperties(nativeFd)

                // 读取 Metadata
                val metaFd = FdUtils.getNativeFd(pfd)
                val metadata = TagLib.getMetadata(metaFd, readPictures) ?: return@withContext AudioTagData()

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

                props.forEach{(key, value) ->
                    Log.d(TAG, "Read tag: $key = ${value.joinToString()}")
                }

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

                fun joinedOf(separator: String, vararg keys: String): String? {
                    for (key in keys) {
                        val arr = props[key]
                        if (!arr.isNullOrEmpty()) {
                            val filtered = arr.map { it.trim() }.filter { it.isNotEmpty() }
                            if (filtered.isNotEmpty()) {
                                return filtered.joinToString(separator)
                            }
                        }
                    }
                    return null
                }

                fun firstIntOf(vararg keys: String): Int? {
                    val raw = firstOf(*keys) ?: return null
                    return raw.substringBefore('/').toIntOrNull()
                }

                val lyrics = joinedOf(
                    multiValueSeparator,
                    "LYRICS",
                    "UNSYNCED LYRICS",
                    "USLT",
                    "LYRIC",
                    "LYRICSENG"

                )

                val ttmlLyrics = firstOf("TTML LYRICS")

                val albumArtist = joinedOf(
                    multiValueSeparator,
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

                val composer = joinedOf(
                    multiValueSeparator,
                    "COMPOSER",
                    "TCOM",           // ID3v2
                    "©wrt"            // MP4
                )

                val lyricist = joinedOf(
                    multiValueSeparator,
                    "LYRICIST",
                    "TEXT",           // ID3v2 作词
                    "WRITER",
                    "LYRICS BY"
                )

                val comment = joinedOf(
                    multiValueSeparator,
                    "COMMENT",
                    "COMM",           // ID3
                    "DESCRIPTION"
                )

                val copyright = joinedOf(
                    multiValueSeparator,
                    "COPYRIGHT",
                    "TCOP",
                    "CPRO",
                    "©cpy"
                )

                var ratingStar: Int? = null
                val rawRating = firstOf("RATING", "POPM", "RATE")
                if (rawRating != null) {
                    if (rawRating.contains("|")) {
                        val popm = rawRating.split("|").getOrNull(1)?.toIntOrNull() ?: 0
                        ratingStar = when {
                            popm >= 255 -> 5
                            popm >= 196 -> 4
                            popm >= 128 -> 3
                            popm >= 64 -> 2
                            popm > 0 -> 1
                            else -> 0
                        }
                    } else {
                        val r = rawRating.toIntOrNull() ?: 0
                        ratingStar = if (r > 5) {
                            when {
                                r >= 100 -> 5
                                r >= 80 -> 4
                                r >= 60 -> 3
                                r >= 40 -> 2
                                r > 0 -> 1
                                else -> 0
                            }
                        } else {
                            r
                        }
                    }
                    if (ratingStar == 0) ratingStar = null
                }

                val customFields = props.entries
                    .asSequence()
                    .filterNot { (key, _) -> AudioTagKeys.isReserved(key) }
                    .map { (key, values) ->
                        CustomTagField(
                            key = key,
                            value = values.joinToString("; ")
                        )
                    }
                    .sortedBy { it.key.uppercase() }
                    .toList()

                return@withContext AudioTagData(
                    title = joinedOf(multiValueSeparator, "TITLE"),
                    artist = joinedOf(multiValueSeparator, "ARTIST"),
                    album = joinedOf(multiValueSeparator, "ALBUM"),
                    genre = joinedOf(multiValueSeparator, "GENRE", "TCON")
                        ?: joinedOf(multiValueSeparator, "STYLE", "SUBGENRE", "MOOD"),
                    date = joinedOf(multiValueSeparator, "DATE", "YEAR"),
                    language = joinedOf(multiValueSeparator, "LANGUAGE", "TLAN"),
                    trackNumber = firstIntOf("TRACKNUMBER", "TRACK", "TRCK")?.toString(),

                    albumArtist = albumArtist,
                    discNumber = discNumber,
                    composer = composer,
                    lyricist = lyricist,
                    comment = comment,
                    lyrics = lyrics,
                    ttmlLyrics = ttmlLyrics,
                    copyright = copyright,
                    rating = ratingStar,
                    replayGainTrackGain = joinedOf(multiValueSeparator, "REPLAYGAIN_TRACK_GAIN"),
                    replayGainTrackPeak = joinedOf(multiValueSeparator, "REPLAYGAIN_TRACK_PEAK"),
                    replayGainAlbumGain = joinedOf(multiValueSeparator, "REPLAYGAIN_ALBUM_GAIN"),
                    replayGainAlbumPeak = joinedOf(multiValueSeparator, "REPLAYGAIN_ALBUM_PEAK"),
                    replayGainReferenceLoudness = joinedOf(
                        multiValueSeparator,
                        "REPLAYGAIN_REFERENCE_LOUDNESS"
                    ),

                    durationMilliseconds = audioProps?.length ?: 0,
                    bitrate = audioProps?.bitrate ?: 0,
                    sampleRate = audioProps?.sampleRate ?: 0,
                    channels = audioProps?.channels ?: 0,
                    rawProperties = props,
                    customFields = customFields,
                    pictures = picList,
                    supportsTypedPictures = metadata.supportsTypedPictures
                )

            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                AudioTagData()
            }
        }
    }
    suspend fun readPicture(
        pfd: ParcelFileDescriptor,
        pictureType: AudioPictureType = AudioPictureType.FrontCover,
        fallbackPictureTypes: List<AudioPictureType> = emptyList(),
        fallbackToAny: Boolean = pictureType == AudioPictureType.FrontCover
    ): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val metaFd = FdUtils.getNativeFd(pfd)
                val metadata = TagLib.getPicture(
                    fd = metaFd,
                    pictureType = pictureType,
                    fallbackPictureTypes = fallbackPictureTypes,
                    fallbackToAny = fallbackToAny
                )
                val pic = metadata?.data
                return@withContext pic ?: byteArrayOf()
            } catch (e: Exception) {
                Log.e(TAG, "Read error", e)
                byteArrayOf()
            }
        }
    }
}
