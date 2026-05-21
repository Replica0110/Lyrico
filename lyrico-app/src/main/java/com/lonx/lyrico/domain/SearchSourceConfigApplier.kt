package com.lonx.lyrico.domain

import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.plugin.source.SearchSourceProvider
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SearchSourceConfigApplier(
    private val settingsRepository: SettingsRepository,
    private val searchSourceProvider: SearchSourceProvider
) {
    suspend fun applyOnce() {
        apply(settingsRepository.sourceSettingsByIdFlow.first())
    }

    suspend fun apply(configs: Map<String, SourceRuntimeConfig>) {
        searchSourceProvider.getAllSources().forEach { source ->
            source.applyConfig(configs[source.id] ?: SourceRuntimeConfig())
        }
    }

    fun observeIn(scope: CoroutineScope): Job {
        return combine(
            settingsRepository.sourceSettingsByIdFlow,
            searchSourceProvider.observeAllSources()
        ) { configs, sources -> configs to sources }
            .onEach { (configs, sources) ->
                sources.forEach { source ->
                    source.applyConfig(configs[source.id] ?: SourceRuntimeConfig())
                }
            }
            .launchIn(scope)
    }
}
