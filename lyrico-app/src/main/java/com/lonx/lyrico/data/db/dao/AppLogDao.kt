package com.lonx.lyrico.data.db.dao

import com.lonx.lyrico.data.log.AppLogLevel
import com.lonx.lyrico.data.log.AppLogType
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lonx.lyrico.data.db.entity.AppLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLogDao {
    @Insert
    suspend fun insert(log: AppLogEntity)

    @Query("SELECT * FROM app_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeLatest(limit: Int = 300): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLatest(limit: Int = 1000): List<AppLogEntity>

    @Query("SELECT * FROM app_logs WHERE (:level IS NULL OR level = :level) AND (:type IS NULL OR type = :type) ORDER BY createdAt DESC LIMIT 300")
    fun observeFiltered(level: AppLogLevel?, type: AppLogType?): Flow<List<AppLogEntity>>

    @Query("SELECT id FROM app_logs WHERE (:level IS NULL OR level = :level) AND (:type IS NULL OR type = :type)")
    suspend fun getMatchingIds(level: AppLogLevel?, type: AppLogType?): List<Long>

    @Query("SELECT * FROM app_logs WHERE id IN (:ids) ORDER BY createdAt DESC")
    suspend fun getByIds(ids: List<Long>): List<AppLogEntity>

    @Query("SELECT * FROM app_logs WHERE relatedId = :relatedId ORDER BY createdAt DESC")
    fun observeByRelatedId(relatedId: String): Flow<List<AppLogEntity>>

    @Query("DELETE FROM app_logs")
    suspend fun clear()

    @Query("DELETE FROM app_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM app_logs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM app_logs WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
