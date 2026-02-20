package com.lonx.lyrico.utils

import com.lonx.lyrico.data.dto.UpdateDTO
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
    val updateDTO: UpdateDTO? = null,
    val info: String? = null,
)
data class UpdateEffect(val message: String)

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
            when (val result = updateRepository.checkForUpdate()) {
                is UpdateCheckResult.NewVersion -> {
                    _state.update { it.copy(updateDTO = result.info) }
                }
                is UpdateCheckResult.NoUpdateAvailable -> {
                    _state.update { it.copy(info = "已经是最新版本") }
                    _effect.emit(UpdateEffect("已经是最新版本"))
                }
                is UpdateCheckResult.ApiError -> {
                    _state.update { it.copy(info = "API错误: ${result.code}") }
                    _effect.emit(UpdateEffect("API错误: ${result.code}"))
                }
                is UpdateCheckResult.NetworkError -> {
                    _state.update { it.copy(info = "网络错误，请检查连接") }
                    _effect.emit(UpdateEffect("网络错误，请检查连接"))
                }
                UpdateCheckResult.ParsingError -> {
                    _state.update { it.copy(info = "解析更新信息失败") }
                    _effect.emit(UpdateEffect("解析更新信息失败"))
                }
                UpdateCheckResult.TimeoutError -> {
                    _state.update { it.copy(info = "检查更新超时") }
                    _effect.emit(UpdateEffect("检查更新超时"))
                }
            }
            _state.update { it.copy(isChecking = false) }
        }
    }
    override fun dismissUpdateDialog() {
        _state.update { it.copy(
            updateDTO = null
        ) }
    }
    override fun resetUpdateState() {
        _state.update {
            it.copy(
                isChecking = false,
                updateDTO = null,
                info = null
            )
        }
    }

}