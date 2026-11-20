package com.lonx.lyrico.data.model.batch

import androidx.annotation.StringRes
import com.lonx.lyrico.R

enum class BatchTaskStatus(
    @field:StringRes val labelRes: Int
) {
    QUEUED(R.string.batch_task_status_queued),
    RUNNING(R.string.batch_task_status_running),
    SUCCEEDED(R.string.batch_task_status_succeeded),
    FAILED(R.string.batch_task_status_failed),
    SKIPPED(R.string.batch_task_status_skipped),
    CANCELLED(R.string.batch_task_status_cancelled)
}
