package com.lonx.lyrico.data.update

interface UpdateRepository {
    suspend fun checkForUpdate(
        owner: String,
        repo: String
    ): UpdateCheckResult
}
