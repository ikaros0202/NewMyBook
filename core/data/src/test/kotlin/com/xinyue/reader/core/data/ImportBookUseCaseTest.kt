package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class ImportBookUseCaseTest {
    @Test
    fun `stages commits and publishes a local txt book`() = runTest {
        val repository = FakeBookRepository()
        val fileStore = FakeBookFileStore()
        val useCase = ImportBookUseCase(
            repository = repository,
            fileStore = fileStore,
            idFactory = { "book-1" },
            nowEpochMillis = { 1234 },
        )
        val source = ImportSource(
            displayName = "测试小说.txt",
            sizeBytes = 12,
            openStream = { ByteArrayInputStream("第一章".toByteArray()) },
        )

        val imported = useCase(source)

        assertThat(fileStore.stagedIds).containsExactly("book-1")
        assertThat(fileStore.committedIds).containsExactly("book-1")
        assertThat(repository.books.value).containsExactly(imported)
        assertThat(imported.title).isEqualTo("测试小说")
        assertThat(imported.contentSha256).isEqualTo("sha")
        assertThat(imported.normalizedPath).isEqualTo("books/book-1/content.txt")
    }

    @Test
    fun `rejects a file larger than 50 MB before staging`() = runTest {
        val repository = FakeBookRepository()
        val fileStore = FakeBookFileStore()
        val useCase = useCase(repository, fileStore)
        val source = ImportSource(
            displayName = "超大合集.txt",
            sizeBytes = ImportBookUseCase.MAX_TXT_BYTES + 1,
            openStream = { ByteArrayInputStream(byteArrayOf()) },
        )

        assertFailsWith<IllegalArgumentException> { useCase(source) }
        assertThat(fileStore.stagedIds).isEmpty()
    }

    @Test
    fun `allows providers with unknown metadata size and relies on streamed limit`() = runTest {
        val repository = FakeBookRepository()
        val fileStore = FakeBookFileStore()
        val imported = useCase(repository, fileStore)(
            ImportSource(
                displayName = "未知大小.txt",
                sizeBytes = ImportSource.UNKNOWN_SIZE_BYTES,
                openStream = { ByteArrayInputStream("正文".toByteArray()) },
            ),
        )

        assertThat(imported.title).isEqualTo("未知大小")
        assertThat(fileStore.stagedIds).containsExactly("book-1")
    }

    @Test
    fun `publishes extracted title author and series metadata`() = runTest {
        val repository = FakeBookRepository()
        val fileStore = FakeBookFileStore().apply {
            suggestedTitle = "长夜列车"
            suggestedAuthor = "林川"
            suggestedSeriesName = "星海纪事"
            suggestedSeriesOrder = 3
        }

        val imported = useCase(repository, fileStore)(sampleSource())

        assertThat(imported.title).isEqualTo("长夜列车")
        assertThat(imported.author).isEqualTo("林川")
        assertThat(imported.seriesName).isEqualTo("星海纪事")
        assertThat(imported.seriesOrder).isEqualTo(3)
    }

    @Test
    fun `discards staged files when the book is a duplicate`() = runTest {
        val repository = FakeBookRepository().apply { books.value = listOf(sampleBook()) }
        val fileStore = FakeBookFileStore()
        val useCase = useCase(repository, fileStore)

        assertFailsWith<DuplicateBookException> { useCase(sampleSource()) }
        assertThat(fileStore.discardedIds).containsExactly("book-1")
        assertThat(fileStore.committedIds).isEmpty()
    }

    @Test
    fun `skips a duplicate without publishing another book`() = runTest {
        val existing = sampleBook()
        val repository = FakeBookRepository().apply { books.value = listOf(existing) }
        val fileStore = FakeBookFileStore()

        val outcome = useCase(repository, fileStore).importReliably(
            source = sampleSource(),
            duplicateResolution = DuplicateResolution.SKIP,
        )

        assertThat(outcome).isEqualTo(ReliableImportOutcome.Skipped(existing))
        assertThat(fileStore.discardedIds).containsExactly("book-1")
        assertThat(repository.books.value).containsExactly(existing)
    }

    @Test
    fun `imports a duplicate as a separately named copy`() = runTest {
        val repository = FakeBookRepository().apply { books.value = listOf(sampleBook()) }
        val fileStore = FakeBookFileStore()

        val outcome = useCase(repository, fileStore).importReliably(
            source = sampleSource(),
            duplicateResolution = DuplicateResolution.COPY,
        ) as ReliableImportOutcome.Imported

        assertThat(outcome.book.title).isEqualTo("测试小说（副本）")
        assertThat(repository.books.value.map(Book::id)).containsExactly("existing", "book-1")
        assertThat(fileStore.committedIds).containsExactly("book-1")
    }

    @Test
    fun `replaces a duplicate through one repository transaction then removes old files`() = runTest {
        val existing = sampleBook()
        val repository = FakeBookRepository().apply { books.value = listOf(existing) }
        val fileStore = FakeBookFileStore()
        val annotations = RecordingAnnotationRepository()
        val repairCoordinator = AnchorRepairCoordinator(
            repository,
            annotations,
            object : TextSource {
                override suspend fun readWindow(
                    normalizedPath: String,
                    anchorOffset: Long,
                    beforeUtf16Units: Int,
                    afterUtf16Units: Int,
                ): TextWindow = error("No anchors should require a text read")
            },
        )

        val outcome = ImportBookUseCase(
            repository = repository,
            fileStore = fileStore,
            idFactory = { "book-1" },
            nowEpochMillis = { 1234 },
            anchorRepairCoordinator = repairCoordinator,
        ).importReliably(
            source = sampleSource(),
            duplicateResolution = DuplicateResolution.REPLACE,
        ) as ReliableImportOutcome.Imported

        assertThat(outcome.replacedBookId).isEqualTo("existing")
        assertThat(repository.replacements).containsExactly("existing" to "existing")
        assertThat(repository.books.value.map(Book::id)).containsExactly("existing")
        assertThat(fileStore.removedPaths).containsExactly("books/existing/content.txt")
        assertThat(annotations.requestedBookIds).containsExactly("existing")
    }

    @Test
    fun `removes committed files when publishing the database record fails`() = runTest {
        val repository = FakeBookRepository().apply { failOnAdd = true }
        val fileStore = FakeBookFileStore()
        val useCase = useCase(repository, fileStore)

        assertFailsWith<IllegalStateException> { useCase(sampleSource()) }
        assertThat(fileStore.removedPaths).containsExactly("books/book-1/content.txt")
    }

    private fun useCase(repository: FakeBookRepository, fileStore: FakeBookFileStore) = ImportBookUseCase(
        repository = repository,
        fileStore = fileStore,
        idFactory = { "book-1" },
        nowEpochMillis = { 1234 },
    )

    private fun sampleSource() = ImportSource(
        displayName = "测试小说.txt",
        sizeBytes = 12,
        openStream = { ByteArrayInputStream("第一章".toByteArray()) },
    )

    private fun sampleBook() = Book(
        id = "existing",
        title = "已存在",
        author = null,
        originalFileName = "已存在.txt",
        originalPath = "books/existing/original.txt",
        normalizedPath = "books/existing/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "sha",
        contentLength = 3,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private class FakeBookFileStore : BookFileStore {
        val stagedIds = mutableListOf<String>()
        val committedIds = mutableListOf<String>()
        val discardedIds = mutableListOf<String>()
        val removedPaths = mutableListOf<String>()
        var suggestedTitle: String? = null
        var suggestedAuthor: String? = null
        var suggestedSeriesName: String? = null
        var suggestedSeriesOrder: Int? = null

        override suspend fun stage(bookId: String, source: ImportSource, preferredCharsetName: String?): StagedBookFiles {
            stagedIds += bookId
            return StagedBookFiles(
                bookId = bookId,
                originalFileName = source.displayName,
                charsetName = "UTF-8",
                contentSha256 = "sha",
                contentLength = 3,
                suggestedTitle = suggestedTitle,
                suggestedAuthor = suggestedAuthor,
                suggestedSeriesName = suggestedSeriesName,
                suggestedSeriesOrder = suggestedSeriesOrder,
            )
        }

        override suspend fun commit(staged: StagedBookFiles): StoredBookFiles {
            committedIds += staged.bookId
            return StoredBookFiles(
                originalPath = "books/${staged.bookId}/original.txt",
                normalizedPath = "books/${staged.bookId}/content.txt",
            )
        }

        override suspend fun discard(staged: StagedBookFiles) {
            discardedIds += staged.bookId
        }

        override suspend fun remove(stored: StoredBookFiles) {
            removedPaths += stored.normalizedPath
        }
    }

    private class FakeBookRepository : BookRepository {
        val books = MutableStateFlow<List<Book>>(emptyList())
        var failOnAdd = false
        val replacements = mutableListOf<Pair<String, String>>()

        override fun observeBooks(): Flow<List<Book>> = books

        override fun observeProgress(): Flow<List<ReadingProgress>> = kotlinx.coroutines.flow.flowOf(emptyList())

        override suspend fun addBook(book: Book) {
            if (failOnAdd) error("database write failed")
            books.value += book
        }

        override suspend fun getBook(bookId: String): Book? = books.value.firstOrNull { it.id == bookId }

        override suspend fun findBySha256(contentSha256: String): Book? =
            books.value.firstOrNull { it.contentSha256 == contentSha256 }

        override suspend fun saveProgress(progress: ReadingProgress) = Unit

        override suspend fun getProgress(bookId: String): ReadingProgress? = null

        override suspend fun renameBook(bookId: String, title: String) = Unit

        override suspend fun deleteBook(bookId: String) = Unit

        override suspend fun replaceBook(existingBookId: String, replacement: Book) {
            replacements += existingBookId to replacement.id
            books.value = books.value.filterNot { it.id == existingBookId } + replacement
        }

        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private class RecordingAnnotationRepository : AnnotationRepository {
        val requestedBookIds = mutableListOf<String>()

        override fun observe(bookId: String): Flow<List<ReaderAnnotation>> = kotlinx.coroutines.flow.flowOf(emptyList())
        override fun observe(bookId: String, kind: AnnotationKind): Flow<List<ReaderAnnotation>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun getForBook(bookId: String): List<ReaderAnnotation> {
            requestedBookIds += bookId
            return emptyList()
        }
        override suspend fun get(annotationId: String): ReaderAnnotation? = null
        override suspend fun findOverlapping(
            bookId: String,
            startOffset: Long,
            endOffset: Long,
        ): List<ReaderAnnotation> = emptyList()
        override suspend fun upsert(annotation: ReaderAnnotation) = Unit
        override suspend fun delete(annotationId: String) = Unit
    }
}
