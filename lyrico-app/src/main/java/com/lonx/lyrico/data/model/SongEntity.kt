package com.lonx.lyrico.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 数据库中存储的歌曲实体
 * @param filePath 文件URI路径（唯一标识）
 * @param fileName 文件名称
 * @param title 歌曲标题
 * @param artist 艺术家
 * @param album 专辑
 * @param genre 类型
 * @param date 日期
 * @param lyrics 歌词
 * @param durationMilliseconds 时长（毫秒）
 * @param bitrate 比特率
 * @param sampleRate 采样率
 * @param channels 声道数
 * @param rawProperties 原始属性JSON字符串
 * @param coverPath 封面图片缓存路径
 * @param fileLastModified 文件最后修改时间戳
 * @param dbUpdateTime 数据库更新时间
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val filePath: String,
    val fileName: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val date: String? = null,
    val lyrics: String? = null,
    val durationMilliseconds: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val rawProperties: String? = null,
    val fileLastModified: Long = 0,
    val dbUpdateTime: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SongEntity

        if (filePath != other.filePath) return false
        if (fileName != other.fileName) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (genre != other.genre) return false
        if (date != other.date) return false
        if (lyrics != other.lyrics) return false
        if (durationMilliseconds != other.durationMilliseconds) return false
        if (bitrate != other.bitrate) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (rawProperties != other.rawProperties) return false
        if (fileLastModified != other.fileLastModified) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (date?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + durationMilliseconds.hashCode()
        result = 31 * result + bitrate
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + (rawProperties?.hashCode() ?: 0)
        result = 31 * result + fileLastModified.hashCode()
        return result
    }
}
