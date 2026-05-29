package com.lonx.lyrico.worker.processor

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.BatchMatchConfig
import com.lonx.lyrico.data.model.ScoredSearchResult
import com.lonx.lyrico.data.model.entity.BatchTaskEntity
import com.lonx.lyrico.data.model.entity.BatchTaskItemEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.lyrics.LyricRenderConfig
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import com.lonx.lyrico.data.model.plugin.GlobalFieldProcessSettings
import com.lonx.lyrico.data.model.plugin.PluginFieldProcessConfig
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldTarget
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRule
import com.lonx.lyrico.data.model.plugin.PluginMetadataFieldWriteRuleFactory
import com.lonx.lyrico.data.model.plugin.PluginMetadataWriteMode
import com.lonx.lyrico.data.model.plugin.defaultPluginFieldProcessConfig
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.domain.song.usecase.PatchSongTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.MatchScoreDetail
import com.lonx.lyrico.utils.MetadataFieldResolver
import com.lonx.lyrico.utils.MusicMatchUtils
import com.lonx.lyrico.utils.PluginFieldPostProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchMetadataProcessor(
    private val audioTagRepository: AudioTagRepository,
    private val patchSongTagsUseCase: PatchSongTagsUseCase,
    private val songLibraryRepository: SongLibraryRepository,
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

        val song = songLibraryRepository.getSongByUri(item.songUri)
            ?: throw BatchTaskSkippedException("Song not found")

        val plan = buildPlan(
            matchConfig = matchConfig,
            metadataRules = config.metadataFieldWriteRules,
            sources = sources
        )

        if (!plan.requiresSearch) {
            throw BatchTaskSkippedException("No fields need processing")
        }

        onProgress(0.05f)

        val separator = config.separator

        val shouldWriteLyrics = shouldWriteTarget(
            plan = plan,
            target = PluginMetadataFieldTarget.LYRICS,
            song = song
        )

        val lyricConfig = if (shouldWriteLyrics) {
            config.lyricRenderConfig ?: settingsRepository.getLyricRenderConfig()
        } else {
            null
        }

        val fieldProcessor = PluginFieldPostProcessor(
            GlobalFieldProcessSettings(
                scriptConversion = lyricConfig?.conversionMode
                    ?: settingsRepository.conversionMode.first(),
                removeEmptyLines = lyricConfig?.removeEmptyLines
                    ?: settingsRepository.removeEmptyLines.first()
            )
        )

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
                enabledSourceOrder.indexOf(source.id).let { index ->
                    if (index == -1) Int.MAX_VALUE else index
                }
            }

        var bestMatch: ScoredSearchResult? = null
        var bestMatchDetail: MatchScoreDetail? = null
        val allScoredResults = mutableListOf<ScoredSearchResult>()

        searchLoop@ for ((queryIndex, query) in queries.withIndex()) {
            var queryBest: ScoredSearchResult? = null
            var queryBestDetail: MatchScoreDetail? = null

            for ((sourceIndex, source) in orderedSources.withIndex()) {
                val sourceResults = try {
                    source.searchSongs(
                        keyword = query,
                        separator = separator,
                        pageSize = 2
                    ).mapIndexed { index, result ->
                        val detail = MusicMatchUtils.calculateMatchScoreDetail(
                            result = result,
                            song = song,
                            preferFileName = matchConfig.preferFileName,
                            rankIndex = index
                        )

                        ScoredSearchResult(
                            result = result,
                            score = detail.finalScore,
                            source = source
                        ) to detail
                    }
                } catch (_: Exception) {
                    emptyList()
                }

                allScoredResults += sourceResults.map { (scoredResult, _) ->
                    scoredResult
                }

                val sourceBest = sourceResults.maxByOrNull { (_, detail) ->
                    detail.finalScore
                }

                if (sourceBest != null) {
                    val currentScoredResult = sourceBest.first
                    val currentDetail = sourceBest.second

                    if (
                        queryBest == null ||
                        currentDetail.finalScore > (queryBestDetail?.finalScore ?: 0.0)
                    ) {
                        queryBest = currentScoredResult
                        queryBestDetail = currentDetail
                    }

                    if (
                        bestMatch == null ||
                        currentDetail.finalScore > (bestMatchDetail?.finalScore ?: 0.0)
                    ) {
                        bestMatch = currentScoredResult
                        bestMatchDetail = currentDetail
                    }

                    if (
                        currentDetail.finalScore >= 0.92 &&
                        currentDetail.textScore >= 0.86
                    ) {
                        bestMatch = currentScoredResult
                        bestMatchDetail = currentDetail
                        break@searchLoop
                    }
                }

                val sourceCount = orderedSources.size.coerceAtLeast(1)
                val totalSteps = queries.size.coerceAtLeast(1) * sourceCount
                val currentStep = queryIndex * sourceCount + sourceIndex + 1
                onProgress(0.05f + 0.45f * currentStep / totalSteps.toFloat())
            }

            if (
                queryBest != null &&
                queryBestDetail != null &&
                (
                        bestMatch == null ||
                                queryBestDetail.finalScore > (bestMatchDetail?.finalScore ?: 0.0)
                        )
            ) {
                bestMatch = queryBest
                bestMatchDetail = queryBestDetail
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

        val newLyrics = if (shouldWriteLyrics && lyricConfig != null) {
            try {
                coroutineScope {
                    val deferred = async(Dispatchers.Default) {
                        finalMatch.source?.getLyrics(finalMatch.result)?.let { result ->
                            val sourceId = finalMatch.source.id
                            val processConfig = config.pluginFieldProcessConfigs[sourceId]
                                ?: defaultPluginFieldProcessConfig(sourceId)

                            val processed = fieldProcessor.processLyrics(
                                lyrics = result,
                                config = processConfig
                            )

                            LyricEncoder.encode(
                                result = processed,
                                config = lyricConfig.copy(
                                    conversionMode = com.lonx.lyrico.data.model.ConversionMode.NONE
                                )
                            )
                        }
                    }
                    deferred.await()
                }
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        onProgress(0.75f)

        val processedFields = finalMatch.source?.let { source ->
            val processConfig = config.pluginFieldProcessConfigs[source.id]
                ?: defaultPluginFieldProcessConfig(source.id)

            fieldProcessor.processFields(
                pluginId = source.id,
                fields = finalMatch.result.normalizedFields(),
                config = processConfig,
                fieldDefinitions = source.metadataFields,
                writeRules = plan.metadataRules
            )
        }.orEmpty()

        val normalizedFields = finalMatch.result.normalizedFields()

        val standardTagData = buildStandardTagData(
            plan = plan,
            song = song,
            processedFields = processedFields,
            normalizedFields = normalizedFields,
            finalMatch = finalMatch,
            newLyrics = newLyrics
        )

        val metadataTagData = metadataFieldResolver.resolve(
            currentSong = song,
            scoredResults = allScoredResults.map { scoredResult ->
                val source = scoredResult.source ?: return@map scoredResult
                val processConfig = config.pluginFieldProcessConfigs[source.id]
                    ?: defaultPluginFieldProcessConfig(source.id)

                scoredResult.copy(
                    result = scoredResult.result.copy(
                        fields = fieldProcessor.processFields(
                            pluginId = source.id,
                            fields = scoredResult.result.normalizedFields(),
                            config = processConfig,
                            fieldDefinitions = source.metadataFields,
                            writeRules = plan.metadataRules
                        )
                    )
                )
            },
            rules = plan.metadataRules
        )

        val tagDataToWrite = metadataFieldResolver.mergeNonNull(
            metadataTagData,
            standardTagData
        )

        if (standardTagData.isEmpty() && metadataTagData.isEmpty()) {
            throw BatchTaskSkippedException("No fields to update")
        }

        onProgress(0.9f)

        val result = patchSongTagsUseCase(song.uri, tagDataToWrite)
        if (result !is SaveAudioTagsResult.Success) {
            throw Exception("Write failed")
        }

        onProgress(1f)

        return BatchTaskProcessResult()
    }

    private fun buildPlan(
        matchConfig: BatchMatchConfig,
        metadataRules: List<PluginMetadataFieldWriteRule>,
        sources: List<SearchSource>
    ): MatchMetadataPlan {
        val enabledTargetModes = matchConfig.targetModes
            .filterValues { mode ->
                mode != PluginMetadataWriteMode.DISABLED
            }

        val applicableMetadataRules = PluginMetadataFieldWriteRuleFactory
            .mergeWithDeclaredFields(
                savedRules = metadataRules,
                searchSources = sources
            )
            .filter { rule ->
                rule.mode != PluginMetadataWriteMode.DISABLED
            }
            .mapNotNull { rule ->
                val batchMode = enabledTargetModes[rule.target]
                    ?: return@mapNotNull null

                rule.copy(mode = batchMode)
            }

        return MatchMetadataPlan(
            targetModes = enabledTargetModes,
            metadataRules = applicableMetadataRules
        )
    }

    private suspend fun shouldWriteTarget(
        plan: MatchMetadataPlan,
        target: PluginMetadataFieldTarget,
        song: SongEntity
    ): Boolean {
        return when (plan.targetModes[target]) {
            PluginMetadataWriteMode.OVERWRITE -> true
            PluginMetadataWriteMode.SUPPLEMENT -> isTargetBlank(target, song)
            PluginMetadataWriteMode.DISABLED,
            null -> false
        }
    }

    private suspend fun isTargetBlank(
        target: PluginMetadataFieldTarget,
        song: SongEntity
    ): Boolean {
        return when (target) {
            PluginMetadataFieldTarget.TITLE -> song.title.isNullOrBlank()
            PluginMetadataFieldTarget.ARTIST -> song.artist.isNullOrBlank()
            PluginMetadataFieldTarget.ALBUM -> song.album.isNullOrBlank()
            PluginMetadataFieldTarget.ALBUM_ARTIST -> song.albumArtist.isNullOrBlank()
            PluginMetadataFieldTarget.GENRE -> song.genre.isNullOrBlank()
            PluginMetadataFieldTarget.DATE -> song.date.isNullOrBlank()
            PluginMetadataFieldTarget.TRACK_NUMBER -> song.trackerNumber.isNullOrBlank()
            PluginMetadataFieldTarget.DISC_NUMBER -> song.discNumber == null
            PluginMetadataFieldTarget.COMPOSER -> song.composer.isNullOrBlank()
            PluginMetadataFieldTarget.LYRICIST -> song.lyricist.isNullOrBlank()
            PluginMetadataFieldTarget.COMMENT -> song.comment.isNullOrBlank()
            PluginMetadataFieldTarget.LYRICS -> song.lyrics.isNullOrBlank()
            PluginMetadataFieldTarget.COVER -> !hasEmbeddedCover(song)
            PluginMetadataFieldTarget.LANGUAGE -> true
            PluginMetadataFieldTarget.COPYRIGHT -> true
            PluginMetadataFieldTarget.RATING -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN -> song.replayGainTrackGain.isNullOrBlank()
            PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK -> song.replayGainTrackPeak.isNullOrBlank()
            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK -> true
            PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS ->
                song.replayGainReferenceLoudness.isNullOrBlank()
            PluginMetadataFieldTarget.CUSTOM -> true
        }
    }

    private suspend fun hasEmbeddedCover(song: SongEntity): Boolean {
        return runCatching {
            audioTagRepository.read(song.uri).pictures.isNotEmpty()
        }.getOrDefault(false)
    }

    private suspend fun buildStandardTagData(
        plan: MatchMetadataPlan,
        song: SongEntity,
        processedFields: Map<String, String>,
        normalizedFields: Map<String, String>,
        finalMatch: ScoredSearchResult,
        newLyrics: String?
    ): AudioTagData {
        fun field(vararg keys: String): String? {
            for (key in keys) {
                processedFields[key]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }

                normalizedFields[key]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }

            return null
        }

        suspend fun writable(target: PluginMetadataFieldTarget): Boolean {
            return shouldWriteTarget(
                plan = plan,
                target = target,
                song = song
            )
        }

        return AudioTagData(
            title = if (writable(PluginMetadataFieldTarget.TITLE)) {
                field("title") ?: finalMatch.result.title.takeIf { it.isNotBlank() }
            } else {
                null
            },
            artist = if (writable(PluginMetadataFieldTarget.ARTIST)) {
                field("artist", "artists") ?: finalMatch.result.artist.takeIf { it.isNotBlank() }
            } else {
                null
            },
            album = if (writable(PluginMetadataFieldTarget.ALBUM)) {
                field("album") ?: finalMatch.result.album.takeIf { it.isNotBlank() }
            } else {
                null
            },
            albumArtist = if (writable(PluginMetadataFieldTarget.ALBUM_ARTIST)) {
                field("album_artist", "albumArtist", "albumartist")
            } else {
                null
            },
            genre = if (writable(PluginMetadataFieldTarget.GENRE)) {
                field("genre")
            } else {
                null
            },
            date = if (writable(PluginMetadataFieldTarget.DATE)) {
                field("date", "year", "release_date", "releaseDate")
                    ?: finalMatch.result.date.takeIf { it.isNotBlank() }
            } else {
                null
            },
            trackNumber = if (writable(PluginMetadataFieldTarget.TRACK_NUMBER)) {
                field("track_number", "trackNumber", "track")
                    ?: finalMatch.result.trackNumber.takeIf { it.isNotBlank() }
            } else {
                null
            },
            discNumber = if (writable(PluginMetadataFieldTarget.DISC_NUMBER)) {
                field("disc_number", "discNumber", "disc")?.toInt()
            } else {
                null
            },
            composer = if (writable(PluginMetadataFieldTarget.COMPOSER)) {
                field("composer")
            } else {
                null
            },
            lyricist = if (writable(PluginMetadataFieldTarget.LYRICIST)) {
                field("lyricist", "writer", "author")
            } else {
                null
            },
            lyrics = if (writable(PluginMetadataFieldTarget.LYRICS)) {
                newLyrics
            } else {
                null
            },
            picUrl = if (writable(PluginMetadataFieldTarget.COVER)) {
                finalMatch.result.picUrl.takeIf { it.isNotBlank() }
                    ?: field("cover_url", "picUrl", "cover", "artwork")
            } else {
                null
            },
            comment = if (writable(PluginMetadataFieldTarget.COMMENT)) {
                field("comment", "subtitle", "description")
            } else {
                null
            },
            replayGainTrackGain = if (writable(PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN)) {
                field(
                    "replaygain_track_gain",
                    "replay_gain_track_gain",
                    "track_gain"
                )
            } else {
                null
            },
            replayGainTrackPeak = if (writable(PluginMetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK)) {
                field(
                    "replaygain_track_peak",
                    "replay_gain_track_peak",
                    "track_peak"
                )
            } else {
                null
            },
            replayGainAlbumGain = if (writable(PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN)) {
                field(
                    "replaygain_album_gain",
                    "replay_gain_album_gain",
                    "album_gain"
                )
            } else {
                null
            },
            replayGainAlbumPeak = if (writable(PluginMetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK)) {
                field(
                    "replaygain_album_peak",
                    "replay_gain_album_peak",
                    "album_peak"
                )
            } else {
                null
            },
            replayGainReferenceLoudness = if (
                writable(PluginMetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS)
            ) {
                field(
                    "replaygain_reference_loudness",
                    "replay_gain_reference_loudness",
                    "reference_loudness"
                )
            } else {
                null
            }
        )
    }

    private fun AudioTagData.isEmpty(): Boolean {
        return title == null &&
                artist == null &&
                album == null &&
                genre == null &&
                albumArtist == null &&
                date == null &&
                trackNumber == null &&
                discNumber == null &&
                composer == null &&
                lyricist == null &&
                lyrics == null &&
                picUrl == null &&
                comment == null &&
                replayGainTrackGain == null &&
                replayGainTrackPeak == null &&
                replayGainAlbumGain == null &&
                replayGainAlbumPeak == null &&
                replayGainReferenceLoudness == null
    }
}

private data class MatchMetadataPlan(
    val targetModes: Map<PluginMetadataFieldTarget, PluginMetadataWriteMode>,
    val metadataRules: List<PluginMetadataFieldWriteRule>
) {
    val requiresSearch: Boolean
        get() = targetModes.isNotEmpty() || metadataRules.isNotEmpty()
}

@Serializable
data class MatchMetadataTaskConfig(
    val matchConfig: BatchMatchConfig,
    val separator: String,
    val enabledSourceOrderIds: List<String>,
    val metadataFieldWriteRules: List<PluginMetadataFieldWriteRule> = emptyList(),
    val sourceSettings: Map<String, Map<String, String>> = emptyMap(),
    val pluginFieldProcessConfigs: Map<String, PluginFieldProcessConfig> = emptyMap(),
    val lyricRenderConfig: LyricRenderConfig? = null,
    val concurrency: Int = 3
)