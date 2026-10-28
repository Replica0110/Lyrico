package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.log.AppLogLevel
import com.lonx.lyrico.data.model.log.AppLogType
import com.lonx.lyrico.data.model.entity.AppLogEntity
import kotlinx.coroutines.flow.Flow
import com.lonx.lyrico.data.model.log.LogRecordingState
import kotlinx.coroutines.flow.StateFlow

interface AppLogRepository {
    val recordingState: StateFlow<LogRecordingState>
    fun startRecording()
    fun stopRecording()
    fun clearRecording()
    suspend fun exportRecording(expectedSessionId: Long? = null): String
    fun observeLatest(limit: Int = 300): Flow<List<AppLogEntity>>
    fun observeFiltered(level: AppLogLevel?, type: AppLogType?): Flow<List<AppLogEntity>>
    suspend fun getMatchingIds(level: AppLogLevel?, type: AppLogType?): List<Long>
    fun observeByRelatedId(relatedId: String): Flow<List<AppLogEntity>>
    suspend fun getLatest(limit: Int = 1000): List<AppLogEntity>
    suspend fun getByIds(ids: List<Long>): List<AppLogEntity>
    suspend fun exportText(limit: Int = 1000): String
    suspend fun exportText(ids: List<Long>): String
    /**
     * [recordingStartedAt]: null captures the current session at call time; 0 explicitly excludes
     * recording; a positive value identifies the session captured by an asynchronous caller.
     * [occurredAt] is the event time, rather than the later database insertion time.
     */
    suspend fun log(
        level: AppLogLevel,
        type: AppLogType,
        tag: String,
        message: String,
        detail: String? = null,
        relatedId: String? = null,
        recordingStartedAt: Long? = null,
        occurredAt: Long = System.currentTimeMillis()
    )
    suspend fun logException(
        type: AppLogType,
        tag: String,
        message: String,
        throwable: Throwable,
        relatedId: String? = null
    )
    suspend fun clear()
    suspend fun deleteByIds(ids: List<Long>)
    suspend fun trim()
    suspend fun applyRetentionPolicy()
}
