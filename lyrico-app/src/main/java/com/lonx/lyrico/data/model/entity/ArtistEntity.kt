package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "artists",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["groupKey", "sortKey"])
    ]
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val groupKey: String = "#",
    val sortKey: String = "#",
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val coverSongUri: String? = null,
    val coverSongLastModified: Long = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

