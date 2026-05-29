package com.lonx.lyrico.domain.song.usecase

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.song.tag.AudioTagMutationFactory
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode

class PatchSongTagsUseCase(
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase
) {
    suspend operator fun invoke(
        uri: String,
        tagData: AudioTagData
    ): SaveAudioTagsResult {
        val mutation = AudioTagMutationFactory.fromAudioTagData(
            data = tagData,
            mode = AudioTagMutationMode.Patch
        )
        return saveAudioTagsUseCase(uri, mutation)
    }
}
