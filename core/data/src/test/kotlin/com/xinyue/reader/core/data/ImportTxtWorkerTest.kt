package com.xinyue.reader.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.ImportTaskDao
import com.xinyue.reader.core.database.entity.ImportTaskEntity
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.repository.BookRepository
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class ImportTxtWorkerTest {
    @Test
    fun `failure after source resolution keeps the real display name`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskDao = RecordingImportTaskDao(initialTask())
        val importer = ImportBookUseCase(
            repository = EmptyBookRepository(),
            fileStore = ConfigurableBookFileStore(IllegalArgumentException("synthetic import failure")),
            idFactory = { "book-1" },
            nowEpochMillis = { 1L },
        )
        val worker = buildWorker(context, taskDao, importer)

        worker.doWork()

        assertThat(taskDao.current.displayName).isEqualTo("真实书名.txt")
        assertThat(taskDao.current.status).isEqualTo(ImportItemStatus.FAILED.name)
        assertThat(taskDao.current.errorMessage).isEqualTo("导入失败，请检查文件后重试")
    }

    @Test
    fun `retry after source resolution keeps the real display name`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskDao = RecordingImportTaskDao(initialTask())
        val importer = ImportBookUseCase(
            repository = EmptyBookRepository(),
            fileStore = ConfigurableBookFileStore(IOException("provider unavailable")),
            idFactory = { "book-1" },
            nowEpochMillis = { 1L },
        )

        buildWorker(context, taskDao, importer, runAttemptCount = 0).doWork()

        assertThat(taskDao.current.displayName).isEqualTo("真实书名.txt")
        assertThat(taskDao.current.status).isEqualTo(ImportItemStatus.QUEUED.name)
        assertThat(taskDao.current.errorMessage).isEqualTo("暂时无法读取所选文件，稍后将自动重试")
    }

    @Test
    fun `duplicate decision after source resolution keeps the real display name`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskDao = RecordingImportTaskDao(initialTask())
        val importer = ImportBookUseCase(
            repository = EmptyBookRepository(existingBook()),
            fileStore = ConfigurableBookFileStore(),
            idFactory = { "book-1" },
            nowEpochMillis = { 1L },
        )

        buildWorker(context, taskDao, importer).doWork()

        assertThat(taskDao.current.displayName).isEqualTo("真实书名.txt")
        assertThat(taskDao.current.status).isEqualTo(ImportItemStatus.NEEDS_DECISION.name)
        assertThat(taskDao.current.existingBookTitle).isEqualTo("已存在")
    }

    @Test
    fun `cancellation after source resolution keeps the real display name`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val taskDao = RecordingImportTaskDao(initialTask())
        val importer = ImportBookUseCase(
            repository = EmptyBookRepository(),
            fileStore = ConfigurableBookFileStore(CancellationException("cancelled")),
            idFactory = { "book-1" },
            nowEpochMillis = { 1L },
        )

        assertFailsWith<CancellationException> {
            buildWorker(context, taskDao, importer).doWork()
        }

        assertThat(taskDao.current.displayName).isEqualTo("真实书名.txt")
        assertThat(taskDao.current.status).isEqualTo(ImportItemStatus.QUEUED.name)
    }

    private fun buildWorker(
        context: Context,
        taskDao: RecordingImportTaskDao,
        importer: ImportBookUseCase,
        runAttemptCount: Int = 0,
    ): ImportTxtWorker {
        val sourceFactory = object : ImportSourceFactory {
            override suspend fun create(uriString: String): ImportSource = ImportSource(
                displayName = "真实书名.txt",
                sizeBytes = 3L,
                openStream = { ByteArrayInputStream("正文".toByteArray()) },
            )
        }
        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = ImportTxtWorker(
                appContext = appContext,
                workerParameters = workerParameters,
                taskDao = taskDao,
                sourceFactory = sourceFactory,
                importer = importer,
                executionGate = ImportExecutionGate(),
                permissionManager = ImportUriPermissionManager(appContext),
            )
        }
        return TestListenableWorkerBuilder.from(context, ImportTxtWorker::class.java)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString(ImportWorkContract.KEY_TASK_ID, taskDao.current.id)
                    .build(),
            )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(workerFactory)
            .build()
    }

    private fun initialTask() = ImportTaskEntity(
        id = "task-1",
        batchId = "batch-1",
        itemIndex = 0,
        totalItems = 1,
        attempt = 0,
        uriString = "content://documents/msf%3A198",
        displayName = "msf%3A198",
        preferredCharsetName = null,
        duplicateResolution = DuplicateResolution.ASK.name,
        status = ImportItemStatus.QUEUED.name,
        progressPercent = 0,
        bookId = null,
        existingBookId = null,
        existingBookTitle = null,
        errorMessage = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private class RecordingImportTaskDao(initial: ImportTaskEntity) : ImportTaskDao {
        var current = initial
            private set

        override suspend fun insertAll(tasks: List<ImportTaskEntity>) = Unit

        override suspend fun update(task: ImportTaskEntity) {
            current = task
        }

        override suspend fun get(id: String): ImportTaskEntity? = current.takeIf { it.id == id }

        override fun observeLatestBatch(): Flow<List<ImportTaskEntity>> = flowOf(listOf(current))
    }

    private fun existingBook() = Book(
        id = "existing-book",
        title = "已存在",
        author = null,
        originalFileName = "已存在.txt",
        originalPath = "books/existing/original.txt",
        normalizedPath = "books/existing/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "sha",
        contentLength = 2L,
        createdAtEpochMillis = 1L,
        lastOpenedAtEpochMillis = null,
    )

    private class ConfigurableBookFileStore(private val failure: Throwable? = null) : BookFileStore {
        override suspend fun stage(
            bookId: String,
            source: ImportSource,
            preferredCharsetName: String?,
        ): StagedBookFiles {
            failure?.let { throw it }
            return StagedBookFiles(
                bookId = bookId,
                originalFileName = source.displayName,
                charsetName = "UTF-8",
                contentSha256 = "sha",
                contentLength = 2L,
                suggestedTitle = null,
                suggestedAuthor = null,
            )
        }

        override suspend fun commit(staged: StagedBookFiles): StoredBookFiles = error("not used")
        override suspend fun discard(staged: StagedBookFiles) = Unit
        override suspend fun remove(stored: StoredBookFiles) = Unit
    }

    private class EmptyBookRepository(existing: Book? = null) : BookRepository {
        private val books = listOfNotNull(existing)

        override fun observeBooks(): Flow<List<Book>> = flowOf(emptyList())
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = null
        override suspend fun findBySha256(contentSha256: String): Book? =
            books.firstOrNull { it.contentSha256 == contentSha256 }
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgress(bookId: String): ReadingProgress? = null
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }
}
