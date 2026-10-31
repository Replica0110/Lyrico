package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.file.DeleteSongFileResult
import com.lonx.lyrico.data.song.file.SongFileRepository

class DeleteSongsUseCase(
    private val songFileRepository: SongFileRepository,
    private val songLibraryRepository: SongLibraryRepository,
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
            songLibraryRepository.deleteSongsByUris(deletableSongs.map { it.uri })
        }

        return DeleteSongsResult(
            total = songs.size,
            deleted = deletableSongs.size,
            items = fileResult.items.map { item ->
                DeleteSongsItemResult(item.song, item.result)
            }
        )
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
