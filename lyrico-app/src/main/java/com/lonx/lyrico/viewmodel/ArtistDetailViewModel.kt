package com.lonx.lyrico.viewmodel

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.audiotag.model.AudioPicture
import com.lonx.audiotag.model.AudioPictureType
import com.lonx.lyrico.R
import com.lonx.lyrico.data.model.artist.ArtistSplitConfig
import com.lonx.lyrico.data.model.entity.AlbumEntity
import com.lonx.lyrico.data.model.entity.ArtistEntity
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.data.repository.LibraryIndexRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import com.lonx.lyrico.data.utils.ArtistNameSplitter
import com.lonx.lyrico.domain.poster.ArtistPosterEmbedding
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsUseCase
import com.lonx.lyrico.domain.song.usecase.SaveAudioTagsResult
import com.lonx.lyrico.utils.ArtistPosterFileWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一张已读入的待设置海报。字节只读一次：后续可能写入多首歌，也可能等待写入授权。
 */
data class ArtistImageSource(
    val picture: AudioPicture,
    val fileName: String?
)

/**
 * 「设置艺术家海报」的进度。[message] 只在结束后有值，进行中靠 [done]/[total] 表达。
 */
data class ArtistPosterEmbedUiState(
    val isRunning: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val skipped: Int = 0,
    /** 正在写的那首歌，进行中给用户一个「确实在动」的凭据。 */
    val activeSong: String? = null,
    val failures: List<String> = emptyList(),
    /** 写盘需要用户授权；带着授权入口保持可见，授权后原样重跑一遍即可补完。 */
    val permissionIntentSender: IntentSender? = null,
    /** 已经取好文案的结果（成功/失败/中止），界面直接拿去提示即可。 */
    val message: String? = null
) {
    val progress: Float get() = if (total <= 0) 0f else done.toFloat() / total

    /** 有内容可展示（进行中、待授权、或刚出结果）时才需要占用底部面板。 */
    val hasContent: Boolean
        get() = isRunning || permissionIntentSender != null || message != null
}

/**
 * 艺术家详情页的数据，以及「设置艺术家海报」的两条路径：内嵌到每首歌的标签，或存进海报文件夹。
 */
class ArtistDetailViewModel(
    private val libraryIndexRepository: LibraryIndexRepository,
    private val artistId: Long,
    private val audioTagRepository: AudioTagRepository,
    private val saveAudioTagsUseCase: SaveAudioTagsUseCase,
    private val settingsRepository: SettingsRepository,
    private val application: Application
) : ViewModel() {

    private val TAG = "ArtistDetailVM"

    val artist: StateFlow<ArtistEntity?> = libraryIndexRepository
        .observeArtistById(artistId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    val songs: StateFlow<List<SongEntity>> = libraryIndexRepository
        .observeSongsByArtistId(artistId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val albums: StateFlow<List<AlbumEntity>> = libraryIndexRepository
        .observeAlbumsByArtistId(artistId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /** 已配置的艺术家海报文件夹；界面用它决定「存进文件夹」是否已有去处。 */
    val artistPosterFolder: StateFlow<String?> = settingsRepository.artistPosterFolder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    /** 海报描述要与艺术家字段的拆分规则对齐，所以读同一份配置。 */
    private val artistSplitConfig = settingsRepository.artistSplitConfigFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ArtistSplitConfig()
        )

    private val _artistPosterEmbedState = MutableStateFlow(ArtistPosterEmbedUiState())
    val artistPosterEmbedState: StateFlow<ArtistPosterEmbedUiState> = _artistPosterEmbedState.asStateFlow()

    private var artistPosterJob: Job? = null

    /**
     * 读取选图器返回的本地图片。URI 是单次授权，字节必须当场读出。
     */
    fun prepareArtistImage(context: Context, uri: Uri, onReady: (ArtistImageSource) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) {
                try {
                    val resolver = appContext.contentResolver
                    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        null
                    } else {
                        ArtistImageSource(
                            picture = AudioPicture(
                                data = bytes,
                                mimeType = resolver.getType(uri)
                                    ?.substringBefore(';')
                                    ?.trim()
                                    ?.takeIf { it.startsWith("image/") }
                                    ?: "image/jpeg",
                                description = "",
                                pictureType = AudioPictureType.Artist.tagLibName
                            ),
                            fileName = uri.lastPathSegment
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "读取艺术家海报失败: $uri", e)
                    null
                }
            }

            if (source == null) {
                _artistPosterEmbedState.update {
                    it.copy(message = resolvedMessage(R.string.msg_read_artist_image_failed))
                }
            }
            if (source != null) onReady(source)
        }
    }

    /**
     * 把 [source] 内嵌进这位艺术家的每一首歌。已是这张图的歌会跳过，授权后重跑只补未写的。
     */
    fun embedArtistImageForAllSongs(
        artistName: String,
        targetSongs: List<SongEntity>,
        source: ArtistImageSource
    ) {
        val name = artistName.trim()
        if (name.isEmpty() || targetSongs.isEmpty()) return

        artistPosterJob?.cancel()
        _artistPosterEmbedState.value = ArtistPosterEmbedUiState(
            isRunning = true,
            total = targetSongs.size
        )

        artistPosterJob = viewModelScope.launch {
            val failures = mutableListOf<String>()
            var done = 0
            var skipped = 0
            var needsPermission = false

            try {
                for ((index, song) in targetSongs.withIndex()) {
                    _artistPosterEmbedState.update {
                        it.copy(done = index, activeSong = song.displayTitle())
                    }

                    when (val result = writeArtistPicture(song, artistName = name, source = source.picture)) {
                        null -> skipped++
                        is SaveAudioTagsResult.PermissionRequired -> {
                            needsPermission = true
                            _artistPosterEmbedState.update {
                                it.copy(
                                    done = index,
                                    skipped = skipped,
                                    isRunning = false,
                                    activeSong = null,
                                    failures = failures.toList(),
                                    permissionIntentSender = result.intentSender,
                                    message = resolvedMessage(R.string.msg_artist_poster_permission_required)
                                )
                            }
                            break
                        }

                        is SaveAudioTagsResult.Failed -> failures += song.displayTitle()
                        is SaveAudioTagsResult.Success -> Unit
                    }

                    done = index + 1
                    _artistPosterEmbedState.update {
                        it.copy(done = done, skipped = skipped, failures = failures.toList())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "写入艺术家海报时出错", e)
                _artistPosterEmbedState.update {
                    it.copy(
                        isRunning = false,
                        activeSong = null,
                        failures = failures.toList(),
                        message = resolvedMessage(R.string.msg_artist_poster_embed_failed)
                    )
                }
                return@launch
            }

            if (needsPermission) return@launch

            _artistPosterEmbedState.update {
                it.copy(
                    isRunning = false,
                    done = done,
                    activeSong = null,
                    failures = failures.toList(),
                    message = if (skipped > 0) {
                        resolvedMessage(R.string.msg_artist_poster_embed_result, done - skipped - failures.size, skipped, failures.size)
                    } else if (failures.isEmpty()) {
                        resolvedMessage(R.string.msg_artist_poster_embedded, done)
                    } else {
                        resolvedMessage(
                            R.string.msg_artist_poster_embed_partial,
                            done - failures.size,
                            failures.size
                        )
                    }
                )
            }
        }
    }

    /**
     * 把 [source] 按艺术家名存进艺术家海报文件夹；没有可用文件夹时写入失败并提示。
     */
    fun saveArtistImageToFolder(artistName: String, source: ArtistImageSource) {
        val name = artistName.trim()
        if (name.isEmpty()) return

        artistPosterJob?.cancel()
        _artistPosterEmbedState.value = ArtistPosterEmbedUiState(
            isRunning = true,
            total = 1,
            activeSong = source.fileName
        )

        artistPosterJob = viewModelScope.launch {
            val writtenName = try {
                val folder = settingsRepository.artistPosterFolder.first()
                ArtistPosterFileWriter.write(
                    context = application.applicationContext,
                    artistName = name,
                    data = source.picture.data,
                    mimeType = source.picture.mimeType,
                    posterFolder = folder
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "保存艺术家海报文件失败", e)
                null
            }

            // 外置海报是按文件名找的，写完要让取图链路重新去读一次
            if (writtenName != null) {
                refreshArtistPostersSafely()
            }

            _artistPosterEmbedState.update {
                it.copy(
                    isRunning = false,
                    done = if (writtenName != null) 1 else 0,
                    activeSong = null,
                    message = if (writtenName != null) {
                        resolvedMessage(R.string.msg_artist_poster_saved_to_folder, writtenName)
                    } else {
                        resolvedMessage(R.string.msg_artist_poster_folder_save_failed)
                    }
                )
            }
        }
    }

    fun cancelArtistPosterWrite() {
        artistPosterJob?.cancel()
        artistPosterJob = null
        _artistPosterEmbedState.update {
            it.copy(
                isRunning = false,
                activeSong = null,
                permissionIntentSender = null,
                message = resolvedMessage(R.string.msg_artist_poster_cancelled)
            )
        }
    }

    /**
     * 注册新的艺术家海报文件夹。持久化权限由界面回调负责，这里只登记并触发重新读取。
     */
    fun setArtistPosterFolder(uri: Uri, onAdded: () -> Unit) {
        artistPosterJob = viewModelScope.launch {
            try {
                settingsRepository.setArtistPosterFolder(uri.toString())
                refreshArtistPostersSafely()
                onAdded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "添加艺术家海报文件夹失败", e)
                _artistPosterEmbedState.update {
                    it.copy(message = resolvedMessage(R.string.artist_poster_folder_error))
                }
            }
        }
    }

    /** 授权界面已经拉起，把这次请求消费掉，避免重组时反复弹窗。 */
    fun consumeArtistPosterPermissionRequest() {
        _artistPosterEmbedState.update { it.copy(permissionIntentSender = null) }
    }

    /** 关掉进度面板：丢掉这一轮的结果，下次打开重新开始。 */
    fun clearArtistPosterStatus() {
        if (_artistPosterEmbedState.value.isRunning) return
        _artistPosterEmbedState.value = ArtistPosterEmbedUiState()
    }

    fun clearArtistPosterMessage() {
        _artistPosterEmbedState.update { it.copy(message = null) }
    }

    /** 把文案在 ViewModel 里就取好，界面不必再持有 Context 或消息模型。 */
    private fun resolvedMessage(@StringRes resId: Int, vararg args: Any): String =
        application.getString(resId, *args)

    /**
     * 把 [source] 写进 [song]：读取当前标签，按该歌的艺术家字段算出描述，再整份写回。
     * 必须整份覆盖：只改一张会让写入与读取的图片列表对不上。
     */
    private suspend fun writeArtistPicture(
        song: SongEntity,
        artistName: String,
        source: AudioPicture
    ): SaveAudioTagsResult? {
        val current = audioTagRepository.read(song.uri, com.lonx.lyrico.data.song.tag.AudioTagReadOptions(strict = true))
        // MP4/M4A cannot retain an artist type or owner description. Leave its covers intact.
        if (!current.supportsTypedPictures) return null
        val artistNames = ArtistNameSplitter.splitArtists(current.artist, artistSplitConfig.value)
        val nextPictures = com.lonx.lyrico.domain.poster.ArtistPosterBatchEdit.apply(
            current.pictures, artistNames, setOf(artistName), source
        )
        if (nextPictures == current.pictures) return SaveAudioTagsResult.Success(song = song, tagData = current)

        return saveAudioTagsUseCase.overwrite(
            uri = song.uri,
            tagData = current.copy(pictures = nextPictures),
            picturesAuthored = true
        )
    }

    private fun SongEntity.displayTitle(): String =
        title?.takeIf { it.isNotBlank() } ?: fileName

    private suspend fun refreshArtistPostersSafely() {
        try {
            settingsRepository.refreshArtistPosters()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "刷新艺术家海报缓存失败", e)
        }
    }
}
