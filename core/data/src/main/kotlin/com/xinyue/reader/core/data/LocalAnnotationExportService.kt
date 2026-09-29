package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.AnnotationExportRequest
import com.xinyue.reader.core.domain.model.AnnotationExportResult
import com.xinyue.reader.core.domain.model.AnnotationKind
import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.model.ReaderAnnotation
import com.xinyue.reader.core.domain.repository.AnnotationExportService
import com.xinyue.reader.core.domain.repository.AnnotationRepository
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.text.DetectedChapter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class LocalAnnotationExportService internal constructor(
    private val bookRepository: BookRepository,
    private val annotationRepository: AnnotationRepository,
    private val textSource: TextSource,
    private val chapterIndexStore: ChapterIndexStore,
    private val documentGateway: BackupDocumentGateway,
    private val formatter: AnnotationExportFormatter = AnnotationExportFormatter(),
    private val nowEpochMillis: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) : AnnotationExportService {
    @Inject
    constructor(
        bookRepository: BookRepository,
        annotationRepository: AnnotationRepository,
        textSource: TextSource,
        chapterIndexStore: ChapterIndexStore,
        documentGateway: AndroidBackupDocumentGateway,
    ) : this(
        bookRepository = bookRepository,
        annotationRepository = annotationRepository,
        textSource = textSource,
        chapterIndexStore = chapterIndexStore,
        documentGateway = documentGateway,
        nowEpochMillis = System::currentTimeMillis,
        ioDispatcher = Dispatchers.IO,
    )

    override suspend fun export(
        destinationUri: String,
        request: AnnotationExportRequest,
    ): AnnotationExportResult = withContext(ioDispatcher) {
        var publicationStarted = false
        try {
            require(destinationUri.isNotBlank()) { "导出目标不能为空" }
            val requestedIds = request.bookIds
            require(requestedIds == null || requestedIds.isNotEmpty()) { "至少选择一本书" }
            val allBooks = bookRepository.observeBooks().first()
            val books = allBooks
                .filter { requestedIds?.contains(it.id) != false }
                .sortedWith(compareBy<Book> { it.title.lowercase() }.thenBy(Book::id))
            require(requestedIds == null || books.map(Book::id).toSet() == requestedIds) {
                "部分书籍已经不存在"
            }

            var annotationCount = 0
            val exportBooks = books.mapNotNull { book ->
                val annotations = annotationRepository.getForBook(book.id)
                    .asSequence()
                    .filter { request.includeBookmarks || it.kind != AnnotationKind.BOOKMARK }
                    .sortedWith(
                        compareBy<ReaderAnnotation>(ReaderAnnotation::bookId)
                            .thenBy { it.range.startOffset }
                            .thenBy { it.range.endOffset }
                            .thenBy(ReaderAnnotation::createdAtEpochMillis)
                            .thenBy(ReaderAnnotation::id),
                    )
                    .toList()
                annotationCount += annotations.size
                require(annotationCount <= MAX_ANNOTATIONS) { "批注数量超过单次导出上限" }
                if (annotations.isEmpty()) {
                    null
                } else {
                    buildBook(book, annotations)
                }
            }
            val document = AnnotationExportDocument(
                exportedAtEpochMillis = nowEpochMillis(),
                includeBookmarks = request.includeBookmarks,
                books = exportBooks,
            )
            val bytes = formatter.render(document, request.format).encodeToByteArray()
            require(bytes.size <= MAX_OUTPUT_BYTES) { "导出文档超过大小上限" }

            publicationStarted = true
            documentGateway.openForWrite(destinationUri).use { output ->
                output.write(bytes)
                output.flush()
            }
            AnnotationExportResult.Success(
                bookCount = exportBooks.size,
                annotationCount = annotationCount,
                byteCount = bytes.size.toLong(),
            )
        } catch (_: Throwable) {
            if (publicationStarted) documentGateway.invalidate(destinationUri)
            AnnotationExportResult.Failure("无法导出批注，请重新选择保存位置后再试")
        }
    }

    private suspend fun buildBook(
        book: Book,
        annotations: List<ReaderAnnotation>,
    ): AnnotationExportBook {
        val chapters = chapterIndexStore.getSnapshot(book.id).chapters
            .sortedBy(DetectedChapter::startOffset)
        val grouped = linkedMapOf<ChapterKey, MutableList<AnnotationExportItem>>()
        annotations.forEach { annotation ->
            val chapter = chapters.lastOrNull { it.startOffset.toLong() <= annotation.range.startOffset }
            val key = ChapterKey(
                title = chapter?.title ?: "正文",
                startOffset = chapter?.startOffset?.toLong() ?: 0,
            )
            grouped.getOrPut(key, ::mutableListOf) += annotation.toExportItem(book)
        }
        return AnnotationExportBook(
            bookId = book.id,
            title = book.title,
            author = book.author,
            seriesName = book.seriesName,
            seriesOrder = book.seriesOrder,
            chapters = grouped.map { (chapter, items) ->
                AnnotationExportChapter(
                    title = chapter.title,
                    startOffset = chapter.startOffset,
                    annotations = items,
                )
            },
        )
    }

    private suspend fun ReaderAnnotation.toExportItem(book: Book): AnnotationExportItem {
        val selectedLength = (range.endOffset - range.startOffset).coerceAtLeast(0)
        val selectedReadLength = selectedLength.coerceAtMost(MAX_SELECTED_UTF16_UNITS.toLong()).toInt()
        val window = textSource.readWindow(
            normalizedPath = book.normalizedPath,
            anchorOffset = range.startOffset,
            beforeUtf16Units = CONTEXT_UTF16_UNITS,
            afterUtf16Units = (selectedReadLength + CONTEXT_UTF16_UNITS).coerceAtLeast(1),
        )
        val localStart = (range.startOffset - window.startOffset)
            .coerceIn(0, window.text.length.toLong())
            .toInt()
        val localSelectedEnd = (localStart + selectedReadLength).coerceAtMost(window.text.length)
        val contextStart = (localStart - CONTEXT_UTF16_UNITS).coerceAtLeast(0)
        val contextEnd = (localSelectedEnd + CONTEXT_UTF16_UNITS).coerceAtMost(window.text.length)
        return AnnotationExportItem(
            id = id,
            kind = kind,
            startOffset = range.startOffset,
            endOffset = range.endOffset,
            selectedText = window.text.substring(localStart, localSelectedEnd),
            selectedTextTruncated = selectedLength > selectedReadLength,
            contextBefore = window.text.substring(contextStart, localStart),
            contextAfter = window.text.substring(localSelectedEnd, contextEnd),
            note = note,
            color = color,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    private data class ChapterKey(val title: String, val startOffset: Long)

    private companion object {
        const val CONTEXT_UTF16_UNITS = 80
        const val MAX_SELECTED_UTF16_UNITS = 2_048
        const val MAX_ANNOTATIONS = 50_000
        const val MAX_OUTPUT_BYTES = 16 * 1024 * 1024
    }
}
