package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.ExtraMetadataWriteRule
import com.lonx.lyrico.data.model.ExtraWriteMode
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.ExtraMetadataResolver
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchMetadataProcessor(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val sources: List<SearchSource>
) : BatchTaskProcessor {
    private val extraMetadataResolver = ExtraMetadataResolver()

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<MatchMetadataTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val matchConfig = config.matchConfig
        val song = songRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val needsProcessing = matchConfig.fields.any { (field, mode) ->
            if (mode == BatchMatchMode.OVERWRITE) return@any true
            when (field) {
                BatchMatchField.TITLE -> song.title.isNullOrBlank()
                BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
                BatchMatchField.ALBUM -> song.album.isNullOrBlank()
                BatchMatchField.GENRE -> song.genre.isNullOrBlank()
                BatchMatchField.DATE -> song.date.isNullOrBlank()
                BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
                BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
                BatchMatchField.COVER -> true
            }
        }

        val needsExtraProcessing = config.extraWriteRules.any { it.mode != ExtraWriteMode.DISABLED }
        if (!needsProcessing && !needsExtraProcessing) {
            throw BatchTaskSkippedException("No fields need processing")
        }

        val separator = config.separator
        val lyricConfig = settingsRepository.getLyricRenderConfig()
        val enabledSourceOrder = config.enabledSourceOrderIds.mapNotNull { id ->
            Source.entries.find { it.id == id }
        }
        val queries = MusicMatchUtils.buildSearchQueries(
            song = song,
            preferFileName = matchConfig.preferFileName
        )

        val orderedSources = sources
            .filter { source ->
                enabledSourceOrder.isEmpty() || source.sourceType in enabledSourceOrder
            }
            .sortedBy { source ->
                enabledSourceOrder.indexOf(source.sourceType).let { if (it == -1) Int.MAX_VALUE else it }
            }

        var bestMatch: ScoredSearchResult? = null
        var bestMatchDetail: MatchScoreDetail? = null
        val allScoredResults = mutableListOf<ScoredSearchResult>()

        for (query in queries) {
            val searchTasks = orderedSources.map { source ->
                coroutineScope {
                    async(Dispatchers.IO) {
                        try {
                            val results = source.search(
                                keyword = query,
                                separator = separator,
                                pageSize = 2
                            )

                            results.mapIndexed { index, res ->
                                val detail = MusicMatchUtils.calculateMatchScoreDetail(
                                    result = res,
                                    song = song,
                                    preferFileName = matchConfig.preferFileName,
                                    rankIndex = index
                                )

                                ScoredSearchResult(
                                    result = res,
                                    score = detail.finalScore,
                                    source = source
                                ) to detail
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
            }

            val allResults = searchTasks.awaitAll().flatten()

            allScoredResults += allResults.map { (scoredResult, _) ->
                scoredResult
            }

            val currentBest = allResults.maxByOrNull { (_, detail) ->
                detail.finalScore
            }

            if (currentBest != null) {
                val currentScoredResult = currentBest.first
                val currentDetail = currentBest.second

                if (
                    bestMatch == null ||
                    currentDetail.finalScore > (bestMatchDetail?.finalScore ?: 0.0)
                ) {
                    bestMatch = currentScoredResult
                    bestMatchDetail = currentDetail
                }

                // 文本分和最终分都非常高时才提前停止搜索
                if (currentDetail.finalScore >= 0.92 && currentDetail.textScore >= 0.86) {
                    break
                }
            }
        }

        val finalMatch = bestMatch ?: throw BatchTaskSkippedException("No match found")
        val finalDetail = bestMatchDetail ?: throw BatchTaskSkippedException("No match detail found")

        if (finalDetail.finalScore < 0.76 || finalDetail.textScore < 0.72) {
            throw BatchTaskSkippedException(
                "Match score too low: final=${finalDetail.finalScore}, text=${finalDetail.textScore}"
            )
        }

        val newLyrics = try {
            coroutineScope {
                val deferred = async(Dispatchers.Default) {
                    finalMatch.source?.getLyrics(finalMatch.result)?.let { result ->
                        LyricEncoder.encode(result = result, config = lyricConfig)
                    }
                }
                deferred.await()
            }
        } catch (e: Exception) {
            null
        }
        val newTitle = resolveValue(matchConfig, BatchMatchField.TITLE, song.title, finalMatch.result.title)
        val newArtist = resolveValue(matchConfig, BatchMatchField.ARTIST, song.artist, finalMatch.result.artist)
        val newAlbum = resolveValue(matchConfig, BatchMatchField.ALBUM, song.album, finalMatch.result.album)
        val newDate = resolveValue(matchConfig, BatchMatchField.DATE, song.date, finalMatch.result.date)
        val newTrack = resolveValue(matchConfig, BatchMatchField.TRACK_NUMBER, song.trackerNumber, finalMatch.result.trackerNumber)
        val newGenre = resolveValue(matchConfig, BatchMatchField.GENRE, song.genre, null)
        val newLyricsResolved = resolveValue(matchConfig, BatchMatchField.LYRICS, song.lyrics, newLyrics)

        val shouldUpdateCover = shouldUpdate(matchConfig, BatchMatchField.COVER, null)
        val picUrl = if (shouldUpdateCover) finalMatch.result.picUrl else null

        val standardTagData = AudioTagData(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            genre = newGenre,
            date = newDate,
            trackNumber = newTrack,
            lyrics = newLyricsResolved,
            picUrl = picUrl
        )
        val extraTagData = extraMetadataResolver.resolve(
            currentSong = song,
            scoredResults = allScoredResults,
            rules = config.extraWriteRules
        )
        val tagDataToWrite = extraMetadataResolver.mergeNonNull(standardTagData, extraTagData)

        val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                newGenre == null && newDate == null && newTrack == null &&
                newLyricsResolved == null && picUrl == null && extraTagData.isEmpty()

        if (isEffectivelyEmpty) {
            throw BatchTaskSkippedException("No fields to update")
        }

        val success = songRepository.patchAudioTags(song.uri, tagDataToWrite)
        if (!success) {
            throw Exception("Write failed")
        }

        return BatchTaskProcessResult()
    }

    private fun resolveValue(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?,
        newValue: String?
    ): String? {
        if (!config.fields.containsKey(field)) return null
        val mode = config.fields[field]!!
        return if (mode == BatchMatchMode.OVERWRITE) {
            newValue
        } else {
            if (currentValue.isNullOrBlank()) newValue else null
        }
    }

    private fun shouldUpdate(
        config: BatchMatchConfig,
        field: BatchMatchField,
        currentValue: String?
    ): Boolean {
        if (!config.fields.containsKey(field)) return false
        val mode = config.fields[field]!!
        if (mode == BatchMatchMode.OVERWRITE) return true
        return currentValue.isNullOrBlank()
    }

    private fun AudioTagData.isEmpty(): Boolean {
        return title == null && artist == null && album == null && genre == null &&
                date == null && trackNumber == null && lyrics == null && picUrl == null &&
                comment == null && replayGainTrackGain == null && replayGainTrackPeak == null &&
                replayGainAlbumGain == null && replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val extraWriteRules: List<ExtraMetadataWriteRule> = emptyList(),
    val concurrency: Int = 3
)
