package com.lonx.audiotag.model

/**
 * AudioProperties contains information about the audio properties of a file.
 *
 * @property length Length in milliseconds
 * @property bitrate Bitrate in kbps
 * @property sampleRate Sample rate in Hz
 * @property channels Number of channels
 */
public data class AudioProperties(
    val length: Int,
    val bitrate: Int,
    val sampleRate: Int,
    val channels: Int,
)
