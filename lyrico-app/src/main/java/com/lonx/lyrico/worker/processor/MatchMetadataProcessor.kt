package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.BatchMatchField
import com.lonx.lyrico.data.model.BatchMatchMode
import com.lonx.lyrico.data.model.MetadataFieldWriteRule
import com.lonx.lyrico.data.model.MetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MetadataFieldResolver
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchMetadataProcessor(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val searchSourceProvider: SearchSourceProvider
) : BatchTaskProcessor {
    private val metadataFieldResolver = MetadataFieldResolver()

    override suspend fun process(
        task: BatchTaskEntity,
        item: BatchTaskItemEntity,
        onProgress: suspend (Float) -> Unit
    ): BatchTaskProcessResult {
        val config = task.configJson?.let {
            Json.decodeFromString<MatchMetadataTaskConfig>(it)
        } ?: throw BatchTaskSkippedException("No config")

        val matchConfig = config.matchConfig
        val sources = searchSourceProvider.getAllSources()
        sources.forEach { source ->
            val values = config.sourceSettings[source.id].orEmpty()
            source.applyConfig(SourceRuntimeConfig(values))
        }
        val song = songRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val plan = buildPlan(matchConfig, config.metadataFieldWriteRules, song, sources)
        if (!plan.requiresSearch) {
            throw BatchTaskSkippedException("No fields need processing")
        }
        onProgress(0.05f)

        val separator = config.separator
        val lyricConfig = if (plan.shouldFetchLyrics) {
            settingsRepository.getLyricRenderConfig()
        } else {
            null
        }
        val enabledSourceOrder = config.enabledSourceOrderIds
        val queries = MusicMatchUtils.buildSearchQueries(
            song = song,
            preferFileName = matchConfig.preferFileName
        )

        val orderedSources = sources
            .filter { source ->
                enabledSourceOrder.isEmpty() || source.id in enabledSourceOrder
            }
            .sortedBy { source ->
                enabledSourceOrder.indexOf(source.id).let { if (it == -1) Int.MAX_VALUE else it }
            }

        var bestMatch: ScoredSearchResult? = null
        var bestMatchDetail: MatchScoreDetail? = null
        val allScoredResults = mutableListOf<ScoredSearchResult>()

        searchLoop@ for ((queryIndex, query) in queries.withIndex()) {
            val searchTasks = orderedSources.map { source ->
                coroutineScope {
                    async(Dispatchers.IO) {
                        try {
                            val results = source.searchSongs(
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
            onProgress(0.05f + 0.45f * (queryIndex + 1) / queries.size.coerceAtLeast(1).toFloat())

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
                    break@searchLoop
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
        onProgress(0.55f)

        val newLyrics = if (plan.shouldFetchLyrics && lyricConfig != null) try {
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
        } else {
            null
        }
        onProgress(0.75f)
        val newTitle = resolveValue(plan, BatchMatchField.TITLE, finalMatch.result.title)
        val newArtist = resolveValue(plan, BatchMatchField.ARTIST, finalMatch.result.artist)
        val newAlbum = resolveValue(plan, BatchMatchField.ALBUM, finalMatch.result.album)
        val newDate = resolveValue(plan, BatchMatchField.DATE, finalMatch.result.date)
        val newTrack = resolveValue(plan, BatchMatchField.TRACK_NUMBER, finalMatch.result.trackNumber)
        val newGenre = resolveValue(plan, BatchMatchField.GENRE, null)
        val newLyricsResolved = resolveValue(plan, BatchMatchField.LYRICS, newLyrics)
        val newComment = resolveValue(plan, BatchMatchField.COMMENT,
            finalMatch.result.normalizedFields()["subtitle"]
        )
        val picUrl = if (plan.shouldUpdateCover) finalMatch.result.picUrl else null

        val standardTagData = AudioTagData(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            genre = newGenre,
            date = newDate,
            trackNumber = newTrack,
            lyrics = newLyricsResolved,
            picUrl = picUrl,
            comment = newComment,
        )
        val metadataTagData = metadataFieldResolver.resolve(
            currentSong = song,
            scoredResults = allScoredResults,
            rules = plan.metadataRules
        )
        val tagDataToWrite = metadataFieldResolver.mergeNonNull(standardTagData, metadataTagData)

        val isEffectivelyEmpty = newTitle == null && newArtist == null && newAlbum == null &&
                newGenre == null && newDate == null && newTrack == null &&
                newLyricsResolved == null && picUrl == null && newComment == null && metadataTagData.isEmpty()

        if (isEffectivelyEmpty) {
            throw BatchTaskSkippedException("No fields to update")
        }

        onProgress(0.9f)
        val success = songRepository.patchAudioTags(song.uri, tagDataToWrite)
        if (!success) {
            throw Exception("Write failed")
        }
        onProgress(1f)

        return BatchTaskProcessResult()
    }

    private suspend fun buildPlan(
        matchConfig: BatchMatchConfig,
        metadataRules: List<MetadataFieldWriteRule>,
        song: SongEntity,
        sources: List<SearchSource>
    ): MatchMetadataPlan {
        val standardFields = matchConfig.fields.mapNotNull { (field, mode) ->
            if (shouldUpdateField(field, mode, song)) field else null
        }.toSet()
        val applicableMetadataRules = MetadataFieldWriteRuleFactory.mergeWithDeclaredFields(metadataRules, sources)
            .filter { shouldApplyMetadataRule(it, song) }

        return MatchMetadataPlan(
            standardFields = standardFields,
            metadataRules = applicableMetadataRules
        )
    }

    private suspend fun shouldUpdateField(
        field: BatchMatchField,
        mode: BatchMatchMode,
        song: SongEntity
    ): Boolean {
        if (mode == BatchMatchMode.OVERWRITE) return true
        return when (field) {
            BatchMatchField.TITLE -> song.title.isNullOrBlank()
            BatchMatchField.ARTIST -> song.artist.isNullOrBlank()
            BatchMatchField.ALBUM -> song.album.isNullOrBlank()
            BatchMatchField.GENRE -> song.genre.isNullOrBlank()
            BatchMatchField.DATE -> song.date.isNullOrBlank()
            BatchMatchField.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
            BatchMatchField.LYRICS -> song.lyrics.isNullOrBlank()
            BatchMatchField.COMMENT -> song.comment.isNullOrBlank()
            BatchMatchField.COVER -> !hasEmbeddedCover(song)
        }
    }

    private suspend fun hasEmbeddedCover(song: SongEntity): Boolean {
        return runCatching {
            songRepository.readAudioTagData(song.uri).pictures.isNotEmpty()
        }.getOrDefault(false)
    }

    private suspend fun shouldApplyMetadataRule(
        rule: MetadataFieldWriteRule,
        song: SongEntity
    ): Boolean {
        if (rule.mode == com.lonx.lyrico.data.model.MetadataWriteMode.DISABLED) return false
        if (rule.mode == com.lonx.lyrico.data.model.MetadataWriteMode.OVERWRITE) return true

        return when (rule.target) {
            com.lonx.lyrico.data.model.MetadataFieldTarget.TITLE -> song.title.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.ARTIST -> song.artist.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.ALBUM -> song.album.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.ALBUM_ARTIST -> song.albumArtist.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.GENRE -> song.genre.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.DATE -> song.date.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.DISC_NUMBER -> song.discNumber == null
            com.lonx.lyrico.data.model.MetadataFieldTarget.COMPOSER -> song.composer.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.LYRICIST -> song.lyricist.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.COMMENT -> song.comment.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.LYRICS -> song.lyrics.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.COVER -> !hasEmbeddedCover(song)
            com.lonx.lyrico.data.model.MetadataFieldTarget.LANGUAGE -> true
            com.lonx.lyrico.data.model.MetadataFieldTarget.COPYRIGHT -> true
            com.lonx.lyrico.data.model.MetadataFieldTarget.RATING -> true
            com.lonx.lyrico.data.model.MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> song.replayGainTrackGain.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> song.replayGainTrackPeak.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN -> true
            com.lonx.lyrico.data.model.MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK -> true
            com.lonx.lyrico.data.model.MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS -> song.replayGainReferenceLoudness.isNullOrBlank()
            com.lonx.lyrico.data.model.MetadataFieldTarget.CUSTOM -> true
        }
    }

    private fun resolveValue(
        plan: MatchMetadataPlan,
        field: BatchMatchField,
        newValue: String?
    ): String? {
        return if (field in plan.standardFields) newValue else null
    }

    private fun AudioTagData.isEmpty(): Boolean {
        return title == null && artist == null && album == null && genre == null &&
                albumArtist == null && date == null && trackNumber == null &&
                discNumber == null && composer == null && lyricist == null &&
                lyrics == null && picUrl == null && comment == null &&
                replayGainTrackGain == null && replayGainTrackPeak == null &&
                replayGainAlbumGain == null && replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }
}

private data class MatchMetadataPlan(
    val standardFields: Set<BatchMatchField>,
    val metadataRules: List<MetadataFieldWriteRule>
) {
    val requiresSearch: Boolean
        get() = standardFields.isNotEmpty() || metadataRules.isNotEmpty()

    val shouldFetchLyrics: Boolean
        get() = BatchMatchField.LYRICS in standardFields

    val shouldUpdateCover: Boolean
        get() = BatchMatchField.COVER in standardFields
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val metadataFieldWriteRules: List<MetadataFieldWriteRule> = emptyList(),
    val sourceSettings: Map<String, Map<String, String>> = emptyMap(),
    val concurrency: Int = 3
)
