package com.lonx.audiotag.internal

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


    fun read(fd:Int): MetadataResult {
        return readNative(fd)
    }
    fun readPicture(fd:Int): ByteArray? {
        return readPictureNative(fd)
    }
    fun write(fd:Int, map: HashMap<String, List<String>>): Boolean {
        return writeNative(fd, map)
    }
    fun writePictures(fd:Int, picturesData: Array<ByteArray>): Boolean {
        return writePicturesNative(fd, picturesData)
    }

    private external fun readNative(fd: Int): MetadataResult
    private external fun readPictureNative(fd: Int): ByteArray?
    private external fun writeNative(pfd: Int, map: HashMap<String, List<String>>): Boolean
    private external fun writePicturesNative(fd: Int, picturesData: Array<ByteArray>): Boolean
}