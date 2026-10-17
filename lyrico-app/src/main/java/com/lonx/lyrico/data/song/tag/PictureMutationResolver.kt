package com.lonx.lyrico.data.song.tag

import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.audiotag.model.removePictureType
import com.lonx.audiotag.model.replacePicture
import com.lonx.audiotag.model.type

class PictureMutationResolver(
    private val imageBytesFetcher: ImageBytesFetcher,
    private val mimeTypeDetector: ImageMimeTypeDetector
) {
    suspend fun resolve(
        currentPictures: List<AudioPicture>,
        supportsTypedPictures: Boolean,
        update: PictureUpdate
    ): PictureWriteCommand {
        return when (update) {
            PictureUpdate.Unchanged -> PictureWriteCommand.Unchanged
            PictureUpdate.RemoveFrontCover -> PictureWriteCommand.ReplaceAll(
                if (supportsTypedPictures) currentPictures.removePictureType(AudioPictureType.FrontCover)
                else emptyList()
            )
            is PictureUpdate.RemovePicture -> PictureWriteCommand.ReplaceAll(
                if (supportsTypedPictures) {
                    update.basePictures.orCurrent(currentPictures).removePictureType(update.type)
                } else {
                    requireCoverType(update.type)
                    emptyList()
                }
            )
            PictureUpdate.RemoveAllPictures -> PictureWriteCommand.ReplaceAll(emptyList())
            is PictureUpdate.ReplaceFrontCover -> {
                replacePicture(
                    currentPictures = currentPictures,
                    supportsTypedPictures = supportsTypedPictures,
                    type = AudioPictureType.FrontCover,
                    source = update.source,
                    basePictures = null
                )
            }
            is PictureUpdate.ReplacePicture -> {
                replacePicture(
                    currentPictures = currentPictures,
                    supportsTypedPictures = supportsTypedPictures,
                    type = update.type,
                    source = update.source,
                    basePictures = update.basePictures
                )
            }
            is PictureUpdate.ReplaceAll -> {
                if (!supportsTypedPictures) {
                    // A read-back MP4 cover has no type. Preserve those lists, but never
                    // silently turn an explicitly authored artist/back-cover image into a cover.
                    update.pictures.forEach { requireCoverType(it.type) }
                }
                PictureWriteCommand.ReplaceAll(update.pictures)
            }
        }
    }

    private suspend fun replacePicture(
        currentPictures: List<AudioPicture>,
        supportsTypedPictures: Boolean,
        type: AudioPictureType,
        source: PictureSource,
        basePictures: List<AudioPicture>?
    ): PictureWriteCommand {
        if (!supportsTypedPictures) requireCoverType(type)
        val bytes = imageBytesFetcher.fetch(source)
            ?: throw IllegalStateException("Unable to read image source: $source")
        val picture = AudioPicture(
            data = bytes,
            mimeType = (source as? PictureSource.Bytes)?.mimeType
                ?: mimeTypeDetector.detect(bytes),
            description = "",
            pictureType = type.tagLibName
        )
        return PictureWriteCommand.ReplaceAll(
            if (!supportsTypedPictures) listOf(picture)
            else basePictures.orCurrent(currentPictures).replacePicture(
                picture = picture,
                type = type
            )
        )
    }

    private fun requireCoverType(type: AudioPictureType) {
        require(type == AudioPictureType.FrontCover || type == AudioPictureType.Other) {
            "This audio format does not support typed pictures: $type"
        }
    }

    private fun List<AudioPicture>?.orCurrent(currentPictures: List<AudioPicture>): List<AudioPicture> {
        return this ?: currentPictures
    }
}
