package com.lonx.lyrico.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["isIgnored"]),
        Index(value = ["songCount"]),
        Index(value = ["addedBySaf"])
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val path: String,

    val treeUri: String? = null,

    val songCount: Int = 0,

    val isIgnored: Boolean = false,

    val addedBySaf: Boolean = false,

    val lastScanned: Long = System.currentTimeMillis(),

    val dbUpdateTime: Long = System.currentTimeMillis()
)