package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.model.BatchMatchHistory
import com.lonx.lyrico.data.model.BatchMatchResult
import com.lonx.lyrico.data.model.entity.BatchMatchRecordEntity
import com.lonx.lyrico.data.repository.BatchMatchHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class BatchMatchHistoryUiState(
    val historyList: List<BatchMatchHistory> = emptyList(),
    val records: List<BatchMatchRecordEntity> = emptyList(),
    val selectedTab: BatchMatchResult = BatchMatchResult.SUCCESS,
    val isLoading: Boolean = false
)

class BatchMatchHistoryViewModel(
    private val repository: BatchMatchHistoryRepository
) : ViewModel() {

    private val selectedTabFlow =
        MutableStateFlow(BatchMatchResult.SUCCESS)

    private val historyIdFlow =
        MutableStateFlow<Long?>(null)

    private val _uiState =
        MutableStateFlow(BatchMatchHistoryUiState(isLoading = true))

    val uiState: StateFlow<BatchMatchHistoryUiState> =
        _uiState.asStateFlow()

    init {
        observeHistoryList()
        observeRecords()
    }

    /**
     * 由 UI 调用，告诉 ViewModel 当前页面对应的 historyId
     */
    fun loadHistory(historyId: Long) {
        historyIdFlow.value = historyId
    }

    fun onTabSelected(status: BatchMatchResult) {
        selectedTabFlow.value = status
    }

    private fun observeHistoryList() {
        viewModelScope.launch {
            repository.getAllHistory().collect { list ->
                _uiState.update {
                    it.copy(historyList = list)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeRecords() {
        viewModelScope.launch {

            historyIdFlow
                .filterNotNull()
                .flatMapLatest { historyId ->
                    repository.getRecordsByHistoryId(historyId)
                }
                .combine(selectedTabFlow) { records, tab ->
                    records.filter { it.status == tab }
                }
                .collect { filteredRecords ->
                    _uiState.update {
                        it.copy(
                            records = filteredRecords,
                            selectedTab = selectedTabFlow.value,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    suspend fun getHistoryById(id: Long): BatchMatchHistory? {
        return repository.getHistoryById(id)
    }
}
