package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "artist_song",
    primaryKeys = ["artistId", "songId"],
    indices = [
        Index(value = ["artistId"]),
        Index(value = ["songId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artistId"],
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
data class ArtistSongCrossRef(
    val artistId: Long,
    val songId: Long
)

