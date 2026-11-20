package com.lonx.lyrico.ui.screens.about

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonx.lyrico.App.Companion.OWNER_ID
import com.lonx.lyrico.App.Companion.REPO_NAME
import com.lonx.lyrico.data.model.contributor.ContributorInfo
import com.lonx.lyrico.data.repository.GhContributorRepository
import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

sealed interface UiError {
    data object LoadFailed : UiError
    data class Message(val text: String) : UiError
}
enum class ProVersionTapResult { NONE, ACTIVATED, CONFIRM_DEACTIVATION }

class AboutViewModel(
    private val settingsRepository: SettingsRepository,
    private val updateManager: UpdateManager,
    private val contributorRepository: GhContributorRepository
) : ViewModel() {
    val proActivated = settingsRepository.proActivated
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    private val versionTapMutex = Mutex()
    private var versionTapCount = 0
    private var lastVersionTap = 0L

    /** Only successive taps count; persistence must succeed before reporting activation. */
    suspend fun onVersionTap(): ProVersionTapResult = versionTapMutex.withLock {
        val now = SystemClock.elapsedRealtime()
        versionTapCount = if (now - lastVersionTap <= 1500L) versionTapCount + 1 else 1
        lastVersionTap = now
        if (versionTapCount < 7) return@withLock ProVersionTapResult.NONE
        versionTapCount = 0
        if (settingsRepository.proActivated.first()) return@withLock ProVersionTapResult.CONFIRM_DEACTIVATION
        settingsRepository.activatePro()
        ProVersionTapResult.ACTIVATED
    }

    val checkUpdateEnabled: StateFlow<Boolean> =
        settingsRepository.checkUpdateEnabled
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val updateEffect = updateManager.effect
    private val _contributors = MutableStateFlow<List<ContributorInfo>>(emptyList())
    val contributors: StateFlow<List<ContributorInfo>> = _contributors

    private val _loadingContributors = MutableStateFlow(false)
    val loadingContributors: StateFlow<Boolean> = _loadingContributors

    private val _contributorsError = MutableStateFlow<UiError?>(null)
    val contributorsError: StateFlow<UiError?> = _contributorsError
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
                    _contributorsError.value =
                        it.message?.let { msg -> UiError.Message(msg) }
                            ?: UiError.LoadFailed
                }

            _loadingContributors.value = false
        }
    }
}
