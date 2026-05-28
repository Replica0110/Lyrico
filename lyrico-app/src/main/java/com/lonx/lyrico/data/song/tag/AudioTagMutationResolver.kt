package com.lonx.lyrico.data.song.tag

import com.lonx.audiotag.model.AudioTagData

data class ResolvedAudioTagWrite(
    val tags: Map<String, String>,
    val pictures: PictureWriteCommand
)

class AudioTagMutationResolver(
    private val tagMapBuilder: TagMapBuilder,
    private val pictureResolver: PictureMutationResolver
) {
    suspend fun resolve(
        uri: String,
        current: AudioTagData,
        mutation: AudioTagMutation
    ): ResolvedAudioTagWrite {
        return ResolvedAudioTagWrite(
            tags = tagMapBuilder.build(
                uri = uri,
                current = current,
                mutation = mutation
            ),
            pictures = pictureResolver.resolve(
                currentPictures = current.pictures,
                update = mutation.pictureUpdate
            )
        )
    }
}
