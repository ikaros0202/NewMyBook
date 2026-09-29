package com.xinyue.reader.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the publish-sensitive part of imports inside the app process.
 *
 * WorkManager may run items from one multi-select batch concurrently. The
 * duplicate lookup and database publication must therefore share one gate,
 * otherwise two identical files can both pass the lookup before either one is
 * published.
 */
@Singleton
class ImportExecutionGate @Inject constructor() {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
