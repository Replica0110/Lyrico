package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.UpdateCheckResult

interface UpdateRepository {
    suspend fun checkForUpdate(): UpdateCheckResult
}