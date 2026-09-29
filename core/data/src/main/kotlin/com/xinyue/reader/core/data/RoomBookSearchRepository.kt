package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.SearchChunkHit
import com.xinyue.reader.core.database.dao.SearchIndexDao
import com.xinyue.reader.core.domain.model.BookSearchIndexState
import com.xinyue.reader.core.domain.model.BookSearchIndexStatus
import com.xinyue.reader.core.domain.model.BookSearchResult
import com.xinyue.reader.core.domain.repository.BookRepository
import com.xinyue.reader.core.domain.repository.BookSearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RoomBookSearchRepository @Inject constructor(
    private val dao: SearchIndexDao,
    private val books: BookRepository,
    private val scheduler: SearchIndexScheduler,
) : BookSearchRepository {
    override suspend fun ensureIndexed(bookId: String) {
        val book = books.getBook(bookId) ?: return
        val state = dao.getState(bookId)
        if (
            state?.status != SearchIndexStatus.READY ||
            state.contentSha256 != book.contentSha256 ||
            state.indexedUtf16Length != book.contentLength
        ) {
            scheduler.ensure(bookId)
        }
    }

    override fun observeIndexState(bookId: String): Flow<BookSearchIndexState> =
        dao.observeState(bookId).map { state ->
            BookSearchIndexState(
                bookId = bookId,
                status = when (state?.status) {
                    SearchIndexStatus.BUILDING -> BookSearchIndexStatus.BUILDING
                    SearchIndexStatus.READY -> BookSearchIndexStatus.READY
                    SearchIndexStatus.ERROR -> BookSearchIndexStatus.ERROR
                    else -> BookSearchIndexStatus.NOT_INDEXED
                },
                indexedUtf16Length = state?.indexedUtf16Length ?: 0,
                errorMessage = if (state?.status == SearchIndexStatus.ERROR) "建立搜索索引失败" else null,
            )
        }

    override suspend fun search(bookId: String, query: String, limit: Int): List<BookSearchResult> {
        require(limit in 1..500) { "搜索结果数量必须在 1 到 500 之间" }
        val normalized = query.trim()
        require(normalized.codePointCount(0, normalized.length) >= 2) { "请至少输入两个字符" }
        ensureIndexed(bookId)
        val fetchLimit = (limit * 3).coerceAtMost(1_500)
        val hits = if (normalized.codePointCount(0, normalized.length) == 2) {
            dao.searchShort(bookId, "%${normalized.escapeSearchLike()}%", fetchLimit)
        } else {
            dao.searchTrigram(bookId, "\"${normalized.replace("\"", "\"\"")}\"", fetchLimit)
        }
        val unique = linkedMapOf<Pair<Long, Long>, BookSearchResult>()
        for (hit in hits) {
            hit.exactSearchResults(normalized).forEach { result ->
                unique.putIfAbsent(result.offset to result.endOffset, result)
            }
            if (unique.size >= limit) break
        }
        return unique.values.take(limit)
    }

    override suspend fun rebuild(bookId: String) = scheduler.rebuild(bookId)

    override suspend fun cancelIndex(bookId: String) = scheduler.cancel(bookId)
}

internal fun String.escapeSearchLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

internal fun SearchChunkHit.exactSearchResults(query: String): Sequence<BookSearchResult> = sequence {
    var from = 0
    while (from <= content.length - query.length) {
        val localOffset = content.indexOf(query, from)
        if (localOffset < 0) break
        val absoluteStart = startOffset + localOffset
        val snippetStart = (localOffset - 24).coerceAtLeast(0)
        val snippetEnd = (localOffset + query.length + 24).coerceAtMost(content.length)
        val prefix = if (snippetStart > 0) "…" else ""
        val suffix = if (snippetEnd < content.length) "…" else ""
        val raw = content.substring(snippetStart, snippetEnd).replace('\n', ' ')
        yield(
            BookSearchResult(
                offset = absoluteStart,
                endOffset = absoluteStart + query.length,
                chapterStartOffset = chapterStartOffset,
                snippet = prefix + raw + suffix,
                highlightStart = prefix.length + localOffset - snippetStart,
                highlightEnd = prefix.length + localOffset - snippetStart + query.length,
            ),
        )
        from = localOffset + 1
    }
}
