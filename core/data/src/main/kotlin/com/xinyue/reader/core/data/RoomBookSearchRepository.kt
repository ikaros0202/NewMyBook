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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class RoomBookSearchRepository @Inject constructor(
    private val dao: SearchIndexDao,
    private val books: BookRepository,
    private val scheduler: SearchIndexScheduler,
    private val textSource: TextSource,
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
        val book = books.getBook(bookId) ?: return emptyList()
        if (normalized.length > book.contentLength) return emptyList()
        val prefix = normalized.searchCandidatePrefix()
        val unique = linkedMapOf<Pair<Long, Long>, BookSearchResult>()
        var afterRowId = 0L
        while (unique.size < limit) {
            currentCoroutineContext().ensureActive()
            val hits = if (prefix.codePointCount(0, prefix.length) == 2) {
                dao.searchShort(bookId, "%${prefix.escapeSearchLike()}%", 64, afterRowId)
            } else {
                dao.searchTrigram(bookId, "\"${prefix.replace("\"", "\"\"")}\"", 64, afterRowId)
            }
            if (hits.isEmpty()) break
            for (hit in hits) {
                hit.searchIncludingBoundary(normalized, prefix, book.normalizedPath, textSource).forEach { result ->
                    unique.putIfAbsent(result.offset to result.endOffset, result)
                }
                if (unique.size >= limit) break
            }
            afterRowId = hits.last().rowId
        }
        return unique.values.take(limit)
    }

    override suspend fun rebuild(bookId: String) = scheduler.rebuild(bookId)

    override suspend fun cancelIndex(bookId: String) = scheduler.cancel(bookId)
}

// Existing read windows overlap by two UTF-16 units. A BMP trigram, or two code points
// when supplementary characters occur, is guaranteed to survive either index boundary.
internal fun String.searchCandidatePrefix(): String {
    val firstThree = take(offsetByCodePoints(0, minOf(3, codePointCount(0, length))))
    return if (firstThree.length == 3) firstThree else take(offsetByCodePoints(0, 2))
}

internal suspend fun SearchChunkHit.searchIncludingBoundary(
    query: String,
    prefix: String,
    normalizedPath: String,
    textSource: TextSource,
): List<BookSearchResult> {
    val results = exactSearchResults(query).toMutableList()
    val tailStart = (content.length - query.length + 1).coerceAtLeast(0)
    val candidate = content.indexOf(prefix, tailStart)
    if (candidate < 0) return results
    val window = textSource.readWindow(
        normalizedPath, startOffset + candidate, 24,
        (content.length.toLong() - candidate + query.length + 24).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
    results += copy(startOffset = window.startOffset, content = window.text).exactSearchResults(query)
        .filter { it.offset >= startOffset + candidate && it.offset < startOffset + content.length }.toList()
    return results
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
