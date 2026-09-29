package com.xinyue.reader.feature.importing

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.data.DuplicateResolution
import com.xinyue.reader.core.data.BookManager
import com.xinyue.reader.core.data.ImportBatchState
import com.xinyue.reader.core.data.ImportItemState
import com.xinyue.reader.core.data.ImportItemStatus
import com.xinyue.reader.core.data.ImportPreflightItem
import com.xinyue.reader.core.data.ImportPreflightService
import com.xinyue.reader.core.data.ImportRequest
import com.xinyue.reader.core.data.ImportTaskScheduler
import com.xinyue.reader.core.text.TextByteSegments
import com.xinyue.reader.core.text.TxtEncodingAnalyzer
import com.xinyue.reader.core.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `enqueues every selected uri and observes persisted batch state`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val viewModel = ImportViewModel(scheduler, FakePreflightService(), FakeBookManager())

        viewModel.enqueue(listOf("content://books/1", "content://books/2"), "GB18030")
        advanceUntilIdle()

        assertThat(scheduler.requests.map(ImportRequest::uriString))
            .containsExactly("content://books/1", "content://books/2").inOrder()
        assertThat(scheduler.requests.map(ImportRequest::preferredCharsetName).distinct())
            .containsExactly("GB18030")
        assertThat(viewModel.uiState.value.batch?.items).hasSize(2)
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
    }

    @Test
    fun `forwards duplicate decisions and single item retry`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val viewModel = ImportViewModel(scheduler, FakePreflightService(), FakeBookManager())
        val item = sampleItem(status = ImportItemStatus.NEEDS_DECISION)

        viewModel.resolveDuplicate(item, DuplicateResolution.COPY)
        viewModel.retry(item.copy(status = ImportItemStatus.FAILED))
        advanceUntilIdle()

        assertThat(scheduler.resolutions).containsExactly(item.workId to DuplicateResolution.COPY)
        assertThat(scheduler.retriedIds).containsExactly(item.workId)
    }

    @Test
    fun `does not expose provider exception text in the Chinese interface`() = runTest(dispatcher) {
        val scheduler = FakeScheduler().apply {
            enqueueFailure = IllegalStateException("provider rejected content://private/path")
        }
        val viewModel = ImportViewModel(scheduler, FakePreflightService(), FakeBookManager())

        viewModel.enqueue(listOf("content://books/1"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("无法创建导入任务")
    }

    @Test
    fun `pauses an uncertain file for three segment encoding confirmation`() = runTest(dispatcher) {
        val scheduler = FakeScheduler()
        val uri = "content://books/ascii"
        val analysis = TxtEncodingAnalyzer.analyze(TextByteSegments.single("Chapter one".toByteArray()))
        val preflight = FakePreflightService(
            listOf(ImportPreflightItem(uri, "ascii.txt", 11, analysis, null)),
        )
        val viewModel = ImportViewModel(scheduler, preflight, FakeBookManager())

        viewModel.prepareImport(listOf(uri))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.encodingDecision?.displayName).isEqualTo("ascii.txt")
        assertThat(scheduler.requests).isEmpty()

        viewModel.confirmEncoding(requireNotNull(viewModel.uiState.value.encodingDecision), "UTF-8")
        advanceUntilIdle()

        assertThat(scheduler.requests).containsExactly(ImportRequest(uri, "UTF-8"))
        assertThat(viewModel.uiState.value.encodingDecision).isNull()
    }

    @Test
    fun `edits title and author directly from a successful import item`() = runTest(dispatcher) {
        val manager = FakeBookManager().apply { book = sampleBook() }
        val viewModel = ImportViewModel(FakeScheduler(), FakePreflightService(), manager)
        val item = sampleItem(status = ImportItemStatus.SUCCEEDED).copy(bookId = "book-1")

        viewModel.editImportedBook(item)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.editingBook?.title).isEqualTo("测试小说")

        viewModel.saveImportedBookMetadata("新书名", "新作者")
        advanceUntilIdle()

        assertThat(manager.updates).containsExactly(Triple("book-1", "新书名", "新作者"))
        assertThat(viewModel.uiState.value.editingBook).isNull()
    }

    private class FakeScheduler : ImportTaskScheduler {
        private val mutableBatch = MutableStateFlow<ImportBatchState?>(null)
        override val latestBatch: Flow<ImportBatchState?> = mutableBatch
        var requests = emptyList<ImportRequest>()
        var enqueueFailure: Throwable? = null
        val resolutions = mutableListOf<Pair<String, DuplicateResolution>>()
        val retriedIds = mutableListOf<String>()

        override suspend fun enqueueRequests(requests: List<ImportRequest>): String {
            enqueueFailure?.let { throw it }
            this.requests = requests
            mutableBatch.value = ImportBatchState("batch", requests.mapIndexed { index, request ->
                sampleItem(index = index, uri = request.uriString)
            })
            return "batch"
        }

        override suspend fun resolveDuplicate(item: ImportItemState, resolution: DuplicateResolution) {
            resolutions += item.workId to resolution
        }

        override suspend fun retry(item: ImportItemState) {
            retriedIds += item.workId
        }
    }

    private class FakePreflightService(
        private val results: List<ImportPreflightItem> = emptyList(),
    ) : ImportPreflightService {
        override suspend fun analyze(uriStrings: List<String>): List<ImportPreflightItem> = results
    }

    private class FakeBookManager : BookManager {
        var book: Book? = null
        val updates = mutableListOf<Triple<String, String, String?>>()

        override suspend fun get(bookId: String): Book? = book?.takeIf { it.id == bookId }
        override suspend fun rename(bookId: String, title: String) = Unit
        override suspend fun updateMetadata(bookId: String, title: String, author: String?) {
            updates += Triple(bookId, title, author)
            book = book?.copy(title = title, author = author)
        }
        override suspend fun delete(bookId: String) = Unit
    }

    private companion object {
        fun sampleBook() = Book(
            id = "book-1",
            title = "测试小说",
            author = null,
            originalFileName = "测试小说.txt",
            originalPath = "books/book-1/original.txt",
            normalizedPath = "books/book-1/content.txt",
            charsetName = "UTF-8",
            contentSha256 = "sha",
            contentLength = 100,
            createdAtEpochMillis = 1,
            lastOpenedAtEpochMillis = null,
        )

        fun sampleItem(
            index: Int = 0,
            uri: String = "content://books/1",
            status: ImportItemStatus = ImportItemStatus.QUEUED,
        ) = ImportItemState(
            workId = "work-$index",
            batchId = "batch",
            index = index,
            total = 2,
            attempt = 0,
            uriString = uri,
            displayName = "小说$index.txt",
            preferredCharsetName = null,
            duplicateResolution = DuplicateResolution.ASK,
            status = status,
            progressPercent = 0,
            bookId = null,
            existingBookId = null,
            existingBookTitle = null,
            errorMessage = null,
        )
    }
}
