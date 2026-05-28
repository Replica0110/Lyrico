package com.lonx.lyrico.data.song.scan

interface LibraryScanRepository {
    suspend fun synchronize(
        request: LibraryScanRequest,
        onProgress: suspend (LibraryScanProgress) -> Unit = {}
    ): LibraryScanResult
}
