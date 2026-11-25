package com.lonx.lyrico.domain.rename

import java.io.File

object ConflictResolver {
    fun resolveConflict(filePath: String, existingFiles: Set<String>): String {
        if (!existingFiles.contains(filePath)) {
            return filePath
        }

        val file = File(filePath)
        val nameWithoutExt = file.nameWithoutExtension
        val extension = file.extension
        val parent = file.parent ?: ""

        var counter = 1
        while (true) {
            val newName = if (extension.isNotEmpty()) {
                "$nameWithoutExt ($counter).$extension"
            } else {
                "$nameWithoutExt ($counter)"
            }
            val newPath = if (parent.isNotEmpty()) {
                File(parent, newName).absolutePath
            } else {
                newName
            }
            if (!existingFiles.contains(newPath)) {
                return newPath
            }
            counter++
        }
    }
}
