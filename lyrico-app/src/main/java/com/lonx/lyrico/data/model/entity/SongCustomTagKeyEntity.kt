package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "song_custom_tag_keys",
    primaryKeys = ["songUri", "key"],
    indices = [
        Index(value = ["key"]),
        Index(value = ["songUri"]),
    ],
)
data class SongCustomTagKeyEntity(
    val songUri: String,
    val key: String,
)
