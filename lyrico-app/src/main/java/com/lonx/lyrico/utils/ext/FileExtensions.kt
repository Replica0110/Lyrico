package com.lonx.lyrico.utils.ext

import android.util.Log
import java.io.File

fun File.getFolderSize(): Long {
    if (!exists()) return 0L
    var total = 0L
    if (isDirectory) {
        listFiles()?.forEach { child ->
            total += child.getFolderSize()
        }
    } else {
        total += length()
    }
    Log.d("File", "Total size: $total")
    return total
}
fun File.deleteRecursivelySafe() {
    if (!exists()) return
    kotlin.runCatching { deleteRecursively() }
}