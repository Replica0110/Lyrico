package com.lonx.lyrico.data.repository

import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.dao.BatchMatchHistoryDao
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.model.BatchMatchResult
import kotlinx.coroutines.flow.Flow

interface BatchMatchHistoryRepository {
    fun getAllHistory(): Flow<List<BatchMatchHistory>>
    suspend fun getHistoryById(id: Long): BatchMatchHistory?
    fun getRecordsByHistoryId(historyId: Long): Flow<List<BatchMatchRecordEntity>>
    fun getRecordsByHistoryIdAndStatus(historyId: Long, status: BatchMatchResult): Flow<List<BatchMatchRecordEntity>>
    suspend fun saveHistory(history: BatchMatchHistory, records: List<BatchMatchRecordEntity>): Long
    suspend fun deleteHistory(id: Long)
    suspend fun clearAllHistory()
}

class BatchMatchHistoryRepositoryImpl(
    private val batchMatchHistoryDao: BatchMatchHistoryDao
) : BatchMatchHistoryRepository {
    override fun getAllHistory(): Flow<List<BatchMatchHistory>> {
        return batchMatchHistoryDao.getAllHistory()
    }

    override suspend fun getHistoryById(id: Long): BatchMatchHistory? {
        return batchMatchHistoryDao.getHistoryById(id)
    }

    override fun getRecordsByHistoryId(historyId: Long): Flow<List<BatchMatchRecordEntity>> {
        return batchMatchHistoryDao.getRecordsByHistoryId(historyId)
    }

    override fun getRecordsByHistoryIdAndStatus(
        historyId: Long,
        status: BatchMatchResult
    ): Flow<List<BatchMatchRecordEntity>> {
        return batchMatchHistoryDao.getRecordsByHistoryIdAndStatus(historyId, status)
    }

    override suspend fun saveHistory(history: BatchMatchHistory, records: List<BatchMatchRecordEntity>): Long {
        val historyId = batchMatchHistoryDao.insertHistory(history)
        val recordsWithId = records.map { it.copy(historyId = historyId) }
        batchMatchHistoryDao.insertRecords(recordsWithId)
        return historyId
    }

    override suspend fun deleteHistory(id: Long) {
        batchMatchHistoryDao.deleteHistory(id)
    }

    override suspend fun clearAllHistory() {
        batchMatchHistoryDao.clearAllHistory()
    }
}
