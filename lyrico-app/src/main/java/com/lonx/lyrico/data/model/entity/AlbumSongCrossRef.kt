package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "album_song",
    primaryKeys = ["albumId", "songId"],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["songId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AlbumSongCrossRef(
    val albumId: Long,
    val songId: Long
)

