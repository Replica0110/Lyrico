package com.lonx.lyrics.source.kg

import android.util.Base64
import android.util.Log
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.KgCryptoUtils
import com.lonx.lyrics.utils.KrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody


class KgSource(
    private val api: KgApi
): SearchSource {
    override val sourceType = Source.KG
    private val deviceMid by lazy {
        KgCryptoUtils.md5(System.currentTimeMillis().toString())
    }

    private var dfid: String? = null
    private val dfidMutex = Mutex()

    private val SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"

    /**
     * 获取 DFID (对应 Python 的 init)
     * 签名算法：MD5("1014" + sorted_VALUES_string + "1014")
     */
    private suspend fun getDfid(): String {
        dfidMutex.withLock {
            if (!dfid.isNullOrEmpty() && dfid != "-") return dfid!!

            val params = mutableMapOf(
                "appid" to "1014",
                "platid" to "4",
                "mid" to deviceMid
            )

            // DFID 签名是按 Value 排序
            val sortedValues = params.values
                .filter { it.isNotEmpty() }
                .sorted()
                .joinToString("")

            params["signature"] = KgCryptoUtils.md5("1014${sortedValues}1014")

            val bodyJson = "{\"uuid\":\"\"}"
            val bodyBase64 = Base64.encodeToString(bodyJson.toByteArray(), Base64.NO_WRAP)
            val requestBody = bodyBase64.toRequestBody("text/plain".toMediaTypeOrNull())

            dfid = try {
                val resp = api.registerDev(params, requestBody)
                // 如果 error_code 不是 0，data 可能是错误字符串，这里会解析失败抛异常
                // 如果签名正确，应该返回 0
                if (resp.errorCode == 0 && resp.data != null) {
                    Log.d("KgSource", "DFID obtained: $dfid")
                    resp.data.dfid
                } else {
                    Log.e("KgSource", "Get DFID error: code=${resp.errorCode}")
                    "-"
                }
            } catch (e: Exception) {
                Log.e("KgSource", "Failed to get DFID", e)
                "-"
            }
            return dfid!!
        }
    }



    override suspend fun search(keyword: String, page: Int, separator: String, pageSize: Int): List<SongSearchResult> = withContext(Dispatchers.IO) {
        val params = mapOf(
            "keyword" to keyword,
            "page" to page.toString(),
            "pagesize" to pageSize.toString()
        )

        try {
            val signedParams = buildSignedParams(params, body = "", module = "Search")
            val response = api.searchSong(signedParams)

            if (response.errorCode != 0) return@withContext emptyList()

            response.data?.lists?.map { item ->
                SongSearchResult(
                    id = item.id ?: "",
                    title = item.songName,
                    artist = item.singers.joinToString(separator) { it.name },
                    album = item.albumName ?: "",
                    duration = (item.duration * 1000).toLong(),
                    source = Source.KG,
                    date = item.publishDate ?: "",
                    extras = mapOf("hash" to item.fileHash),
                    picUrl = if (item.picUrl.isNotBlank()) item.picUrl.replace("{size}", "480") else "",
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }



    private suspend fun buildSignedParams(
        customParams: Map<String, String>,
        body: String = "",
        module: String = "Search"
    ): Map<String, String> = withContext(Dispatchers.Default) {
        val currentTime = System.currentTimeMillis()
        val baseParams = mutableMapOf<String, String>()

        if (module == "Lyric") {
            baseParams["appid"] = "3116"
            baseParams["clientver"] = "11070"
        } else {
            baseParams["userid"] = "0"
            baseParams["appid"] = "3116"
            baseParams["token"] = ""
            baseParams["clienttime"] = (currentTime / 1000).toString()
            baseParams["iscorrection"] = "1"
            baseParams["uuid"] = "-"
            baseParams["mid"] = deviceMid
            baseParams["dfid"] = if (module == "Search") "-" else getDfid()
            baseParams["clientver"] = "11070"
            baseParams["platform"] = "AndroidFilter"
        }

        baseParams.putAll(customParams)

        // 签名逻辑
        val sortedString = baseParams.toSortedMap()
            .entries.joinToString("") { "${it.key}=${it.value}" }

        val raw = "$SALT$sortedString$body$SALT"
        baseParams["signature"] = KgCryptoUtils.md5(raw)

        baseParams
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        val hash = song.extras["hash"] ?: return@withContext null

        try {
            val searchParams = mapOf(
                "album_audio_id" to song.id,
                "duration" to song.duration.toString(),
                "hash" to hash,
                "keyword" to "${song.artist} - ${song.title}",
                "lrctxt" to "1",
                "man" to "no"
            )

            val signedSearchParams = buildSignedParams(searchParams, module = "Lyric")
            val searchResp = api.searchLyrics(signedSearchParams)
            val candidate = searchResp.candidates?.firstOrNull() ?: return@withContext null

            val downloadParams = mapOf(
                "accesskey" to candidate.accesskey,
                "charset" to "utf8",
                "client" to "mobi",
                "fmt" to "krc",
                "id" to candidate.id,
                "ver" to "1"
            )
            val signedDownloadParams = buildSignedParams(downloadParams, module = "Lyric")

            val contentResp = api.downloadLyrics(signedDownloadParams)
            val rawBase64 = contentResp.content

            val lyricText = withContext(Dispatchers.Default) {
                if (contentResp.contenttype == 2) {
                    String(Base64.decode(rawBase64, Base64.DEFAULT), Charsets.UTF_8)
                } else {
                    KgCryptoUtils.decryptKrc(rawBase64)
                }
            }

            KrcParser.parse(lyricText)
        } catch (e: Exception) {
            null
        }
    }
}