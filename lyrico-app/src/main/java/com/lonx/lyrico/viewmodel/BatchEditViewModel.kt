package com.lonx.lyrico.viewmodel

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.CustomTagField
import com.lonx.audiotag.model.frontCoverOrFallback
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.model.metadata.MetadataFieldTarget
import com.lonx.lyrico.data.editfield.CustomTagKey
import com.lonx.lyrico.data.editfield.EditFieldConfigRepository
import com.lonx.lyrico.data.editfield.EditFieldDefinition
import com.lonx.lyrico.data.editfield.EditFieldScene
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.search.SongSearchRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.worker.BatchTaskScheduler
import com.lonx.lyrico.worker.processor.EditTagsCustomField
import com.lonx.lyrico.worker.processor.EditTagsTaskConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * 可批量编辑的标签字段枚举
 */
enum class BatchEditField(val target: MetadataFieldTarget) {
    TITLE(MetadataFieldTarget.TITLE),
    ARTIST(MetadataFieldTarget.ARTIST),
    ALBUM_ARTIST(MetadataFieldTarget.ALBUM_ARTIST),
    ALBUM(MetadataFieldTarget.ALBUM),
    DATE(MetadataFieldTarget.DATE),
    LANGUAGE(MetadataFieldTarget.LANGUAGE),
    GENRE(MetadataFieldTarget.GENRE),
    TRACK_NUMBER(MetadataFieldTarget.TRACK_NUMBER),
    DISC_NUMBER(MetadataFieldTarget.DISC_NUMBER),
    COMPOSER(MetadataFieldTarget.COMPOSER),
    LYRICIST(MetadataFieldTarget.LYRICIST),
    COPYRIGHT(MetadataFieldTarget.COPYRIGHT),
    COMMENT(MetadataFieldTarget.COMMENT),
    LYRICS(MetadataFieldTarget.LYRICS),
    REPLAY_GAIN_TRACK_GAIN(MetadataFieldTarget.REPLAY_GAIN_TRACK_GAIN),
    REPLAY_GAIN_TRACK_PEAK(MetadataFieldTarget.REPLAY_GAIN_TRACK_PEAK),
    REPLAY_GAIN_ALBUM_GAIN(MetadataFieldTarget.REPLAY_GAIN_ALBUM_GAIN),
    REPLAY_GAIN_ALBUM_PEAK(MetadataFieldTarget.REPLAY_GAIN_ALBUM_PEAK),
    REPLAY_GAIN_REFERENCE_LOUDNESS(MetadataFieldTarget.REPLAY_GAIN_REFERENCE_LOUDNESS),
    COVER(MetadataFieldTarget.COVER),
    RATING(MetadataFieldTarget.RATING);

    val labelResId: Int get() = target.labelRes
}

data class BatchEditUiState(
    val songCount: Int = 0,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Int = 0,
    val saveTotal: Int = 0,
    val saveSuccess: Boolean? = null,
    val saveResultMessage: UiMessage? = null,
    val errorMessage: UiMessage? = null,

    /** 各字段当前编辑值（"<keep>"表示不修改） */
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

    /** 封面相关 */
    val coverUri: Any? = null,
    val removeCover: Boolean = false,

    /** 歌词偏移（毫秒），0 表示不偏移 */
    val lyricsOffset: Long = 0L,

    /** 回放增益（"<keep>"表示不修改，""表示清除） */
    val replayGainTrackGain: String = "<keep>",
    val replayGainTrackPeak: String = "<keep>",
    val replayGainAlbumGain: String = "<keep>",
    val replayGainAlbumPeak: String = "<keep>",
    val replayGainReferenceLoudness: String = "<keep>",

    /** 自定义标签 */
    val customFields: List<CustomTagField> = emptyList(),

    /** 保存进度显示相关字段 */
    val saveProgressBottomSheet: Boolean = false,  // 是否显示保存进度对话框
    val currentFile: String = "",  // 当前处理的文件名
    val successCount: Int = 0,  // 成功计数
    val skippedCount: Int = 0,  // 跳过计数
    val failureCount: Int = 0,  // 失败计数
    val saveTimeMillis: Long = 0,  // 保存总用时（毫秒）
    val selectedSongsVersion: Int = 0,
    val customTagPreviewVersion: Int = 0
) {
    /** 传给批处理任务的偏移值：0 表示不偏移（空串），任务配置沿用字符串以兼容历史任务。 */
    val lyricsOffsetForTask: String
        get() = lyricsOffset.takeIf { it != 0L }?.toString().orEmpty()
}

data class BatchEditPreview(
    val songUri: String,
    val fileName: String,
    val changes: List<BatchEditPreviewChange>
)

data class BatchEditPreviewChange(
    val labelResId: Int?,
    val customLabel: String?,
    val oldValue: String,
    val newValue: String
)

data class BatchEditSelectableValue(
    val title: String,
    val summary: String,
    val value: String,
    val sourceUri: String
)

data class BatchEditSelectableCover(
    val title: String,
    val summary: String,
    val sourceUri: String,
    val previewUri: String,
    val fileLastModified: Long
)

class BatchEditViewModel(
    private val songLibraryRepository: SongLibraryRepository,
    private val songSearchRepository: SongSearchRepository,
    private val audioTagRepository: AudioTagRepository,
    private val selectionManager: SharedSelectionManager,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler,
    private val editFieldConfigRepository: EditFieldConfigRepository,
) : ViewModel() {

    private val TAG = "BatchEditVM"
    private var saveJob: Job? = null
    private var observeJob: Job? = null
    private var currentTaskId: String? = null
    private var customTagPreviewValues: Map<String, Map<String, String>> = emptyMap()

    private val _uiState = MutableStateFlow(BatchEditUiState())
    val uiState: StateFlow<BatchEditUiState> = _uiState.asStateFlow()

    /** 批量编辑页按配置排序后的可见字段。 */
    val visibleFields: StateFlow<List<EditFieldDefinition>> =
        editFieldConfigRepository.configFlow
            .map { config ->
                config.visibleFieldsForScene(EditFieldScene.BatchEdit)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    /** 保存选中的文件uri */
    private var selectedUris: List<String> = emptyList()
    private var selectedSongs: List<SongEntity> = emptyList()

    init {
        val uris = selectionManager.selectedUris.value.toList()
        selectedUris = uris
        _uiState.update { it.copy(songCount = uris.size) }
        viewModelScope.launch(Dispatchers.IO) {
            selectedSongs = uris.mapNotNull { uri ->
                songLibraryRepository.getSongByUri(uri)
            }
            _uiState.update { it.copy(selectedSongsVersion = it.selectedSongsVersion + 1) }
        }
        viewModelScope.launch {
            val runningTask = batchTaskRepository.getRunningTaskByType(BatchTaskType.EDIT_TAGS)
            if (runningTask != null) {
                resumeObservingTask(runningTask.taskId)
            }
        }
    }


    // ── 标签值更新 ──────────────────────────────────────────

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value) }
    }

    fun updateArtist(value: String) {
        _uiState.update { it.copy(artist = value) }
    }

    fun updateAlbumArtist(value: String) {
        _uiState.update { it.copy(albumArtist = value) }
    }

    fun updateAlbum(value: String) {
        _uiState.update { it.copy(album = value) }
    }

    fun updateDate(value: String) {
        _uiState.update { it.copy(date = value) }
    }

    fun updateLanguage(value: String) {
        _uiState.update { it.copy(language = value) }
    }

    fun updateGenre(value: String) {
        _uiState.update { it.copy(genre = value) }
    }

    fun updateTrackNumber(value: String) {
        _uiState.update { it.copy(trackNumber = value) }
    }

    fun updateDiscNumber(value: String) {
        _uiState.update { it.copy(discNumber = value) }
    }

    fun updateComposer(value: String) {
        _uiState.update { it.copy(composer = value) }
    }

    fun updateLyricist(value: String) {
        _uiState.update { it.copy(lyricist = value) }
    }

    fun updateCopyright(value: String) {
        _uiState.update { it.copy(copyright = value) }
    }

    fun updateComment(value: String) {
        _uiState.update { it.copy(comment = value) }
    }

    fun updateLyrics(value: String) {
        _uiState.update { it.copy(lyrics = value) }
    }

    fun updateRating(value: Int) {
        _uiState.update { it.copy(rating = value, ratingModified = true) }
    }

    fun resetRating() {
        _uiState.update { it.copy(rating = 0, ratingModified = false) }
    }

    // ── 歌词偏移 ──────────────────────────────────────────

    fun updateLyricsOffset(value: Long) {
        _uiState.update { it.copy(lyricsOffset = value) }
    }

    // ── 回放增益 ──────────────────────────────────────────

    fun updateReplayGainTrackGain(value: String) {
        _uiState.update { it.copy(replayGainTrackGain = value) }
    }

    fun updateReplayGainTrackPeak(value: String) {
        _uiState.update { it.copy(replayGainTrackPeak = value) }
    }

    fun updateReplayGainAlbumGain(value: String) {
        _uiState.update { it.copy(replayGainAlbumGain = value) }
    }

    fun updateReplayGainAlbumPeak(value: String) {
        _uiState.update { it.copy(replayGainAlbumPeak = value) }
    }

    fun updateReplayGainReferenceLoudness(value: String) {
        _uiState.update { it.copy(replayGainReferenceLoudness = value) }
    }

    // ── 自定义标签 ──────────────────────────────────────────

    fun setCustomFieldValue(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        _uiState.update { state ->
            val nextFields = state.customFields
                .filterNot { it.key.equals(normalizedKey, ignoreCase = true) }
                .toMutableList()

            nextFields += CustomTagField(normalizedKey, value)

            state.copy(
                customFields = nextFields,
            )
        }
    }

    fun keepCustomField(key: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        _uiState.update { state ->
            state.copy(
                customFields = state.customFields
                    .filterNot { it.key.equals(normalizedKey, ignoreCase = true) },
            )
        }
    }

    fun addCustomFieldAndShow(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        viewModelScope.launch {
            editFieldConfigRepository.addCustomTag(normalizedKey)
        }

        setCustomFieldValue(normalizedKey, value)
    }

    private fun normalizeCustomTagKey(input: String): String? = CustomTagKey.normalize(input)

    // ── 封面管理 ──────────────────────────────────────────

    fun updateCover(uri: Uri) {
        _uiState.update { it.copy(coverUri = uri, removeCover = false) }
    }

    fun removeCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = true) }
    }

    fun revertCover() {
        _uiState.update { it.copy(coverUri = null, removeCover = false) }
    }

    suspend fun getSelectedSongFieldValues(field: BatchEditField): List<BatchEditSelectableValue> =
        withContext(Dispatchers.IO) {
            val fieldColumn = field.databaseColumnName() ?: return@withContext emptyList()
            songSearchRepository.getDistinctSongFieldValues(selectedUris, fieldColumn)
                .map { fieldValue ->
                    BatchEditSelectableValue(
                        title = fieldValue.value,
                        summary = if (fieldValue.value.length > 80)
                            fieldValue.value.take(80) + "..."
                        else
                            fieldValue.value,
                        value = fieldValue.value,
                        sourceUri = fieldValue.sourceUri
                    )
                }
        }

    suspend fun getSelectedSongCustomTagValues(key: String): List<BatchEditSelectableValue> =
        withContext(Dispatchers.IO) {
            val normalizedKey = normalizeCustomTagKey(key) ?: return@withContext emptyList()
            ensureSelectedSongsLoaded()

            selectedSongs.mapNotNull { song ->
                val value = try {
                    audioTagRepository.read(song.uri)
                        .customFields
                        .firstOrNull { field ->
                            normalizeCustomTagKey(field.key) == normalizedKey
                        }
                        ?.value
                } catch (e: Exception) {
                    Log.e(TAG, "读取已选歌曲自定义标签失败: ${song.uri}", e)
                    null
                }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

                BatchEditSelectableValue(
                    title = value,
                    summary = song.title?.takeIf { it.isNotBlank() } ?: song.fileName,
                    value = value,
                    sourceUri = song.uri
                )
            }.distinctBy { it.value }
        }

    private fun BatchEditField.databaseColumnName(): String? = when (this) {
        BatchEditField.TITLE -> "title"
        BatchEditField.ARTIST -> "artist"
        BatchEditField.ALBUM_ARTIST -> "albumArtist"
        BatchEditField.ALBUM -> "album"
        BatchEditField.DATE -> "date"
        BatchEditField.LANGUAGE -> "language"
        BatchEditField.GENRE -> "genre"
        BatchEditField.TRACK_NUMBER -> "trackerNumber"
        BatchEditField.DISC_NUMBER -> "discNumber"
        BatchEditField.COMPOSER -> "composer"
        BatchEditField.LYRICIST -> "lyricist"
        BatchEditField.COPYRIGHT -> "copyright"
        BatchEditField.COMMENT -> "comment"
        BatchEditField.LYRICS -> "lyrics"
        BatchEditField.REPLAY_GAIN_TRACK_GAIN -> "replayGainTrackGain"
        BatchEditField.REPLAY_GAIN_TRACK_PEAK -> "replayGainTrackPeak"
        BatchEditField.REPLAY_GAIN_ALBUM_GAIN -> "replayGainAlbumGain"
        BatchEditField.REPLAY_GAIN_ALBUM_PEAK -> "replayGainAlbumPeak"
        BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS -> "replayGainReferenceLoudness"
        BatchEditField.COVER,
        BatchEditField.RATING -> null
    }

    suspend fun getSelectedSongCovers(): List<BatchEditSelectableCover> =
        withContext(Dispatchers.IO) {
            ensureSelectedSongsLoaded()
            selectedSongs.map { song ->
                BatchEditSelectableCover(
                    title = song.title?.takeIf { title -> title.isNotBlank() } ?: song.fileName,
                    summary = song.artist.orEmpty(),
                    sourceUri = song.uri,
                    previewUri = song.getUri.toString(),
                    fileLastModified = song.fileLastModified
                )
            }
        }

    suspend fun getSelectedSongCover(uri: String): Any? =
        withContext(Dispatchers.IO) {
            try {
                val tagData = audioTagRepository.read(uri)
                tagData.pictures.frontCoverOrFallback()?.data
            } catch (e: Exception) {
                Log.e(TAG, "读取已选歌曲封面失败: $uri", e)
                null
            }
        }

    fun loadCustomTagPreviewValues(keys: List<String>) {
        val normalizedKeys = keys.mapNotNull { normalizeCustomTagKey(it) }.toSet()
        if (normalizedKeys.isEmpty() || selectedUris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            ensureSelectedSongsLoaded()
            val values = selectedSongs.associate { song ->
                val customValues = try {
                    audioTagRepository.read(song.uri)
                        .customFields
                        .mapNotNull { field ->
                            val key = normalizeCustomTagKey(field.key) ?: return@mapNotNull null
                            if (key in normalizedKeys) key to field.value else null
                        }
                        .toMap()
                } catch (e: Exception) {
                    Log.e(TAG, "读取自定义标签预览失败: ${song.uri}", e)
                    emptyMap()
                }
                song.uri to customValues
            }

            customTagPreviewValues = values
            _uiState.update {
                it.copy(customTagPreviewVersion = it.customTagPreviewVersion + 1)
            }
        }
    }

    fun buildEditPreviews(visibleFieldCodes: Set<String>): List<BatchEditPreview> {
        val state = _uiState.value.filterHiddenEditFields(visibleFieldCodes)
        if (selectedSongs.isEmpty()) return emptyList()
        return selectedSongs.mapNotNull { song ->
            val changes = buildPreviewChanges(song, state, visibleFieldCodes)
            if (changes.isEmpty()) {
                null
            } else {
                BatchEditPreview(
                    songUri = song.uri,
                    fileName = song.fileName,
                    changes = changes
                )
            }
        }
    }

    private fun buildPreviewChanges(
        song: SongEntity,
        state: BatchEditUiState,
        visibleFieldCodes: Set<String>
    ): List<BatchEditPreviewChange> {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun visible(code: String): Boolean = code in visibleFieldCodes
        fun value(text: String?): String = text.orEmpty()
        fun addTextChange(
            changes: MutableList<BatchEditPreviewChange>,
            code: String,
            labelResId: Int,
            oldValue: String?,
            newValue: String
        ) {
            if (visible(code) && newValue != keep) {
                changes += BatchEditPreviewChange(
                    labelResId = labelResId,
                    customLabel = null,
                    oldValue = value(oldValue),
                    newValue = newValue
                )
            }
        }

        return buildList {
            addTextChange(this, "title", BatchEditField.TITLE.labelResId, song.title, state.title)
            addTextChange(this, "artist", BatchEditField.ARTIST.labelResId, song.artist, state.artist)
            addTextChange(this, "album_artist", BatchEditField.ALBUM_ARTIST.labelResId, song.albumArtist, state.albumArtist)
            addTextChange(this, "album", BatchEditField.ALBUM.labelResId, song.album, state.album)
            addTextChange(this, "date", BatchEditField.DATE.labelResId, song.date, state.date)
            addTextChange(this, "language", BatchEditField.LANGUAGE.labelResId, song.language, state.language)
            addTextChange(this, "genre", BatchEditField.GENRE.labelResId, song.genre, state.genre)
            addTextChange(this, "track_number", BatchEditField.TRACK_NUMBER.labelResId, song.trackerNumber, state.trackNumber)
            addTextChange(this, "disc_number", BatchEditField.DISC_NUMBER.labelResId, song.discNumber?.toString(), state.discNumber)
            addTextChange(this, "composer", BatchEditField.COMPOSER.labelResId, song.composer, state.composer)
            addTextChange(this, "lyricist", BatchEditField.LYRICIST.labelResId, song.lyricist, state.lyricist)
            addTextChange(this, "copyright", BatchEditField.COPYRIGHT.labelResId, song.copyright, state.copyright)
            addTextChange(this, "comment", BatchEditField.COMMENT.labelResId, song.comment, state.comment)
            addTextChange(this, "lyrics", BatchEditField.LYRICS.labelResId, song.lyrics, state.lyrics)
            addTextChange(this, "track_gain", BatchEditField.REPLAY_GAIN_TRACK_GAIN.labelResId, song.replayGainTrackGain, state.replayGainTrackGain)
            addTextChange(this, "track_peak", BatchEditField.REPLAY_GAIN_TRACK_PEAK.labelResId, song.replayGainTrackPeak, state.replayGainTrackPeak)
            addTextChange(this, "album_gain", BatchEditField.REPLAY_GAIN_ALBUM_GAIN.labelResId, song.replayGainAlbumGain, state.replayGainAlbumGain)
            addTextChange(this, "album_peak", BatchEditField.REPLAY_GAIN_ALBUM_PEAK.labelResId, song.replayGainAlbumPeak, state.replayGainAlbumPeak)
            addTextChange(this, "reference_loudness", BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS.labelResId, song.replayGainReferenceLoudness, state.replayGainReferenceLoudness)

            if (visible("rating") && state.ratingModified) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.RATING.labelResId,
                        customLabel = null,
                        oldValue = song.rating?.toString().orEmpty(),
                        newValue = state.rating.toString()
                    )
                )
            }

            if (visible("picture") && (state.removeCover || state.coverUri != null)) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.COVER.labelResId,
                        customLabel = null,
                        oldValue = "<current_cover>",
                        newValue = if (state.removeCover) "<remove_cover>" else state.coverUri?.toString().orEmpty()
                    )
                )
            }

            if (visible("lyrics_offset") && state.lyricsOffset != 0L && song.lyrics != null) {
                add(
                    BatchEditPreviewChange(
                        labelResId = R.string.label_lyrics_offset,
                        customLabel = null,
                        oldValue = song.lyrics,
                        newValue = LyricEncoder.shiftLyricsOffset(song.lyrics, state.lyricsOffset)
                    )
                )
            }

            state.customFields
                .filter { it.key.isNotBlank() && it.value != keep }
                .forEach { field ->
                    add(
                        BatchEditPreviewChange(
                            labelResId = null,
                            customLabel = field.key,
                            oldValue = customTagPreviewValues[song.uri]
                                ?.get(normalizeCustomTagKey(field.key))
                                .orEmpty(),
                            newValue = field.value
                        )
                    )
                }

        }
    }

    private suspend fun ensureSelectedSongsLoaded() {
        if (selectedSongs.size == selectedUris.size) return
        selectedSongs = withContext(Dispatchers.IO) {
            selectedUris.mapNotNull { uri ->
                songLibraryRepository.getSongByUri(uri)
            }
        }
        _uiState.update { it.copy(selectedSongsVersion = it.selectedSongsVersion + 1) }
    }

    // ── 批量保存 ──────────────────────────────────────────

    fun saveBatchEdit() {
        if (_uiState.value.isSaving || saveJob?.isActive == true || selectedUris.isEmpty()) return

        saveJob = viewModelScope.launch {
            val visibleFieldCodes = editFieldConfigRepository.configFlow.first()
                .visibleFieldCodesForScene(EditFieldScene.BatchEdit)
            val state = _uiState.value.filterHiddenEditFields(visibleFieldCodes)
            _uiState.update {
                state.copy(
                    isSaving = true,
                    saveProgressBottomSheet = true,
                    saveProgress = 0,
                    saveTotal = selectedUris.size,
                    currentFile = "",
                    successCount = 0,
                    skippedCount = 0,
                    failureCount = 0,
                    saveTimeMillis = 0,
                    saveSuccess = null,
                    saveResultMessage = null,
                    errorMessage = null
                )
            }

            val songs = selectedUris.mapNotNull { uri ->
                songLibraryRepository.getSongByUri(uri)
            }
            if (songs.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveProgressBottomSheet = false,
                        errorMessage = UiMessage.StringResource(R.string.no_song_selected)
                    )
                }
                return@launch
            }

            val configJson = Json.encodeToString(
                EditTagsTaskConfig.serializer(),
                state.toTaskConfig(visibleFieldCodes)
            )
            val taskId = batchTaskScheduler.schedule(
                type = BatchTaskType.EDIT_TAGS,
                songs = songs,
                configJson = configJson
            )
            resumeObservingTask(taskId)
        }
    }

    private fun BatchEditUiState.filterHiddenEditFields(
        visibleFieldCodes: Set<String>,
    ): BatchEditUiState {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun visible(code: String): Boolean = code in visibleFieldCodes

        return copy(
            title = if (visible("title")) title else keep,
            artist = if (visible("artist")) artist else keep,
            albumArtist = if (visible("album_artist")) albumArtist else keep,
            album = if (visible("album")) album else keep,
            date = if (visible("date")) date else keep,
            language = if (visible("language")) language else keep,
            genre = if (visible("genre")) genre else keep,
            trackNumber = if (visible("track_number")) trackNumber else keep,
            discNumber = if (visible("disc_number")) discNumber else keep,
            composer = if (visible("composer")) composer else keep,
            lyricist = if (visible("lyricist")) lyricist else keep,
            copyright = if (visible("copyright")) copyright else keep,
            comment = if (visible("comment")) comment else keep,
            lyrics = if (visible("lyrics")) lyrics else keep,
            rating = if (visible("rating")) rating else 0,
            ratingModified = visible("rating") && ratingModified,
            coverUri = if (visible("picture")) coverUri else null,
            removeCover = visible("picture") && removeCover,
            lyricsOffset = if (visible("lyrics_offset")) lyricsOffset else 0L,
            replayGainTrackGain = if (visible("track_gain")) replayGainTrackGain else keep,
            replayGainTrackPeak = if (visible("track_peak")) replayGainTrackPeak else keep,
            replayGainAlbumGain = if (visible("album_gain")) replayGainAlbumGain else keep,
            replayGainAlbumPeak = if (visible("album_peak")) replayGainAlbumPeak else keep,
            replayGainReferenceLoudness = if (visible("reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                keep
            },
            customFields = customFields
                .filter {
                    visible(com.lonx.lyrico.data.editfield.EditFieldRegistry.customTagCode(it.key.uppercase(Locale.ROOT))) &&
                        it.value != keep
                }
                .distinctBy { it.key },
        )
    }

    private fun resumeObservingTask(taskId: String) {
        observeJob?.cancel()
        currentTaskId = taskId
        _uiState.update {
            it.copy(
                isSaving = true,
                saveProgressBottomSheet = true,
                saveProgress = 0,
                saveTotal = 0,
                currentFile = "",
                successCount = 0,
                skippedCount = 0,
                failureCount = 0,
                saveTimeMillis = 0,
                saveSuccess = null,
                saveResultMessage = null,
                errorMessage = null
            )
        }
        observeJob = viewModelScope.launch {
            batchTaskRepository.observeTask(taskId).collect { task ->
                if (task == null) return@collect
                val isRunning = task.status == BatchTaskStatus.RUNNING ||
                        task.status == BatchTaskStatus.QUEUED
                val duration = if (!isRunning && task.startedAt != null && task.finishedAt != null) {
                    task.finishedAt - task.startedAt
                } else {
                    0L
                }
                _uiState.update {
                    it.copy(
                        isSaving = isRunning,
                        saveProgressBottomSheet = true,
                        saveProgress = task.current,
                        saveTotal = task.total,
                        currentFile = task.currentFile ?: "",
                        successCount = task.successCount,
                        skippedCount = task.skippedCount,
                        failureCount = task.failureCount,
                        saveTimeMillis = duration,
                        saveSuccess = if (isRunning) null else task.status == BatchTaskStatus.SUCCEEDED && task.failureCount == 0,
                        saveResultMessage = if (isRunning) {
                            null
                        } else {
                            UiMessage.StringResource(
                                R.string.batch_edit_result_summary,
                                task.successCount,
                                task.total,
                                task.skippedCount,
                                task.failureCount
                            )
                        }
                    )
                }
                if (!isRunning) {
                    currentTaskId = null
                    observeJob?.cancel()
                }
            }
        }
    }

    private fun BatchEditUiState.toTaskConfig(
        visibleFieldCodes: Set<String>,
    ): EditTagsTaskConfig {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun visible(code: String): Boolean = code in visibleFieldCodes

        return EditTagsTaskConfig(
            title = if (visible("title")) title else keep,
            artist = if (visible("artist")) artist else keep,
            albumArtist = if (visible("album_artist")) albumArtist else keep,
            album = if (visible("album")) album else keep,
            date = if (visible("date")) date else keep,
            language = if (visible("language")) language else keep,
            genre = if (visible("genre")) genre else keep,
            trackNumber = if (visible("track_number")) trackNumber else keep,
            discNumber = if (visible("disc_number")) discNumber else keep,
            composer = if (visible("composer")) composer else keep,
            lyricist = if (visible("lyricist")) lyricist else keep,
            copyright = if (visible("copyright")) copyright else keep,
            comment = if (visible("comment")) comment else keep,
            lyrics = if (visible("lyrics")) lyrics else keep,
            rating = if (visible("rating")) rating else 0,
            ratingModified = visible("rating") && ratingModified,
            coverUri = if (visible("picture")) coverUri?.toString() else null,
            removeCover = visible("picture") && removeCover,
            lyricsOffset = if (visible("lyrics_offset")) lyricsOffsetForTask else "",
            replayGainTrackGain = if (visible("track_gain")) replayGainTrackGain else keep,
            replayGainTrackPeak = if (visible("track_peak")) replayGainTrackPeak else keep,
            replayGainAlbumGain = if (visible("album_gain")) replayGainAlbumGain else keep,
            replayGainAlbumPeak = if (visible("album_peak")) replayGainAlbumPeak else keep,
            replayGainReferenceLoudness = if (visible("reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                keep
            },
            customFields = customFields
                .filter { it.key.isNotBlank() && it.value != keep }
                .distinctBy { it.key }
                .map { EditTagsCustomField(key = it.key, value = it.value) }
        )
    }

    // ── 状态清理 ──────────────────────────────────────────

    /**
     * 关闭保存进度对话框
     */
    fun closeSaveBottomSheet() {
        _uiState.update {
            it.copy(
                saveProgressBottomSheet = false,
                currentFile = "",
                isSaving = false,
                saveTimeMillis = 0,
                successCount = 0,
                skippedCount = 0,
                failureCount = 0
            )
        }
    }

    /**
     * 中止保存
     */
    fun abortSave() {
        val taskId = currentTaskId
        if (taskId != null) {
            batchTaskScheduler.cancel(taskId)
            viewModelScope.launch {
                batchTaskRepository.markCancelled(taskId)
            }
        }
        saveJob?.cancel()
        saveJob = null
        _uiState.update {
            it.copy(
                isSaving = false,
                saveProgressBottomSheet = false,
                currentFile = "",
                saveTimeMillis = 0,
                successCount = 0,
                skippedCount = 0,
                failureCount = 0
            )
        }
    }

    /** 获取同专辑歌曲封面，优先同专辑且同歌手的结果。 */
    suspend fun getSameAlbumCovers(): List<Pair<String, Any?>> {
        val uiState = _uiState.value
        val editedAlbum = uiState.album
        val editedArtist = uiState.artist

        val targetAlbum: String
        val targetArtist: String

        if (editedAlbum != "<keep>" && editedAlbum.isNotBlank()) {
            targetAlbum = editedAlbum
            targetArtist =
                if (editedArtist != "<keep>" && editedArtist.isNotBlank()) editedArtist else ""
        } else {
            ensureSelectedSongsLoaded()
            var commonAlbum: String? = null
            var commonArtist: String? = null
            var hasMismatch = false

            for (song in selectedSongs) {
                val album = song.album
                val artist = song.artist

                if (commonAlbum == null && commonArtist == null) {
                    commonAlbum = album
                    commonArtist = artist
                } else {
                    if (album != commonAlbum || artist != commonArtist) {
                        hasMismatch = true
                        break
                    }
                }
            }

            if (hasMismatch || commonAlbum.isNullOrBlank()) {
                _uiState.update {
                    it.copy(errorMessage = UiMessage.StringResource(R.string.batch_edit_cover_mismatch))
                }
                return emptyList()
            }

            targetAlbum = commonAlbum
            targetArtist =
                if (editedArtist != "<keep>" && editedArtist.isNotBlank()) editedArtist else (commonArtist
                    ?: "")
        }

        _uiState.update { it.copy(errorMessage = null) }

        val sameAlbumSongs = songLibraryRepository.getSongsByAlbum(targetAlbum, targetArtist)
        for (song in sameAlbumSongs) {
            try {
                val tagData = audioTagRepository.read(song.uri)
                val cover = tagData.pictures.frontCoverOrFallback()?.data
                if (cover != null) {
                    val title = "${song.title} - ${song.artist}"
                    return listOf(title to cover)
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取同专辑歌曲封面失败: ${song.uri}", e)
            }
        }

        return emptyList()
    }

    override fun onCleared() {
        saveJob?.cancel()
        super.onCleared()
    }
}
