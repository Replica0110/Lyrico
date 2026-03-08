package com.lonx.audiotag.internal

import java.io.FileInputStream

sealed interface MetadataResult {
    data class Success(val metadata: Metadata?) : MetadataResult

    data object NoMetadata : MetadataResult

    data object NotAudio : MetadataResult

    data object ProviderFailed : MetadataResult
}

internal object TagLibJNI {
    init {
        System.loadLibrary("tagJNI")
    }

    /**
     * Open a file and extract a tag.
     *
     * Note: This method is blocking and should be handled as such if calling from a coroutine.
     */
    fun open(deviceFile: AudioFile, fis: FileInputStream): MetadataResult {
        val inputStream = NativeInputStream(deviceFile, fis)
        val tag = openNative(inputStream)
        inputStream.close()
        return tag
    }

    private external fun openNative(inputStream: NativeInputStream): MetadataResult
}