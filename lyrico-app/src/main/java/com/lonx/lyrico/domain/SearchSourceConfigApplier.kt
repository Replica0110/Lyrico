package com.lonx.lyrico.domain

import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.model.SourceRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SearchSourceConfigApplier(
    private val settingsRepository: SettingsRepository,
    private val searchSources: List<SearchSource>
) {
    suspend fun applyOnce() {
        apply(settingsRepository.sourceSettingsFlow.first())
    }

    fun apply(configs: Map<Source, SourceRuntimeConfig>) {
        searchSources.forEach { source ->
            source.applyConfig(configs[source.sourceType] ?: SourceRuntimeConfig())
        }
    }

    fun observeIn(scope: CoroutineScope): Job {
        return settingsRepository.sourceSettingsFlow
            .onEach(::apply)
            .launchIn(scope)
    }
}
