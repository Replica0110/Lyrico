package com.lonx.lyrico.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lonx.lyrico.data.batch.BatchTaskStatus

@Entity(
    tableName = "batch_task_items",
    indices = [Index("taskId"), Index("status")]
)
data class BatchTaskItemEntity(
    @PrimaryKey val itemId: String,
    val taskId: String,
    val mediaId: Long,
    val songUri: String,
    val filePath: String?,
    val fileName: String,
    val status: BatchTaskStatus,
    val progress: Float?,
    val resultJson: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
