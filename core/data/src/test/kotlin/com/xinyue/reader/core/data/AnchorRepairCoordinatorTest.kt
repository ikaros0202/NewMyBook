package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.model.ReadingProgress
import com.xinyue.reader.core.domain.model.TextAnchor
import com.xinyue.reader.core.domain.model.TextRangeAnchor
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.text.AnchorRepairResult
import com.xinyue.reader.core.text.TextFingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AnchorRepairCoordinatorTest {
    @Test
    fun `repairs shifted progress and annotation through bounded reads`() = runTest {
        val original = "甲乙目标后文"
        val current = "新增$original"
        val pointFingerprint = TextFingerprint.capture(original, 2, 2)
        val rangeFingerprint = TextFingerprint.capture(original, 2, 4)
        val progress = ReadingProgress(
            bookId = BOOK_ID,
            anchor = TextAnchor(
                offset = 2,
                contextHash = "old-context",
                prefix = pointFingerprint.prefix,
                suffix = pointFingerprint.suffix,
            ),
            contentLength = original.length.toLong(),
            updatedAtEpochMillis = 10,
        )
        val annotation = sampleAnnotation(
            id = "annotation-shifted",
            range = TextRangeAnchor(
                startOffset = 2,
                endOffset = 4,
                prefix = rangeFingerprint.prefix,
                suffix = rangeFingerprint.suffix,
                selectedSha256 = rangeFingerprint.selectedSha256,
            ),
        )
        val books = FakeBookRepository(sampleBook(current.length.toLong()), progress)
        val annotations = FakeAnnotationRepository(listOf(annotation))
        val textSource = RecordingTextSource(current)

        val report = AnchorRepairCoordinator(books, annotations, textSource).repairBook(BOOK_ID)

        assertThat(report.progressResult).isEqualTo(AnchorRepairResult.Repaired(4, 4, 2))
        assertThat(books.savedProgress).hasSize(1)
        assertThat(books.savedProgress.single().anchor.offset).isEqualTo(4)
        assertThat(books.savedProgress.single().contentLength).isEqualTo(current.length.toLong())
        assertThat(report.repairedAnnotationIds).containsExactly(annotation.id)
        assertThat(report.unresolvedAnnotationIds).isEmpty()
        assertThat(annotations.upserts).hasSize(1)
        assertThat(annotations.upserts.single()).isEqualTo(
            annotation.copy(range = annotation.range.copy(startOffset = 4, endOffset = 6)),
        )
        assertThat(textSource.requests).hasSize(2)
        assertThat(textSource.requests.all {
            it.beforeUtf16Units <= AnchorRepairCoordinator.REPAIR_WINDOW_BEFORE &&
                it.afterUtf16Units <= AnchorRepairCoordinator.REPAIR_WINDOW_AFTER &&
                it.beforeUtf16Units + it.afterUtf16Units <=
                AnchorRepairCoordinator.REPAIR_WINDOW_BEFORE + AnchorRepairCoordinator.REPAIR_WINDOW_AFTER
        }).isTrue()
    }

    @Test
    fun `leaves an ambiguous annotation unchanged and reports its id`() = runTest {
        val current = "a目标b--a目标b"
        val selectedHash = TextFingerprint.capture("a目标b", 1, 3).selectedSha256
        val annotation = sampleAnnotation(
            id = "annotation-ambiguous",
            range = TextRangeAnchor(
                startOffset = 4,
                endOffset = 6,
                prefix = "a",
                suffix = "b",
                selectedSha256 = selectedHash,
            ),
        )
        val books = FakeBookRepository(sampleBook(current.length.toLong()), progress = null)
        val annotations = FakeAnnotationRepository(listOf(annotation))

        val report = AnchorRepairCoordinator(
            books,
            annotations,
            RecordingTextSource(current),
        ).repairBook(BOOK_ID)

        assertThat(report.progressResult).isNull()
        assertThat(report.repairedAnnotationIds).isEmpty()
        assertThat(report.unresolvedAnnotationIds).containsExactly(annotation.id)
        assertThat(annotations.upserts).isEmpty()
    }

    private data class ReadRequest(
        val beforeUtf16Units: Int,
        val afterUtf16Units: Int,
    )

    private class RecordingTextSource(private val text: String) : TextSource {
        val requests = mutableListOf<ReadRequest>()

        override suspend fun readWindow(
            normalizedPath: String,
            anchorOffset: Long,
            beforeUtf16Units: Int,
            afterUtf16Units: Int,
        ): TextWindow {
            requests += ReadRequest(beforeUtf16Units, afterUtf16Units)
            return TextWindow(0, text, text.length.toLong())
        }
    }

    private class FakeBookRepository(
        private val book: Book,
        private var progress: ReadingProgress?,
    ) : BookRepository {
        val savedProgress = mutableListOf<ReadingProgress>()

        override fun observeBooks(): Flow<List<Book>> = flowOf(listOf(book))
        override fun observeProgress(): Flow<List<ReadingProgress>> = flowOf(listOfNotNull(progress))
        override suspend fun addBook(book: Book) = Unit
        override suspend fun getBook(bookId: String): Book? = book.takeIf { it.id == bookId }
        override suspend fun findBySha256(contentSha256: String): Book? = null
        override suspend fun saveProgress(progress: ReadingProgress) {
            this.progress = progress
            savedProgress += progress
        }
        override suspend fun getProgress(bookId: String): ReadingProgress? = progress
        override suspend fun renameBook(bookId: String, title: String) = Unit
        override suspend fun deleteBook(bookId: String) = Unit
        override suspend fun replaceBook(existingBookId: String, replacement: Book) = Unit
        override suspend fun markOpened(bookId: String, epochMillis: Long) = Unit
    }

    private class FakeAnnotationRepository(
        private val annotations: List<ReaderAnnotation>,
    ) : AnnotationRepository {
        val upserts = mutableListOf<ReaderAnnotation>()

        override fun observe(bookId: String): Flow<List<ReaderAnnotation>> = flowOf(annotations)
        override fun observe(bookId: String, kind: AnnotationKind): Flow<List<ReaderAnnotation>> =
            flowOf(annotations.filter { it.kind == kind })
        override suspend fun getForBook(bookId: String): List<ReaderAnnotation> = annotations
        override suspend fun get(annotationId: String): ReaderAnnotation? =
            annotations.firstOrNull { it.id == annotationId }
        override suspend fun findOverlapping(
            bookId: String,
            startOffset: Long,
            endOffset: Long,
        ): List<ReaderAnnotation> = emptyList()
        override suspend fun upsert(annotation: ReaderAnnotation) {
            upserts += annotation
        }
        override suspend fun delete(annotationId: String) = Unit
    }

    private fun sampleBook(contentLength: Long) = Book(
        id = BOOK_ID,
        title = "测试小说",
        author = null,
        originalFileName = "测试.txt",
        originalPath = "books/$BOOK_ID/original.txt",
        normalizedPath = "books/$BOOK_ID/content.txt",
        charsetName = "UTF-8",
        contentSha256 = "sha",
        contentLength = contentLength,
        createdAtEpochMillis = 1,
        lastOpenedAtEpochMillis = null,
    )

    private fun sampleAnnotation(id: String, range: TextRangeAnchor) = ReaderAnnotation(
        id = id,
        bookId = BOOK_ID,
        kind = AnnotationKind.HIGHLIGHT,
        range = range,
        color = null,
        note = "保留内容",
        createdAtEpochMillis = 5,
        updatedAtEpochMillis = 6,
    )

    private companion object {
        const val BOOK_ID = "book-1"
    }
}
