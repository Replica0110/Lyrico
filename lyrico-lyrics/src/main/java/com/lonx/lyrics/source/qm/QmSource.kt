package com.lonx.lyrics.source.qm


import android.util.Base64
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsData
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.QmCryptoUtils
import com.lonx.lyrics.utils.QrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import kotlin.random.Random

class QmSource: SearchSource {
    override val sourceType: Source = Source.QM
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("User-Agent", "okhttp/3.14.9")
                .addHeader("Referer", "https://y.qq.com/") // QM 即使是 App API 有时也校验 Referer
                .build()
            chain.proceed(req)
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://u.y.qq.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(QmApi::class.java)

    // 公共参数
    private val comm = mapOf(
        "ct" to "11",
        "cv" to "1003006",
        "v" to "1003006",
        "os_ver" to "15",
        "phonetype" to "24122RKC7C",
        "tmeAppID" to "qqmusiclight",
        "nettype" to "NETWORK_WIFI"
    )

    override suspend fun search(keyword: String, page: Int,separator: String, pageSize: Int): List<SongSearchResult> = withContext(Dispatchers.IO) {
        val param = buildJsonObject {
            put("search_id", Random.nextLong(10000000000000000L, 90000000000000000L).toString())
            put("remoteplace", "search.android.keyboard")
            put("query", keyword)
            put("search_type", 0) // 0: Song
            put("num_per_page", pageSize)
            put("page_num", page)
            put("highlight", 0)
            put("nqc_flag", 0)
            put("page_id", 1)
            put("grp", 1)
        }

        val reqBody = QmRequestBody(
            comm = comm,
            req_0 = QmRequestModule(
                method = "DoSearchForQQMusicLite",
                module = "music.search.SearchCgiService",
                param = param
            )
        )

        try {
            val resp = api.searchSong(reqBody)
            val songs = resp.req_0.data?.body?.songs ?: emptyList()

            return@withContext songs.map { item ->
                val singerList = item.singer.map { it.name }
                val picUrl = if (item.album.name.isNotEmpty()) {
                    "https://y.gtimg.cn/music/photo_new/T002R800x800M000${item.album.mid}.jpg"
                } else {
                    ""
                }
                SongSearchResult(
                    id = item.id,
                    title = item.title,
                    artist = singerList.joinToString(separator),
                    album = item.album.name,
                    duration = item.interval * 1000L,
                    source = Source.QM,
                    date = item.timePublic ?: "",
                    trackerNumber = item.trackerNumber,
                    picUrl = picUrl
                )
            }

        } catch (e: Exception) {
            Log.e("QmSource", "Search failed", e)
            return@withContext emptyList()
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        Log.d("QmSource", "Get Lyric: $song")
        if (song.id == "0") return@withContext null

        val param = buildJsonObject {
            put("songID", song.id.toLong())
            put("songName", Base64.encodeToString(song.title.toByteArray(), Base64.NO_WRAP))
            put("albumName", Base64.encodeToString(song.album.toByteArray(), Base64.NO_WRAP))
            put("singerName", Base64.encodeToString(song.artist.toByteArray(), Base64.NO_WRAP))
            put("crypt", 1) // 启用加密
            put("qrc", 1)   // 获取 QRC
            put("trans", 1) // 获取翻译
            put("roma", 1)  // 获取罗马音
            put("cv", 2111)
            put("ct", 19)
            put("lrc_t", 0)
            put("qrc_t", 0)
            put("roma_t", 0)
            put("trans_t", 0)
            put("type", 0)
            put("interval", song.duration / 1000)
        }

        val reqBody = QmRequestBody(
            comm = comm,
            req_0 = QmRequestModule(
                method = "GetPlayLyricInfo",
                module = "music.musichallSong.PlayLyricInfo",
                param = param
            )
        )

        try {
            val resp = api.getLyrics(reqBody)
            val data = resp.req_0.data ?: return@withContext null

            // 提取 QRC (lyric)
            val lyricHex = data.lyric
            var qrcText = ""

            if (lyricHex.isNotEmpty()) {
                qrcText = QmCryptoUtils.decryptQrc(lyricHex)
                Log.d("QmSource", "Decrypt QRC: $qrcText")
            }

            // 处理翻译 (trans) 和 罗马音 (roma)
            // 它们也是 Hex String，同样可以用 decryptQrc 解密
            val transHex = data.trans
            val romaHex = data.roma
            
            var transText: String? = null
            var romaText: String? = null
            
            if (transHex.isNotEmpty()) {
                transText = QmCryptoUtils.decryptQrc(transHex)
                Log.d("QmSource", "Decrypt trans: $transText")
            }
            
            if (romaHex.isNotEmpty()) {
                romaText = QmCryptoUtils.decryptQrc(romaHex)
                Log.d("QmSource", "Decrypt roma: $romaText")
            }

            val lyricsData = LyricsData(
                original = qrcText.ifEmpty { null },
                translated = transText,
                type = if (qrcText.isNotEmpty()) "qrc" else "lrc",
                romanization = romaText
            )
            
            return@withContext QrcParser.parse(lyricsData)

        } catch (e: Exception) {
            Log.e("QmSource", "Get Lyric failed", e)
            return@withContext null
        }
    }

}