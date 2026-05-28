package com.lonx.lyrico.domain.song.usecase

import com.lonx.lyrico.data.song.scan.LibraryScanProgress
import com.lonx.lyrico.data.song.scan.LibraryScanRepository
import com.lonx.lyrico.data.song.scan.LibraryScanRequest
import com.lonx.lyrico.data.song.scan.LibraryScanResult

class SynchronizeLibraryUseCase(
    private val libraryScanRepository: LibraryScanRepository
) {
    suspend operator fun invoke(
        request: LibraryScanRequest,
        onProgress: suspend (LibraryScanProgress) -> Unit = {}
    ): LibraryScanResult {
        return libraryScanRepository.synchronize(request, onProgress)
    }
}
