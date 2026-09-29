package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.PendingFileCleanupDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface PendingBookFileCleanup {
    /** Returns true only when every durable cleanup record was completed. */
    suspend fun cleanAll(): Boolean
}

@Singleton
class DurablePendingBookFileCleanup @Inject constructor(
    private val cleanupDao: PendingFileCleanupDao,
    private val fileStore: BookFileStore,
) : PendingBookFileCleanup {
    override suspend fun cleanAll(): Boolean {
        var allSucceeded = true
        cleanupDao.getAll().forEach { pending ->
            try {
                fileStore.remove(
                    StoredBookFiles(
                        originalPath = pending.originalPath,
                        normalizedPath = pending.normalizedPath,
                    ),
                )
                cleanupDao.delete(pending.bookId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                allSucceeded = false
            }
        }
        return allSucceeded
    }
}
