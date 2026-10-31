package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.file.RenameSongFileResult
import com.lonx.lyrico.data.song.file.SongFileRepository
import com.lonx.lyrico.data.song.mapper.SortKeyUpdater

class RenameSongUseCase(
    private val songFileRepository: SongFileRepository,
    private val songLibraryRepository: SongLibraryRepository,
    private val sortKeyUpdater: SortKeyUpdater
) {
    suspend operator fun invoke(
        song: SongEntity,
        newFileName: String
    ): RenameSongResult {
        return when (val fileResult = songFileRepository.renameFile(song, newFileName)) {
            is RenameSongFileResult.Success -> {
                val updatedSong = sortKeyUpdater.update(
                    song.copy(
                        uri = fileResult.newUri,
                        filePath = fileResult.newFilePath,
                        fileName = fileResult.newFileName,
                        fileLastModified = System.currentTimeMillis()
                    )
                )

                songLibraryRepository.synchronizeRenamedSong(updatedSong, fileResult.oldUri)
                RenameSongResult.Success(updatedSong, fileResult.oldUri)
            }
            is RenameSongFileResult.NameConflict -> RenameSongResult.NameConflict(fileResult.targetName)
            is RenameSongFileResult.PermissionRequired -> {
                RenameSongResult.PermissionRequired(fileResult.intentSender)
            }
            is RenameSongFileResult.Failed -> RenameSongResult.Failed(fileResult.throwable)
        }
    }
}

sealed interface RenameSongResult {
    data class Success(
        val song: SongEntity,
        val oldUri: String
    ) : RenameSongResult

    data class NameConflict(
        val targetName: String
    ) : RenameSongResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : RenameSongResult

    data class Failed(
        val throwable: Throwable
    ) : RenameSongResult
}
