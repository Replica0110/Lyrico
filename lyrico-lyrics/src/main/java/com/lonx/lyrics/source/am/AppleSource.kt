package com.lonx.lyrics.source.am

import android.util.Log
import com.lonx.lyrics.model.LyricsLine
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.LyricsWord
import com.lonx.lyrics.model.SearchResultExtraField
import com.lonx.lyrics.model.SearchResultExtraKeys
import com.lonx.lyrics.model.SearchResultExtraTarget
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.model.SourceConfigDependency
import com.lonx.lyrics.model.SourceConfigField
import com.lonx.lyrics.model.SourceConfigFieldType
import com.lonx.lyrics.model.SourceConfigOption
import com.lonx.lyrics.model.SourceRuntimeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

class AppleSource(
    private val api: AppleApi,
    private val json: Json,
    private val appUserAgent: String
) {
    private val sourceType: Source = Source.AM
    val extraFields = listOf(
        SearchResultExtraField(
            key = SearchResultExtraKeys.APPLE_ID,
            title = "Apple ID",
            summary = "Apple Music 请求所需的内部字段",
            writeable = false
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.COMPOSER,
            title = "作曲",
            summary = "Apple Music composerName 字段",
            defaultTarget = SearchResultExtraTarget.COMPOSER
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.GENRE,
            title = "流派",
            summary = "Apple Music genreNames 字段",
            defaultTarget = SearchResultExtraTarget.GENRE
        ),
        SearchResultExtraField(
            key = SearchResultExtraKeys.DISC_NUMBER,
            title = "碟号",
            summary = "Apple Music discNumber 字段",
            defaultTarget = SearchResultExtraTarget.DISC_NUMBER
        )
    )

    private val tokenMutex = Mutex()
    private var cachedToken: String = ""
    private var config = SourceRuntimeConfig()

    fun applyConfig(config: SourceRuntimeConfig) {
        this.config = config
    }

    fun getConfigFields(): List<SourceConfigField> {
        return listOf(
            SourceConfigField(
                key = AppleSourceConfigKeys.LYRICS_PROVIDER,
                title = "歌词源",
                summary = "选择第三方歌词接口或 Apple 官方接口",
                type = SourceConfigFieldType.DROPDOWN,
                required = true,
                defaultValue = "third_party",
                options = listOf(
                    SourceConfigOption("third_party", "第三方"),
                    SourceConfigOption("official", "官方")
                )
            ),
            SourceConfigField(
                key = AppleSourceConfigKeys.TOKEN,
                title = "Token",
                summary = "使用官方歌词源时必填",
                type = SourceConfigFieldType.PASSWORD,
                required = true,
                dependency = SourceConfigDependency.Match(
                    key = AppleSourceConfigKeys.LYRICS_PROVIDER,
                    value = "official"
                )
            ),
            SourceConfigField(
                key = AppleSourceConfigKeys.REGION,
                title = "地区",
                summary = "用于 Apple Music 请求的语言/地区参数",
                type = SourceConfigFieldType.DROPDOWN,
                required = true,
                defaultValue = "zh-CN",
                options = listOf(
                    SourceConfigOption("zh-CN", "zh-CN"),
                    SourceConfigOption("en-US", "en-US"),
                    SourceConfigOption("ja-JP", "ja-JP"),
                    SourceConfigOption("ko-KR", "ko-KR")
                )
            ),
            SourceConfigField(
                key = AppleSourceConfigKeys.COVER_SIZE,
                title = "封面大小",
                summary = "Apple Music 封面图片尺寸",
                type = SourceConfigFieldType.DROPDOWN,
                required = true,
                defaultValue = "3000",
                options = listOf(
                    SourceConfigOption("500", "500 x 500"),
                    SourceConfigOption("1000", "1000 x 1000"),
                    SourceConfigOption("3000", "3000 x 3000")
                )
            )
        )
    }

    suspend fun search(
        keyword: String,
        page: Int,
        separator: String,
        pageSize: Int
    ): List<SongSearchResult> = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext emptyList()
        val offset = (page - 1).coerceAtLeast(0) * pageSize
        val query = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://amp-api.music.apple.com/v1/catalog/${storefront()}/search" +
            "?term=$query&types=songs&limit=$pageSize&offset=$offset&l=${languageTag()}&platform=web&format[resources]=map"

        try {
            val body = requestText(url, appleMusicHeaders(token)) ?: return@withContext emptyList()
            parseSearchResults(body, separator)
        } catch (e: Exception) {
            if (e.message?.contains("401") == true) clearToken()
            Log.e(TAG, "Search exception", e)
            emptyList()
        }
    }

    suspend fun searchCover(keyword: String, pageSize: Int): List<SongSearchResult> =
        search(keyword = keyword, page = 1, separator = "/", pageSize = pageSize)
            .filter { it.picUrl.isNotBlank() }

    suspend fun getLyrics(song: SongSearchResult): LyricsResult? {
        return when (config.getString(AppleSourceConfigKeys.LYRICS_PROVIDER, "third_party")) {
            "official" -> getOfficialLyrics(song)
            else -> getThirdPartyLyrics(song)
        }
    }

    private suspend fun getThirdPartyLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        val appleId = song.extras[SearchResultExtraKeys.APPLE_ID] ?: song.id
        if (appleId.isBlank()) return@withContext null

        try {
            val body = requestText(
                url = "https://lyrics.paxsenix.org/apple-music/lyrics?id=$appleId&ttml=false",
                headers = mapOf(
                    "accept" to "application/json",
                    "User-Agent" to appUserAgent
                )
            ) ?: return@withContext null

            parseLyrics(body, song)
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics exception", e)
            null
        }
    }

    private suspend fun getOfficialLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        val token = config.getString(AppleSourceConfigKeys.TOKEN)
        if (token.isBlank() || song.id.isBlank()) return@withContext null

        // Official lyrics API is intentionally left unimplemented until a stable endpoint is available.
        null
    }

    private fun languageTag(): String {
        return config.getString(AppleSourceConfigKeys.REGION, "zh-CN")
    }

    private fun storefront(): String {
        return when (languageTag()) {
            "zh-CN" -> "cn"
            "ja-JP" -> "jp"
            "ko-KR" -> "kr"
            "en-US" -> "us"
            else -> "us"
        }
    }

    private fun coverSize(): String {
        return config.getString(AppleSourceConfigKeys.COVER_SIZE, "3000")
    }

    private suspend fun getToken(): String? {
        if (cachedToken.isNotBlank()) return cachedToken

        return tokenMutex.withLock {
            if (cachedToken.isNotBlank()) return@withLock cachedToken

            try {
                val home = requestText(
                    url = "https://beta.music.apple.com",
                    headers = mapOf("User-Agent" to WEB_USER_AGENT)
                ) ?: return@withLock null

                val indexPath = INDEX_JS_REGEX.find(home)?.value ?: return@withLock null
                val js = requestText(
                    url = "https://beta.music.apple.com$indexPath",
                    headers = mapOf("User-Agent" to WEB_USER_AGENT)
                ) ?: return@withLock null

                val token = TOKEN_REGEX.find(js)?.value.orEmpty()
                cachedToken = token
                token.ifBlank { null }
            } catch (e: Exception) {
                Log.e(TAG, "Token exception", e)
                null
            }
        }
    }

    private fun clearToken() {
        cachedToken = ""
    }

    private suspend fun requestText(url: String, headers: Map<String, String>): String? {
        val response = api.get(url, headers)
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code()} for $url")
        }
        return response.body()?.string()
    }

    private fun appleMusicHeaders(token: String): Map<String, String> = mapOf(
        "Authorization" to "Bearer $token",
        "Origin" to "https://music.apple.com",
        "Referer" to "https://music.apple.com/",
        "User-Agent" to "Mozilla/5.0"
    )

    private fun parseSearchResults(rawJson: String, separator: String): List<SongSearchResult> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val songIds = root["results"]
            ?.jsonObject
            ?.get("songs")
            ?.jsonObject
            ?.get("data")
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
            .orEmpty()

        val resources = root["resources"]
            ?.jsonObject
            ?.get("songs")
            ?.jsonObject
            .orEmpty()

        return songIds.mapNotNull { id ->
            val song = resources[id]?.jsonObject ?: return@mapNotNull null
            val attrs = song["attributes"]?.jsonObject ?: return@mapNotNull null
            val title = attrs.string("name")
            if (title.isBlank()) return@mapNotNull null

            val artistName = attrs.string("artistName")
            val appleId = attrs.string("url")
                .substringAfterLast("?i=", "")
                .substringBefore("&")
                .ifBlank { attrs.string("url").substringAfterLast("/") }
                .ifBlank { id }

            SongSearchResult(
                id = appleId,
                title = title,
                artist = artistName.split(", ", " & ")
                    .filter { it.isNotBlank() }
                    .joinToString(separator)
                    .ifBlank { artistName },
                album = attrs.string("albumName"),
                duration = attrs["durationInMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
                source = sourceType,
                date = attrs.string("releaseDate"),
                trackerNumber = attrs["trackNumber"]?.jsonPrimitive?.intOrNull?.toString().orEmpty(),
                picUrl = attrs["artwork"]?.jsonObject?.string("url")
                    ?.replace("{w}", coverSize())
                    ?.replace("{h}", coverSize())
                    ?.replace("{f}", "jpg")
                    .orEmpty(),
                extras = buildMap {
                    put(SearchResultExtraKeys.APPLE_ID, appleId)
                    attrs.string("composerName").takeIf { it.isNotBlank() }?.let {
                        put(SearchResultExtraKeys.COMPOSER, it)
                    }
                    val genres = attrs["genreNames"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                        ?.joinToString(" / ")
                        .orEmpty()
                    if (genres.isNotBlank()) put(SearchResultExtraKeys.GENRE, genres)
                    attrs["discNumber"]?.jsonPrimitive?.intOrNull?.let {
                        put(SearchResultExtraKeys.DISC_NUMBER, it.toString())
                    }
                }
            )
        }
    }

    private fun parseLyrics(rawJson: String, fallbackSong: SongSearchResult): LyricsResult? {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val rawPlainLrc = root.string("lrc")
        val rawSinglePersonEnhancedLrc = root.string("elrc")
        val rawTtml = root.string("ttmlContent")
        val rawMultiPersonEnhancedLrc = root.string("elrcMultiPerson")
        val rawEnhancedLrc = rawSinglePersonEnhancedLrc.ifBlank { rawMultiPersonEnhancedLrc }
        val content = root["content"]?.jsonArray.orEmpty()
        val original = content.mapNotNull { element ->
            val line = element.jsonObject
            val start = line["timestamp"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val end = line["endtime"]?.jsonPrimitive?.longOrNull ?: start
            val wordsJson = line["text"]?.jsonArray.orEmpty()

            val words = wordsJson.mapIndexedNotNull { index, wordElement ->
                val word = wordElement.jsonObject
                val text = word.string("text")
                if (text.isEmpty()) return@mapIndexedNotNull null

                val nextText = wordsJson.getOrNull(index + 1)?.jsonObject?.string("text").orEmpty()
                LyricsWord(
                    start = word["timestamp"]?.jsonPrimitive?.longOrNull ?: start,
                    end = word["endtime"]?.jsonPrimitive?.longOrNull ?: end,
                    text = text + if (shouldAppendSpace(text, nextText)) " " else ""
                )
            }

            if (words.isNotEmpty()) {
                LyricsLine(start = start, end = end, words = words)
            } else {
                val text = line.string("plain").ifBlank {
                    line["text"]?.jsonPrimitive?.content.orEmpty()
                }
                if (text.isBlank()) null else LyricsLine(
                    start = start,
                    end = end,
                    words = listOf(LyricsWord(start = start, end = end, text = text))
                )
            }
        }

        if (
            original.isEmpty() &&
            rawPlainLrc.isBlank() &&
            rawEnhancedLrc.isBlank() &&
            rawTtml.isBlank() &&
            rawMultiPersonEnhancedLrc.isBlank()
        ) return null

        val track = root["track"]?.jsonObject
        val metadata = buildMap {
            put("ti", track?.string("name").orEmpty().ifBlank { fallbackSong.title })
            put("ar", track?.string("artistName").orEmpty().ifBlank { fallbackSong.artist })
            put("al", track?.string("albumName").orEmpty().ifBlank { fallbackSong.album })
            track?.string("composerName")?.takeIf { it.isNotBlank() }?.let { put("composer", it) }
            track?.string("releaseDate")?.takeIf { it.isNotBlank() }?.let { put("date", it) }
            val songwriters = root["metadata"]
                ?.jsonObject
                ?.get("songwriters")
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
                ?.joinToString(" / ")
                .orEmpty()
            if (songwriters.isNotBlank()) put("lyricist", songwriters)
        }

        return LyricsResult(
            tags = metadata,
            original = original,
            translated = null,
            romanization = null,
            isWordByWord = root["type"]?.jsonPrimitive?.content == "Syllable" ||
                original.any { it.words.size > 1 },
            rawPlainLrc = rawPlainLrc,
            rawEnhancedLrc = rawEnhancedLrc,
            rawTtml = rawTtml,
            rawMultiPersonEnhancedLrc = rawMultiPersonEnhancedLrc
        )
    }

    private fun shouldAppendSpace(current: String, next: String): Boolean {
        if (next.isBlank()) return false
        val currentLast = current.lastOrNull() ?: return false
        val nextFirst = next.firstOrNull() ?: return false
        if (!currentLast.isLetterOrDigit() || !nextFirst.isLetterOrDigit()) return false
        return current.any(::isAsciiLetterOrDigit) || next.any(::isAsciiLetterOrDigit)
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty()

    private fun isAsciiLetterOrDigit(char: Char): Boolean =
        char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9'

    private companion object {
        const val TAG = "AppleSource"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val INDEX_JS_REGEX = Regex("""/assets/index~[^/]+\.js""")
        val TOKEN_REGEX = Regex("""eyJh[^"]*""")
    }

    private object AppleSourceConfigKeys {
        const val LYRICS_PROVIDER = "lyrics_provider"
        const val TOKEN = "token"
        const val REGION = "region"
        const val COVER_SIZE = "cover_size"
    }
}
