package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import androidx.room.withTransaction
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.model.log.withLogOperation
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.mapper.SongMetadataMapper
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagWriteResult

class SaveAudioTagsUseCase(
    private val database: LyricoDatabase,
    private val songLibraryRepository: SongLibraryRepository,
    private val audioTagRepository: AudioTagRepository,
    private val customTagKeyRepository: CustomTagKeyRepository,
    private val libraryIndexRepository: LibraryIndexRepository,
    private val songMetadataMapper: SongMetadataMapper,
    private val appLogRepository: AppLogRepository
) {
    suspend operator fun invoke(
        uri: String,
        mutation: AudioTagMutation
    ): SaveAudioTagsResult = withLogOperation("save-metadata") {
        val writeResult = when (mutation.mode) {
            AudioTagMutationMode.Overwrite -> audioTagRepository.overwrite(uri, mutation)
            AudioTagMutationMode.Patch -> audioTagRepository.patch(uri, mutation)
        }

        when (writeResult) {
            is AudioTagWriteResult.Success -> {
                val savedData = writeResult.savedData
                val updatedSong = try {
                    val song = songLibraryRepository.getSongByUri(uri)
                    if (song != null) {
                        val updated = songMetadataMapper.applyAudioTagData(
                            old = song,
                            tag = savedData
                        )

                        database.withTransaction {
                            database.songDao().update(updated)
                            customTagKeyRepository.replaceForSong(updated.uri, savedData.customFields)
                            libraryIndexRepository.reindexSongInTransaction(updated)
                        }
                        updated
                    } else {
                        null
                    }
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    appLogRepository.recordOperation(AppLogType.DATABASE, "SaveMetadata", "File saved but library update failed",
                        "uri=$uri\nstage=update-library-index\nfileWriteSucceeded=true\n${error.stackTraceToString()}", uri, AppLogLevel.ERROR)
                    throw error
                }
                appLogRepository.recordOperation(AppLogType.METADATA, "SaveMetadata", "Metadata save completed",
                    "uri=$uri\nmode=${mutation.mode}\nfileWriteSucceeded=true\nlibraryUpdated=${updatedSong != null}", uri, AppLogLevel.INFO)

                SaveAudioTagsResult.Success(
                    song = updatedSong,
                    tagData = savedData
                )
            }
            is AudioTagWriteResult.PermissionRequired -> {
                SaveAudioTagsResult.PermissionRequired(writeResult.intentSender)
            }
            is AudioTagWriteResult.Failed -> SaveAudioTagsResult.Failed(writeResult.error)
        }
    }
}

sealed interface SaveAudioTagsResult {
    data class Success(
        val song: SongEntity?,
        val tagData: AudioTagData
    ) : SaveAudioTagsResult

    data class PermissionRequired(
        val intentSender: IntentSender
    ) : SaveAudioTagsResult

    data class Failed(
        val error: Throwable
    ) : SaveAudioTagsResult
}
