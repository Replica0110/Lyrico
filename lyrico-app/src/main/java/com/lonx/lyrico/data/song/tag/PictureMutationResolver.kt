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
            is PictureUpdate.RemovePicture -> PictureWriteCommand.ReplaceAll(
                update.basePictures.orCurrent(currentPictures).removePictureType(update.type)
            )
            PictureUpdate.RemoveAllPictures -> PictureWriteCommand.ReplaceAll(emptyList())
            is PictureUpdate.ReplaceFrontCover -> {
                replacePicture(
                    currentPictures = currentPictures,
                    type = AudioPictureType.FrontCover,
                    source = update.source,
                    basePictures = null
                )
            }
            is PictureUpdate.ReplacePicture -> {
                replacePicture(
                    currentPictures = currentPictures,
                    type = update.type,
                    source = update.source,
                    basePictures = update.basePictures
                )
            }
            is PictureUpdate.ReplaceAll -> PictureWriteCommand.ReplaceAll(update.pictures)
        }
    }

    private suspend fun replacePicture(
        currentPictures: List<AudioPicture>,
        type: AudioPictureType,
        source: PictureSource,
        basePictures: List<AudioPicture>?
    ): PictureWriteCommand {
        val bytes = imageBytesFetcher.fetch(source)
            ?: return PictureWriteCommand.Unchanged
        val picture = AudioPicture(
            data = bytes,
            mimeType = (source as? PictureSource.Bytes)?.mimeType
                ?: mimeTypeDetector.detect(bytes),
            description = "",
            pictureType = type.tagLibName
        )
        return PictureWriteCommand.ReplaceAll(
            basePictures.orCurrent(currentPictures).replacePicture(
                picture = picture,
                type = type
            )
        )
    }

    private fun List<AudioPicture>?.orCurrent(currentPictures: List<AudioPicture>): List<AudioPicture> {
        return this ?: currentPictures
    }
}
