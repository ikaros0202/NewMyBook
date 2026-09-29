package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.Book
import com.xinyue.reader.core.domain.repository.BookRepository

class ImportBookUseCase(
    private val repository: BookRepository,
    private val fileStore: BookFileStore,
    private val idFactory: () -> String,
    private val nowEpochMillis: () -> Long,
    private val anchorRepairCoordinator: AnchorRepairCoordinator? = null,
) : BookImporter {
    override suspend fun import(source: ImportSource, preferredCharsetName: String?): Book =
        invoke(source, preferredCharsetName)

    suspend operator fun invoke(
        source: ImportSource,
        preferredCharsetName: String? = null,
    ): Book = when (
        val outcome = importReliably(source, preferredCharsetName, DuplicateResolution.ASK)
    ) {
        is ReliableImportOutcome.Imported -> outcome.book
        is ReliableImportOutcome.Skipped -> outcome.existingBook
    }

    suspend fun importReliably(
        source: ImportSource,
        preferredCharsetName: String? = null,
        duplicateResolution: DuplicateResolution = DuplicateResolution.ASK,
    ): ReliableImportOutcome {
        require(
            source.sizeBytes == ImportSource.UNKNOWN_SIZE_BYTES || source.sizeBytes in 1..MAX_TXT_BYTES,
        ) { "TXT 文件必须介于 1 字节和 50 MB 之间" }

        val bookId = idFactory()
        val staged = fileStore.stage(bookId, source, preferredCharsetName)
        val existingBook = repository.findBySha256(staged.contentSha256)
        if (existingBook != null) {
            when (duplicateResolution) {
                DuplicateResolution.ASK -> {
                    fileStore.discard(staged)
                    throw DuplicateBookException(staged.contentSha256, existingBook)
                }
                DuplicateResolution.SKIP -> {
                    fileStore.discard(staged)
                    return ReliableImportOutcome.Skipped(existingBook)
                }
                DuplicateResolution.REPLACE,
                DuplicateResolution.COPY,
                -> Unit
            }
        }

        val stored = fileStore.commit(staged)
        val baseTitle = source.displayName.substringBeforeLast('.').ifBlank { "未命名书籍" }
        val extractedTitle = staged.suggestedTitle?.takeIf(String::isNotBlank) ?: baseTitle
        val book = Book(
            id = if (duplicateResolution == DuplicateResolution.REPLACE && existingBook != null) {
                existingBook.id
            } else {
                bookId
            },
            title = if (duplicateResolution == DuplicateResolution.COPY && existingBook != null) {
                "$extractedTitle（副本）"
            } else {
                extractedTitle
            },
            author = staged.suggestedAuthor,
            originalFileName = staged.originalFileName,
            originalPath = stored.originalPath,
            normalizedPath = stored.normalizedPath,
            charsetName = staged.charsetName,
            contentSha256 = staged.contentSha256,
            contentLength = staged.contentLength,
            createdAtEpochMillis = nowEpochMillis(),
            lastOpenedAtEpochMillis = null,
            seriesName = staged.suggestedSeriesName,
            seriesOrder = staged.suggestedSeriesOrder,
        )

        try {
            if (duplicateResolution == DuplicateResolution.REPLACE && existingBook != null) {
                repository.replaceBook(existingBook.id, book)
            } else {
                repository.addBook(book)
            }
        } catch (error: Throwable) {
            fileStore.remove(stored)
            throw error
        }
        if (duplicateResolution == DuplicateResolution.REPLACE && existingBook != null) {
            fileStore.remove(
                StoredBookFiles(
                    originalPath = existingBook.originalPath,
                    normalizedPath = existingBook.normalizedPath,
                ),
            )
            runCatching { anchorRepairCoordinator?.repairBook(existingBook.id) }
        }
        return ReliableImportOutcome.Imported(
            book = book,
            replacedBookId = existingBook?.id.takeIf {
                duplicateResolution == DuplicateResolution.REPLACE
            },
        )
    }

    companion object {
        const val MAX_TXT_BYTES: Long = 50L * 1024L * 1024L
    }
}

interface BookImporter {
    suspend fun import(source: ImportSource, preferredCharsetName: String? = null): Book
}

class DuplicateBookException(
    val contentSha256: String,
    val existingBook: Book,
) : IllegalStateException("该 TXT 已经导入")

enum class DuplicateResolution {
    ASK,
    SKIP,
    REPLACE,
    COPY,
}

sealed interface ReliableImportOutcome {
    data class Imported(
        val book: Book,
        val replacedBookId: String? = null,
    ) : ReliableImportOutcome

    data class Skipped(val existingBook: Book) : ReliableImportOutcome
}
