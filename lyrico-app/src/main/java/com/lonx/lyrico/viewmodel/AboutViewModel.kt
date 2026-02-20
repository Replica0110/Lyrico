package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.utils.UpdateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AboutUiState(
    val checkUpdateEnabled: Boolean = true,
    val updateEffect: UpdateEffect = UpdateEffect("立即检查应用是否有更新")
)

class AboutViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager
) : ViewModel() {

    val checkUpdateEnabled: StateFlow<Boolean> =
        settingsRepository.checkUpdateEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val updateEffect = updateManager.effect


    fun setCheckUpdateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.saveCheckUpdateEnabled(enabled)
        }
    }

    fun openBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun checkUpdate() {
        viewModelScope.launch {
            updateManager.checkForUpdate()
        }
    }
}