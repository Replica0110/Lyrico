package com.lonx.lyrico.domain

import com.lonx.lyrico.data.repository.SettingsRepository
import com.lonx.lyrico.data.model.lyrics.SearchSource
import com.lonx.lyrico.data.model.lyrics.SourceRuntimeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class SearchSourceConfigApplier(
    private val settingsRepository: SettingsRepository
) {
    private fun apply(
        sources: List<SearchSource>,
        configs: Map<String, SourceRuntimeConfig>
    ) {
        sources.forEach { source ->
            source.applyConfig(configs[source.id] ?: SourceRuntimeConfig())
        }
    }

    fun observeIn(
        scope: CoroutineScope,
        sources: Flow<List<SearchSource>>
    ): Job {
        return combine(
            settingsRepository.sourceSettingsByIdFlow,
            sources
        ) { configs, sources -> configs to sources }
            .onEach { (configs, sources) ->
                apply(sources, configs)
            }
            .launchIn(scope)
    }
}
