package com.xinyue.reader.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ReaderGlobalSettingsWriteCoordinator @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> write(block: suspend () -> T): T = mutex.withLock { block() }
}
