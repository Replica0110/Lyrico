package com.lonx.lyrico.ui.update

import androidx.annotation.StringRes
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.R
import com.lonx.lyrico.data.update.ReleaseInfo
import com.lonx.lyrico.data.update.UpdateCheckResult
import com.lonx.lyrico.data.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UpdatePromptUiState(
    val isChecking: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val releaseInfo: ReleaseInfo? = null
)
data class UpdatePromptEffect(
    @field:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
)

class UpdatePromptCoordinator(
    private val appScope: CoroutineScope,
    private val updateRepository: UpdateRepository
) {
    private val _state = MutableStateFlow(UpdatePromptUiState())
    val state: StateFlow<UpdatePromptUiState> = _state.asStateFlow()
    private val _effect = MutableSharedFlow<UpdatePromptEffect>()
    val effect: Flow<UpdatePromptEffect> = _effect.asSharedFlow()

    fun checkForUpdate() {

        if (_state.value.isChecking) return

        appScope.launch {
            _state.update { it.copy(isChecking = true) }
            when (val result = updateRepository.checkForUpdate(
                owner = OWNER_ID,
                repo = REPO_NAME
            )) {
                is UpdateCheckResult.NewVersion -> {
                    _state.update { it.copy(releaseInfo = result.info, showUpdateDialog = true) }
                }
                is UpdateCheckResult.NoUpdateAvailable -> {
                    _effect.emit(UpdatePromptEffect(R.string.update_already_latest))
                }
                is UpdateCheckResult.ApiError -> {
                    _effect.emit(UpdatePromptEffect(R.string.update_api_error))
                }
                is UpdateCheckResult.NetworkError -> {
                    _effect.emit(UpdatePromptEffect(R.string.update_network_error))
                }
                UpdateCheckResult.ParsingError -> {
                    _effect.emit(UpdatePromptEffect(R.string.update_parse_error))
                }
                UpdateCheckResult.TimeoutError -> {
                    _effect.emit(UpdatePromptEffect(R.string.update_timeout))
                }
            }
            _state.update { it.copy(isChecking = false) }
        }
    }
    fun dismissUpdateDialog() {
        _state.update { it.copy(
            showUpdateDialog = false
        ) }
    }
    fun resetUpdateState() {
        _state.update {
            // A new result may reopen the dialog before the previous dismissal finishes.
            if (it.showUpdateDialog) it else it.copy(releaseInfo = null)
        }
    }

}
