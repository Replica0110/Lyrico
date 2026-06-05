package com.lonx.lyrico.data.model.entity

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 数据库中存储的歌曲实体（Song Entity）
 *
 * 设计说明：
 * - uri 是歌曲的唯一资源标识，推荐为 content:// 形式（MediaStore 或 SAF）。
 * - filePath 仅作为兼容字段与展示用途，不应用于文件写入。
 * - mediaId 通常对应 MediaStore.Audio.Media._ID。
 *
 * 字段说明：
 *
 * @property id 主键 ID（自增）
 *
 * —— 资源定位相关 ——
 * @property uri 歌曲的唯一资源 URI（推荐 content:// 形式，用于实际读写）
 * @property mediaId MediaStore 中的音频 ID（对应 _ID，可用于快速构建 contentUri）
 * @property filePath 文件的绝对路径（仅用于展示或兼容旧数据，不保证可写）
 * @property fileName 文件名（不含路径）
 * @property fileSize 文件大小（字节）
 * @property folderId 所属文件夹 ID（用于分组展示）
 *
 * —— 标签元数据（Tag Metadata） ——
 * @property title 标题
 * @property artist 艺术家
 * @property albumArtist 专辑艺术家
 * @property album 专辑名
 * @property genre 流派
 * @property language 语言
 * @property composer 作曲
 * @property lyricist 作词
 * @property comment 备注
 * @property discNumber 碟号（多碟专辑排序用）
 * @property trackerNumber 音轨号（专辑内排序）
 * @property date 发行或录制日期（字符串形式，保留原始格式）
 * @property lyrics 歌词文本
 * @property replayGainTrackGain ReplayGain 曲目增益
 * @property replayGainTrackPeak ReplayGain 曲目峰值
 * @property replayGainAlbumGain ReplayGain 专辑增益
 * @property replayGainAlbumPeak ReplayGain 专辑峰值
 * @property replayGainReferenceLoudness ReplayGain 参考响度
 *
 * —— 音频技术参数（Audio Technical Info） ——
 * @property durationMilliseconds 时长（毫秒）
 * @property bitrate 比特率（kbps）
 * @property sampleRate 采样率（Hz）
 * @property channels 声道数（1=单声道，2=立体声）
 *
 * —— 文件与数据库状态 ——
 * @property fileLastModified 文件最后修改时间（毫秒，用于增量扫描）
 * @property fileAdded 文件添加时间（毫秒，用于排序）
 * @property dbUpdateTime 数据库更新时间（毫秒，用于同步与刷新判断）
 *
 * —— 排序与分组索引 ——
 * @property titleGroupKey 标题分组键（A–Z 或 #）
 * @property titleSortKey 标题排序键（拼音或英文首字母）
 * @property artistGroupKey 艺术家分组键（A–Z 或 #）
 * @property artistSortKey 艺术家排序键（拼音或英文首字母）
 */
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["filePath"]),
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

    @ColumnInfo(defaultValue = "'MEDIA_STORE'")
    val source: String = "MEDIA_STORE",

    val filePath: String,
    val fileName: String,
    @ColumnInfo(defaultValue = "0")
    val fileSize: Long = 0,

    val fileExtension: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val discNumber: Int? = null,
    val composer: String? = null,
    val lyricist: String? = null,
    val comment: String? = null,
    val album: String? = null,
    val genre: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val language: String? = null,
    val trackerNumber: String? = null,
    val date: String? = null,
    val lyrics: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val copyright: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val rating: Int? = null,
    @ColumnInfo(defaultValue = "NULL")
    val replayGainTrackGain: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val replayGainTrackPeak: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val replayGainAlbumGain: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val replayGainAlbumPeak: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val replayGainReferenceLoudness: String? = null,

    val durationMilliseconds: Int = 0,
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,

    val fileLastModified: Long = 0,

    @ColumnInfo(defaultValue = "0")
    val fileAdded: Long = 0,

    val dbUpdateTime: Long = System.currentTimeMillis(),

    val titleGroupKey: String = "#",
    val titleSortKey: String = "#",
    val artistGroupKey: String = "#",
    val artistSortKey: String = "#",

    @ColumnInfo(defaultValue = "0")
    val uri: String = "",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SongEntity

        if (filePath != other.filePath) return false
        if (fileName != other.fileName) return false
        if (uri != other.uri) return false
        if (source != other.source) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (album != other.album) return false
        if (genre != other.genre) return false
        if (language != other.language) return false
        if (trackerNumber != other.trackerNumber) return false
        if (date != other.date) return false
        if (lyrics != other.lyrics) return false
        if (replayGainTrackGain != other.replayGainTrackGain) return false
        if (replayGainTrackPeak != other.replayGainTrackPeak) return false
        if (replayGainAlbumGain != other.replayGainAlbumGain) return false
        if (replayGainAlbumPeak != other.replayGainAlbumPeak) return false
        if (replayGainReferenceLoudness != other.replayGainReferenceLoudness) return false
        if (durationMilliseconds != other.durationMilliseconds) return false
        if (bitrate != other.bitrate) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
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
        result = 31 * result + uri.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (genre?.hashCode() ?: 0)
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + (trackerNumber?.hashCode() ?: 0)
        result = 31 * result + (date?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (replayGainTrackGain?.hashCode() ?: 0)
        result = 31 * result + (replayGainTrackPeak?.hashCode() ?: 0)
        result = 31 * result + (replayGainAlbumGain?.hashCode() ?: 0)
        result = 31 * result + (replayGainAlbumPeak?.hashCode() ?: 0)
        result = 31 * result + (replayGainReferenceLoudness?.hashCode() ?: 0)
        result = 31 * result + durationMilliseconds.hashCode()
        result = 31 * result + bitrate
        result = 31 * result + sampleRate
        result = 31 * result + channels
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
    get() = uri.toUri()
