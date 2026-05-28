package com.lonx.lyrico.data.song.file

import android.content.IntentSender
import com.lonx.lyrico.data.model.entity.SongEntity

sealed interface DeleteSongFileResult {
    data object Deleted : DeleteSongFileResult
    data object AlreadyMissing : DeleteSongFileResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : DeleteSongFileResult

    data class Failed(
        val throwable: Throwable
    ) : DeleteSongFileResult
}

data class BatchSongFileOperationResult(
    val items: List<BatchSongFileOperationItem>
)

data class BatchSongFileOperationItem(
    val song: SongEntity,
    val result: DeleteSongFileResult
)

sealed interface RenameSongFileResult {
    data class Success(
        val oldUri: String,
        val newUri: String,
        val newFilePath: String,
        val newFileName: String
    ) : RenameSongFileResult

    data class NameConflict(
        val targetName: String
    ) : RenameSongFileResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : RenameSongFileResult

    data class Failed(
        val throwable: Throwable
    ) : RenameSongFileResult
}
