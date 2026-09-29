package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.PendingFileCleanupDao
import com.xinyue.reader.core.database.entity.PendingFileCleanupEntity
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DurablePendingBookFileCleanupTest {
    @Test
    fun `successful cleanup removes private files and completes the durable record`() = runTest {
        val record = cleanupRecord()
        val dao = FakeCleanupDao(record)
        val files = FakeBookFileStore()

        val succeeded = DurablePendingBookFileCleanup(dao, files).cleanAll()

        assertThat(succeeded).isTrue()
        assertThat(files.removed).containsExactly(record.toStoredFiles())
        assertThat(dao.records).isEmpty()
    }

    @Test
    fun `failed cleanup keeps the durable record for a later retry`() = runTest {
        val record = cleanupRecord()
        val dao = FakeCleanupDao(record)
        val files = FakeBookFileStore(removeFailure = IOException("busy"))

        val succeeded = DurablePendingBookFileCleanup(dao, files).cleanAll()

        assertThat(succeeded).isFalse()
        assertThat(dao.records.values).containsExactly(record)
    }

    @Test
    fun `cleanup removes the whole private directory including custom cover`() = runTest {
        val root = Files.createTempDirectory("whole-book-cleanup").toFile()
        val privateDirectory = root.resolve("books/book-1").apply { mkdirs() }
        privateDirectory.resolve("original.txt").writeText("fixture")
        privateDirectory.resolve("content.txt").writeText("fixture")
        privateDirectory.resolve("cover.webp").writeBytes(byteArrayOf(1, 2, 3))
        val dao = FakeCleanupDao(cleanupRecord())

        assertThat(DurablePendingBookFileCleanup(dao, LocalBookFileStore(root)).cleanAll()).isTrue()

        assertThat(privateDirectory.exists()).isFalse()
        assertThat(dao.records).isEmpty()
        root.deleteRecursively()
    }

    private class FakeCleanupDao(vararg initial: PendingFileCleanupEntity) : PendingFileCleanupDao {
        val records = initial.associateByTo(linkedMapOf(), PendingFileCleanupEntity::bookId)

        override suspend fun get(bookId: String): PendingFileCleanupEntity? = records[bookId]

        override suspend fun getAll(): List<PendingFileCleanupEntity> = records.values.toList()

        override suspend fun delete(bookId: String) {
            records.remove(bookId)
        }
    }

    private class FakeBookFileStore(
        private val removeFailure: Throwable? = null,
    ) : BookFileStore {
        val removed = mutableListOf<StoredBookFiles>()

        override suspend fun stage(
            bookId: String,
            source: ImportSource,
            preferredCharsetName: String?,
        ): StagedBookFiles = error("not used")

        override suspend fun commit(staged: StagedBookFiles): StoredBookFiles = error("not used")

        override suspend fun discard(staged: StagedBookFiles) = Unit

        override suspend fun remove(stored: StoredBookFiles) {
            removed += stored
            removeFailure?.let { throw it }
        }
    }

    private companion object {
        fun cleanupRecord() = PendingFileCleanupEntity(
            bookId = "book-1",
            originalPath = "books/book-1/original.txt",
            normalizedPath = "books/book-1/content.txt",
            queuedAtEpochMillis = 42,
        )

        fun PendingFileCleanupEntity.toStoredFiles() = StoredBookFiles(
            originalPath = originalPath,
            normalizedPath = normalizedPath,
        )
    }
}
