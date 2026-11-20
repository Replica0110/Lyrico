package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.update.UpdateCheckResult

interface UpdateRepository {
    suspend fun checkForUpdate(
        owner: String,
        repo: String
    ): UpdateCheckResult
}
