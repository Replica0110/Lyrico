package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["isIgnored"]),
        Index(value = ["songCount"])
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 绝对路径，如 /storage/emulated/0/Music/Pop */
    val path: String,

    /** 当前扫描到的歌曲数量 */
    val songCount: Int = 0,

    /** 是否被用户忽略 */
    val isIgnored: Boolean = false,

    /** 是否来自 SAF（用于 UI 提示） */
    val addedBySaf: Boolean = false,

    /** 最近一次被扫描到的时间 */
    val lastScanned: Long = System.currentTimeMillis(),

    /** 数据库更新时间 */
    val dbUpdateTime: Long = System.currentTimeMillis()
)