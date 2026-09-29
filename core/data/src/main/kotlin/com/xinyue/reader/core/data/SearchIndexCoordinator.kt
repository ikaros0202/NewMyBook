package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.SearchIndexDao
import com.xinyue.reader.core.database.entity.SearchChunkEntity
import com.xinyue.reader.core.database.entity.SearchIndexStateEntity
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.time.EpochClock
import com.xinyue.reader.core.text.SearchChunker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class SearchIndexCoordinator @Inject constructor(
    private val books: BookRepository,
    private val textSource: TextSource,
    private val dao: SearchIndexDao,
    private val clock: EpochClock,
) {
    suspend fun build(bookId: String) {
        val book = books.getBook(bookId) ?: return
        val previous = dao.getState(bookId)
        val generationId = UUID.randomUUID().toString()
        dao.beginBuild(
            SearchIndexStateEntity(
                bookId = bookId,
                activeGenerationId = previous?.activeGenerationId,
                buildingGenerationId = generationId,
                status = SearchIndexStatus.BUILDING,
                contentSha256 = book.contentSha256,
                indexedUtf16Length = 0,
                updatedAtEpochMillis = clock.nowEpochMillis(),
                errorMessage = null,
            ),
        )
        try {
            var cursor = 0L
            while (cursor < book.contentLength) {
                val window = textSource.readWindow(
                    normalizedPath = book.normalizedPath,
                    anchorOffset = cursor,
                    beforeUtf16Units = if (cursor == 0L) 0 else WINDOW_OVERLAP_UTF16_UNITS,
                    afterUtf16Units = READ_WINDOW_UTF16_UNITS,
                )
                val batch = ArrayList<SearchChunkEntity>(INSERT_BATCH_SIZE)
                for (chunk in SearchChunker.fromWindow(window.startOffset, window.text)) {
                    batch += SearchChunkEntity(
                        bookId = bookId,
                        generationId = generationId,
                        chapterStartOffset = null,
                        startOffset = chunk.startOffset,
                        content = chunk.content,
                    )
                    if (batch.size == INSERT_BATCH_SIZE) {
                        dao.insertChunks(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) dao.insertChunks(batch)
                val next = window.endOffset.coerceAtMost(book.contentLength)
                check(next > cursor) { "搜索索引读取没有向前推进" }
                cursor = next
                dao.upsertState(
                    SearchIndexStateEntity(
                        bookId = bookId,
                        activeGenerationId = previous?.activeGenerationId,
                        buildingGenerationId = generationId,
                        status = SearchIndexStatus.BUILDING,
                        contentSha256 = book.contentSha256,
                        indexedUtf16Length = cursor,
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                        errorMessage = null,
                    ),
                )
            }
            dao.activate(
                SearchIndexStateEntity(
                    bookId = bookId,
                    activeGenerationId = generationId,
                    buildingGenerationId = null,
                    status = SearchIndexStatus.READY,
                    contentSha256 = book.contentSha256,
                    indexedUtf16Length = book.contentLength,
                    updatedAtEpochMillis = clock.nowEpochMillis(),
                    errorMessage = null,
                ),
                generationId,
            )
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                dao.failBuild(
                    SearchIndexStateEntity(
                        bookId = bookId,
                        activeGenerationId = previous?.activeGenerationId,
                        buildingGenerationId = null,
                        status = SearchIndexStatus.ERROR,
                        contentSha256 = book.contentSha256,
                        indexedUtf16Length = previous?.indexedUtf16Length ?: 0,
                        updatedAtEpochMillis = clock.nowEpochMillis(),
                        errorMessage = if (error is CancellationException) "索引任务已取消" else "建立搜索索引失败",
                    ),
                    generationId,
                )
            }
            throw error
        }
    }

    private companion object {
        const val READ_WINDOW_UTF16_UNITS = 256 * 1024
        const val WINDOW_OVERLAP_UTF16_UNITS = 2
        const val INSERT_BATCH_SIZE = 64
    }
}

internal object SearchIndexStatus {
    const val BUILDING = "BUILDING"
    const val READY = "READY"
    const val ERROR = "ERROR"
}
