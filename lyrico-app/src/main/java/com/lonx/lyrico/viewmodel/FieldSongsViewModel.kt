package com.lonx.lyrico.viewmodel

import com.lonx.lyrico.data.model.SortOrder
import com.lonx.lyrico.data.model.SortBy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.editfield.EditFieldConfig
import com.lonx.lyrico.data.editfield.EditFieldRegistry
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.isTargetBlank
import com.lonx.lyrico.data.repository.CustomTagKeyRepository
import com.lonx.lyrico.data.song.library.SongLibraryRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FieldSongsUiState(
    val allSongs: List<SongEntity> = emptyList(),
    val songsWithField: List<SongEntity> = emptyList(),
    val songsWithoutField: List<SongEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

class FieldSongsViewModel(
    fieldCode: String,
    private val component: Boolean,
    private val customTagKeyRepository: CustomTagKeyRepository,
    private val songLibraryRepository: SongLibraryRepository,
    private val audioTagRepository: AudioTagRepository,
    private val selectionManager: SharedSelectionManager,
) : ViewModel() {
    private val customKey = EditFieldRegistry.customTagKeyOf(fieldCode)
    val field = EditFieldRegistry.fieldMap[fieldCode]
        ?: customKey?.let { EditFieldConfig(customTags = listOf(it)).customFields.first() }
    private val state = MutableStateFlow(FieldSongsUiState())
    val uiState = state.asStateFlow()
    private var songJob: Job? = null

    private var previousSongs: List<SongEntity> = emptyList()
    private var hasLoaded = false
    private val coverCache = mutableMapOf<String, Pair<SongEntity, Boolean>>()

    init {
        selectionManager.exitSelectionMode()
        refresh()
    }

    // Keep one live subscription; the last result is reused when returning to this screen.
    fun refresh() {
        songJob?.cancel()
        songJob = viewModelScope.launch {
            try {
                val definition = requireNotNull(field)
                val library = songLibraryRepository.observeSongs(SortBy.TITLE, SortOrder.ASC)
                val source = if (customKey != null) {
                    combine(library, customTagKeyRepository.observeSongUrisByKey(customKey).distinctUntilChanged()) {
                        songs, uris -> songs to uris.toSet()
                    }
                } else {
                    library.map { songs -> songs to emptySet<String>() }
                }
                source.distinctUntilChanged().collectLatest { (songs, taggedUris) ->
                    val result = withContext(Dispatchers.IO) {
                        val withField = mutableListOf<SongEntity>()
                        val withoutField = mutableListOf<SongEntity>()
                        var failed = false
                        for (song in songs) {
                            ensureActive()
                            val hasValue = try {
                                when {
                                    component -> EditFieldRegistry.fields
                                        .filter { it.kind == definition.kind }
                                        .any { !song.isTargetBlank(requireNotNull(it.target)) }
                                    customKey != null -> song.uri in taggedUris
                                    definition.code == "picture" -> {
                                        val cached = coverCache[song.uri]
                                        if (cached?.first == song) cached.second else {
                                            audioTagRepository.read(song.uri).pictures.isNotEmpty().also {
                                                coverCache[song.uri] = song to it
                                            }
                                        }
                                    }
                                    definition.code == "lyrics_offset" -> OFFSET_TAG.containsMatchIn(song.lyrics.orEmpty())
                                    else -> !song.isTargetBlank(requireNotNull(definition.target))
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // Unreadable songs must not be classified as having an empty field.
                                failed = true
                                continue
                            }
                            if (hasValue) withField.add(song) else withoutField.add(song)
                        }
                        coverCache.keys.retainAll(songs.map { it.uri }.toSet())
                        FieldSongsUiState(allSongs = songs, songsWithField = withField, songsWithoutField = withoutField, isLoading = false, loadFailed = failed)
                    }
                    if (hasLoaded) {
                        val selected = selectionManager.selectedUris.value
                        val reconciled = reconcileFieldSongSelection(selected, previousSongs, songs)
                        if (selected != reconciled) selectionManager.setUris(reconciled)
                    }
                    previousSongs = songs
                    hasLoaded = true
                    state.value = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = state.value.copy(isLoading = false, loadFailed = true)
            }
        }
    }

    private companion object {
        val OFFSET_TAG = Regex("\\[offset:[+-]?\\d+\\]", RegexOption.IGNORE_CASE)
    }
}

/** Database IDs survive renames; reconcile only after a complete library emission. */
internal fun reconcileFieldSongSelection(
    selectedUris: Set<String>,
    previousSongs: List<SongEntity>,
    songs: List<SongEntity>,
): Set<String> {
    val previousByUri = previousSongs.associateBy { it.uri }
    val currentById = songs.filter { it.id != 0L }.associateBy { it.id }
    val currentUris = songs.map { it.uri }.toSet()
    return selectedUris.mapNotNull { uri ->
        val previousId = previousByUri[uri]?.id?.takeIf { it != 0L }
        if (previousId != null) currentById[previousId]?.uri
        else uri.takeIf { it in currentUris }
    }.toSet()
}
