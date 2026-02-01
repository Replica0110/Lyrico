package com.lonx.lyrico.data.repository

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.model.SongEntity
import com.lonx.lyrico.viewmodel.SortBy
import com.lonx.lyrico.viewmodel.SortOrder
import kotlinx.coroutines.flow.Flow

/**
 * 歌曲数据存储库接口
 * 定义了应用程序中歌曲数据的核心操作，包括数据库同步、元数据读写、搜索等。
 */
interface SongRepository {

    /**
     * 同步数据库与设备文件
     *
     * 扫描设备上的音频文件，将新发现的歌曲添加到数据库，更新已修改的歌曲，并移除数据库中不再存在的文件记录。
     * 此外，还会更新最后一次扫描的时间戳。
     */
    suspend fun synchronizeWithDevice()

    /**
     * 获取所有歌曲
     *
     * @return 返回包含所有 [SongEntity] 的 Flow 流。
     */
    fun getAllSongs(): Flow<List<SongEntity>>

    /**
     * 根据查询条件搜索歌曲
     *
     * @param query 搜索关键词，会同时匹配标题、艺术家和专辑。
     * @return 返回匹配的 [SongEntity] 列表 Flow 流。
     */
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * 更新歌曲元数据（仅更新数据库）
     *
     * @param audioTagData 新的音频标签数据
     * @param filePath 歌曲文件路径
     * @param lastModified 文件最后修改时间戳
     * @return 如果更新成功返回 true，否则返回 false。
     */
    suspend fun updateSongMetadata(audioTagData: AudioTagData, filePath: String, lastModified: Long): Boolean

    /**
     * 将元数据写入物理音频文件
     *
     * @param filePath 目标文件路径
     * @param audioTagData 要写入的音频标签数据
     * @return 如果写入操作成功返回 true，否则返回 false。
     */
    suspend fun writeAudioTagData(filePath: String, audioTagData: AudioTagData): Boolean

    /**
     * 读取物理音频文件的元数据
     *
     * @param filePath 文件路径
     * @return 返回读取到的 [AudioTagData]，如果读取失败会尝试返回包含文件名的基础数据。
     */
    suspend fun readAudioTagData(filePath: String): AudioTagData

    /**
     * 获取数据库中歌曲总数
     *
     * @return 歌曲数量
     */
    suspend fun getSongsCount(): Int

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
    fun getAllSongsSorted(sortBy: SortBy, order: SortOrder): Flow<List<SongEntity>>

    /**
     * 获取文件显示名称
     *
     * 尝试通过 ContentResolver 获取文件名，如果失败则从路径中提取。
     *
     * @param filePath 文件路径或 URI
     * @return 文件名称
     */
    fun getFileName(filePath: String): String
}