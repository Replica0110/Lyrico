package com.lonx.lyrics.source.kg

import android.util.Base64
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit


class KgSource: SearchSource {
    override val sourceType = Source.KG
    // 配置更宽容的 JSON 解析器
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val module = if (original.url.encodedPath.contains("search")) "SearchSong" else "Lyric"

            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Android14-1070-11070-201-0-$module-wifi")
                .header("KG-Rec", "1")
                .header("KG-RC", "1")
                .header("KG-CLIENTTIMEMS", System.currentTimeMillis().toString())
                .header("mid", deviceMid)

            chain.proceed(requestBuilder.build())
        }
        .build()
    private val deviceMid by lazy {
        KgCryptoUtils.md5(System.currentTimeMillis().toString())
    }
    private val api: KgApi = Retrofit.Builder()
        .baseUrl("http://complexsearch.kugou.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(KgApi::class.java)

    private var dfid: String? = null
    private val dfidMutex = Mutex()

    private val APPID = "3116"
    private val CLIENT_VER = "11070"
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

            // 使用 RequestBody 发送纯文本，避免 Retrofit 加引号
            val requestBody = bodyBase64.toRequestBody("text/plain". toMediaTypeOrNull())

            try {
                val resp = api.registerDev(params, requestBody)
                // 如果 error_code 不是 0，data 可能是错误字符串，这里会解析失败抛异常
                // 如果签名正确，应该返回 0
                if (resp.errorCode == 0 && resp.data != null) {
                    dfid = resp.data.dfid
                    Log.d("KgSource", "DFID obtained: $dfid")
                } else {
                    Log.e("KgSource", "Get DFID error: code=${resp.errorCode}")
                    dfid = "-"
                }
            } catch (e: Exception) {
                Log.e("KgSource", "Failed to get DFID", e)
                dfid = "-"
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
            // 注意：Search 不需要 Body，所以 body 传空字符串参与签名
            val signedParams = buildSignedParams(params, body = "", module = "Search")
            val response = api.searchSong(signedParams)
            Log.d("KgSource", "Search response: ${response.data}")

            if (response.errorCode != 0) {
                Log.e("KgSource", "Search failed: ${response.errorCode}")
                return@withContext emptyList()
            }
            Log.d("KgSource", "Search result: ${response.data?.lists}")
            return@withContext response.data?.lists?.map { item ->
                SongSearchResult(
                    id = item.id ?: "",
                    title = item.songName,
                    artist = item.singers.joinToString(separator) { it.name },
                    album = item.albumName ?: "",
                    duration = (item.duration * 1000).toLong(),
                    source = Source.KG,
                    hash = item.fileHash,
                    date = item.publishDate ?:"",
                    picUrl = if (item.picUrl != "") item.picUrl.replace("{size}", "480") else "", // 酷狗默认480*480
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("KgSource", "Search exception", e)
            return@withContext emptyList()
        }
    }



    private suspend fun buildSignedParams(
        customParams: Map<String, String>,
        body: String = "",
        module: String = "Search"
    ): Map<String, String> {
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

        return baseParams
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        if (song.hash.isNullOrEmpty()) return@withContext null

        try {
            // 搜索歌词
            val searchParams = mapOf(
                "album_audio_id" to song.id,
                "duration" to song.duration.toString(),
                "hash" to song.hash,
                "keyword" to "${song.artist} - ${song.title}",
                "lrctxt" to "1",
                "man" to "no"
            )

            val signedSearchParams = buildSignedParams(searchParams, module = "Lyric")

            val searchResp = api.searchLyrics(signedSearchParams)

            val candidate = searchResp.candidates?.firstOrNull()

            if (candidate == null) {
                Log.w("KgSource", "No lyrics candidates found for ${song.title}")
                return@withContext null
            }

            // 下载歌词
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

            var lyricText = ""
            if (contentResp.contenttype == 2) {
                lyricText = String(Base64.decode(rawBase64, Base64.DEFAULT), Charsets.UTF_8)
            } else {
                lyricText = KgCryptoUtils.decryptKrc(rawBase64)
            }
            Log.d("KgSource", "Lyric text: $lyricText")
            return@withContext KrcParser.parse(lyricText)

        } catch (e: Exception) {
            Log.e("KgSource", "Get Lyric exception", e)
            return@withContext null
        }
    }
}