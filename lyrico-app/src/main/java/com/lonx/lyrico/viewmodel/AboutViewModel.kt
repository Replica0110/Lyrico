package com.lonx.lyrico.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.data.model.GitHubContributor
import com.lonx.lyrico.data.repository.GhContributorRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.utils.UpdateEffect
import com.lonx.lyrico.utils.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AboutUiState(
    val checkUpdateEnabled: Boolean = true,
    val updateEffect: UpdateEffect = UpdateEffect("立即检查应用是否有更新"),

    val contributors: List<GitHubContributor> = emptyList(),
    val loadingContributors: Boolean = false,
    val contributorsError: String? = null
)

class AboutViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
    private val contributorRepository: GhContributorRepository
) : ViewModel() {

    val checkUpdateEnabled: StateFlow<Boolean> =
        settingsRepository.checkUpdateEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val updateEffect = updateManager.effect
    private val _contributors = MutableStateFlow<List<GitHubContributor>>(emptyList())
    val contributors: StateFlow<List<GitHubContributor>> = _contributors

    private val _loadingContributors = MutableStateFlow(false)
    val loadingContributors: StateFlow<Boolean> = _loadingContributors

    private val _contributorsError = MutableStateFlow<String?>(null)
    val contributorsError: StateFlow<String?> = _contributorsError
    init {
        loadContributors()
    }
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
    fun loadContributors() {
        viewModelScope.launch {
            _loadingContributors.value = true
            _contributorsError.value = null

            val result = contributorRepository.getContributors(
                owner = OWNER_ID,
                repo = REPO_NAME
            )

            result
                .onSuccess {
                    _contributors.value = it
                }
                .onFailure {
                    _contributorsError.value = it.message ?: "加载失败"
                }

            _loadingContributors.value = false
        }
    }
}