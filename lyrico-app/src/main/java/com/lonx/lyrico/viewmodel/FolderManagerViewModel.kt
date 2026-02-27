package com.lonx.lyrico.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.LyricoDatabase
import com.lonx.lyrico.data.model.entity.FolderEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class FolderManagerUiState(
    val folders: List<FolderEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FolderManagerViewModel(
    private val database: LyricoDatabase
) : ViewModel() {

    private val folderDao = database.folderDao()

    val uiState: StateFlow<FolderManagerUiState> =
        folderDao.getAllFolders()
            .map { folders ->
                FolderManagerUiState(folders = folders)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                FolderManagerUiState()
            )

    fun toggleFolderIgnore(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.setIgnored(folder.id, !folder.isIgnored)
        }
    }

    fun addFolderByPath(path: String) {
        viewModelScope.launch {
            val id = folderDao.upsertAndGetId(path, addedBySaf = true)
            folderDao.setIgnored(id, true)
        }
    }

    fun deleteFolder(folder: FolderEntity) {
        viewModelScope.launch {
            folderDao.deleteFolderPermanently(folder.id)
        }
    }
}
