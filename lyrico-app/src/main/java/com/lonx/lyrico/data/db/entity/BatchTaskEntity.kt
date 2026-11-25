package com.lonx.lyrico.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lonx.lyrico.data.batch.BatchTaskStatus
import com.lonx.lyrico.data.batch.BatchTaskType

@Entity(
    tableName = "batch_tasks",
    indices = [Index("status")]
)
data class BatchTaskEntity(
    @PrimaryKey val taskId: String,
    val type: BatchTaskType,
    val status: BatchTaskStatus,
    val total: Int,
    val current: Int,
    val successCount: Int,
    val failureCount: Int,
    val skippedCount: Int,
    val currentFile: String?,
    val configJson: String?,
    val workId: String?,
    val startedAt: Long?,
    val finishedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?
)
