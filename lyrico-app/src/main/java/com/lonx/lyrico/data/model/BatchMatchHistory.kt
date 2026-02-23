package com.lonx.lyrico.data.model

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lonx.lyrico.R

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
enum class BatchMatchResult(
    @field:StringRes val labelRes: Int
) {
    SUCCESS(R.string.batch_match_stat_success),
    FAILURE(R.string.batch_match_stat_failure),
    SKIPPED(R.string.batch_match_stat_skipped)
}
