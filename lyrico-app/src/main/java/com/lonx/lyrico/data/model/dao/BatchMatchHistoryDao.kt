package com.lonx.lyrico.data.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.model.BatchMatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchMatchHistoryDao {
    @Insert
    suspend fun insertHistory(history: BatchMatchHistory): Long

    @Insert
    suspend fun insertRecords(records: List<BatchMatchRecordEntity>)

    @Query("SELECT * FROM batch_match_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BatchMatchHistory>>

    @Query("SELECT * FROM batch_match_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): BatchMatchHistory?

    @Query("SELECT * FROM batch_match_records WHERE historyId = :historyId")
    fun getRecordsByHistoryId(historyId: Long): Flow<List<BatchMatchRecordEntity>>

    @Query("SELECT * FROM batch_match_records WHERE historyId = :historyId AND status = :status")
    fun getRecordsByHistoryIdAndStatus(historyId: Long, status: BatchMatchStatus): Flow<List<BatchMatchRecordEntity>>

    @Query("DELETE FROM batch_match_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM batch_match_history")
    suspend fun clearAllHistory()

}