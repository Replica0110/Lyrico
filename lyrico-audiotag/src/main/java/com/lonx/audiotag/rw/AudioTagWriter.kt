package com.lonx.audiotag.rw

import android.os.ParcelFileDescriptor
import android.util.Log
import com.lonx.audiotag.internal.FdUtils
import com.lonx.audiotag.internal.MetadataResult
import com.lonx.audiotag.internal.TagLibJNI
import com.lonx.audiotag.model.AudioPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.HashMap
import kotlin.collections.iterator
/**
 * ID3 frame → TagLib PropertyMap key
 */
val id3ToProperty = mapOf(

    // ===== 基本信息 =====
    "TIT2" to "TITLE",
    "TPE1" to "ARTIST",
    "TPE2" to "ALBUMARTIST",
    "TALB" to "ALBUM",
    "TRCK" to "TRACKNUMBER",
    "TPOS" to "DISCNUMBER",
    "TCON" to "GENRE",
    "TBPM" to "BPM",
    "TKEY" to "INITIALKEY",
    "TLAN" to "LANGUAGE",
    "TLEN" to "LENGTH",
    "TMOO" to "MOOD",

    // ===== 日期 =====
    "TDRC" to "DATE",
    "TDOR" to "ORIGINALDATE",
    "TDRL" to "RELEASEDATE",
    "TDTG" to "TAGGINGDATE",
    "TORY" to "ORIGINALYEAR",

    // ID3v2.3 legacy
    "TYER" to "DATE",
    "TDAT" to "DATE",
    "TIME" to "TIME",

    // ===== 人员信息 =====
    "TCOM" to "COMPOSER",
    "TEXT" to "LYRICIST",
    "TOLY" to "ORIGINALLYRICIST",
    "TPE3" to "CONDUCTOR",
    "TPE4" to "REMIXER",

    // ===== 制作信息 =====
    "TPUB" to "PUBLISHER",
    "TCOP" to "COPYRIGHT",
    "TENC" to "ENCODEDBY",
    "TDEN" to "ENCODINGTIME",
    "TSSE" to "ENCODERSETTINGS",
    "TPRO" to "PRODUCEDNOTICE",

    // ===== 原始信息 =====
    "TOAL" to "ORIGINALALBUM",
    "TOFN" to "ORIGINALFILENAME",
    "TOPE" to "ORIGINALARTIST",

    // ===== 排序字段 =====
    "TSOA" to "ALBUMSORT",
    "TSOP" to "ARTISTSORT",
    "TSOT" to "TITLESORT",
    "TSO2" to "ALBUMARTISTSORT",
    "TSOC" to "COMPOSERSORT",

    // ===== 其他文本 =====
    "TMED" to "MEDIA",
    "TIPL" to "INVOLVEDPEOPLE",
    "TMCL" to "MUSICIANCREDITS",
    "TFLT" to "FILETYPE",
    "TOWN" to "OWNER",

    // ===== 评论 / 歌词 =====
    "COMM" to "COMMENT",
    "USLT" to "LYRICS",
    "SYLT" to "SYNCEDLYRICS",

    // ===== 封面 =====
    "APIC" to "COVERART",

    // ===== 标识 =====
    "UFID" to "UNIQUEFILEIDENTIFIER",
    "MCDI" to "MUSICCDIDENTIFIER",
    "TSRC" to "ISRC",

    // ===== 流行度 =====
    "POPM" to "POPULARIMETER",

    // ===== ReplayGain =====
    "RVA2" to "REPLAYGAIN",

    // ===== URL =====
    "WCOM" to "COMMERCIALINFO",
    "WCOP" to "COPYRIGHTINFO",
    "WOAF" to "OFFICIALAUDIOFILE",
    "WOAR" to "OFFICIALARTIST",
    "WOAS" to "OFFICIALAUDIOSOURCE",
    "WORS" to "OFFICIALRADIOSTATION",
    "WPAY" to "PAYMENT",
    "WPUB" to "PUBLISHERSOFFICIALPAGE",

    // ===== iTunes 私有 =====
    "TCMP" to "COMPILATION",
    "GRP1" to "GROUPING",

    // ===== MusicBrainz =====
    "UFID:http://musicbrainz.org" to "MUSICBRAINZ_TRACKID",
    "TXXX:MusicBrainz Artist Id" to "MUSICBRAINZ_ARTISTID",
    "TXXX:MusicBrainz Album Id" to "MUSICBRAINZ_ALBUMID",
    "TXXX:MusicBrainz Album Artist Id" to "MUSICBRAINZ_ALBUMARTISTID",
    "TXXX:MusicBrainz Release Group Id" to "MUSICBRAINZ_RELEASEGROUPID",
    "TXXX:MusicBrainz Work Id" to "MUSICBRAINZ_WORKID",

    // ===== AcoustID =====
    "TXXX:Acoustid Id" to "ACOUSTID_ID",
    "TXXX:Acoustid Fingerprint" to "ACOUSTID_FINGERPRINT",

    // ===== ReplayGain (TXXX) =====
    "TXXX:REPLAYGAIN_TRACK_GAIN" to "REPLAYGAIN_TRACK_GAIN",
    "TXXX:REPLAYGAIN_TRACK_PEAK" to "REPLAYGAIN_TRACK_PEAK",
    "TXXX:REPLAYGAIN_ALBUM_GAIN" to "REPLAYGAIN_ALBUM_GAIN",
    "TXXX:REPLAYGAIN_ALBUM_PEAK" to "REPLAYGAIN_ALBUM_PEAK",

    // ===== 其他 TXXX =====
    "TXXX:ENGINEER" to "ENGINEER",
    "TXXX:PRODUCER" to "PRODUCER",
    "TXXX:MIXER" to "MIXER",
    "TXXX:DJMIXER" to "DJMIXER",
    "TXXX:ARRANGER" to "ARRANGER",

    "TXXX:ARTISTWEBPAGE" to "ARTISTWEBPAGE",
    "TXXX:COPYRIGHTWEBPAGE" to "COPYRIGHTWEBPAGE",

    "TXXX:RELEASECOUNTRY" to "RELEASECOUNTRY",
    "TXXX:CATALOGNUMBER" to "CATALOGNUMBER",
    "TXXX:BARCODE" to "BARCODE",
    "TXXX:ASIN" to "ASIN",
    "TXXX:SCRIPT" to "SCRIPT",
    "TXXX:DISCID" to "DISCID"
)
object AudioTagWriter {

    private const val TAG = "AudioTagWriter"

    /**
     * ID3 frame → TagLib PropertyMap key
     */


    /**
     * 移除重复 TXXX 前缀
     */
    private fun cleanKey(key: String): String {
        var k = key
        while (k.startsWith("TXXX:")) {
            k = k.removePrefix("TXXX:")
        }
        return k
    }

    /**
     * 转换 key 为 PropertyMap key
     */
    private fun normalizeKey(key: String): String {
        val cleaned = cleanKey(key)
        return id3ToProperty[cleaned] ?: cleaned
    }

    /**
     * 规范化整个 map
     */
    private fun normalizeTags(
        map: Map<String, List<String>>
    ): Map<String, List<String>> {

        val result = LinkedHashMap<String, List<String>>()

        for ((k, v) in map) {

            val key = normalizeKey(k)

            result[key] = v
        }

        return result
    }

    /**
     * 通用标签写入方法
     * 写入歌词请在 updates 中包含 "LYRICS"
     */
    suspend fun writeTags(
        pfd: ParcelFileDescriptor,
        updates: Map<String, String>,
        preserveOldTags: Boolean = true
    ): Boolean {

        return withContext(Dispatchers.IO) {

            try {

                val fd = FdUtils.getNativeFd(pfd)

                val mapToSave = LinkedHashMap<String, List<String>>()

                if (preserveOldTags) {

                    val oldFd = FdUtils.getNativeFd(pfd)

                    val oldMeta = TagLibJNI.read(oldFd)

                    if (oldMeta is MetadataResult.Success) {

                        oldMeta.metadata?.let { meta ->

                            mapToSave.putAll(meta.id3v2)
                            mapToSave.putAll(meta.xiph)
                            mapToSave.putAll(meta.mp4)
                        }
                    }
                }

                for ((k, v) in updates) {

                    mapToSave[k] = listOf(v)
                }

                val normalized = normalizeTags(mapToSave)


                return@withContext TagLibJNI.write(
                    fd,
                    HashMap(normalized)
                )

            } catch (e: Exception) {

                Log.e(TAG, "Write tags error", e)

                return@withContext false
            }
        }
    }

    /**
     * 写封面
     */
    suspend fun writePictures(
        pfd: ParcelFileDescriptor,
        pictures: List<AudioPicture>
    ): Boolean {

        return withContext(Dispatchers.IO) {

            try {

                val fd = FdUtils.getNativeFd(pfd)

                val picturesData = pictures.map { it.data }.toTypedArray()

                return@withContext TagLibJNI.writePictures(fd, picturesData)

            } catch (e: Exception) {

                Log.e(TAG, "Write pictures error", e)

                return@withContext false
            }
        }
    }
}