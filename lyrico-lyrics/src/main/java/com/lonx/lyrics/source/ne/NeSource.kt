package com.lonx.lyrics.source.ne

import android.util.Base64
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.NeCryptoUtils
import com.lonx.lyrics.utils.YrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

class NeSource: SearchSource {
    override val sourceType = Source.NE
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    // Cookie 管理
    private val cookieMap = mutableMapOf<String, String>()
    private val DEVICEID_XOR_KEY = "3go8&$8*3*3h0k(2)2"

    private val client = OkHttpClient.Builder().build()

    private val api: NeApi = Retrofit.Builder()
        .baseUrl("https://interface.music.163.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NeApi::class.java)

    private val initMutex = Mutex()
    private var isInitialized = false
    private var userId: Long = 0

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    // 模拟 PC 客户端常量
    private val APP_VER = "3.1.3.203419"
    private val OS_VER = "Microsoft-Windows-10--build-19045-64bit"
    private val DEVICE_ID = UUID.randomUUID().toString().replace("-", "")
    private var clientSign: String = ""

    init {
        // 初始化时生成 clientSign
        clientSign = generateClientSign()
    }

    /**
     * 生成 ClientSign (对应 Python 代码中的逻辑)
     * 格式: MAC@@@RANDOM@@@@@@HASH
     */
    private fun generateClientSign(): String {
        val mac = (1..6).joinToString(":") {
            "%02X".format(Random.nextInt(256))
        }
        val randomStr = (1..8).map {
            ('A'..'Z').random()
        }.joinToString("")
        val hashPart = (1..32).joinToString("") {
            "%02x".format(Random.nextInt(256))
        }
        val realHashPart = (1..64).map {
            "0123456789abcdef".random()
        }.joinToString("")

        return "$mac@@@$randomStr@@@@@@$realHashPart"
    }

    private suspend fun ensureInit() {
        if (isInitialized) return

        initMutex.withLock {
            if (isInitialized) return

            // 1. 构造初始 Cookie
            cookieMap["os"] = "pc"
            cookieMap["appver"] = APP_VER
            cookieMap["osver"] = OS_VER
            cookieMap["deviceId"] = DEVICE_ID
            cookieMap["channel"] = "netease"
            cookieMap["clientSign"] = clientSign

            val path = "/eapi/register/anonimous"

            val username = getAnonimousUsername(DEVICE_ID)

            val params = buildJsonObject {
                put("username", username)
                put("e_r", true)
            }

            try {
                val result = doRequest(path, params, "/api/register/anonimous")
                Log.d("NeSource", "Register result: $result")

                val jsonRes = json.decodeFromString<JsonObject>(result)

                if (jsonRes["code"].toString() == "200") {
                    userId = jsonRes["userId"].toString().toLongOrNull() ?: 0
                    isInitialized = true
                    Log.d("NeSource", "Anonimous login success, uid: $userId")
                } else {
                    Log.e("NeSource", "Login failed code: ${jsonRes["code"]}")
                }
            } catch (e: Exception) {
                Log.e("NeSource", "Init failed", e)
            }
        }
    }

    private suspend fun doRequest(
        path: String,
        params: JsonObject,
        encryptPath: String? = null
    ): String {
        val headerParam = buildJsonObject {
            put("clientSign", clientSign)
            put("osver", OS_VER)
            put("deviceId", DEVICE_ID)
            put("os", "pc")
            put("appver", APP_VER)
            put("requestId", System.currentTimeMillis().toString())
        }

        // The python implementation shows that the header field must be a string.
        val headerParamString = json.encodeToString(headerParam)

        val finalParams = params.toMutableMap()
        finalParams["header"] = JsonPrimitive(headerParamString)

        if (!finalParams.containsKey("e_r")) {
            finalParams["e_r"] = JsonPrimitive(true)
        }

        val mergedParams = JsonObject(finalParams)
        val paramsStr = json.encodeToString(mergedParams)
        val actualEncryptPath = encryptPath ?: path.replace("/eapi/", "/api/")
        val encryptedBytes = NeCryptoUtils.encryptParams(actualEncryptPath, paramsStr)

        // The request body should be in the format "params=ENCRYPTED_DATA"
        val encryptedHexString = encryptedBytes.joinToString("") { "%02x".format(it) }.uppercase()
        val formBody = "params=$encryptedHexString"
        val requestBody = formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())


        val headers = mutableMapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER",
            "Referer" to "https://music.163.com/"
        )
        val cookieStr = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
        headers["Cookie"] = cookieStr

        val fullUrl = "https://interface.music.163.com$path"

        val responseBody = api.request(fullUrl, headers, requestBody)

        // Read the response body only once to avoid consuming the stream.
        val responseBytes = responseBody.bytes()

        if (responseBytes.isEmpty()) {
            Log.w("NeSource", "doRequest for $path failed: response body is empty")
            return ""
        }

        try {
            val decrypted = NeCryptoUtils.aesDecrypt(responseBytes)
            // Log.d("NeSource", "Decrypted: $decrypted")
            return decrypted
        } catch (e: Exception) {
            Log.e("NeSource", "Decrypt failed for $path", e)
            return ""
        }
    }

    override suspend fun search(keyword: String, page: Int, separator: String, pageSize: Int): List<SongSearchResult> = withContext(
        Dispatchers.IO) {
        ensureInit()

        val path = "/eapi/search/song/list/page"
        val offset = (page - 1) * 20

        val params = buildJsonObject {
            put("limit", pageSize.toString())
            put("offset", offset.toString())
            put("keyword", keyword)
            put("scene", "NORMAL")
            put("needCorrect", "true")
        }

        try {
            val rawJson = doRequest(path, params)
            Log.d("NeSource", "Search raw: $rawJson")

            val resp = json.decodeFromString<NeSearchResponse>(rawJson)

            if (resp.code != 200) return@withContext emptyList()
            Log.d("NeSource", "Search result: $resp")
            return@withContext resp.data?.resources?.map { res ->
                val song = res.baseInfo.simpleSongData
                SongSearchResult(
                    id = song.id.toString(),
                    title = song.name,
                    artist = song.artists.joinToString(separator) { it.name },
                    album = song.album.name,
                    duration = song.duration,
                    source = Source.NE,
                    date = song.publishTime?.let { formatMillisToDate(it) } ?: "",
                    trackerNumber = song.trackerNumber,
                    hash = song.id.toString(),
                    picUrl = song.album.picUrl
                )
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e("NeSource", "Search exception", e)
            return@withContext emptyList()
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        ensureInit()
        val path = "/eapi/song/lyric/v1"
        val params = buildJsonObject {
            put("id", song.id.toLongOrNull() ?: 0)
            put("lv", "-1")
            put("tv", "-1")
            put("rv", "-1")
            put("yv", "-1")
        }
        val rawJson = doRequest(path, params)
        val resp = try {
            json.decodeFromString<NeLyricResponse>(rawJson)
        } catch (e: Exception) { return@withContext null }
        Log.d("NeSource", "Lyric lrc result: ${resp.lrc}")
        Log.d("NeSource", "Lyric yrc result: ${resp.yrc}")
        Log.d("NeSource", "Lyric translation result: ${resp.tlyric}")
        Log.d("NeSource", "Lyric romanization result: ${resp.romalrc}")
        return@withContext YrcParser.parse(
            yrc = resp.yrc?.lyric,
            lrc = resp.lrc?.lyric,
            tlyric = resp.tlyric?.lyric,
            romalrc = resp.romalrc?.lyric
        )
    }


    /**
     * 生成游客登录所需的 username
     */
    fun getAnonimousUsername(deviceId: String): String {
        val keyLength = DEVICEID_XOR_KEY.length
        val sb = StringBuilder()

        deviceId.forEachIndexed { index, char ->
            val keyChar = DEVICEID_XOR_KEY[index % keyLength]
            val xoredChar = (char.code xor keyChar.code).toChar()
            sb.append(xoredChar)
        }
        val xoredString = sb.toString()
        val md = MessageDigest.getInstance("MD5")
        val md5Digest = md.digest(xoredString.toByteArray(Charsets.UTF_8))

        val base64Md5 = Base64.encodeToString(md5Digest, Base64.NO_WRAP)

        val combinedStr = "$deviceId $base64Md5"

        return Base64.encodeToString(combinedStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    private fun formatMillisToDate(millis: Long): String {
        if (millis <= 0L) return ""

        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }
}