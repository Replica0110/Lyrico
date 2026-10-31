package com.lonx.lyrico.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Keeps a scan's folder snapshot valid until its database and index writes finish. */
class LibraryOperationLock {
    private val mutex = Mutex()

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }
}
