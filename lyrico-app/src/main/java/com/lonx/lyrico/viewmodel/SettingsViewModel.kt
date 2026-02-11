package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.ArtistSeparator
import com.lonx.lyrico.data.model.dao.FolderDao
import com.lonx.lyrico.data.model.entity.FolderEntity
import com.lonx.lyrico.data.model.ThemeMode
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.LyricDisplayMode
import com.lonx.lyrico.data.model.dao.SongDao
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lonx.lyrico.data.model.toArtistSeparator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val lyricDisplayMode: LyricDisplayMode = LyricDisplayMode.WORD_BY_WORD,
    val separator: ArtistSeparator = ArtistSeparator.SLASH,
    val romaEnabled: Boolean = false,
    val ignoreShortAudio: Boolean = false,
    val folders: List<FolderEntity> = emptyList(),
    val searchSourceOrder: List<Source> = emptyList(),
    val searchPageSize: Int = 20,
    val themeMode: ThemeMode = ThemeMode.AUTO
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderDao: FolderDao = database.folderDao()
    private val songDao: SongDao = database.songDao()
    private val _uiState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        folderDao.getAllFolders()
    ) { settings, folders ->
        SettingsUiState(
            lyricDisplayMode = settings.lyricDisplayMode,
            romaEnabled = settings.romaEnabled,
            separator = settings.separator.toArtistSeparator(),
            folders = folders,
            searchSourceOrder = settings.searchSourceOrder,
            searchPageSize = settings.searchPageSize,
            themeMode = settings.themeMode,
            ignoreShortAudio = settings.ignoreShortAudio
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )



    fun toggleFolderIgnore(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.setIgnored(folder.id, !folder.isIgnored)
        }
    }

    fun setLyricDisplayMode(mode: LyricDisplayMode) {
        viewModelScope.launch {
            settingsRepository.saveLyricDisplayMode(mode)
            _uiState.update {
                it.copy(lyricDisplayMode = mode)
            }
        }
    }

    fun setRomaEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveRomaEnabled(enabled)
            _uiState.update {
                it.copy(romaEnabled = enabled)
            }
        }
    }

    fun setSeparator(separator: ArtistSeparator) {
        viewModelScope.launch {
            settingsRepository.saveSeparator(separator.toText())
            _uiState.update {
                it.copy(separator = separator)
            }
        }
    }

    fun setIgnoreShortAudio(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveIgnoreShortAudio(enabled)
            _uiState.update {
                it.copy(ignoreShortAudio = enabled)
            }
        }
    }
    fun setSearchSourceOrder(sources: List<Source>) {
        viewModelScope.launch {
            settingsRepository.saveSearchSourceOrder(sources)
            _uiState.update {
                it.copy(searchSourceOrder = sources)
            }
        }
    }
    fun setSearchPageSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.saveSearchPageSize(size)
            _uiState.update {
                it.copy(searchPageSize = size)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.saveThemeMode(mode)
            _uiState.update {
                it.copy(themeMode = mode)
            }
        }
    }

    /**
     * 如果用户想手动添加一个还没被扫描到的文件夹并忽略它
     */
    fun addFolderByPath(path: String) {
        viewModelScope.launch {
            // 确保文件夹在数据库中存在（upsert）
            val id = folderDao.upsertAndGetId(path = path, addedBySaf = true)
            // 设置为忽略
            folderDao.setIgnored(id, true)
        }
    }
    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.deleteFolderPermanently(folder.id)
            // 注意：删除文件夹记录后，SongDao 会因为外键约束或逻辑关联
            // 导致这些歌曲在库中不可见（因为 JOIN 找不到 folderId）
        }
    }

}

