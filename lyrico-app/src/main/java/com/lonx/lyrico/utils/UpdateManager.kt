package com.lonx.lyrico.utils

import androidx.annotation.StringRes
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.R
import com.lonx.lyrico.data.dto.ReleaseInfo
import com.lonx.lyrico.data.model.UpdateCheckResult
import com.lonx.lyrico.data.repository.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class UpdateState(
    val isChecking: Boolean = false,
    val releaseInfo: ReleaseInfo? = null
)
data class UpdateEffect(
    @field:StringRes val messageRes: Int,
    val formatArgs: List<Any> = emptyList()
)

interface UpdateManager {
    val state: StateFlow<UpdateState>
    val effect: Flow<UpdateEffect>
    fun checkForUpdate()

    fun dismissUpdateDialog()
    fun resetUpdateState()
}
class UpdateManagerImpl(
    private val appScope: CoroutineScope,
    private val updateRepository: UpdateRepository
): UpdateManager {
    private val _state = MutableStateFlow(UpdateState())
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val _effect = MutableSharedFlow<UpdateEffect>()
    override val effect: Flow<UpdateEffect> = _effect.asSharedFlow()

    override fun checkForUpdate() {

        if (_state.value.isChecking) return

        appScope.launch {
            _state.update { it.copy(isChecking = true) }
            when (val result = updateRepository.checkForUpdate(
                owner = OWNER_ID,
                repo = REPO_NAME
            )) {
                is UpdateCheckResult.NewVersion -> {
                    _state.update { it.copy(releaseInfo = result.info) }
                }
                is UpdateCheckResult.NoUpdateAvailable -> {
                    _effect.emit(UpdateEffect(R.string.update_already_latest))
                }
                is UpdateCheckResult.ApiError -> {
                    _effect.emit(UpdateEffect(R.string.update_api_error))
                }
                is UpdateCheckResult.NetworkError -> {
                    _effect.emit(UpdateEffect(R.string.update_network_error))
                }
                UpdateCheckResult.ParsingError -> {
                    _effect.emit(UpdateEffect(R.string.update_parse_error))
                }
                UpdateCheckResult.TimeoutError -> {
                    _effect.emit(UpdateEffect(R.string.update_timeout))
                }
            }
            _state.update { it.copy(isChecking = false) }
        }
    }
    override fun dismissUpdateDialog() {
        _state.update { it.copy(
            releaseInfo = null
        ) }
    }
    override fun resetUpdateState() {
        _state.update {
            it.copy(
                isChecking = false,
                releaseInfo = null
            )
        }
    }

}