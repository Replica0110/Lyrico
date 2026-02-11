package com.lonx.lyrico.data.model.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.BatchMatchStatus

@Entity(
    tableName = "batch_match_records",
    foreignKeys = [
        ForeignKey(
            entity = BatchMatchHistory::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.Companion.CASCADE
        )
    ],
    indices = [Index(value = ["historyId"])]
)
data class BatchMatchRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val historyId: Long,
    val filePath: String,
    val status: BatchMatchStatus
)