package com.lonx.lyrico.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.audiotag.model.CustomTagField
import com.lonx.lyrico.R
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.editfield.EditFieldScene
import com.lonx.lyrico.data.editfield.EditFieldVisibilityRepository
import com.lonx.lyrico.data.editfield.VisibleEditFieldGroup
import com.lonx.lyrico.data.model.BatchTaskStatus
import com.lonx.lyrico.data.model.BatchTaskType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.BatchTaskRepository
import com.lonx.lyrico.data.repository.CustomTagSettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.utils.LyricEncoder
import com.lonx.lyrico.utils.UiMessage
import com.lonx.lyrico.utils.UriUtils
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 可批量编辑的标签字段枚举
 */
enum class BatchEditField(val labelResId: Int) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artists),
    ALBUM_ARTIST(R.string.label_album_artist),
    ALBUM(R.string.label_album),
    DATE(R.string.label_year),
    LANGUAGE(R.string.label_language),
    GENRE(R.string.label_genre),
    TRACK_NUMBER(R.string.label_track_number),
    DISC_NUMBER(R.string.label_disc_number),
    COMPOSER(R.string.label_composer),
    LYRICIST(R.string.label_lyricist),
    COPYRIGHT(R.string.label_copyright),
    COMMENT(R.string.label_comment),
    LYRICS(R.string.label_lyrics),
    REPLAY_GAIN_TRACK_GAIN(R.string.label_replaygain_track_gain),
    REPLAY_GAIN_TRACK_PEAK(R.string.label_replaygain_track_peak),
    REPLAY_GAIN_ALBUM_GAIN(R.string.label_replaygain_album_gain),
    REPLAY_GAIN_ALBUM_PEAK(R.string.label_replaygain_album_peak),
    REPLAY_GAIN_REFERENCE_LOUDNESS(R.string.label_replaygain_reference_loudness),
    COVER(R.string.label_cover),
    RATING(R.string.label_rating),
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

    /** 歌词偏移（毫秒） */
    val lyricsOffset: String = "",

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
)

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
    private val songRepository: SongRepository,
    private val selectionManager: SharedSelectionManager,
    private val batchTaskRepository: BatchTaskRepository,
    private val batchTaskScheduler: BatchTaskScheduler,
    private val editFieldVisibilityRepository: EditFieldVisibilityRepository,
    private val customTagSettingsRepository: CustomTagSettingsRepository,
    private val application: Application
) : ViewModel() {

    private val TAG = "BatchEditVM"
    private val contentResolver = application.contentResolver
    private var saveJob: Job? = null
    private var observeJob: Job? = null
    private var currentTaskId: String? = null
    private var customTagPreviewValues: Map<String, Map<String, String>> = emptyMap()

    private val _uiState = MutableStateFlow(BatchEditUiState())
    val uiState: StateFlow<BatchEditUiState> = _uiState.asStateFlow()

    val visibleFieldGroups: StateFlow<List<VisibleEditFieldGroup>> =
        editFieldVisibilityRepository.configFlow
            .map { config ->
                config.visibleGroupsForScene(EditFieldScene.BatchEdit)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    val visibleCustomKeys: StateFlow<List<String>> =
        customTagSettingsRepository.settingsFlow
            .map { it.visibleKeys }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
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
                songRepository.getSongByUri(uri)
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

    fun updateLyricsOffset(value: String) {
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

    fun updateCustomFieldValue(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        _uiState.update { state ->
            val nextFields = state.customFields
                .filterNot { it.key.equals(normalizedKey, ignoreCase = true) }
                .toMutableList()

            if (value != EditTagsTaskConfig.KEEP_VALUE) {
                nextFields += CustomTagField(normalizedKey, value)
            }

            state.copy(customFields = nextFields)
        }
    }

    fun addCustomFieldAndShow(key: String, value: String) {
        val normalizedKey = normalizeCustomTagKey(key) ?: return

        viewModelScope.launch {
            customTagSettingsRepository.addVisibleKey(normalizedKey)
        }

        updateCustomFieldValue(normalizedKey, value)
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
            songRepository.getDistinctSongFieldValues(selectedUris, fieldColumn)
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
                val tagData = songRepository.readAudioTagData(uri)
                tagData.pictures.firstOrNull()?.data ?: tagData.picUrl
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
                    songRepository.readAudioTagData(song.uri)
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
            addTextChange(this, "basic_info.title", BatchEditField.TITLE.labelResId, song.title, state.title)
            addTextChange(this, "basic_info.artist", BatchEditField.ARTIST.labelResId, song.artist, state.artist)
            addTextChange(this, "basic_info.album_artist", BatchEditField.ALBUM_ARTIST.labelResId, song.albumArtist, state.albumArtist)
            addTextChange(this, "basic_info.album", BatchEditField.ALBUM.labelResId, song.album, state.album)
            addTextChange(this, "basic_info.date", BatchEditField.DATE.labelResId, song.date, state.date)
            addTextChange(this, "basic_info.language", BatchEditField.LANGUAGE.labelResId, song.language, state.language)
            addTextChange(this, "basic_info.genre", BatchEditField.GENRE.labelResId, song.genre, state.genre)
            addTextChange(this, "track_details.track_number", BatchEditField.TRACK_NUMBER.labelResId, song.trackerNumber, state.trackNumber)
            addTextChange(this, "track_details.disc_number", BatchEditField.DISC_NUMBER.labelResId, song.discNumber?.toString(), state.discNumber)
            addTextChange(this, "credits_other.composer", BatchEditField.COMPOSER.labelResId, song.composer, state.composer)
            addTextChange(this, "credits_other.lyricist", BatchEditField.LYRICIST.labelResId, song.lyricist, state.lyricist)
            addTextChange(this, "credits_other.copyright", BatchEditField.COPYRIGHT.labelResId, song.copyright, state.copyright)
            addTextChange(this, "credits_other.comment", BatchEditField.COMMENT.labelResId, song.comment, state.comment)
            addTextChange(this, "lyrics.lyrics", BatchEditField.LYRICS.labelResId, song.lyrics, state.lyrics)
            addTextChange(this, "replay_gain.track_gain", BatchEditField.REPLAY_GAIN_TRACK_GAIN.labelResId, song.replayGainTrackGain, state.replayGainTrackGain)
            addTextChange(this, "replay_gain.track_peak", BatchEditField.REPLAY_GAIN_TRACK_PEAK.labelResId, song.replayGainTrackPeak, state.replayGainTrackPeak)
            addTextChange(this, "replay_gain.album_gain", BatchEditField.REPLAY_GAIN_ALBUM_GAIN.labelResId, song.replayGainAlbumGain, state.replayGainAlbumGain)
            addTextChange(this, "replay_gain.album_peak", BatchEditField.REPLAY_GAIN_ALBUM_PEAK.labelResId, song.replayGainAlbumPeak, state.replayGainAlbumPeak)
            addTextChange(this, "replay_gain.reference_loudness", BatchEditField.REPLAY_GAIN_REFERENCE_LOUDNESS.labelResId, song.replayGainReferenceLoudness, state.replayGainReferenceLoudness)

            if (visible("cover.rating") && state.ratingModified) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.RATING.labelResId,
                        customLabel = null,
                        oldValue = song.rating?.toString().orEmpty(),
                        newValue = state.rating.toString()
                    )
                )
            }

            if (visible("cover.picture") && (state.removeCover || state.coverUri != null)) {
                add(
                    BatchEditPreviewChange(
                        labelResId = BatchEditField.COVER.labelResId,
                        customLabel = null,
                        oldValue = "<current_cover>",
                        newValue = if (state.removeCover) "<remove_cover>" else state.coverUri?.toString().orEmpty()
                    )
                )
            }

            if (visible("lyrics.lyrics_offset") && state.lyricsOffset.isNotBlank()) {
                val offsetValue = parseLyricsOffset(state.lyricsOffset)
                if (offsetValue != 0 && song.lyrics != null) {
                    add(
                        BatchEditPreviewChange(
                            labelResId = R.string.label_lyrics_offset,
                            customLabel = null,
                            oldValue = song.lyrics,
                            newValue = LyricEncoder.shiftLyricsOffset(song.lyrics, offsetValue.toLong())
                        )
                    )
                }
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
                songRepository.getSongByUri(uri)
            }
        }
        _uiState.update { it.copy(selectedSongsVersion = it.selectedSongsVersion + 1) }
    }

    // ── 批量保存 ──────────────────────────────────────────

    fun saveBatchEdit() {
        val visibleFieldCodes = currentVisibleFieldCodes()
        val state = _uiState.value.filterHiddenEditFields(visibleFieldCodes)
        if (state.isSaving || selectedUris.isEmpty()) return

        saveJob = viewModelScope.launch {
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
                songRepository.getSongByUri(uri)
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
            val taskId = batchTaskRepository.createTask(
                type = BatchTaskType.EDIT_TAGS,
                songs = songs,
                configJson = configJson
            )
            batchTaskScheduler.enqueue(taskId)
            resumeObservingTask(taskId)
        }
    }

    private fun currentVisibleFieldCodes(): Set<String> {
        return visibleFieldGroups.value
            .flatMap { it.fields }
            .map { it.code }
            .toSet()
    }

    private fun BatchEditUiState.filterHiddenEditFields(
        visibleFieldCodes: Set<String>,
    ): BatchEditUiState {
        val keep = EditTagsTaskConfig.KEEP_VALUE
        fun visible(code: String): Boolean = code in visibleFieldCodes

        return copy(
            title = if (visible("basic_info.title")) title else keep,
            artist = if (visible("basic_info.artist")) artist else keep,
            albumArtist = if (visible("basic_info.album_artist")) albumArtist else keep,
            album = if (visible("basic_info.album")) album else keep,
            date = if (visible("basic_info.date")) date else keep,
            language = if (visible("basic_info.language")) language else keep,
            genre = if (visible("basic_info.genre")) genre else keep,
            trackNumber = if (visible("track_details.track_number")) trackNumber else keep,
            discNumber = if (visible("track_details.disc_number")) discNumber else keep,
            composer = if (visible("credits_other.composer")) composer else keep,
            lyricist = if (visible("credits_other.lyricist")) lyricist else keep,
            copyright = if (visible("credits_other.copyright")) copyright else keep,
            comment = if (visible("credits_other.comment")) comment else keep,
            lyrics = if (visible("lyrics.lyrics")) lyrics else keep,
            rating = if (visible("cover.rating")) rating else 0,
            ratingModified = visible("cover.rating") && ratingModified,
            coverUri = if (visible("cover.picture")) coverUri else null,
            removeCover = visible("cover.picture") && removeCover,
            lyricsOffset = if (visible("lyrics.lyrics_offset")) lyricsOffset else "",
            replayGainTrackGain = if (visible("replay_gain.track_gain")) replayGainTrackGain else keep,
            replayGainTrackPeak = if (visible("replay_gain.track_peak")) replayGainTrackPeak else keep,
            replayGainAlbumGain = if (visible("replay_gain.album_gain")) replayGainAlbumGain else keep,
            replayGainAlbumPeak = if (visible("replay_gain.album_peak")) replayGainAlbumPeak else keep,
            replayGainReferenceLoudness = if (visible("replay_gain.reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                keep
            },
            customFields = customFields
                .filter { it.key.isNotBlank() && it.value != keep }
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
            title = if (visible("basic_info.title")) title else keep,
            artist = if (visible("basic_info.artist")) artist else keep,
            albumArtist = if (visible("basic_info.album_artist")) albumArtist else keep,
            album = if (visible("basic_info.album")) album else keep,
            date = if (visible("basic_info.date")) date else keep,
            language = if (visible("basic_info.language")) language else keep,
            genre = if (visible("basic_info.genre")) genre else keep,
            trackNumber = if (visible("track_details.track_number")) trackNumber else keep,
            discNumber = if (visible("track_details.disc_number")) discNumber else keep,
            composer = if (visible("credits_other.composer")) composer else keep,
            lyricist = if (visible("credits_other.lyricist")) lyricist else keep,
            copyright = if (visible("credits_other.copyright")) copyright else keep,
            comment = if (visible("credits_other.comment")) comment else keep,
            lyrics = if (visible("lyrics.lyrics")) lyrics else keep,
            rating = if (visible("cover.rating")) rating else 0,
            ratingModified = visible("cover.rating") && ratingModified,
            coverUri = if (visible("cover.picture")) coverUri?.toString() else null,
            removeCover = visible("cover.picture") && removeCover,
            lyricsOffset = if (visible("lyrics.lyrics_offset")) lyricsOffset else "",
            replayGainTrackGain = if (visible("replay_gain.track_gain")) replayGainTrackGain else keep,
            replayGainTrackPeak = if (visible("replay_gain.track_peak")) replayGainTrackPeak else keep,
            replayGainAlbumGain = if (visible("replay_gain.album_gain")) replayGainAlbumGain else keep,
            replayGainAlbumPeak = if (visible("replay_gain.album_peak")) replayGainAlbumPeak else keep,
            replayGainReferenceLoudness = if (visible("replay_gain.reference_loudness")) {
                replayGainReferenceLoudness
            } else {
                keep
            },
            customFields = customFields
                .filter { it.key.isNotBlank() && it.value != keep }
                .distinctBy { it.key }
                .map { EditTagsCustomField(it.key, it.value) }
        )
    }

    private fun saveBatchEditLegacy() {
        val visibleFieldCodes = currentVisibleFieldCodes()
        val state = _uiState.value.filterHiddenEditFields(visibleFieldCodes)
        if (state.isSaving || selectedUris.isEmpty()) return

        saveJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val successCounter = AtomicInteger(0)
            val failureCounter = AtomicInteger(0)

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
            for ((index, uri) in selectedUris.withIndex()) {
                val fileName =
                    UriUtils.getMediaStoreFileName(contentResolver, uri.toUri()) ?: "Unknown"
                _uiState.update {
                    it.copy(
                        currentFile = fileName
                    )
                }
                try {
                    val success = withContext(Dispatchers.IO) {
                        updateAudioTags(uri, state, visibleFieldCodes)
                    }
                    if (success) {
                        val s = successCounter.incrementAndGet()
                        _uiState.update { it.copy(successCount = s) }
                    } else {
                        val f = failureCounter.incrementAndGet()
                        _uiState.update { it.copy(failureCount = f) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "批量编辑失败: $uri", e)
                    val f = failureCounter.incrementAndGet()
                    _uiState.update { it.copy(failureCount = f) }
                }

                _uiState.update { it.copy(saveProgress = index + 1) }
            }

            val totalTime = System.currentTimeMillis() - startTime

            _uiState.update {
                it.copy(
                    isSaving = false,
                    currentFile = "",
                    saveTimeMillis = totalTime,
                    saveSuccess = failureCounter.get() == 0,
                    saveResultMessage = UiMessage.StringResource(
                        R.string.batch_edit_result_summary,
                        successCounter.get(),
                        selectedUris.size,
                        0,
                        failureCounter.get()
                    )
                )
            }
        }
    }

    /**
     * 处理单首歌曲的批量编辑
     * 先读取原始标签，再合并用户选择的字段，最后写回
     */
    private suspend fun updateAudioTags(
        uri: String,
        state: BatchEditUiState,
        visibleFieldCodes: Set<String>,
    ): Boolean {
        // 读取当前标签
        val uriString = uri
        val currentTag = try {
            songRepository.readAudioTagData(uriString)
        } catch (e: Exception) {
            Log.e(TAG, "无法读取标签: $uri", e)
            return false
        }

        // 按用户启用的字段合并数据
        val mergedTag = buildMergedTag(currentTag, state, visibleFieldCodes)

        // 写入文件
        return try {
            val success = songRepository.overwriteAudioTags(uriString, mergedTag)
            if (success) {
                songRepository.updateSongMetadata(mergedTag, uriString, System.currentTimeMillis())
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "写入标签失败: $uri", e)
            false
        }
    }

    /**
     * 根据用户编辑的值，将批量编辑值合并到原标签中
     * 值为"<keep>"时表示不修改该字段
     */
    private fun buildMergedTag(
        original: AudioTagData,
        state: BatchEditUiState,
        visibleFieldCodes: Set<String>,
    ): AudioTagData {
        var tag = original
        fun visible(code: String): Boolean = code in visibleFieldCodes

        if (visible("basic_info.title") && state.title != "<keep>") tag = tag.copy(title = state.title)
        if (visible("basic_info.artist") && state.artist != "<keep>") tag = tag.copy(artist = state.artist)
        if (visible("basic_info.album_artist") && state.albumArtist != "<keep>") tag = tag.copy(albumArtist = state.albumArtist)
        if (visible("basic_info.album") && state.album != "<keep>") tag = tag.copy(album = state.album)
        if (visible("basic_info.date") && state.date != "<keep>") tag = tag.copy(date = state.date)
        if (visible("basic_info.language") && state.language != "<keep>") tag = tag.copy(language = state.language)
        if (visible("basic_info.genre") && state.genre != "<keep>") tag = tag.copy(genre = state.genre)
        if (visible("track_details.track_number") && state.trackNumber != "<keep>") tag = tag.copy(trackNumber = state.trackNumber)
        if (visible("track_details.disc_number") && state.discNumber != "<keep>") tag =
            tag.copy(discNumber = state.discNumber.toIntOrNull())
        if (visible("credits_other.composer") && state.composer != "<keep>") tag = tag.copy(composer = state.composer)
        if (visible("credits_other.lyricist") && state.lyricist != "<keep>") tag = tag.copy(lyricist = state.lyricist)
        if (visible("credits_other.copyright") && state.copyright != "<keep>") tag = tag.copy(copyright = state.copyright)
        if (visible("credits_other.comment") && state.comment != "<keep>") tag = tag.copy(comment = state.comment)
        if (visible("lyrics.lyrics") && state.lyrics != "<keep>") tag = tag.copy(lyrics = state.lyrics)

        // 处理回放增益
        if (visible("replay_gain.track_gain") && state.replayGainTrackGain != "<keep>") {
            tag = tag.copy(replayGainTrackGain = state.replayGainTrackGain)
        }
        if (visible("replay_gain.track_peak") && state.replayGainTrackPeak != "<keep>") {
            tag = tag.copy(replayGainTrackPeak = state.replayGainTrackPeak)
        }
        if (visible("replay_gain.album_gain") && state.replayGainAlbumGain != "<keep>") {
            tag = tag.copy(replayGainAlbumGain = state.replayGainAlbumGain)
        }
        if (visible("replay_gain.album_peak") && state.replayGainAlbumPeak != "<keep>") {
            tag = tag.copy(replayGainAlbumPeak = state.replayGainAlbumPeak)
        }
        if (visible("replay_gain.reference_loudness") && state.replayGainReferenceLoudness != "<keep>") {
            tag = tag.copy(replayGainReferenceLoudness = state.replayGainReferenceLoudness)
        }

        // 处理 rating - 只在明确修改时才更新
        if (visible("cover.rating") && state.ratingModified) tag = tag.copy(rating = state.rating)

        // 处理覆盖图
        if (visible("cover.picture") && state.removeCover) {
            tag = tag.copy(picUrl = "")
        } else if (visible("cover.picture") && state.coverUri != null) {
            tag = tag.copy(picUrl = state.coverUri.toString())
        }

        // 处理歌词偏移（直接修改歌词文本中的时间戳）
        if (visible("lyrics.lyrics_offset") && state.lyricsOffset.isNotBlank() && tag.lyrics != null) {
            val offsetValue = parseLyricsOffset(state.lyricsOffset)
            if (offsetValue != 0) {
                val shiftedLyrics =
                    LyricEncoder.shiftLyricsOffset(tag.lyrics!!, offsetValue.toLong())
                tag = tag.copy(lyrics = shiftedLyrics)
            }
        }

        if (state.customFields.isNotEmpty()) {
            tag = tag.copy(customFields = tag.customFields.toMutableList().apply {
                state.customFields.forEach { newField ->
                    val key = normalizeCustomTagKey(newField.key) ?: return@forEach
                    val field = CustomTagField(key = key, value = newField.value)
                    val existingIndex = indexOfFirst { it.key.equals(key, ignoreCase = true) }
                    if (existingIndex >= 0) {
                        this[existingIndex] = field
                    } else {
                        add(field)
                    }
                }
            })
        }

        return tag
    }

    /**
     * 解析歌词偏移值
     * 支持正负号，未填写正负号默认为正
     */
    private fun parseLyricsOffset(input: String): Int {
        return try {
            val trimmed = input.trim()
            if (trimmed.startsWith("+") || trimmed.startsWith("-")) {
                trimmed.toInt()
            } else {
                // 未填写正负号，默认为正
                trimmed.toInt()
            }
        } catch (e: NumberFormatException) {
            0
        }
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

    /**
     * 获取同专辑歌曲封面
     * 优先使用同专辑且同歌手的查询结果作为封面
     * 如果专辑或艺术家字段被修改过，则使用修改后的值进行查询
     */
    suspend fun getSameAlbumCovers(): List<Pair<String, Any?>> {
        val uiState = _uiState.value
        val editedAlbum = uiState.album
        val editedArtist = uiState.artist

        // 确定使用的专辑和艺术家值
        val targetAlbum: String
        val targetArtist: String

        if (editedAlbum != "<keep>" && editedAlbum.isNotBlank()) {
            // 如果专辑被修改过，使用修改后的值
            targetAlbum = editedAlbum
            targetArtist =
                if (editedArtist != "<keep>" && editedArtist.isNotBlank()) editedArtist else ""
        } else {
            // 如果专辑没被修改，检查所有选中歌曲的专辑和艺术家是否一致
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

            // 如果存在不一致的情况，更新错误消息并返回空列表
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

        // 清除之前的错误消息
        _uiState.update { it.copy(errorMessage = null) }

        // 查询同专辑的歌曲封面
        val sameAlbumSongs = songRepository.getSongsByAlbum(targetAlbum, targetArtist)
        for (song in sameAlbumSongs) {
            try {
                val tagData = songRepository.readAudioTagData(song.uri)
                val cover = tagData.pictures.firstOrNull()?.data ?: tagData.picUrl
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
