package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationExportFormat
import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.text.DetectedChapter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LocalAnnotationExportServiceTest {
    @Test
    fun `exports selected books sorted by chapter and position with bookmarks excluded by default`() = runTest {
        val book = sampleBook()
        val annotations = FakeAnnotationRepository(
            listOf(
                annotation("bookmark", AnnotationKind.BOOKMARK, 3, 3),
                annotation("second", AnnotationKind.HIGHLIGHT, 12, 15),
                annotation("first", AnnotationKind.NOTE, 6, 10, note = "公开批注"),
            ),
        )
        val gateway = FakeDocumentGateway()
        val textSource = FakeTextSource("012345selected-text-after")
        val service = LocalAnnotationExportService(
            bookRepository = FakeBookRepository(listOf(book)),
            annotationRepository = annotations,
            textSource = textSource,
            chapterIndexStore = FakeChapterIndexStore(),
            documentGateway = gateway,
            nowEpochMillis = { 1_700_000_000_000 },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = service.export(
            destinationUri = "content://public/export.json",
            request = AnnotationExportRequest(
                format = AnnotationExportFormat.JSON,
                bookIds = setOf(book.id),
            ),
        )

        assertThat(result).isEqualTo(AnnotationExportResult.Success(1, 2, gateway.bytes.size.toLong()))
        val exported = gateway.bytes.toString(Charsets.UTF_8)
        assertThat(exported).doesNotContain("\"id\": \"bookmark\"")
        assertThat(exported.indexOf("\"id\": \"first\""))
            .isLessThan(exported.indexOf("\"id\": \"second\""))
        assertThat(exported).contains("\"title\": \"第一章\"")
        assertThat(exported).contains("\"title\": \"第二章\"")
        assertThat(textSource.requests).hasSize(2)
        assertThat(textSource.requests.all { it.beforeUtf16Units <= 80 && it.afterUtf16Units <= 2_128 })
            .isTrue()
    }

    @Test
    fun `invalidates a partially written destination after publication failure`() = runTest {
        val gateway = FakeDocumentGateway(failWrite = true)
        val service = LocalAnnotationExportService(
            bookRepository = FakeBookRepository(listOf(sampleBook())),
            annotationRepository = FakeAnnotationRepository(
                listOf(annotation("note", AnnotationKind.NOTE, 1, 2)),
            ),
            textSource = FakeTextSource("public"),
            chapterIndexStore = FakeChapterIndexStore(),
            documentGateway = gateway,
            nowEpochMillis = { 1 },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = service.export(
            "content://public/failure.md",
            AnnotationExportRequest(AnnotationExportFormat.MARKDOWN),
        )

        assertThat(result).isInstanceOf(AnnotationExportResult.Failure::class.java)
        assertThat(gateway.invalidatedUris).containsExactly("content://public/failure.md")
    }

    private class FakeTextSource(private val content: String) : TextSource {
        data class Request(
            val anchorOffset: Long,
            val beforeUtf16Units: Int,
            val afterUtf16Units: Int,
        )

        val requests = mutableListOf<Request>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            requests += Request(anchorOffset, beforeUtf16Units, afterUtf16Units)
            val start = (anchorOffset - beforeUtf16Units).coerceAtLeast(0).toInt()
            val end = (anchorOffset + afterUtf16Units).coerceAtMost(content.length.toLong()).toInt()
            return TextWindow(start.toLong(), content.substring(start, end), content.length.toLong())
        }
    }

    private class FakeChapterIndexStore : ChapterIndexStore {
        override suspend fun getSnapshot(bookId: String) = ChapterIndexSnapshot(
            chapters = listOf(
                DetectedChapter("第一章", 0),
                DetectedChapter("第二章", 10),
            ),
        )

        override suspend fun replaceAutomatically(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: com.xinyue.reader.core.text.ChapterRuleSet,
        ) = true

        override suspend fun replaceManually(bookId: String, chapters: List<DetectedChapter>) = Unit

        override suspend fun replaceForRuleChange(
            bookId: String,
            chapters: List<DetectedChapter>,
            ruleSet: com.xinyue.reader.core.text.ChapterRuleSet,
        ) = Unit
    }

    private class FakeDocumentGateway(private val failWrite: Boolean = false) : BackupDocumentGateway {
        var bytes = ByteArray(0)
        val invalidatedUris = mutableListOf<String>()

        override fun openForWrite(destinationUri: String): OutputStream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    super.close()
                    bytes = toByteArray()
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (failWrite) error("write failed")
                    super.write(buffer, offset, length)
                }
            }

        override fun invalidate(destinationUri: String): Boolean {
            invalidatedUris += destinationUri
            return true
        }

        override fun openForRead(sourceUri: String): InputStream = ByteArrayInputStream(bytes)
    }

    private class FakeAnnotationRepository(
        private val annotations: List<ReaderAnnotation>,
    ) : AnnotationRepository {
        override fun observe(bookId: String): Flow<List<ReaderAnnotation>> = flowOf(getFor(bookId))
        override fun observe(bookId: String, kind: AnnotationKind): Flow<List<ReaderAnnotation>> =
            flowOf(getFor(bookId).filter { it.kind == kind })
        override suspend fun getForBook(bookId: String): List<ReaderAnnotation> = getFor(bookId)
        override suspend fun get(annotationId: String): ReaderAnnotation? =
            annotations.firstOrNull { it.id == annotationId }
        override suspend fun findOverlapping(
            bookId: String,
            startOffset: Long,
            endOffset: Long,
        ): List<ReaderAnnotation> = getFor(bookId)
        override suspend fun upsert(annotation: ReaderAnnotation) = Unit
        override suspend fun delete(annotationId: String) = Unit
        private fun getFor(bookId: String) = annotations.filter { it.bookId == bookId }
    }

    private class FakeBookRepository(private val books: List<Book>) : BookRepository {
        override fun observeBooks(): Flow<List<Book>> = flowOf(books)
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(emptyList())
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = books.firstOrNull { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) = Unit
        override suspend fun getProgress(bookId: String): ReadingProgress? = null
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private fun annotation(
        id: String,
        kind: AnnotationKind,
        start: Long,
        end: Long,
        note: String? = null,
    ) = ReaderAnnotation(
        id = id,
        bookId = "book-1",
        kind = kind,
        range = TextRangeAnchor(start, end, "", "", null),
        color = null,
        note = note,
        createdAtEpochMillis = start,
        updatedAtEpochMillis = end,
    )

    private fun sampleBook() = Book(
        id = "book-1",
        title = "公开小说",
        author = "作者",
        originalFileName = "public.txt",
        originalPath = "books/book-1/original.txt",
        normalizedPath = "books/book-1/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "public-hash",
        contentLength = 26,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )
}
