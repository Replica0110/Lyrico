package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import androidx.room.withTransaction
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.song.file.DeleteSongFileResult
import com.lonx.lyrico.data.song.file.SongFileRepository

class DeleteSongsUseCase(
    private val database: LyricoDatabase,
    private val songFileRepository: SongFileRepository,
    private val customTagKeyRepository: CustomTagKeyRepository,
    private val libraryIndexRepository: LibraryIndexRepository
) {
    suspend operator fun invoke(songs: List<SongEntity>): DeleteSongsResult {
        if (songs.isEmpty()) {
            return DeleteSongsResult(total = 0, deleted = 0, items = emptyList())
        }

        val fileResult = songFileRepository.deleteFiles(songs)
        val deletableSongs = fileResult.items
            .filter { item ->
                item.result == DeleteSongFileResult.Deleted ||
                    item.result == DeleteSongFileResult.AlreadyMissing
            }
            .map { it.song }

        if (deletableSongs.isNotEmpty()) {
            val uris = deletableSongs.map { it.uri }
            val impactedFolderIds = deletableSongs.map { it.folderId }.distinct()
            database.withTransaction {
                uris.chunked(BATCH_SIZE).forEach { chunk ->
                    database.songDao().deleteByUris(chunk)
                    customTagKeyRepository.removeSongs(chunk)
                }
                impactedFolderIds.forEach { folderId ->
                    database.folderDao().refreshSongCount(folderId)
                }
                database.folderDao().performPostScanCleanup()
            }
            libraryIndexRepository.refreshAndPruneIndexes()
        }

        return DeleteSongsResult(
            total = songs.size,
            deleted = deletableSongs.size,
            items = fileResult.items.map { item ->
                DeleteSongsItemResult(item.song, item.result)
            }
        )
    }

    private companion object {
        const val BATCH_SIZE = 50
    }
}

data class DeleteSongsResult(
    val total: Int,
    val deleted: Int,
    val items: List<DeleteSongsItemResult>
) {
    val permissionRequired: IntentSender?
        get() = items.firstNotNullOfOrNull { item ->
            (item.result as? DeleteSongFileResult.PermissionRequired)?.intentSender
        }
}

data class DeleteSongsItemResult(
    val song: SongEntity,
    val result: DeleteSongFileResult
)
