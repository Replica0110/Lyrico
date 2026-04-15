package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.SharedSelectionManager
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.repository.SongRepository
import com.lonx.lyrico.data.model.BatchMatchConfigDefaults
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.model.entity.getUri
import com.lonx.lyrico.data.repository.PlaybackRepository
import com.lonx.lyrico.utils.MusicContentObserver
import com.lonx.lyrico.utils.UpdateManager
import com.lonx.lyrics.model.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Parcelize
data class SongInfo(
    val uriString: String,
    val tagData: AudioTagData?
): Parcelable

data class SongListUiState(
    val isLoading: Boolean = false,
    val lastScanTime: Long = 0,
    val showBatchConfigDialog: Boolean = false, // Add this
    val isBatchMatching: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showBatchDeleteDialog: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null, // (当前第几首, 总共几首)
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val skippedCount: Int = 0,
    val showScrollTopButton: Boolean = false,
    val currentFile: String = "",
    val batchHistoryId: Long = 0,
    val batchTimeMillis: Long = 0,  // 批量匹配总用时（毫秒）
    val searchQuery: String = "",
    val isSearching: Boolean = false
)
data class SheetUiState(
    val menuSong: SongEntity? = null,
    val detailSong: SongEntity? = null
)


@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SongListViewModel(
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val playbackRepository: PlaybackRepository,
    private val updateManager: UpdateManager,
    private val selectionManager: SharedSelectionManager,
    application: Application
) : ViewModel() {

    private val TAG = "SongListViewModel"
    private val contentResolver = application.contentResolver
    private var musicContentObserver: MusicContentObserver? = null
    private val scanRequest = MutableSharedFlow<Unit>(replay = 0)
    private var batchMatchJob: Job? = null


    val sortInfo: StateFlow<SortInfo> = settingsRepository.sortInfo
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortInfo())

    private val searchSourceOrder: StateFlow<List<Source>> = settingsRepository.searchSourceOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val enabledSearchSources: StateFlow<Set<Source>> = settingsRepository.enabledSearchSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /**
     * 已启用的搜索源，按优先级排序
     */
    val enabledSourceOrder: StateFlow<List<Source>> = combine(
        searchSourceOrder,
        enabledSearchSources
    ) { order, enabled ->
        order.filter { it in enabled }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val separator = settingsRepository.separator
        .stateIn(viewModelScope, SharingStarted.Eagerly, "/")

    val showScrollTopButton = settingsRepository.showScrollTopButton
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val batchMatchConfig = settingsRepository.batchMatchConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, BatchMatchConfigDefaults.DEFAULT_CONFIG)

    // UI 交互状态
    private val _uiState = MutableStateFlow(SongListUiState())
    val uiState = _uiState.asStateFlow()

    private var preDragSelectedIds = emptySet<Long>()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds = _selectedSongIds.asStateFlow()
    private val _searchType = MutableStateFlow(LocalSearchType.ALL)
    val searchType = _searchType.asStateFlow()
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode = _isSelectionMode.asStateFlow()
    private val _sheetState = MutableStateFlow(SheetUiState())
    val sheetState = _sheetState.asStateFlow()
    // 监听 sortInfo 和 搜索词(searchQuery) 的变化。
    // 使用 flatMapLatest，一旦输入新搜索词，会自动取消上一次尚未完成的数据库查询
    val songs: StateFlow<List<SongEntity>> = combine(
        sortInfo,
        _uiState.map { it.searchQuery }.distinctUntilChanged(),
        searchType
    ) { sort, query, type ->
        Triple(sort, query, type) // 组合成 Triple
    }.flatMapLatest { (sort, query, type) ->
        if (query.isBlank()) {
            // 没有搜索词，返回全部歌曲（应用排序）
            songRepository.observeSongs(sort.sortBy, sort.order)
        } else {
            // 有搜索词，将 query 和当前的 searchType 一起传给 Repository
            songRepository.searchSongs(query, type)
        }
    }.onEach {
        // 数据库查询出结果后，关闭 isSearching 状态
        _uiState.update { it.copy(isSearching = false) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun showMenu(song: SongEntity) {
        _sheetState.value = SheetUiState(menuSong = song)
    }
    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun showDetail(song: SongEntity) {
        _sheetState.update {
            it.copy(detailSong = song)
        }
    }

    fun dismissDetail() {
        _sheetState.update { it.copy(detailSong = null) }
    }

    fun dismissAll() {
        _sheetState.value = SheetUiState()
    }



    init {
        registerMusicObserver()

        // 自动同步监听
        viewModelScope.launch {
            scanRequest.debounce(2000L).collect {
                if (!_uiState.value.isBatchMatching) triggerSync(isAuto = true)
            }
        }
    }
    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                // 如果搜索词不为空，立即显示加载状态，直到 songs Flow 的 onEach 将其置为 false
                isSearching = query.isNotBlank()
            )
        }
    }
    fun onSearchTypeChanged(type: LocalSearchType) {
        _searchType.value = type
    }
    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        _searchType.value = LocalSearchType.ALL
    }
    /**
     * 触发滑动选择的起点
     */
    fun startDragSelection(index: Int, songs: List<SongEntity>) {
        val song = songs.getOrNull(index) ?: return
        preDragSelectedIds = _selectedSongIds.value

        // 起点只包含当前长按的这一个元素
        val rangeIds = setOf(song.mediaId)

        // 取对称差集：划过的项状态反转，未划过的保持原样
        _selectedSongIds.value = (preDragSelectedIds - rangeIds) + (rangeIds - preDragSelectedIds)
    }

    /**
     * 拖动过程中更新选中区间
     */
    fun updateDragSelection(startIndex: Int, endIndex: Int, songs: List<SongEntity>) {
        val start = minOf(startIndex, endIndex).coerceAtLeast(0)
        val end = maxOf(startIndex, endIndex).coerceAtMost(songs.size - 1)
        if (start > end) return

        // 获取当前手指划过的所有歌曲 ID
        val rangeIds = songs.subList(start, end + 1).map { it.mediaId }.toSet()

        // preDragSelectedIds - rangeIds  -> 找出原本被选中，且没被划过的项保留下来
        // rangeIds - preDragSelectedIds  -> 找出划过的项中，原本没被选中的项，让它们变成选中
        _selectedSongIds.value = (preDragSelectedIds - rangeIds) + (rangeIds - preDragSelectedIds)
    }

    /**
     * 结束滑动选择
     */
    fun endDragSelection() {
        // 滑动结束，清空基准状态
        preDragSelectedIds = emptySet()
    }
    fun checkForUpdate() {
        viewModelScope.launch {
            val checkUpdateEnabled = settingsRepository.checkUpdateEnabled.first()
            if (checkUpdateEnabled) {
                Log.d(TAG, "检查更新")
                updateManager.checkForUpdate()
            }
        }
    }

    fun play(context: Context, song: SongEntity) {
        val uri = song.getUri
        playbackRepository.play(context, uri)
    }
    fun delete(song: SongEntity) {
        viewModelScope.launch {
            dismissAll()
            songRepository.deleteSong(song)
        }
    }


    fun setScrollToTopButtonEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveShowScrollTopButton(enabled)
        }
    }

    fun onSortChange(newSortInfo: SortInfo) {
        viewModelScope.launch {
            settingsRepository.saveSortInfo(newSortInfo)
        }
    }

    fun initialScanIfEmpty() {
        viewModelScope.launch {
            if (songRepository.getSongCount() == 0) {
                Log.d(TAG, "数据库为空，触发首次扫描")
                triggerSync(isAuto = false)
            }
        }
    }
    fun toggleSelection(mediaId: Long) {
        if (!_isSelectionMode.value) _isSelectionMode.value = true
        _selectedSongIds.update { if (it.contains(mediaId)) it - mediaId else it + mediaId }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedSongIds.value = emptySet()
    }

    fun deselectAll() {
        _selectedSongIds.value = emptySet()
    }
    fun selectAll(songs: List<SongEntity>) {
        _selectedSongIds.value = songs.map { it.mediaId }.toSet()
    }

    fun showBatchDeleteDialog() {
        _uiState.update { it.copy(showBatchDeleteDialog = true) }
    }

    fun dismissBatchDeleteDialog() {
        _uiState.update { it.copy(showBatchDeleteDialog = false) }
    }

    fun batchDelete(songs: List<SongEntity>) {
        val selectedIds = _selectedSongIds.value
        val toDelete = songs.filter { it.mediaId in selectedIds }
        viewModelScope.launch {
            toDelete.forEach { song ->
                songRepository.deleteSong(song)
            }
            exitSelectionMode()
        }
    }

    fun batchShare(context: Context, songs: List<SongEntity>) {
        val selectedIds = _selectedSongIds.value
        val toShare = songs.filter { it.mediaId in selectedIds }
        if (toShare.isEmpty()) return

        val uris = toShare.map { song ->
            ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                song.mediaId
            )
        }.toCollection(ArrayList())

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(com.lonx.lyrico.R.string.share_chooser_title))
        )
    }
    /**
     * 准备跳转到“批量重命名”页面
     * @return Boolean 返回 true 表示有数据可以跳转，false 表示没有选中数据
     */
    fun setSelectionPaths(): Boolean {
        val selectedIds = _selectedSongIds.value
        if (selectedIds.isEmpty()) return false

        // 从当前列表中过滤出选中的歌曲
        val paths = songs.value
            .filter { it.mediaId in selectedIds }
            .map { it.filePath }
            .toSet()


        // 存入全局 Manager
        selectionManager.setUris(paths)
        return true
    }
    fun setSelectionUris(): Boolean {
        val selectedIds = _selectedSongIds.value
        if (selectedIds.isEmpty()) return false

        // 从当前列表中过滤出选中的歌曲
        val uris = songs.value
            .filter { it.mediaId in selectedIds }
            .map { it.uri }
            .toSet()


        Log.d(TAG, "setSelectionUris: $uris")
        // 存入全局 Manager
        selectionManager.setUris(uris)
        return true
    }
    private fun triggerSync(isAuto: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                songRepository.synchronize(false)
            } catch (e: Exception) {
                Log.e(TAG, "同步失败", e)
            } finally {
                delay(500L)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refreshSongs() {
        if (_uiState.value.isLoading) return
        Log.d(TAG, "用户手动刷新歌曲列表")
        triggerSync(isAuto = false)
    }
    private fun registerMusicObserver() {
        musicContentObserver = MusicContentObserver(viewModelScope, Handler(Looper.getMainLooper())) {
            viewModelScope.launch { scanRequest.emit(Unit) }
        }
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, musicContentObserver!!)
    }

    override fun onCleared() {
        musicContentObserver?.let { contentResolver.unregisterContentObserver(it) }
        batchMatchJob?.cancel()
        super.onCleared()
    }
}
