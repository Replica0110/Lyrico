package com.lonx.lyrico.worker.processor

import android.util.Log
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.domain.song.usecase.BatchEditSongsUseCase
import com.lonx.lyrico.domain.song.usecase.BatchTagEditItemRequest
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.LyricEncoder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

@Serializable
data class EditTagsTaskConfig(
    val title: String = "<keep>",
    val artist: String = "<keep>",
    val albumArtist: String = "<keep>",
    val album: String = "<keep>",
    val date: String = "<keep>",
    val language: String = "<keep>",
    val genre: String = "<keep>",
    val trackNumber: String = "<keep>",
    val discNumber: String = "<keep>",
    val composer: String = "<keep>",
    val lyricist: String = "<keep>",
    val copyright: String = "<keep>",
    val comment: String = "<keep>",
    val lyrics: String = "<keep>",
    val rating: Int = 0,
    val ratingModified: Boolean = false,
    val coverUri: String? = null,
    val removeCover: Boolean = false,
    val lyricsOffset: String = "",
    val replayGainTrackGain: String = "<keep>",
    val replayGainTrackPeak: String = "<keep>",
    val replayGainAlbumGain: String = "<keep>",
    val replayGainAlbumPeak: String = "<keep>",
    val replayGainReferenceLoudness: String = "<keep>",
    val customFields: List<EditTagsCustomField> = emptyList(),
    val concurrency: Int = 3
) {
    companion object {
        const val KEEP_VALUE = "<keep>"
    }
}

@Serializable
data class EditTagsCustomField(
    val key: String = "",
    val value: String = "",
)

class EditTagsProcessor(
    private val songLibraryRepository: SongLibraryRepository,
    private val batchEditSongsUseCase: BatchEditSongsUseCase
) : BatchTaskProcessor {

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<EditTagsTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val result = try {
            batchEditSongsUseCase.editOne(
                BatchTagEditItemRequest(
                    song = song,
                    tagDataFactory = { _, currentTag -> buildMergedTag(currentTag, config) }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to edit tags: ${item.songUri}", e)
            throw Exception("Write failed", e)
        }

        if (result.skippedReason != null) {
            throw BatchTaskSkippedException(result.skippedReason)
        }
        if (result.result !is SaveAudioTagsResult.Success) {
            throw Exception("Write failed")
        }

        return BatchTaskProcessResult()
    }

    private fun buildMergedTag(original: AudioTagData, config: EditTagsTaskConfig): AudioTagData {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        var tag = original

        if (config.title != keep) tag = tag.copy(title = config.title)
        if (config.artist != keep) tag = tag.copy(artist = config.artist)
        if (config.albumArtist != keep) tag = tag.copy(albumArtist = config.albumArtist)
        if (config.album != keep) tag = tag.copy(album = config.album)
        if (config.date != keep) tag = tag.copy(date = config.date)
        if (config.language != keep) tag = tag.copy(language = config.language)
        if (config.genre != keep) tag = tag.copy(genre = config.genre)
        if (config.trackNumber != keep) tag = tag.copy(trackNumber = config.trackNumber)
        if (config.discNumber != keep) tag = tag.copy(discNumber = config.discNumber.toIntOrNull())
        if (config.composer != keep) tag = tag.copy(composer = config.composer)
        if (config.lyricist != keep) tag = tag.copy(lyricist = config.lyricist)
        if (config.copyright != keep) tag = tag.copy(copyright = config.copyright)
        if (config.comment != keep) tag = tag.copy(comment = config.comment)
        if (config.lyrics != keep) tag = tag.copy(lyrics = config.lyrics)

        if (config.replayGainTrackGain != keep) {
            tag = tag.copy(replayGainTrackGain = config.replayGainTrackGain)
        }
        if (config.replayGainTrackPeak != keep) {
            tag = tag.copy(replayGainTrackPeak = config.replayGainTrackPeak)
        }
        if (config.replayGainAlbumGain != keep) {
            tag = tag.copy(replayGainAlbumGain = config.replayGainAlbumGain)
        }
        if (config.replayGainAlbumPeak != keep) {
            tag = tag.copy(replayGainAlbumPeak = config.replayGainAlbumPeak)
        }
        if (config.replayGainReferenceLoudness != keep) {
            tag = tag.copy(replayGainReferenceLoudness = config.replayGainReferenceLoudness)
        }

        if (config.ratingModified) tag = tag.copy(rating = config.rating)

        tag = when {
            config.removeCover -> tag.copy(picUrl = "")
            config.coverUri != null -> tag.copy(picUrl = config.coverUri)
            else -> tag
        }

        if (config.lyricsOffset.isNotBlank() && tag.lyrics != null) {
            val offsetValue = parseLyricsOffset(config.lyricsOffset)
            if (offsetValue != 0) {
                tag = tag.copy(
                    lyrics = LyricEncoder.shiftLyricsOffset(tag.lyrics!!, offsetValue.toLong())
                )
            }
        }

        if (config.customFields.isNotEmpty()) {
            tag = tag.copy(customFields = tag.customFields.toMutableList().apply {
                config.customFields.forEach { newField ->
                    val key = normalizeCustomTagKey(newField.key) ?: return@forEach
                    removeAll { it.key.equals(key, ignoreCase = true) }
                    add(CustomTagField(key, newField.value))
                }
            })
        }

        return tag
    }

    private fun parseLyricsOffset(input: String): Int {
        return input.trim().toIntOrNull() ?: 0
    }

    private fun normalizeCustomTagKey(input: String): String? {
        val key = input.trim()
        return when {
            key.isBlank() -> null
            key.length > 64 -> null
            key.any { it == '\n' || it == '\r' } -> null
            else -> key.uppercase(Locale.ROOT)
        }
    }

    private companion object {
        const val TAG = "EditTagsProcessor"
    }
}
