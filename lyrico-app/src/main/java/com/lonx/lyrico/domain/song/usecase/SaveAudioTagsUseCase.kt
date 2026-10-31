package com.lonx.lyrico.domain.song.usecase

import android.content.IntentSender
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.AppLogRepository
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.recordOperation
import com.lonx.lyrico.data.model.log.withLogOperation
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagMutationFactory
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.song.tag.AudioTagWriteResult

class SaveAudioTagsUseCase(
    private val songLibraryRepository: SongLibraryRepository,
    private val audioTagRepository: AudioTagRepository,
    private val appLogRepository: AppLogRepository
) {
    suspend fun patch(uri: String, tagData: AudioTagData): SaveAudioTagsResult = invoke(
        uri, AudioTagMutationFactory.fromAudioTagData(tagData, AudioTagMutationMode.Patch)
    )

    /** Explicitly authored picture lists may be empty; preserve that deletion intent. */
    suspend fun overwrite(uri: String, tagData: AudioTagData, picturesAuthored: Boolean = false): SaveAudioTagsResult = invoke(
        uri, AudioTagMutationFactory.fromAudioTagData(tagData, AudioTagMutationMode.Overwrite, picturesAuthored)
    )

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
                    songLibraryRepository.synchronizeSavedTags(uri, savedData)
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
