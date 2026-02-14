package com.lonx.lyrico.data.model.entity

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

/**
 * 数据库中存储的歌曲实体
 *
 * @param id 主键 ID，自动生成
 * @param folderId 关联的文件夹 ID，用于归类歌曲
 * @param filePath 文件 URI 路径，作为主键（唯一标识）
 * @param fileName 文件名称
 * @param title 歌曲标题
 * @param artist 艺术家名称
 * @param album 专辑名称
 * @param genre 流派
 * @param trackerNumber 音轨号（通常用于专辑排序）
 * @param date 歌曲发行或录制日期
 * @param lyrics 歌词文本
 * @param durationMilliseconds 歌曲时长（毫秒）
 * @param bitrate 比特率（kbps）
 * @param sampleRate 采样率（Hz）
 * @param channels 声道数（1=单声道，2=立体声）
 * @param rawProperties 原始音频属性 JSON 字符串（用于调试或扩展）
 * @param fileLastModified 文件最后修改时间戳（毫秒），用于增量更新
 * @param fileAdded 文件添加时间戳（毫秒），用于排序
 * @param dbUpdateTime 数据库更新时间戳（毫秒），用于排序或同步记录
 * @param titleGroupKey 标题分组索引（A-Z 或 #），用于列表分组
 * @param titleSortKey 标题排序索引（拼音首字母或英文首字母），用于组内排序
 * @param artistGroupKey 艺术家分组索引（A-Z 或 #）
 * @param artistSortKey 艺术家排序索引（拼音首字母或英文首字母）
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["folderId"]),
        Index(value = ["titleGroupKey", "titleSortKey"]),
        Index(value = ["artistGroupKey", "artistSortKey"]),
        Index(value = ["fileLastModified"]),
        Index(value = ["fileAdded"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val mediaId: Long,
    val filePath: String,
    val fileName: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val trackerNumber: String? = null,
    val date: String? = null,
    val lyrics: String? = null,
    val durationMilliseconds: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val rawProperties: String? = null,
    val fileLastModified: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val fileAdded: Long = 0,
    val dbUpdateTime: Long = System.currentTimeMillis(),
    val titleGroupKey: String = "#",
    val titleSortKey: String = "#",
    val artistGroupKey: String = "#",
    val artistSortKey: String = "#",
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
        if (trackerNumber != other.trackerNumber) return false
        if (date != other.date) return false
        if (lyrics != other.lyrics) return false
        if (durationMilliseconds != other.durationMilliseconds) return false
        if (bitrate != other.bitrate) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (rawProperties != other.rawProperties) return false
        if (fileLastModified != other.fileLastModified) return false
        if (fileAdded != other.fileAdded) return false
        if (dbUpdateTime != other.dbUpdateTime) return false
        if (titleGroupKey != other.titleGroupKey) return false
        if (titleSortKey != other.titleSortKey) return false
        if (artistGroupKey != other.artistGroupKey) return false
        if (artistSortKey != other.artistSortKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (trackerNumber?.hashCode() ?: 0)
        result = 31 * result + (date?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + durationMilliseconds.hashCode()
        result = 31 * result + bitrate
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + (rawProperties?.hashCode() ?: 0)
        result = 31 * result + fileLastModified.hashCode()
        result = 31 * result + fileAdded.hashCode()
        result = 31 * result + dbUpdateTime.hashCode()
        result = 31 * result + titleGroupKey.hashCode()
        result = 31 * result + titleSortKey.hashCode()
        result = 31 * result + artistGroupKey.hashCode()
        result = 31 * result + artistSortKey.hashCode()
        return result
    }
}
val SongEntity.getUri: Uri
    get() = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        this.mediaId
    )