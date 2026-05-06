package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.AppLogLevel
import com.lonx.lyrico.data.model.AppLogType
import com.lonx.lyrico.data.model.entity.AppLogEntity
import kotlinx.coroutines.flow.Flow

interface AppLogRepository {
    fun observeLatest(limit: Int = 300): Flow<List<AppLogEntity>>
    fun observeByRelatedId(relatedId: String): Flow<List<AppLogEntity>>
    suspend fun getLatest(limit: Int = 1000): List<AppLogEntity>
    suspend fun getByIds(ids: List<Long>): List<AppLogEntity>
    suspend fun exportText(limit: Int = 1000): String
    suspend fun exportText(ids: List<Long>): String
    suspend fun log(
        level: AppLogLevel,
        type: AppLogType,
        tag: String,
        message: String,
        detail: String? = null,
        relatedId: String? = null
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
