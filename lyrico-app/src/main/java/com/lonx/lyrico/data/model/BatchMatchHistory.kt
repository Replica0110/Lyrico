package com.lonx.lyrico.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "batch_match_history")
data class BatchMatchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val successCount: Int,
    val failureCount: Int,
    val skippedCount: Int,
    val durationMillis: Long
)
enum class BatchMatchStatus(val displayName: String) {
    SUCCESS("成功"),
    FAILURE("失败"),
    SKIPPED("跳过")
}
