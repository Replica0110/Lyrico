package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["normalizedKey"], unique = true),
        Index(value = ["groupKey", "sortKey"])
    ]
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val albumArtist: String?,
    val normalizedKey: String,
    val groupKey: String = "#",
    val sortKey: String = "#",
    val songCount: Int = 0,
    val coverSongUri: String? = null,
    val coverSongLastModified: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

