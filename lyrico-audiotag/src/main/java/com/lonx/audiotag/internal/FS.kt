package com.lonx.audiotag.internal

import android.net.Uri
import kotlinx.coroutines.Deferred
import java.nio.file.Path

sealed interface FSEntry {
    val uri: Uri?
    val path: Path
}

data class Directory(
    override val uri: Uri?,
    override val path: Path,
    val parent: Deferred<Directory>?,
    var children: List<AudioFile>,
) : FSEntry

data class AudioFile(
    override val uri: Uri,
    override val path: Path,
    val modifiedMs: Long,
    val mimeType: String,
    val size: Long,
    val parent: Deferred<Directory>?,
) : FSEntry
