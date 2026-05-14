package com.lonx.lyrico.data.repository

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.LocalSearchType
import com.lonx.lyrico.data.model.dao.AlbumSearchRow
import com.lonx.lyrico.data.model.dao.ArtistSearchRow
import com.lonx.lyrico.data.model.dao.SongFieldValue
import com.lonx.lyrico.data.model.entity.SongEntity
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲数据存储库接口
 * 定义了应用程序中歌曲数据的核心操作，包括数据库同步、元数据读写、搜索等。
 */
interface SongRepository {


    /**
     * 从文件系统中删除歌曲文件
     *
     * @param SongEntity 要删除的歌曲
     */
    suspend fun deleteSong(song: SongEntity)

    suspend fun deleteSongs(songs: List<SongEntity>)


    suspend fun getSongByUri(uri: String): SongEntity?

    suspend fun getDistinctSongFieldValues(
        uris: List<String>,
        fieldColumn: String
    ): List<SongFieldValue>

    /**
     * 同步数据库与设备文件
     *
     * 扫描设备上的音频文件，将新发现的歌曲添加到数据库，更新已修改的歌曲，并移除数据库中不再存在的文件记录。
     * 此外，还会更新最后一次扫描的时间戳。
     *
     * @param fullRescan 是否进行彻底的重新扫描（强制读取所有文件的元数据，忽略修改时间检查）
     */
    suspend fun synchronize(
        fullRescan: Boolean,
        folderIds: Set<Long>? = null,
        onProgress: (suspend (LibraryScanProgress) -> Unit)? = null
    )

    /**
     * 更新歌曲元数据（仅更新数据库）
     *
     * @param updates 要更新的歌曲列表，包含歌曲实体和音频标签数据
     */
    suspend fun updateMetadatas(updates: List<Pair<SongEntity, AudioTagData>>)

    /**
     * 根据查询条件搜索歌曲
     *
     * @param query 搜索关键词，会同时匹配标题、艺术家和专辑。
     * @param type 搜索类型
     * @return 返回匹配的 [SongEntity] 列表 Flow 流。
     */
    fun searchSongs(query: String,type: LocalSearchType): Flow<List<SongEntity>>

    fun searchSongsForLocalSearch(query: String): Flow<List<SongEntity>>

    fun searchAlbumsForLocalSearch(query: String): Flow<List<AlbumSearchRow>>

    fun searchArtistsForLocalSearch(query: String): Flow<List<ArtistSearchRow>>

    fun observeSongsByAlbumForSearch(
        album: String,
        albumArtist: String?
    ): Flow<List<SongEntity>>

    fun observeSongsByArtistForSearch(artist: String): Flow<List<SongEntity>>

    fun observeAlbumsByArtistForSearch(artist: String): Flow<List<AlbumSearchRow>>

    /**
     * 更新歌曲元数据（仅更新数据库）
     *
     * @param audioTagData 新的音频标签数据
     * @param contentUri 歌曲文件uri
     * @param lastModified 文件最后修改时间戳
     * @return 如果更新成功返回 true，否则返回 false。
     */
    suspend fun updateSongMetadata(audioTagData: AudioTagData, contentUri: String, lastModified: Long): Boolean

    /**
     * 将元数据写入物理音频文件
     *
     * @param contentUri 目标文件uri
     * @param audioTagData 要写入的音频标签数据
     * @return 如果写入操作成功返回 true，否则返回 false。
     */
    suspend fun overwriteAudioTags(contentUri: String, audioTagData: AudioTagData): Boolean

    /**
     * 增量更新音频标签（只写入非 null 字段）
     *
     * 与 [overwriteAudioTags] 不同，此方法只会更新 [AudioTagData] 中非 null 的字段，
     * null 字段会被忽略，不会清空原有值。适用于批量匹配等场景，避免意外覆盖未指定的字段。
     *
     * @param contentUri 目标文件uri
     * @param audioTagData 要更新的音频标签数据（null 字段将被忽略）
     * @return 如果写入操作成功返回 true，否则返回 false。
     */
    suspend fun patchAudioTags(contentUri: String, audioTagData: AudioTagData): Boolean

    /**
     * 读取物理音频文件的元数据
     *
     * @param contentUri 文件uri
     * @return 返回读取到的 [AudioTagData]，如果读取失败会尝试返回包含文件名的基础数据。
     */
    suspend fun readAudioTagData(contentUri: String): AudioTagData

    /**
     * 获取数据库中歌曲总数
     *
     * @return 歌曲数量
     */
    suspend fun getSongCount(): Int

    /**
     * 清空数据库中的所有歌曲数据
     */
    suspend fun clearAll()

    /**
     * 获取已排序的歌曲列表
     *
     * @param sortBy 排序字段 (如标题、艺术家、日期等)
     * @param order 排序顺序 (升序或降序)
     * @return 返回排序后的 [SongEntity] 列表 Flow 流。
     */
    fun observeSongs(sortBy: SortBy, order: SortOrder): Flow<List<SongEntity>>

    /**
     * 获取文件显示名称
     *
     * 尝试通过 ContentResolver 获取文件名，如果失败则从路径中提取。
     *
     * @param contentUri 文件路径或 URI
     * @return 文件名称
     */
    fun getDisplayName(contentUri: String): String

    /**
     * 根据专辑和艺术家获取歌曲列表
     * 优先返回同专辑且同艺术家的歌曲，然后返回同专辑的歌曲
     *
     * @param album 专辑名称
     * @param artist 艺术家名称
     * @return 歌曲列表
     */
    suspend fun getSongsByAlbum(album: String, artist: String): List<SongEntity>

    suspend fun renameSong(song: SongEntity, newFileName: String): Boolean

    suspend fun renameSongAndGetResult(song: SongEntity, newFileName: String): RenameSongResult?
}

data class RenameSongResult(
    val oldUri: String,
    val newUri: String,
    val newFilePath: String,
    val newFileName: String
)

data class LibraryScanProgress(
    val stage: LibraryScanStage,
    val current: Int = 0,
    val total: Int = 0,
    val currentFile: String? = null
)

enum class LibraryScanStage {
    LISTING_FILES,
    READING_METADATA,
    WRITING_DATABASE,
    FINISHED
}
