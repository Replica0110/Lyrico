package com.lonx.lyrico.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType

@Entity(
    tableName = "app_logs",
    indices = [
        Index("createdAt"),
        Index("level"),
        Index("type"),
        Index("relatedId")
    ]
)
data class AppLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val level: AppLogLevel,
    val type: AppLogType,
    val tag: String,
    val message: String,
    val detail: String? = null,
    val relatedId: String? = null
)
