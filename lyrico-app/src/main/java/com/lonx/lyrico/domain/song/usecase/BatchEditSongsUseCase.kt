package com.lonx.lyrico.domain.song.usecase

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.song.tag.AudioTagMutation
import com.lonx.lyrico.data.song.tag.AudioTagMutationFactory
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BatchTagEditRequest(
    val songs: List<SongEntity>,
    val tagDataFactory: (SongEntity, AudioTagData) -> AudioTagData,
    val concurrency: Int,
    val dryRun: Boolean = false
)

data class BatchTagEditItemRequest(
    val song: SongEntity,
    val tagDataFactory: (SongEntity, AudioTagData) -> AudioTagData,
    val dryRun: Boolean = false
)

data class BatchTagEditResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val skipped: Int,
    val items: List<BatchTagEditItemResult>
)

data class BatchTagEditItemResult(
    val song: SongEntity,
    val currentTag: AudioTagData?,
    val editedTag: AudioTagData?,
    val mutation: AudioTagMutation?,
    val result: SaveAudioTagsResult?,
    val skippedReason: String? = null
)

class BatchEditSongsUseCase(
    private val audioTagRepository: AudioTagRepository,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase
) {
    suspend fun editOne(request: BatchTagEditItemRequest): BatchTagEditItemResult {
        val currentTag = audioTagRepository.read(request.song.uri)
        val editedTag = request.tagDataFactory(request.song, currentTag)
        if (editedTag == currentTag) {
            return BatchTagEditItemResult(
                song = request.song,
                currentTag = currentTag,
                editedTag = editedTag,
                mutation = null,
                result = null,
                skippedReason = "No changes"
            )
        }

        val mutation = AudioTagMutationFactory.fromAudioTagData(
            data = editedTag,
            mode = AudioTagMutationMode.Overwrite
        )
        val result = if (request.dryRun) {
            null
        } else {
            saveAudioTagsUseCase(request.song.uri, mutation)
        }

        return BatchTagEditItemResult(
            song = request.song,
            currentTag = currentTag,
            editedTag = editedTag,
            mutation = mutation,
            result = result
        )
    }

    suspend operator fun invoke(request: BatchTagEditRequest): BatchTagEditResult {
        val concurrency = request.concurrency.coerceAtLeast(1)
        val semaphore = Semaphore(concurrency)
        val items = coroutineScope {
            request.songs.map { song ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        editOne(
                            BatchTagEditItemRequest(
                                song = song,
                                tagDataFactory = request.tagDataFactory,
                                dryRun = request.dryRun
                            )
                        )
                    }
                }
            }.awaitAll()
        }

        return BatchTagEditResult(
            total = request.songs.size,
            success = items.count { it.result is SaveAudioTagsResult.Success },
            failed = items.count {
                it.result is SaveAudioTagsResult.Failed ||
                    it.result is SaveAudioTagsResult.PermissionRequired
            },
            skipped = items.count { it.skippedReason != null } +
                if (request.dryRun) items.count { it.skippedReason == null } else 0,
            items = items
        )
    }
}
