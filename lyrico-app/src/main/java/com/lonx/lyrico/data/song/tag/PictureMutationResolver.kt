package com.lonx.lyrico.data.song.tag

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.removePictureType
import com.lonx.audiotag.model.replacePicture

class PictureMutationResolver(
    private val imageBytesFetcher: ImageBytesFetcher,
    private val mimeTypeDetector: ImageMimeTypeDetector
) {
    suspend fun resolve(
        currentPictures: List<AudioPicture>,
        update: PictureUpdate
    ): PictureWriteCommand {
        return when (update) {
            PictureUpdate.Unchanged -> PictureWriteCommand.Unchanged
            PictureUpdate.RemoveFrontCover -> PictureWriteCommand.ReplaceAll(
                currentPictures.removePictureType(AudioPictureType.FrontCover)
            )
            PictureUpdate.RemoveAllPictures -> PictureWriteCommand.ReplaceAll(emptyList())
            is PictureUpdate.ReplaceFrontCover -> {
                val bytes = imageBytesFetcher.fetch(update.source)
                    ?: return PictureWriteCommand.Unchanged
                val picture = AudioPicture(
                    data = bytes,
                    mimeType = (update.source as? PictureSource.Bytes)?.mimeType
                        ?: mimeTypeDetector.detect(bytes),
                    description = "",
                    pictureType = AudioPictureType.FrontCover.tagLibName
                )
                PictureWriteCommand.ReplaceAll(
                    currentPictures.replacePicture(
                        picture = picture,
                        type = AudioPictureType.FrontCover
                    )
                )
            }
            is PictureUpdate.ReplaceAll -> PictureWriteCommand.ReplaceAll(update.pictures)
        }
    }
}
