package com.lonx.lyrico.data.song.scan

data class LibraryScanRequest(
    val fullRescan: Boolean,
    val folderIds: Set<Long>? = null,
    val removeMissingSafFolders: Boolean = true,
    val ignoreShortAudio: Boolean
)

data class LibraryScanProgress(
    val stage: LibraryScanStage,
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String? = null
)

enum class LibraryScanStage {
    LISTING_FILES,
    READING_METADATA,
    WRITING_DATABASE,
    FINISHED
}

data class LibraryScanResult(
    val scanned: Int,
    val inserted: Int,
    val updated: Int,
    val deleted: Int,
    val skipped: Int,
    val failures: List<LibraryScanFailure> = emptyList()
)

data class LibraryScanFailure(
    val uri: String?,
    val fileName: String?,
    val stage: LibraryScanFailureStage,
    val message: String,
    val throwable: Throwable? = null
)

enum class LibraryScanFailureStage {
    Collecting,
    ReadingMetadata,
    WritingDatabase
}
