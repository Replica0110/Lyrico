package com.lonx.lyrico.domain.poster

import com.lonx.lyrico.data.model.CharacterMappingDefaults
import com.lonx.lyrico.utils.FileNameSanitizer

/**
 * 艺术家海报文件的命名。外置海报按文件名认艺术家，所以文件名必须写成艺术家名；
 * 文件名放不下的字符按应用重命名规则换成全角字符，`AC/DC` 存成 `AC／DC.jpg` 仍能被认出。
 */
object ArtistPosterFileNaming {

    private val mimeExtensions = mapOf(
        "image/jpeg" to "jpg",
        "image/jpg" to "jpg",
        "image/png" to "png",
        "image/webp" to "webp",
        "video/mp4" to "mp4"
    )

    /**
     * 艺术家名清成可用作文件名的词干；清理后为空（例如名字全是非法字符）时返回 `null`。
     */
    fun stemFor(artistName: String): String? = FileNameSanitizer
        .sanitize(artistName, CharacterMappingDefaults.ALL_BUILTIN_RULES)
        .trim()
        .trimEnd('.')
        .takeIf { it.isNotBlank() }

    /**
     * 按图片的 MIME 类型给出扩展名，取不到时退回 [fileName] 自己的扩展名，再退回 `jpg`。
     */
    fun extensionFor(mimeType: String?, fileName: String? = null): String {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
        mimeExtensions[normalized]?.let { return it }

        val fromName = fileName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all { char -> char.isLetterOrDigit() } }
        return fromName ?: "jpg"
    }

    /**
     * 文件夹里属于 [artistName] 的海报文件名，排序与读取海报时一致，最好的排在最前。
     */
    fun matchesArtist(fileNames: List<String>, artistName: String): List<String> = fileNames
        .mapNotNull { name -> ArtistPosterMatcher.rank(name, artistName)?.let { rank -> rank to name } }
        .sortedWith(compareBy({ it.first }, { it.second.lowercase() }, { it.second }))
        .map { it.second }
}
