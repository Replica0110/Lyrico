package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import androidx.room.withTransaction
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.song.mapper.SongMetadataMapper
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagWriteResult

class EditSongTagsUseCase(
    private val database: LyricoDatabase,
    private val audioTagRepository: AudioTagRepository,
    private val customTagKeyRepository: CustomTagKeyRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val songMetadataMapper: SongMetadataMapper
) {
    suspend operator fun invoke(
        song: SongEntity,
        mutation: AudioTagMutation
    ): EditSongTagsResult {
        val writeResult = when (mutation.mode) {
            AudioTagMutationMode.Overwrite -> audioTagRepository.overwrite(song.uri, mutation)
            AudioTagMutationMode.Patch -> audioTagRepository.patch(song.uri, mutation)
        }

        return when (writeResult) {
            is AudioTagWriteResult.Success -> {
                val savedData = writeResult.savedData
                val updatedSong = songMetadataMapper.applyAudioTagData(
                    old = song,
                    tag = savedData
                )

                database.withTransaction {
                    database.songDao().update(updatedSong)
                    customTagKeyRepository.replaceForSong(updatedSong.uri, savedData.customFields)
                    libraryIndexRepository.reindexSongInTransaction(updatedSong)
                }
                EditSongTagsResult.Success(updatedSong, savedData)
            }
            is AudioTagWriteResult.PermissionRequired -> {
                EditSongTagsResult.PermissionRequired(writeResult.intentSender)
            }
            is AudioTagWriteResult.Failed -> EditSongTagsResult.Failed(writeResult.error)
        }
    }
}

sealed interface EditSongTagsResult {
    data class Success(
        val song: SongEntity,
        val tagData: AudioTagData
    ) : EditSongTagsResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : EditSongTagsResult

    data class Failed(
        val error: Throwable
    ) : EditSongTagsResult
}
