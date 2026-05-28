package com.lonx.lyrico.domain.song.usecase

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagMutationFactory
import com.lonx.lyrico.data.song.tag.AudioTagMutationMode

class PatchSongTagsUseCase(
    private val songLibraryRepository: SongLibraryRepository,
    private val editSongTagsUseCase: EditSongTagsUseCase
) {
    suspend operator fun invoke(
        uri: String,
        tagData: AudioTagData
    ): EditSongTagsResult {
        val song = songLibraryRepository.getSongByUri(uri)
            ?: return EditSongTagsResult.Failed(IllegalStateException("Song not found: $uri"))
        return editSongTagsUseCase(
            song = song,
            mutation = AudioTagMutationFactory.fromAudioTagData(
                data = tagData,
                mode = AudioTagMutationMode.Patch
            )
        )
    }
}
