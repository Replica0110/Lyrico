package com.lonx.lyrico.domain.song.usecase

import com.lonx.audiotag.model.AudioTagData
import com.lonx.lyrico.data.settings.SettingsRepository
import com.lonx.lyrico.data.song.tag.AudioTagReadOptions
import com.lonx.lyrico.data.song.tag.AudioTagRepository
import kotlinx.coroutines.flow.first

class ReadAudioTagsUseCase(
    private val audioTagRepository: AudioTagRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(uri: String): AudioTagData {
        return audioTagRepository.read(
            uri = uri,
            options = AudioTagReadOptions(
                multiValueSeparator = settingsRepository.separator.first()
            )
        )
    }
}
